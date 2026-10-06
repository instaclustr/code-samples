package local.a2a.scenarios.dronesar.a2a.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import local.a2a.scenarios.dronesar.a2a.SarCopilotRecorder;
import local.a2a.scenarios.dronesar.a2a.SarJson;
import local.a2a.scenarios.dronesar.a2a.SarKafkaEventPublisher;
import local.a2a.scenarios.dronesar.a2a.SarMissionEvent;
import local.a2a.scenarios.dronesar.a2a.SarMissionRequest;
import local.a2a.scenarios.dronesar.a2a.SarSafetyAnalystService;
import local.a2a.scenarios.dronesar.a2a.SarSignificantEventDetector;
import local.a2a.scenarios.dronesar.a2a.SarVizExporter;
import local.a2a.scenarios.dronesar.a2a.SarViolationAssessment;
import local.a2a.scenarios.dronesar.model.DroneTelemetry;

/**
 * Phase 2 mission client — official a2a-java SDK over default JSON-RPC transport with SSE streaming.
 */
public final class SarMissionClient {

    public record RunStats(
            String taskId,
            int telemetryArtifacts,
            JsonNode summary,
            boolean successCriteriaMet,
            boolean allTargetsFound,
            Path vizOutDir,
            List<String> narrations) {}

    public record RunOptions(Path vizOutDir, Path replayTemplate) {}

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv().getOrDefault("SAR_A2A_AGENT_URL", "http://localhost:8083");
        Path missionPath = Path.of(System.getenv()
                        .getOrDefault("SAR_MISSION_PATH", "../../missions/test-mission-fast.json"))
                .toAbsolutePath()
                .normalize();
        int maxTicks = Integer.parseInt(System.getenv().getOrDefault("SAR_MAX_TICKS", "500"));
        Path vizOut = resolveVizOut();

        SarMissionRequest request = new SarMissionRequest(missionPath, maxTicks, false);
        RunStats stats = runMission(baseUrl, request, new RunOptions(vizOut, null), event -> {
            if (event.startsWith("status")
                    || event.startsWith("narrator:")
                    || event.startsWith("kafka=")
                    || event.startsWith("copilot=")) {
                System.out.println(event);
            }
        });

