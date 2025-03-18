package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
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
import searchengine.services.morphology.LemmaMorphologyImpl;
import searchengine.services.parsing.SiteMap;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private static final int BATCH_SIZE = 1000;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaMorphologyImpl lemmaMorphology;
    private final SitesList sites;
    private final Object lock = new Object();
    private final List<Thread> indexingThreads;
    private ForkJoinPool forkJoinPool;

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
            if (forkJoinPool == null || forkJoinPool.isShutdown()) {
                log.info("Indexing is not running");
                return new IndexingResponse(false, "Индексация не запущена");
            }
            if (indexingThreads != null) {
                indexingThreads.forEach(thread -> {
                    if (thread.isAlive()) {
                        thread.interrupt();
                        log.info("Thread {} interrupted", thread.getName());
                    }
                });
            }
            forkJoinPool.shutdownNow();
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
            PageEntity pageEntity = pageRepository.findByPath(new URL(page).getPath());

            if (forkJoinPool != null && !forkJoinPool.isTerminated()) {
                log.info("Indexing is running");
                return new IndexingResponse(false, "Индексация уже запущена");
            }

            if (!isValidUrl(page)) {
                return new IndexingResponse(false, "Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле");
            }

            if (pageEntity != null) {
                deletePage(pageEntity);
            }

            if (siteEntity == null) {
                siteEntity = createSingleSite(page);
            }

            initThreadPool();
            SiteEntity finalSite = siteEntity;
            forkJoinPool.submit(() -> indexSinglePage(finalSite, page));
            return new IndexingResponse(true);
        }
    }

    private void indexSite() {
        try {
            saveSitesInDB();
            List<Thread> indexingThreads = new ArrayList<>();
            List<SiteEntity> siteEntities = siteRepository.findAll();

            for (SiteEntity siteEntity : siteEntities) {
                Thread indexingThread = new Thread(() -> indexPages(siteEntity));
                indexingThreads.add(indexingThread);
                indexingThread.start();
            }

            for (Thread thread : indexingThreads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    log.error("Thread interrupted: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception ex) {
            log.error("Error during site indexing: {}", ex.getMessage(), ex);
        } finally {
            cleanupAfterParsing();
            log.info("Indexing finished");
        }
    }

    private void indexPages(SiteEntity siteEntity) {
        SiteMap siteMap = new SiteMap(siteEntity.getUrl(), siteRepository, pageRepository, siteEntity, false);
        forkJoinPool.invoke(siteMap);
        Set<Page> pages = new CopyOnWriteArraySet<>(siteMap.getPages());
        Set<PageEntity> pageEntities = pages.stream()
                .filter(page -> page.getPath().startsWith(siteEntity.getUrl()))
                .map(page -> {
                    try {
                        return createPage(page, siteEntity);
                    } catch (MalformedURLException e) {
                        log.error("Failed to create page for {}: {}", page.getPath(), e.getMessage());
                        return null;
                    }
                })
                .collect(Collectors.toSet());
        processAndSavePages(pageEntities, siteEntity);
    }

    private void indexSinglePage(SiteEntity siteEntity, String page) {
        SiteMap siteMap = new SiteMap(page, siteRepository, pageRepository, siteEntity, true);
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
    private void saveSitesInDB() {
        List<Site> siteList = sites.getSites();

        if (siteList == null || siteList.isEmpty()) {
            log.warn("No sites found for saving in DB");
            return;
        }

        siteList.forEach(site -> {
            SiteEntity existingSite = siteRepository.findByUrl(site.getUrl());

            if (existingSite != null) {
                log.info("Site already exists: {}", site.getUrl());
                deleteSite(site);
            } else {
                log.info("Creating new site: {}", site.getUrl());
                SiteEntity newSiteEntity = createSite(site);
                siteRepository.save(newSiteEntity);
            }
        });

        log.info("Finished saving sites in DB. Total: {}", siteList.size());
    }

    /**
     * Сохраняет все страницы сайта в базу данных
     *
     * @param pageEntities таблица страниц в базе данных
     */
    private void savePageInDB(Set<PageEntity> pageEntities) {
        try {
            int batchSize = 100;
            int totalPages = pageEntities.size();

            List<PageEntity> pageList = new ArrayList<>(pageEntities);

            for (int i = 0; i < totalPages; i += batchSize) {
                int end = Math.min(i + batchSize, totalPages);
                pageRepository.saveAll(pageList.subList(i, end));
            }

            log.info("Successfully saved {} pages in DB.", totalPages);
        } catch (Exception e) {
            log.error("Error saving pages to DB: {}", e.getMessage());
            throw new RuntimeException("Failed to save pages to DB", e);
        }
    }

    private void processAndSavePages(Set<PageEntity> pageEntities, SiteEntity siteEntity) {
        try {
            savePageInDB(pageEntities);

            if (pageEntities.size() > 1) {
                saveLemmaAndIndexSite(siteEntity);
            } else {
                pageEntities.forEach(this::saveLemmaAndIndexPage);
            }

            siteEntity.setStatus(StatusType.INDEXED);

            siteRepository.save(siteEntity);
            log.info("Successfully saved {} pages in DB for site: {}", pageEntities.size(), siteEntity.getUrl());
        } catch (Exception ex) {
            log.error("Error while processing pages for site {}: {}", siteEntity.getUrl(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Метод обрабатывает одну страницу, извлекает леммы и индексные записи,
     * а затем сохраняет их в базу данных
     *
     * @param pageEntity страница сайта
     */
    private void saveLemmaAndIndexPage(PageEntity pageEntity) {
        if (pageEntity.getCode() != 200) {
            return;
        }

        Map<String, LemmaEntity> lemmaEntityMap = loadExistingLemmas(pageEntity.getSiteId());
        List<IndexEntity> indexEntityList = new ArrayList<>();

        processLemmasAndIndexes(pageEntity, lemmaEntityMap, indexEntityList);

        if (!lemmaEntityMap.isEmpty()) {
            batchSave(new ArrayList<>(lemmaEntityMap.values()), lemmaRepository);
        }

        if (!indexEntityList.isEmpty()) {
            batchSave(indexEntityList, indexRepository);
        }
    }

    /**
     * Метод выполняет индексацию всех страниц сайта, извлекает леммы,
     * создает индексные записи и сохраняет их в базе данных
     *
     * @param siteEntity сайт
     */
    private void saveLemmaAndIndexSite(SiteEntity siteEntity) {
        Map<String, LemmaEntity> lemmaEntityMap = new HashMap<>();
        List<IndexEntity> indexEntityList = new ArrayList<>();

        pageRepository.findAllBySiteId(siteEntity).parallelStream()
                .filter(pageEntity -> pageEntity.getCode() == 200)
                .forEach(pageEntity -> processLemmasAndIndexes(pageEntity, lemmaEntityMap, indexEntityList));

        batchSave(new ArrayList<>(lemmaEntityMap.values()), lemmaRepository);
        batchSave(indexEntityList, indexRepository);
    }

    /**
     * Анализ содержимого страницы, извлечение леммы и обновление связанных сущностей LemmaEntity и IndexEntity
     *
     * @param pageEntity      страница сайта
     * @param lemmaEntityMap  коллекция лемм
     * @param indexEntityList список индексов
     */
    private void processLemmasAndIndexes(PageEntity pageEntity,
                                         Map<String, LemmaEntity> lemmaEntityMap,
                                         List<IndexEntity> indexEntityList) {
        if (pageEntity.getCode() == 200) {
            String content = pageEntity.getContent();
            Map<String, Integer> lemmas = lemmaMorphology.collectLemmas(content);
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
            log.info("Deleting site from DB: {}", site.getUrl());
            siteRepository.deleteByUrl(site.getUrl());
            log.info("Site deleted successfully: {}", site.getUrl());
        } else {
            log.warn("Site not found in DB: {}", site.getUrl());
        }
    }

    /**
     * Метод удаляет страницу из базы данных,
     * а также связанные с ней индексы и при необходимости — леммы
     *
     * @param page адрес страницы
     */
    private void deletePage(PageEntity page) {
        log.info("Deleting page: {}", page.getPath());

        List<IndexEntity> indexTables = indexRepository.findAllByPageId(page);
        List<LemmaEntity> lemmaTables = indexTables.stream()
                .map(IndexEntity::getLemmaId)
                .toList();

        indexRepository.deleteByPageId(page);
        lemmaRepository.decrementFrequencyForLemmas(lemmaTables);
        lemmaRepository.deleteWhereFrequencyZero();

        log.info("Trying to delete page from DB: {}", page.getPath());
        pageRepository.deleteById(page.getId());

        if (pageRepository.existsById(page.getId())) {
            log.error("Page {} was not deleted!", page.getPath());
        } else {
            log.info("Page {} successfully deleted", page.getPath());
        }
    }

    /**
     * Метод проверяет, содержится ли хост переданного URL в списке сайтов
     *
     * @param url URL-адрес сайта
     * @return boolean
     */
    private boolean isValidUrl(String url) throws MalformedURLException {
        for (var site : sites.getSites()) {
            if (site.getUrl().equals(getHostName(url))) {
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
     * Инициализирует пул потоков ForkJoinPool, используя количество доступных процессоров
     */
    private void initThreadPool() {
        forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Остановка всех активно выполняемых задач и очистка ForkJoinPool после парсинга страницы
     */
    private void cleanupAfterParsing() {
        synchronized (lock) {
            if (forkJoinPool != null) {
                forkJoinPool.shutdown();
                try {
                    if (!forkJoinPool.awaitTermination(10, TimeUnit.SECONDS)) {
                        forkJoinPool.shutdownNow();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    forkJoinPool.shutdownNow();
                }
                forkJoinPool = null;
            }
        }
    }

    /**
     * Загрузка существующих лемм из БД по идентификатору сайта
     *
     * @param siteId идентификатор сайта
     * @return <a href="">lemma1:10, lemma2:20...</a>
     **/
    private Map<String, LemmaEntity> loadExistingLemmas(SiteEntity siteId) {
        return lemmaRepository.findBySiteId(siteId).stream()
                .collect(Collectors.toMap(LemmaEntity::getLemma, lemma -> lemma));
    }

    /**
     * Сохранение данных в БД порциями
     */
    private <T> void batchSave(List<T> entities, JpaRepository<T, ?> repository) {
        for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, entities.size());
            repository.saveAll(entities.subList(i, end));
        }
    }
}
