package local.kip1034.dlq.news.freeform;

/** Topics for the free-form input + KIP-1034 deserialization DLQ news demo. */
public final class NewsFreeformTopics {

    /** Raw values are UTF-8 text: valid {@link local.kip1034.dlq.news.NewsItem} JSON or arbitrary prose. */
    public static final String RAW = "demo-news-ff-raw";

    public static final String REPAIRED = "demo-news-ff-repaired";

    /** KIP-1034 Streams DLQ: raw key/value bytes + {@code __streams.errors.*} headers. */
    public static final String STREAMS_DLQ = "demo-news-ff-streams-dlq";

    /** Application DLQ: JSON envelopes for {@code MISSING_CATEGORIES} (post-deserialization). */
    public static final String APP_DLQ = "demo-news-ff-app-dlq";

    public static final String WINDOWED = "demo-news-ff-by-category-minute";

    private NewsFreeformTopics() {}
}
