package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

import java.io.IOException;

public interface IndexingService {
    IndexingResponse startIndexing() throws IOException;

    IndexingResponse stopIndexing();
}
