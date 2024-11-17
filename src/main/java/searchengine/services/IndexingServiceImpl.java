package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexingresponse.IndexingResponse;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private ForkJoinPool forkJoinPool;
    private final PageRepositories pageRepositories;
    private final SiteRepositories siteRepositories;
    private final SitesList sites;
    private final Object lock = new Object();
    private boolean startIndexing; // = false

    @Override
    public IndexingResponse startIndexing() throws IOException {
        synchronized (lock) {
            if (forkJoinPool != null && !forkJoinPool.isTerminated()) {
                return new IndexingResponse(false, "Индексирование уже выполняется.");
            }
            initThreadPool();
        }
        new Thread(this::startIndexingInternal).start();
        return new IndexingResponse(true);
    }


    private void startIndexingInternal() {
        try {
            saveSitesInDB();
            List<Thread> indexingThreads = new ArrayList<>();
            for (SiteEntity siteEntity : siteRepositories.findAll()) {
                Thread indexingThread = new Thread(() -> indexPage(siteEntity));
                indexingThreads.add(indexingThread);
                indexingThread.start();
            }
            for (Thread thread : indexingThreads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
    }

    private void indexPage(SiteEntity siteEntity) {
        SiteMapService siteMapService = new SiteMapService(siteEntity.getUrl(), siteRepositories, siteEntity);
        forkJoinPool.invoke(siteMapService);
        Set<Page> pages = new CopyOnWriteArraySet<>(siteMapService.getPages());
        Set<PageEntity> pageEntities = pages.stream()
                .filter(page -> page.getPath().startsWith(siteEntity.getUrl()))
                .map(page -> createPage(page, siteEntity))
                .collect(Collectors.toSet());
        processAndSavePages(pageEntities, siteEntity);
    }

    private void processAndSavePages(Set<PageEntity> pageEntities, SiteEntity siteEntity) {
        savePageInDB(pageEntities);
        siteEntity.setStatus(StatusType.INDEXED);
        siteRepositories.save(siteEntity);
    }

    private PageEntity createPage(Page page, SiteEntity siteEntity) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setSiteId(siteEntity);
        pageEntity.setContent(page.getContent());
        pageEntity.setPath(page.getPath());
        pageEntity.setCode(page.getStatusCode());
        return pageEntity;
    }

    private void saveSitesInDB() {
        List<Site> siteList = sites.getSites();
        siteList.forEach(site -> {
            checkDuplicateInDB(site);
            SiteEntity siteEntity = createSite(site);
            siteRepositories.save(siteEntity);
            log.info("Site save in DB: {}", site.getUrl());
        });
    }

    private void savePageInDB(Set<PageEntity> pageEntities) {
        pageRepositories.saveAll(pageEntities);
    }

    private SiteEntity createSite (Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setStatusTime(Date.from(Instant.now()));
        return siteEntity;
    }

    private void checkDuplicateInDB(Site site) {
        if (siteRepositories.findByUrl(site.getUrl()) != null) {
            deleteSite(siteRepositories.deleteByUrl(site.getUrl()));
        }
    }

    private void deleteSite(SiteEntity url) {
        siteRepositories.deleteByUrl(url.getUrl());
    }

    private void initThreadPool() {
        forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    }

}

