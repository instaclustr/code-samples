package local.a2a.scenarios.dronesar.a2a.agent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.List;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import local.a2a.scenarios.dronesar.a2a.OllamaClient;
import local.a2a.scenarios.dronesar.a2a.SarNarratorRequest;
import local.a2a.scenarios.dronesar.a2a.SarNarratorService;

@ApplicationScoped
public class SarNarratorAgentExecutorProducer {

    private static final String NARRATIVE_ARTIFACT = "sar-narrative";

    @ConfigProperty(name = "sar.ollama.url", defaultValue = "http://localhost:11434")
    String ollamaUrl;

    @ConfigProperty(name = "sar.ollama.model", defaultValue = "llama3:latest")
    String ollamaModel;

    @ConfigProperty(name = "sar.ollama.enabled", defaultValue = "true")
    boolean ollamaEnabled;

    @Produces
    public AgentExecutor agentExecutor() {
        SarNarratorService service =
                new SarNarratorService(new OllamaClient(ollamaUrl, ollamaModel), ollamaEnabled);
        return new SarNarratorAgentExecutor(service);
    }

    static final class SarNarratorAgentExecutor implements AgentExecutor {

        private final SarNarratorService service;

        SarNarratorAgentExecutor(SarNarratorService service) {
            this.service = service;
        }

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            String text = context.getUserInput();
            if (text == null || text.isBlank()) {
                emitter.sendMessage(agentMessage(
                        "Send a narrator request, e.g.:\n"
                                + SarNarratorRequest.PREFIX_EVENT
                                + "\n"
                                + "{\"type\":\"TARGET_FOUND\",\"tick\":20,\"missionId\":\"demo\","
                                + "\"targetId\":\"t1\",\"droneId\":\"d-02\"}"));
                return;
            }

            SarNarratorRequest request;
            try {
                request = SarNarratorRequest.parse(text);
            } catch (Exception ex) {
                emitter.sendMessage(agentMessage("Invalid narrator request: " + ex.getMessage()));
                return;
            }

            try {
                String narrative = switch (request.kind()) {
                    case EVENT -> service.narrateEvent(request.event());
                    case SUMMARY -> service.narrateSummary(request.summaryPayload());
                };
                emitter.startWork(agentMessage("Generating SAR briefing…"));
                emitter.addArtifact(
                        List.of(new TextPart(narrative)), NARRATIVE_ARTIFACT, null, null, false, false);
                emitter.complete(agentMessage(narrative));
            } catch (Exception ex) {
                emitter.fail(agentMessage("Narrator failed: " + ex.getMessage()));
            }
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
            emitter.cancel(agentMessage("Narrator task canceled."));
        }

        private static Message agentMessage(String text) {
            return Message.builder()
                    .role(Message.Role.ROLE_AGENT)
                    .parts(List.of(new TextPart(text)))
                    .build();
        }
    }
}
