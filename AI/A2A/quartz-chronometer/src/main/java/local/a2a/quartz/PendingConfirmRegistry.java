package local.a2a.quartz;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Holds countdown seconds while a confirm-gated task waits in input-required. */
@ApplicationScoped
public final class PendingConfirmRegistry {

    private final Map<String, Integer> pendingSeconds = new ConcurrentHashMap<>();

    public void put(String taskId, int seconds) {
        pendingSeconds.put(taskId, seconds);
    }

    public Integer take(String taskId) {
        return pendingSeconds.remove(taskId);
    }

    public void remove(String taskId) {
        pendingSeconds.remove(taskId);
    }
}
