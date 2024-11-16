package searchengine.services;

import searchengine.dto.indexingresponse.IndexingResponse;

import java.io.IOException;

public interface IndexingService {
    IndexingResponse startIndexing() throws IOException;
}
