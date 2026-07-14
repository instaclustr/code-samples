package local.a2a.bridge.push;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal Push Notification Service for Phase C: accepts A2A webhook POSTs and logs them.
 * Part 7 will add {@code kafkaProducer.send(...)} here.
 */
public final class PushNotificationReceiver {

    private final List<String> payloads = new CopyOnWriteArrayList<>();
    private final AtomicInteger notificationCount = new AtomicInteger();
    private volatile CountDownLatch completionLatch = new CountDownLatch(1);
    private volatile boolean completed;
    private HttpServer server;

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("A2A_BRIDGE_WEBHOOK_PORT", "8082"));
        PushNotificationReceiver receiver = new PushNotificationReceiver();
        receiver.start(port);
        System.out.println("Push Notification Service listening on http://localhost:" + port + "/a2a/webhook");
        System.out.println("POST A2A push notifications here (Ctrl+C to stop).");
        Thread.currentThread().join();
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/a2a/webhook", this::handleWebhook);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public String webhookUrl(int port) {
        return "http://localhost:" + port + "/a2a/webhook";
    }

    public List<String> payloads() {
        return Collections.unmodifiableList(new ArrayList<>(payloads));
    }

    public int notificationCount() {
        return notificationCount.get();
    }

    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return completionLatch.await(timeout, unit);
    }

    private void handleWebhook(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int count = notificationCount.incrementAndGet();
        payloads.add(body);

        System.out.println();
        System.out.println("=== webhook #" + count + " @ " + Instant.now() + " ===");
        System.out.println(body);

        if (!completed && isTerminalPayload(body)) {
            completed = true;
            completionLatch.countDown();
        }

        sendResponse(exchange, 200, "{\"status\":\"ok\"}");
    }

    static boolean isTerminalPayload(String body) {
        return body.contains("TASK_STATE_COMPLETED")
                || body.contains("\"state\":\"completed\"")
                || body.contains("\"final\":true");
    }

    private static void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