        System.out.println("\n=== SAR A2A mission complete ===");
        System.out.println("taskId=" + stats.taskId());
        System.out.println("telemetryArtifacts=" + stats.telemetryArtifacts());
        System.out.println("allTargetsFound=" + stats.allTargetsFound());
        System.out.println("successCriteriaMet=" + stats.successCriteriaMet());
        if (stats.summary() != null) {
            System.out.println("summary=" + SarJson.mapper().writeValueAsString(stats.summary()));
        }
        if (!stats.narrations().isEmpty()) {
            System.out.println("\n=== Mission narrator briefings ===");
            for (int i = 0; i < stats.narrations().size(); i++) {
                System.out.println((i + 1) + ". " + stats.narrations().get(i));
            }
        }
        if (stats.vizOutDir() != null) {
            System.out.println("--- viz artifacts ---");
            System.out.println("replay.json=" + stats.vizOutDir().resolve("replay.json").toAbsolutePath());
            System.out.println("snapshot.svg=" + stats.vizOutDir().resolve("snapshot.svg").toAbsolutePath());
            System.out.println("snapshot.png=" + stats.vizOutDir().resolve("snapshot.png").toAbsolutePath());
            System.out.println("replay.html=" + stats.vizOutDir().resolve("replay.html").toAbsolutePath());
            System.out.println("Open replay: cd " + stats.vizOutDir().toAbsolutePath()
                    + " && python3 -m http.server 8768");
        }
        boolean demoViolations = readDemoViolations(missionPath);
        if (demoViolations && !stats.successCriteriaMet()) {
            System.out.println("\nNote: successCriteriaMet=false is expected for demo violation missions.");
        }
        int exitCode = resolveExitCode(stats, demoViolations);
        System.exit(exitCode);
    }

    static int resolveExitCode(RunStats stats, boolean demoViolations) {
        if (demoViolations) {
            return stats.summary() != null ? 0 : 1;
        }
        return stats.successCriteriaMet() ? 0 : 1;
    }

    static boolean readDemoViolations(Path missionPath) {
        try {
            JsonNode node = SarJson.mapper().readTree(missionPath.toFile());
            return node.path("demoViolations").asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    static Path resolveVizOut() {
        String raw = System.getenv("SAR_VIZ_OUT");
        if (raw == null || raw.isBlank()) {
            return Path.of("../../viz/out/sar-a2a").toAbsolutePath().normalize();
        }
        if ("none".equalsIgnoreCase(raw.trim()) || "off".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return Path.of(raw).toAbsolutePath().normalize();
    }

    public static RunStats runMission(String baseUrl, SarMissionRequest request, Consumer<String> log)
            throws Exception {
        return runMission(baseUrl, request, new RunOptions(resolveVizOut(), null), log);
    }

    public static RunStats runMission(
            String baseUrl, SarMissionRequest request, RunOptions options, Consumer<String> log)
            throws Exception {
        AgentCard card = A2A.getAgentCard(baseUrl);
        log.accept("agent=" + card.name() + " protocol=JSON-RPC streaming=" + card.capabilities().streaming());

        String missionId = readMissionId(request.missionPath());
        double rtbBatteryPct = readRtbBatteryPct(request.missionPath());
        String rulesetId = readRulesetId(request.missionPath());
        SarMissionNarratorClient narrator = SarMissionNarratorClient.fromEnv();
        if (narrator != null) {
            log.accept("narrator=enabled url=" + System.getenv("SAR_NARRATOR_URL"));
        }
        SarSignificantEventDetector eventDetector = new SarSignificantEventDetector().configure(rtbBatteryPct);
        SarKafkaEventPublisher kafkaPublisher = SarKafkaEventPublisher.fromEnv(rulesetId);
        SarCopilotRecorder copilotRecorder = SarCopilotRecorder.enabled() ? new SarCopilotRecorder() : null;
        SarSafetyAnalystService safetyAnalyst = copilotRecorder != null ? SarSafetyAnalystService.fromEnv() : null;
        ExecutorService safetyExecutor = copilotRecorder != null
                ? Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "sar-copilot-safety");
                    t.setDaemon(true);
                    return t;
                })
                : null;
        if (copilotRecorder != null) {
            log.accept("copilot=enabled replay panels=narrator,safety-analyst");
        }
        if (kafkaPublisher != null) {
            log.accept("kafka=enabled bootstrap=" + System.getenv("SAR_KAFKA_BOOTSTRAP")
                    + " topic=" + System.getenv().getOrDefault(
                            "SAR_KAFKA_EVENTS_TOPIC", SarKafkaEventPublisher.DEFAULT_EVENTS_TOPIC));
        }

        try {
            return runMissionInternal(
                    card,
                    request,
                    options,
                    log,
                    missionId,
                    rulesetId,
                    narrator,
                    eventDetector,
                    kafkaPublisher,
                    copilotRecorder,
                    safetyAnalyst,
                    safetyExecutor);
        } finally {
            if (safetyExecutor != null && !safetyExecutor.isShutdown()) {
                safetyExecutor.shutdown();
                try {
                    if (!safetyExecutor.awaitTermination(3, TimeUnit.MINUTES)) {
                        safetyExecutor.shutdownNow();
                    }
                } catch (InterruptedException ex) {
                    safetyExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            if (kafkaPublisher != null) {
                kafkaPublisher.close();
            }
        }
    }

    private static RunStats runMissionInternal(
            AgentCard card,
            SarMissionRequest request,
            RunOptions options,
            Consumer<String> log,
            String missionId,
            String rulesetId,
            SarMissionNarratorClient narrator,
            SarSignificantEventDetector eventDetector,
            SarKafkaEventPublisher kafkaPublisher,
            SarCopilotRecorder copilotRecorder,
            SarSafetyAnalystService safetyAnalyst,
            ExecutorService safetyExecutor)
            throws Exception {

        ClientConfig clientConfig = ClientConfig.builder()
                .setStreaming(true)
                .setPolling(false)
                .setAcceptedOutputModes(List.of("application/json", "text/plain"))
                .build();

        AtomicReference<String> taskIdRef = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicInteger telemetryCount = new AtomicInteger();
        AtomicReference<JsonNode> summaryRef = new AtomicReference<>();
        List<DroneTelemetry> telemetry = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(1);

        try (Client client = Client.builder(card)
                .clientConfig(clientConfig)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .addConsumer((event, agentCard) -> handleEvent(
                        event,
                        taskIdRef,
                        completed,
                        telemetryCount,
                        summaryRef,
                        telemetry,
                        done,
                        missionId,
                        rulesetId,
                        eventDetector,
                        narrator,
                        kafkaPublisher,
                        copilotRecorder,
                        safetyAnalyst,
                        safetyExecutor,
                        log))
                .build()) {

            client.sendMessage(A2A.toUserMessage(request.formatForClient()));
            if (!done.await(5, TimeUnit.MINUTES)) {
                throw new IllegalStateException("Timed out waiting for SAR mission task completion");
            }
        }

        JsonNode summary = summaryRef.get();
        boolean success = summary != null && summary.path("successCriteriaMet").asBoolean(false);
        boolean found = summary != null && summary.path("allTargetsFound").asBoolean(false);

        if (narrator != null && summary != null) {
            try {
                SarJson.MissionSummary missionSummary = SarJson.parseSummary(summary);
                narrator.narrateSummaryAsync(missionId, missionSummary, log);
                narrator.awaitIdle();
                if (copilotRecorder != null) {
                    copilotRecorder.addNarrations(narrator.narrationEntries());
                }
                if (kafkaPublisher != null) {
                    kafkaPublisher.publishSummary(missionId, missionSummary);
                    log.accept("kafka=published MISSION_SAFETY_SUMMARY");
                }
                assessSummaryForCopilot(
                        missionId, rulesetId, missionSummary, copilotRecorder, safetyAnalyst, safetyExecutor, log);
            } catch (Exception ex) {
                log.accept("narrator: failed awaiting summary — " + ex.getMessage());
            }
        } else if (summary != null) {
            SarJson.MissionSummary missionSummary = SarJson.parseSummary(summary);
            if (kafkaPublisher != null) {
                kafkaPublisher.publishSummary(missionId, missionSummary);
                log.accept("kafka=published MISSION_SAFETY_SUMMARY");
            }
            assessSummaryForCopilot(
                    missionId, rulesetId, missionSummary, copilotRecorder, safetyAnalyst, safetyExecutor, log);
        }

        awaitSafetyCopilot(safetyExecutor);

        Path vizWritten = null;
        if (options != null && options.vizOutDir() != null && summary != null && !telemetry.isEmpty()) {
            SarJson.MissionSummary missionSummary = SarJson.parseSummary(summary);
            SarVizExporter.write(
                    options.vizOutDir(),
                    options.replayTemplate(),
                    request,
                    List.copyOf(telemetry),
                    missionSummary,
                    copilotRecorder != null ? copilotRecorder.toReplayCopilot() : null);
            vizWritten = options.vizOutDir();
            log.accept("viz=written dir=" + vizWritten);
        }

        List<String> narrations = narrator != null ? narrator.narrations() : List.of();
        return new RunStats(taskIdRef.get(), telemetryCount.get(), summary, success, found, vizWritten, narrations);
    }

    static void handleEvent(
            ClientEvent event,
            AtomicReference<String> taskIdRef,
            AtomicBoolean completed,
            AtomicInteger telemetryCount,
            AtomicReference<JsonNode> summaryRef,
            List<DroneTelemetry> telemetry,
            CountDownLatch done,
            String missionId,
            String rulesetId,
            SarSignificantEventDetector eventDetector,
            SarMissionNarratorClient narrator,
            SarKafkaEventPublisher kafkaPublisher,
            SarCopilotRecorder copilotRecorder,
            SarSafetyAnalystService safetyAnalyst,
            ExecutorService safetyExecutor,
            Consumer<String> log) {
        if (event instanceof TaskEvent taskEvent) {
            Task task = taskEvent.getTask();
            if (task == null) {
                return;
            }
            if (task.id() != null) {
                taskIdRef.compareAndSet(null, task.id());
                log.accept("taskCreated id=" + task.id());
            }
            logStatus("Task", task.status().state().name(), extractText(task.status().message()), log);
            if (task.status().state().isFinal() && completed.compareAndSet(false, true)) {
                captureSummary(task, summaryRef);
                done.countDown();
            }
            return;
        }

        if (event instanceof TaskUpdateEvent taskUpdateEvent) {
            UpdateEvent update = taskUpdateEvent.getUpdateEvent();
            if (update instanceof TaskStatusUpdateEvent statusUpdate) {
                logStatus(
                        "statusUpdate",
                        statusUpdate.status().state().name(),
                        extractText(statusUpdate.status().message()),
                        log);
                if (statusUpdate.isFinal() && completed.compareAndSet(false, true)) {
                    Task task = taskUpdateEvent.getTask();
                    if (task != null) {
                        captureSummary(task, summaryRef);
                    }
                    done.countDown();
                }
            } else if (update instanceof TaskArtifactUpdateEvent artifactUpdate) {
                handleArtifact(
                        artifactUpdate.artifact(),
                        telemetryCount,
                        summaryRef,
                        telemetry,
                        missionId,
                        rulesetId,
                        eventDetector,
                        narrator,
                        kafkaPublisher,
                        copilotRecorder,
                        safetyAnalyst,
                        safetyExecutor,
                        log);
            }
        }
    }

    private static void handleArtifact(
            Artifact artifact,
            AtomicInteger telemetryCount,
            AtomicReference<JsonNode> summaryRef,
            List<DroneTelemetry> telemetry,
            String missionId,
            String rulesetId,
            SarSignificantEventDetector eventDetector,
            SarMissionNarratorClient narrator,
            SarKafkaEventPublisher kafkaPublisher,
            SarCopilotRecorder copilotRecorder,
            SarSafetyAnalystService safetyAnalyst,
            ExecutorService safetyExecutor,
            Consumer<String> log) {
        if (artifact == null || artifact.parts() == null) {
            return;
        }
        String text = extractArtifactText(artifact);
        if (text.isBlank()) {
            return;
        }
        try {
            JsonNode node = SarJson.mapper().readTree(text);
            if (node.has("ticksRun")) {
                summaryRef.set(node);
                log.accept("artifact=summary ticks=" + node.path("ticksRun").asInt());
            } else if (node.has("droneId")) {
                DroneTelemetry frame = SarJson.parseTelemetry(text);
                telemetry.add(frame);
                int count = telemetryCount.incrementAndGet();
                if (count <= 3 || count % 50 == 0) {
                    log.accept("artifact=telemetry #" + count + " tick=" + node.path("tick").asInt()
                            + " drone=" + node.path("droneId").asText());
                }
                if (narrator != null || kafkaPublisher != null || copilotRecorder != null) {
                    for (SarMissionEvent missionEvent : eventDetector.detect(frame, missionId)) {
                        if (narrator != null) {
                            narrator.narrateEventAsync(missionEvent, log);
                        }
                        if (kafkaPublisher != null && SarSignificantEventDetector.isSafetyEvent(missionEvent.type())) {
                            kafkaPublisher.publishEvent(missionEvent);
                            log.accept("kafka=published " + missionEvent.type() + " tick=" + missionEvent.tick()
                                    + " drone=" + missionEvent.droneId());
                        }
                        if (copilotRecorder != null
                                && safetyAnalyst != null
                                && safetyExecutor != null
                                && SarSignificantEventDetector.isSafetyEvent(missionEvent.type())) {
                            safetyExecutor.submit(() -> {
                                SarViolationAssessment assessment =
                                        safetyAnalyst.assessEvent(rulesetId, missionEvent);
                                copilotRecorder.addViolation(assessment);
                                log.accept("copilot=safety " + assessment.severity() + " tick=" + assessment.tick()
                                        + " " + assessment.summary());
                            });
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            log.accept("artifact=text " + text.substring(0, Math.min(80, text.length())));
        }
    }

    private static void assessSummaryForCopilot(
            String missionId,
            String rulesetId,
            SarJson.MissionSummary missionSummary,
            SarCopilotRecorder copilotRecorder,
            SarSafetyAnalystService safetyAnalyst,
            ExecutorService safetyExecutor,
            Consumer<String> log) {
        if (copilotRecorder == null || safetyAnalyst == null || safetyExecutor == null) {
            return;
        }
        safetyExecutor.submit(() -> {
            SarViolationAssessment assessment = safetyAnalyst.assessSummary(rulesetId, missionId, missionSummary);
            copilotRecorder.addViolation(assessment);
            log.accept("copilot=safety-summary " + assessment.severity() + " " + assessment.summary());
        });
    }

    private static void awaitSafetyCopilot(ExecutorService safetyExecutor) throws InterruptedException {
        if (safetyExecutor == null) {
            return;
        }
        safetyExecutor.shutdown();
        if (!safetyExecutor.awaitTermination(3, TimeUnit.MINUTES)) {
            safetyExecutor.shutdownNow();
        }
    }

    static String readRulesetId(Path missionPath) {
        try {
            JsonNode node = SarJson.mapper().readTree(missionPath.toFile());
            String rulesetId = node.path("rulesetId").asText(null);
            if (rulesetId != null && !rulesetId.isBlank()) {
                return rulesetId;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "airspace-v1";
    }

    static double readRtbBatteryPct(Path missionPath) {
        try {
            JsonNode node = SarJson.mapper().readTree(missionPath.toFile());
            JsonNode rules = node.path("rules");
            if (rules.has("rtbBatteryPct")) {
                return rules.path("rtbBatteryPct").asDouble(30.0);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return 30.0;
    }

    static String readMissionId(Path missionPath) {
        try {
            JsonNode node = SarJson.mapper().readTree(missionPath.toFile());
            String missionId = node.path("missionId").asText(null);
            if (missionId != null && !missionId.isBlank()) {
                return missionId;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return missionPath.getFileName().toString();
    }

    private static void captureSummary(Task task, AtomicReference<JsonNode> summaryRef) {
        if (task.artifacts() == null) {
            return;
        }
        for (Artifact artifact : task.artifacts()) {
            try {
                JsonNode node = SarJson.mapper().readTree(extractArtifactText(artifact));
                if (node.has("ticksRun")) {
                    summaryRef.set(node);
                }
            } catch (Exception ignored) {
                // skip non-json artifacts
            }
        }
    }

    private static void logStatus(String label, String state, String text, Consumer<String> log) {
        log.accept("status label=" + label + " state=" + state + " text=" + text);
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
        if (artifact.parts() == null) {
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

    private SarMissionClient() {}
}
