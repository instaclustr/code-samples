package local.a2a.bridge;

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

@ApplicationScoped
public class BridgeAgentCardProducer {

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8081")
    int httpPort;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        String baseUrl = "http://localhost:" + httpPort;
        return AgentCard.builder()
                .name("Bridge Countdown Agent")
                .description("Part 6 bridge agent: async countdown via official a2a-java SDK (poll, SSE, push)")
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), baseUrl)))
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(true)
                        .build())
                .defaultInputModes(Collections.singletonList("text/plain"))
                .defaultOutputModes(Collections.singletonList("text/plain"))
                .skills(Collections.singletonList(AgentSkill.builder()
                        .id("countdown")
                        .name("Countdown Timer")
                        .description("Counts down asynchronously; client tracks via GetTask poll, SSE, or push webhook")
                        .tags(List.of("countdown", "timer"))
                        .examples(List.of("Count down 60 seconds"))
                        .build()))
                .build();
    }
}
