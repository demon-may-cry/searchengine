package searchengine.services.parsing;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.dto.entity.Page;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveAction;

import static java.lang.Thread.sleep;

@Slf4j
public class SiteMap extends RecursiveAction {
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)";
    private static final String REFERRER = "https://www.yandex.ru";
    private static final boolean IGNORE_CONTENT_TYPE = true;
    private static final boolean IGNORE_HTTP_ERRORS = true;

    private static final Set<Page> allPages = new CopyOnWriteArraySet<>();
    private static final Set<String> allLinks = new CopyOnWriteArraySet<>();
    private final SiteRepository siteRepository;
    private final SiteEntity siteEntity;
    private final String url;
    private final boolean isSinglePage;

    public SiteMap(String url, SiteRepository siteRepository, SiteEntity siteEntity, boolean isSinglePage) {
        this.url = url;
        this.siteRepository = siteRepository;
        this.siteEntity = siteEntity;
        this.isSinglePage = isSinglePage;
    }

    @Override
    protected void compute() {
        try {
            sleep(500);
            Document document = connection(url);
            Elements links = document.select("a[href]");

            List<SiteMap> allTasks = new CopyOnWriteArrayList<>();

            String uri = isSinglePage ? url : siteEntity.getUrl();
            for (var link : links) {
                Page page = new Page();
                String currentUrl = link.attr("abs:href");
                if (currentUrl.contains(uri)
                        && !isFile(currentUrl)
                        && !currentUrl.contains("#")
                        && !allLinks.contains(currentUrl)) {
                    log.info("Current URL: {}", currentUrl);

                    Document documentChild = connection(currentUrl);

                    page.setPath(currentUrl);
                    page.setContent(documentChild.html());
                    page.setStatusCode(documentChild.connection().response().statusCode());

                    allPages.add(page);
                    allLinks.add(currentUrl);

                    setSiteEntityStatusTime();

                    SiteMap subTask = new SiteMap(currentUrl, siteRepository, siteEntity, isSinglePage);
                    allTasks.add(subTask);
                }
            }
            invokeAll(allTasks);
        } catch (InterruptedException | IOException ex) {
            setSiteEntityLastError(ex.getMessage());
            log.error(ex.getMessage());
        }
    }

    private void setSiteEntityStatusTime() {
        siteEntity.setStatusTime(Date.from(Instant.now()));
        siteRepository.save(siteEntity);
    }

    private void setSiteEntityLastError(String error) {
        siteEntity.setLastError(error);
        siteEntity.setStatus(StatusType.FAILED);
        siteRepository.save(siteEntity);
    }

    public Set<Page> getPages() {
        return new HashSet<>(allPages);
    }

    private boolean isFile(String link) {
        return link.toLowerCase().contains(".jpg")
                || link.contains(".jpeg")
                || link.contains(".png")
                || link.contains(".gif")
                || link.contains(".webp")
                || link.contains(".pdf")
                || link.contains(".eps")
                || link.contains(".xlsx")
                || link.contains(".doc")
                || link.contains(".pptx")
                || link.contains(".docx")
                || link.contains(".sql")
                || link.contains(".yaml")
                || link.contains(".zip")
                || link.contains(".7z")
                || link.contains(".rar")
                || link.contains("?_ga");
    }

    private Document connection(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(REFERRER)
                .ignoreContentType(IGNORE_CONTENT_TYPE)
                .ignoreHttpErrors(IGNORE_HTTP_ERRORS)
                .get();
    }
}
