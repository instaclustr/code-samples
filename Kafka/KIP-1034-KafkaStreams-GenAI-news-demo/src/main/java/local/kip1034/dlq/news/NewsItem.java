package local.kip1034.dlq.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsItem {

    public String articleId;
    public String heading;
    public String article;
    public List<String> categories = new ArrayList<>();

    public boolean hasAnyCategory() {
        if (categories == null || categories.isEmpty()) {
            return false;
        }
        return categories.stream().anyMatch(c -> c != null && !c.isBlank());
    }
}
