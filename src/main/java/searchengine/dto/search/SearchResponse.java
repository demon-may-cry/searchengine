package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SearchResponse {
    private boolean result;
    private String error;
    private int count;
    private List<SearchData> data;

    public SearchResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }

    public SearchResponse(boolean result) {
        this.result = result;
    }

    public void setResult(List<SearchData> results) {
        this.count = results.size();
        this.data = results;
    }
}
