package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

import java.io.IOException;
import java.net.MalformedURLException;

public interface IndexingService {
    IndexingResponse startIndexing() throws IOException;

    IndexingResponse stopIndexing();

    IndexingResponse indexPage(String page) throws MalformedURLException;
}
