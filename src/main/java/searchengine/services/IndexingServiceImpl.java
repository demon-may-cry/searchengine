package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.entity.Page;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.parsing.SiteMap;

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

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SitesList sites;
    private final Object lock = new Object();
    private ForkJoinPool forkJoinPool;
    private List<Thread> indexingThreads;

    @Override
    public IndexingResponse startIndexing() {
        synchronized (lock) {
            if (forkJoinPool != null && !forkJoinPool.isTerminated()) {
                return new IndexingResponse(false, "Индексация уже запущена");
            }
            initThreadPool();
        }
        new Thread(this::indexSite).start();
        return new IndexingResponse(true);
    }

    @Override
    public IndexingResponse stopIndexing() {
        try {
            if (forkJoinPool == null) {
                log.info("Indexing is not running");
                return new IndexingResponse(false, "Индексация не запущена");
            }
            indexingThreads.forEach(Thread::interrupt);
            forkJoinPool.shutdownNow();
            log.info("Indexing stopped");
            return new IndexingResponse(true);
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return new IndexingResponse(false, "Ошибка при остановке индексации");
        }
    }

    private void indexSite() {
        try {
            saveSitesInDB();
            indexingThreads = new ArrayList<>();
            for (SiteEntity siteEntity : siteRepository.findAll()) {
                Thread indexingThread = new Thread(() -> indexPage(siteEntity));
                indexingThreads.add(indexingThread);
                indexingThread.start();
            }
            for (Thread thread : indexingThreads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        } finally {
            synchronized (lock) {
                forkJoinPool.shutdownNow();
                forkJoinPool = null;
                log.info("Indexing finished");
            }
        }
    }

    private void indexPage(SiteEntity siteEntity) {
        SiteMap siteMap = new SiteMap(siteEntity.getUrl(), siteRepository, siteEntity);
        forkJoinPool.invoke(siteMap);
        Set<Page> pages = new CopyOnWriteArraySet<>(siteMap.getPages());
        Set<PageEntity> pageEntities = pages.stream()
                .filter(page -> page.getPath().startsWith(siteEntity.getUrl()))
                .map(page -> createPage(page, siteEntity))
                .collect(Collectors.toSet());
        processAndSavePages(pageEntities, siteEntity);
    }

    private void processAndSavePages(Set<PageEntity> pageEntities, SiteEntity siteEntity) {
        savePageInDB(pageEntities);
        siteEntity.setStatus(StatusType.INDEXED);
        siteRepository.save(siteEntity);
        log.info("Pages saved in DB: {}", pageEntities.size());
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
            siteRepository.save(siteEntity);
            log.info("Site save in DB: {}", site.getUrl());
        });
    }

    private void savePageInDB(Set<PageEntity> pageEntities) {
        pageRepository.saveAll(pageEntities);
    }

    private SiteEntity createSite(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setStatusTime(Date.from(Instant.now()));
        return siteEntity;
    }

    private void checkDuplicateInDB(Site site) {
        if (siteRepository.findByUrl(site.getUrl()) != null) {
            log.info("Site already exists in DB: {}", site.getUrl());
            siteRepository.deleteByUrl(site.getUrl());
            log.info("Site deleted from DB: {}", site.getUrl());
        }
    }

    private void initThreadPool() {
        forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    }
}
