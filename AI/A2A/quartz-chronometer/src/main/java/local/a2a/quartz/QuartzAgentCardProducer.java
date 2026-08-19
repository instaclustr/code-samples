package local.a2a.quartz;

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
public class QuartzAgentCardProducer {

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8085")
    int httpPort;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        String baseUrl = "http://localhost:" + httpPort;
        return AgentCard.builder()
                .name("Quartz Chronometer Agent")
                .description(
                        "Clockwork Agent on official A2A JSON-RPC: sync time, SSE countdown, input-required confirm")
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), baseUrl)))
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(Collections.singletonList("text/plain"))
                .defaultOutputModes(Collections.singletonList("text/plain"))
                .skills(List.of(
                        AgentSkill.builder()
                                .id("current-time")
                                .name("Current Time")
                                .description("Returns current time synchronously as a Message")
                                .tags(List.of("time", "sync"))
                                .examples(List.of("What is the current time?"))
                                .build(),
                        AgentSkill.builder()
                                .id("countdown")
                                .name("Countdown Timer")
                                .description("Counts down asynchronously; progress streamed via SSE")
                                .tags(List.of("countdown", "timer"))
                                .examples(List.of("Count down 60 seconds"))
                                .build(),
                        AgentSkill.builder()
                                .id("countdown-confirm")
                                .name("Countdown with Confirmation")
                                .description("Returns input-required first, then streams countdown after confirm")
                                .tags(List.of("countdown", "input-required"))
                                .examples(List.of("Count down 20 seconds with confirm"))
                                .build()))
                .build();
    }
}
