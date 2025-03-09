package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.entity.Page;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.morphology.LemmaMorphology;
import searchengine.services.morphology.LemmaMorphologyImpl;
import searchengine.services.parsing.SiteMap;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaMorphologyImpl lemmaMorphology;
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
            forkJoinPool.shutdownNow(); //TODO: shutdown thread
            log.info("Indexing stopped");
            return new IndexingResponse(true);
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return new IndexingResponse(false, "Ошибка при остановке индексации");
        }
    }

    @Override
    public IndexingResponse indexPage(String page) throws MalformedURLException {
        synchronized (lock) {
            SiteEntity siteEntity = siteRepository.findByUrl(getHostName(page));
            if (forkJoinPool != null && !forkJoinPool.isTerminated()) {
                log.info("Indexing is running");
                return new IndexingResponse(false, "Индексация уже запущена");
            }
            if (!isValidUrl(page)) {
                return new IndexingResponse(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            }
            deletePage(page);
            if (siteEntity == null) {
                siteEntity = createSingleSite(page);
            }
            initThreadPool();
            SiteEntity finalSite = siteEntity;
            new Thread(() -> indexSinglePage(finalSite, page)).start(); //TODO: save page when first added
            return new IndexingResponse(true);
        }
    }

    private void indexSite() {
        try {
            saveSitesInDB();
            indexingThreads = new ArrayList<>();
            for (SiteEntity siteEntity : siteRepository.findAll()) {
                Thread indexingThread = new Thread(() -> indexPages(siteEntity));
                indexingThreads.add(indexingThread);
                indexingThread.start();
            }
            for (var thread : indexingThreads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        } finally {
            cleanupAfterParsing();
            log.info("Indexing finished");
        }
    }

    private void indexPages(SiteEntity siteEntity) {
        SiteMap siteMap = new SiteMap(siteEntity.getUrl(), siteRepository, siteEntity, false);
        forkJoinPool.invoke(siteMap);
        Set<Page> pages = new CopyOnWriteArraySet<>(siteMap.getPages());
        Set<PageEntity> pageEntities = pages.stream()
                .filter(page -> page.getPath().startsWith(siteEntity.getUrl()))
                .map(page -> {
                    try {
                        return createPage(page, siteEntity);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());
        processAndSavePages(pageEntities, siteEntity);
    }

    private void indexSinglePage(SiteEntity siteEntity, String page) {
        SiteMap siteMap = new SiteMap(page, siteRepository, siteEntity, true);
        forkJoinPool.invoke(siteMap);
        siteMap.getPages().stream()
                .filter(pages -> pages.getPath().equals(page))
                .findFirst()
                .map(pages -> {
                    try {
                        return createPage(pages, siteEntity);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .ifPresent(pageEntity -> processAndSavePages(Set.of(pageEntity), siteEntity));
        log.info("Page saved in DB: {}", page);
        cleanupAfterParsing();
    }

    private SiteEntity createSite(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setStatusTime(Date.from(Instant.now()));
        return siteEntity;
    }

    private SiteEntity createSingleSite(String url) throws MalformedURLException {
        String nameSite = null;
        List<Site> siteList = sites.getSites();
        for (var site : siteList) {
            if (site.getUrl().contains(getHostName(url))) {
                nameSite = site.getName();
                break;
            }
        }
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setUrl(getHostName(url));
        siteEntity.setName(nameSite);
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setStatusTime(Date.from(Instant.now()));
        return siteEntity;
    }

    private PageEntity createPage(Page page, SiteEntity siteEntity) throws MalformedURLException {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setSiteId(siteEntity);
        pageEntity.setContent(page.getContent());
        pageEntity.setPath(getPathAddress(page.getPath()));
        pageEntity.setCode(page.getStatusCode());
        return pageEntity;
    }

    /**
     * Сохраняет сайт в базу данных
     */
    protected void saveSitesInDB() {
        List<Site> siteList = sites.getSites();
        siteList.forEach(site -> {
            deleteSite(site);
            SiteEntity siteEntity =
                    createSite(site);
            siteRepository.save(siteEntity);
            log.info("Site save in DB: {}", site.getUrl());
        });
    }

    /**
     * Сохраняет все страницы сайта в базу данных
     *
     * @param pageEntities таблица страниц в базе данных
     */
    private void savePageInDB(Set<PageEntity> pageEntities) {
        pageRepository.saveAll(pageEntities);
    }

    protected void processAndSavePages(Set<PageEntity> pageEntities, SiteEntity siteEntity) {
        savePageInDB(pageEntities);
        if (pageEntities.size() > 1) {
            saveLemmaAndIndexSite(siteEntity);
        } else {
            pageEntities.forEach(this::saveLemmaAndIndexPage);
        }
        siteEntity.setStatus(StatusType.INDEXED);
        siteRepository.save(siteEntity);
        log.info("Pages saved in DB: {}", pageEntities.size());
    }

    private void saveLemmaAndIndexPage(PageEntity pageEntity) {
        Map<String, LemmaEntity> lemmaEntityMap = new HashMap<>();
        List<IndexEntity> indexEntityList = new ArrayList<>();
        processLemmasAndIndexes(pageEntity, lemmaEntityMap, indexEntityList);
        lemmaRepository.saveAll(lemmaEntityMap.values());
        indexRepository.saveAll(indexEntityList);
    }

    private void saveLemmaAndIndexSite(SiteEntity siteEntity) {
        Map<String, LemmaEntity> lemmaEntityMap = new ConcurrentHashMap<>();
        List<IndexEntity> indexEntityList = new CopyOnWriteArrayList<>();
        pageRepository.findAllBySiteId(siteEntity).stream()
                .filter(pageEntity -> pageEntity.getCode() == 200)
                .forEach(pageEntity -> processLemmasAndIndexes(pageEntity, lemmaEntityMap, indexEntityList));
        lemmaRepository.saveAll(lemmaEntityMap.values());
        indexRepository.saveAll(indexEntityList);
    }

    private void processLemmasAndIndexes(PageEntity pageEntity,
                                         Map<String, LemmaEntity> lemmaEntityMap,
                                         List<IndexEntity> indexEntityList) {
        if (pageEntity.getCode() == 200) {
            String content = pageEntity.getContent();
            Map<String, Integer> lemmas = lemmaMorphology.collectLemmas(content, pageEntity);
            lemmas.forEach((lemma, count) -> {
                LemmaEntity lemmaEntity = lemmaEntityMap.get(lemma);
                if (lemmaEntity == null) {
                    LemmaEntity newLemmaEntity = new LemmaEntity();
                    newLemmaEntity.setLemma(lemma);
                    newLemmaEntity.setSiteId(pageEntity.getSiteId());
                    newLemmaEntity.setFrequency(1);
                    lemmaEntityMap.put(lemma, newLemmaEntity);
                } else {
                    lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
                }
                IndexEntity indexEntity = new IndexEntity();
                indexEntity.setPageId(pageEntity);
                indexEntity.setLemmaId(lemmaEntityMap.get(lemma));
                indexEntity.setRank(count);
                indexEntityList.add(indexEntity);
            });
        }
    }

    /**
     * Проверка сайта в базе данных, если такой сайт уже существует в базе данных, то происходит его удаление
     *
     * @param site адрес сайта
     */
    private void deleteSite(Site site) {
        if (siteRepository.findByUrl(site.getUrl()) != null) {
            log.info("Site already exists in DB: {}", site.getUrl());
            siteRepository.deleteByUrl(site.getUrl());
            log.info("Site deleted from DB: {}", site.getUrl());
        }
    }

    /**
     * Проверка адреса страницы в базе данных, если такая страница уже существует в базе данных, то происходит её удаление
     * @param page адрес страницы
     */
    private void deletePage(String page) throws MalformedURLException {
        PageEntity pageEntity = pageRepository.findByPath(getPathAddress(page));
        if (pageEntity != null) {
            log.info("Page already exists in DB: {}", pageEntity.getPath());
            pageRepository.delete(pageEntity);
            log.info("Page deleted from DB: {}", pageEntity.getPath());
        }
    }

    private boolean isValidUrl(String url) throws MalformedURLException {
        for (var site : sites.getSites()) {
            if (site.getUrl().contains(getHostName(url))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Возвращает имя хоста URL-адреса
     *
     * @param url URL-адрес
     * @return <a href="">https://example.com</a>
     */
    private String getHostName(String url) throws MalformedURLException {
        URL uri = new URL(url);
        return uri.getProtocol() + "://" + uri.getHost();
    }

    /**
     * Возвращает адрес страницы от корня сайта
     *
     * @param url URL-адрес
     * @return /example/372189/.../
     */
    private String getPathAddress(String url) throws MalformedURLException {
        URL uri = new URL(url);
        return uri.getPath();
    }

    /**
     * Создает пул потоков по количеству процессоров
     */
    private void initThreadPool() {
        forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Остановка всех активно выполняемых задач и очистка FJP после парсинга страницы
     */
    private void cleanupAfterParsing() {
        synchronized (lock) {
            if (forkJoinPool != null) {
                forkJoinPool.shutdownNow();
                forkJoinPool = null;
            }
        }
    }
}
