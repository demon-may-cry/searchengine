package searchengine.services;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.JsoupConfig;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionService {
    private final JsoupConfig jsoupConfig;

    public Document connection(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(jsoupConfig.getUserAgent())
                .referrer(jsoupConfig.getReferrer())
                .ignoreContentType(jsoupConfig.isIgnoreContentType())
                .get();
    }

    public String getContent(String url) throws IOException {
        return Jsoup.connect(url)
                .ignoreContentType(jsoupConfig.isIgnoreContentType())
                .execute()
                .body();
    }

    public Integer getStatusCode(String url) throws IOException {
        return Jsoup.connect(url)
                .execute()
                .statusCode();
    }
}
