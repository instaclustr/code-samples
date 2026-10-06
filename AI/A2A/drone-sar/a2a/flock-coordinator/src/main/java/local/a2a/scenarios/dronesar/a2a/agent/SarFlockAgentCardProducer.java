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
import local.a2a.scenarios.dronesar.a2a.SarMissionRequest;

@ApplicationScoped
public class SarFlockAgentCardProducer {

    public static final String SKILL_ID = "sar:flock-coordinator";

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8083")
    int httpPort;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        String baseUrl = "http://localhost:" + httpPort;
        return AgentCard.builder()
                .name("SAR Flock Coordinator")
                .description("Phase 2 drone SAR — runs grid sim and streams ~1 Hz drone telemetry via A2A JSON-RPC")
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), baseUrl)))
                .version("0.1.0")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(Collections.singletonList("text/plain"))
                .defaultOutputModes(Collections.singletonList("application/json"))
                .skills(List.of(AgentSkill.builder()
                        .id(SKILL_ID)
                        .name("SAR flock coordinator")
                        .description("Accept mission:search-rescue messages; stream drone telemetry; return mission summary")
                        .tags(List.of("sar", "drone", "search-rescue", "flock"))
                        .examples(List.of(
                                SarMissionRequest.PREFIX
                                        + "\n../missions/test-mission-fast.json\nmaxTicks=500"))
                        .build()))
                .build();
    }
}
