package local.a2a.scenarios.dronesar.a2a.agent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.Collections;
import java.util.List;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import local.a2a.scenarios.dronesar.a2a.SarNarratorRequest;

@ApplicationScoped
public class SarNarratorAgentCardProducer {

    public static final String SKILL_ID = "sar:mission-narrator";

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8084")
    int httpPort;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        String baseUrl = "http://localhost:" + httpPort;
        return AgentCard.builder()
                .name("SAR Mission Narrator")
                .description("Phase 2 drone SAR copilot — narrates significant mission events via local Ollama LLM")
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), baseUrl)))
                .version("0.1.0")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(Collections.singletonList("text/plain"))
                .defaultOutputModes(Collections.singletonList("text/plain"))
                .skills(List.of(AgentSkill.builder()
                        .id(SKILL_ID)
                        .name("SAR mission narrator")
                        .description("Accept narrate:event or narrate:summary messages; return operator briefing text")
                        .tags(List.of("sar", "narrator", "llm", "ollama"))
                        .examples(List.of(
                                SarNarratorRequest.PREFIX_EVENT
                                        + "\n"
                                        + "{\"type\":\"TARGET_FOUND\",\"tick\":20,\"missionId\":\"sar-multi-001\","
                                        + "\"targetId\":\"t1\",\"droneId\":\"d-02\"}",
                                SarNarratorRequest.PREFIX_SUMMARY + "\n{\"missionId\":\"sar-multi-001\"}"))
                        .build()))
                .build();
    }
}
