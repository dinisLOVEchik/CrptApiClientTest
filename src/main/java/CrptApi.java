import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.concurrent.ScheduledExecutorService;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final int interval;
    private final Semaphore semaphore;
    private final AtomicInteger requestCount;
    private final ScheduledExecutorService executorService;

    public CrptApi(TimeUnit timeUnit, int requestLimit, int interval) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.interval = interval;
        this.semaphore = new Semaphore(requestLimit);
        this.requestCount = new AtomicInteger(0);
        this.executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public void createDocument(JSONObject document, String signature) throws IOException {
        if (!semaphore.tryAcquire()) {
            throw new RuntimeException("Request limit exceeded");
        }
        executorService.schedule(() -> semaphore.release(), this.interval, TimeUnit.SECONDS);

        Runnable task = () -> {
            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, document.toString());
            Request request = new Request.Builder()
                    .url("https://ismp.crpt.ru/api/v3/lk/documents/create")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Signature", signature)
                    .build();
            try {
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("API request failed: " + response.body().string());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        executorService.submit(task);
    }
}