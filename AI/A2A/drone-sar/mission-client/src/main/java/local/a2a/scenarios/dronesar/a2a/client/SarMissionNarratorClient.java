package local.a2a.scenarios.dronesar.a2a.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.UpdateEvent;
import local.a2a.scenarios.dronesar.a2a.SarJson;
import local.a2a.scenarios.dronesar.a2a.SarMissionEvent;
import local.a2a.scenarios.dronesar.a2a.SarNarrationEntry;
import local.a2a.scenarios.dronesar.a2a.SarNarratorRequest;

/** Async A2A client for the mission narrator agent (Option A — separate agent). */
public final class SarMissionNarratorClient implements AutoCloseable {

    private final String narratorUrl;
    private final ExecutorService executor;
    private final List<String> narrations = Collections.synchronizedList(new ArrayList<>());
    private final List<SarNarrationEntry> narrationEntries = Collections.synchronizedList(new ArrayList<>());
    private SarMissionEvent pendingEvent;
    private String pendingSummaryMissionId;
    private long pendingSummaryTick;

    public SarMissionNarratorClient(String narratorUrl) {
        this.narratorUrl = narratorUrl;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sar-narrator-client");
            t.setDaemon(true);
            return t;
        });
    }

    public static SarMissionNarratorClient fromEnv() {
        String raw = System.getenv("SAR_NARRATOR_URL");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if ("none".equalsIgnoreCase(trimmed) || "off".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return new SarMissionNarratorClient(trimmed);
    }

    public List<String> narrations() {
        return List.copyOf(narrations);
    }

    public List<SarNarrationEntry> narrationEntries() {
        return List.copyOf(narrationEntries);
    }

    public void narrateEventAsync(SarMissionEvent event, Consumer<String> log) {
        executor.submit(() -> {
            pendingEvent = event;
            pendingSummaryMissionId = null;
            narrate(SarNarratorRequest.formatEventSafe(event), log);
        });
    }

    public void narrateSummaryAsync(String missionId, SarJson.MissionSummary summary, Consumer<String> log) {
        executor.submit(() -> {
            pendingEvent = null;
            pendingSummaryMissionId = missionId;
            pendingSummaryTick = summary.ticksRun();
            try {
                narrate(SarNarratorRequest.formatSummary(missionId, summary), log);
            } catch (Exception ex) {
                log.accept("narrator: failed summary — " + ex.getMessage());
            }
        });
    }

    public void awaitIdle() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(3, TimeUnit.MINUTES)) {
            executor.shutdownNow();
        }
    }

    @Override
    public void close() throws InterruptedException {
        awaitIdle();
    }

    private void narrate(String message, Consumer<String> log) {
        try {
            String text = callNarrator(message);
            narrations.add(text);
            SarMissionEvent event = pendingEvent;
            if (event != null) {
                narrationEntries.add(new SarNarrationEntry(event.tick(), event.type(), text));
            } else if (pendingSummaryMissionId != null) {
                narrationEntries.add(new SarNarrationEntry(pendingSummaryTick, "MISSION_SUMMARY", text));
            }
            log.accept("narrator: " + text);
        } catch (Exception ex) {
            log.accept("narrator: failed — " + ex.getMessage());
        }
    }

    private String callNarrator(String message) throws Exception {
        AgentCard card = A2A.getAgentCard(narratorUrl);
        ClientConfig clientConfig = ClientConfig.builder()
                .setStreaming(true)
                .setPolling(false)
                .setAcceptedOutputModes(List.of("text/plain", "application/json"))
                .build();

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> narrative = new AtomicReference<>("");

        try (Client client = Client.builder(card)
                .clientConfig(clientConfig)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .addConsumer((event, agentCard) -> captureNarrative(event, narrative, done))
                .build()) {
            client.sendMessage(A2A.toUserMessage(message));
            if (!done.await(2, TimeUnit.MINUTES)) {
                throw new IllegalStateException("Timed out waiting for narrator task");
            }
        }
        String text = narrative.get();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Narrator returned empty text");
        }
        return text.trim();
    }

    private static void captureNarrative(ClientEvent event, AtomicReference<String> narrative, CountDownLatch done) {
        if (event instanceof TaskEvent taskEvent) {
            Task task = taskEvent.getTask();
            if (task != null && task.status().state().isFinal()) {
                narrative.compareAndSet("", extractText(task.status().message()));
                captureArtifacts(task, narrative);
                done.countDown();
            }
            return;
        }
        if (event instanceof TaskUpdateEvent taskUpdateEvent) {
            UpdateEvent update = taskUpdateEvent.getUpdateEvent();
            if (update instanceof TaskStatusUpdateEvent statusUpdate) {
                if (statusUpdate.isFinal()) {
                    narrative.compareAndSet("", extractText(statusUpdate.status().message()));
                    Task task = taskUpdateEvent.getTask();
                    if (task != null) {
                        captureArtifacts(task, narrative);
                    }
                    done.countDown();
                }
            } else if (update instanceof TaskArtifactUpdateEvent artifactUpdate) {
                captureArtifact(artifactUpdate.artifact(), narrative);
            }
        }
    }

    private static void captureArtifacts(Task task, AtomicReference<String> narrative) {
        if (task.artifacts() == null) {
            return;
        }
        for (Artifact artifact : task.artifacts()) {
            captureArtifact(artifact, narrative);
        }
    }

    private static void captureArtifact(Artifact artifact, AtomicReference<String> narrative) {
        String text = extractArtifactText(artifact);
        if (!text.isBlank()) {
            narrative.set(text.trim());
        }
    }

    private static String extractText(Message message) {
        if (message == null || message.parts() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                text.append(textPart.text());
            }
        }
        return text.toString();
    }

    private static String extractArtifactText(Artifact artifact) {
        if (artifact == null || artifact.parts() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Part<?> part : artifact.parts()) {
            if (part instanceof TextPart textPart) {
                text.append(textPart.text());
            }
        }
        return text.toString();
    }
}
