package local.kip1034.dlq.news;

/** Per-category payload written into the windowed aggregate (same shape as the classic news demo). */
public class NewsStreamPayload {
    public String articleId;
    public String heading;
    public String category;

    public NewsStreamPayload() {}

    public NewsStreamPayload(String articleId, String heading, String category) {
        this.articleId = articleId;
        this.heading = heading;
        this.category = category;
    }
}
