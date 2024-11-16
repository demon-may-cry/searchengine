package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexingresponse.IndexingResponse;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final PageRepositories pageRepositories;
    private final SiteRepositories siteRepositories;
    private final SitesList sitesList;
    private final IndexingResponse indexingResponse = new IndexingResponse();
    private SiteEntity siteEntity;
    private boolean startIndexing; // = false

    @Override
    public IndexingResponse startIndexing() {
        if (startIndexing) { //если startIndexing = true
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация уже запущена");
            log.error("Indexing is running");
        } else {
            log.info("Indexing run");
            startIndexing = true;
            indexingResponse.setResult(true);
            List<Site> sites = sitesList.getSites();
            sites.forEach((site -> {
                String url = site.getUrl();
                String name = site.getName();
                siteEntity = siteRepositories.findByUrl(url);
                if (siteEntity == null) {
                    siteEntity = new SiteEntity();
                    siteEntity.setUrl(url);
                    siteEntity.setName(name);
                    siteEntity.setStatus(StatusType.INDEXING);
                    siteEntity.setStatusTime(Date.from(Instant.now()));
                    siteRepositories.save(siteEntity);
                    log.info("Save to DB {} -> {}", name, url);
                    new ForkJoinPool().invoke(new SiteMapService(url, siteEntity, pageRepositories));
                } else {
                    log.error("Site is exist in DB: {}", url);
                }
            }));
        }
        return indexingResponse;
    }
}

