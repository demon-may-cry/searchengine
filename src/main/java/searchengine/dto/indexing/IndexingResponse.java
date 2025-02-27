package searchengine.dto.indexing;

import lombok.Data;

@Data
public class IndexingResponse {
    private boolean result;
    private String error;

    public IndexingResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }

    public IndexingResponse(boolean result) {
        this.result = result;
    }
}
