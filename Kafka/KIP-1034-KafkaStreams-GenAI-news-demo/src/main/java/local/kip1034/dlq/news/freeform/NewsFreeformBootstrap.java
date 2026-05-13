package local.kip1034.dlq.news.freeform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;

public final class NewsFreeformBootstrap {

    public static void createTopics(String bootstrap) throws Exception {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        cfg.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        try (AdminClient admin = AdminClient.create(cfg)) {
            List<NewTopic> topics =
                    List.of(
                            new NewTopic(NewsFreeformTopics.RAW, 1, (short) 1),
                            new NewTopic(NewsFreeformTopics.REPAIRED, 1, (short) 1),
                            new NewTopic(NewsFreeformTopics.STREAMS_DLQ, 1, (short) 1),
                            new NewTopic(NewsFreeformTopics.APP_DLQ, 1, (short) 1),
                            new NewTopic(NewsFreeformTopics.WINDOWED, 1, (short) 1));
            try {
                admin.createTopics(topics).all().get();
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw e;
                }
            }
        }
    }

    private NewsFreeformBootstrap() {}
}
