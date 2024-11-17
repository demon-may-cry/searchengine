package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.SiteEntity;
import searchengine.repositories.SiteRepositories;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveAction;

import static java.lang.Thread.sleep;


@Slf4j
public class SiteMapService extends RecursiveAction {

    private static final Set<Page> allPages = new CopyOnWriteArraySet<>();
    private static final Set<String> allLinks = new CopyOnWriteArraySet<>();
    private final SiteRepositories siteRepositories;
    private final SiteEntity siteEntity;
    private final String url;

    public SiteMapService(String url, SiteRepositories siteRepositories, SiteEntity siteEntity) {
        this.url = url;
        this.siteRepositories = siteRepositories;
        this.siteEntity = siteEntity;
    }

    @Override
    protected void compute() {
        try {
            sleep(300);
            Document document = connection(url);

            Elements links = document.select("a[href]");
            List<SiteMapService> allTasks = new CopyOnWriteArrayList<>();

            for (Element link : links) {
                Page page = new Page();
                String currentUrl = link.attr("abs:href");
                if (currentUrl.startsWith(url)
                        && !isFile(currentUrl)
                        && !currentUrl.contains("#")
                        && !allLinks.contains(currentUrl)) {
                    log.info(currentUrl);

                    Document documentChild = connection(currentUrl);

                    page.setPath(currentUrl);
                    page.setContent(documentChild.html());
                    page.setStatusCode(documentChild.connection().response().statusCode());

                    allPages.add(page);
                    allLinks.add(currentUrl);

                    setSiteEntityStatusTime();

                    SiteMapService subTask = new SiteMapService(currentUrl, siteRepositories, siteEntity);
                    allTasks.add(subTask);
                }
            }
            invokeAll(allTasks);
        } catch (InterruptedException | IOException e) {
            log.error(e.getMessage());
        }
    }

    private void setSiteEntityStatusTime() {
        siteEntity.setStatusTime(Date.from(Instant.now()));
        siteRepositories.save(siteEntity);
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
                || link.contains("?_ga");
    }

    private Document connection(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)")
                .referrer("https://www.yandex.ru")
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .get();
    }
}
