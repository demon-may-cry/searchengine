package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepositories;

import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class SiteMapService extends RecursiveAction {

    private final PageRepositories pageRepositories;
    private final String url;
    private final CopyOnWriteArraySet<String> allLinks;
    private SiteEntity siteEntity;

    public SiteMapService(String url, SiteEntity siteEntity, PageRepositories pageRepositories) {
        this.siteEntity = siteEntity;
        this.pageRepositories = pageRepositories;
        this.url = url;
        this.allLinks = new CopyOnWriteArraySet<>();
    }

    public SiteMapService(String url, CopyOnWriteArraySet<String> allLinks, PageRepositories pageRepositories) {
        this.pageRepositories = pageRepositories;
        this.url = url;
        this.allLinks = allLinks;
    }

    @Transactional
    protected void compute() {
        Set<SiteMapService> allTask = new TreeSet<>(Comparator.comparing(o -> o.url));
        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots")
                    .referrer("https://www.yandex.ru")
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .timeout(3_000)
                    .get();
            Elements links = document.select("a");
            for (Element link : links) {
                String currentUrl = link.attr("abs:href");
                PageEntity pageEntity = pageRepositories.findByPath(currentUrl);
                int statusCode = Jsoup.connect(currentUrl).execute().statusCode();
                String body = Jsoup.connect(currentUrl).execute().body();
                if (currentUrl.contains(url) &&
                        !currentUrl.contains("#") &&
                        !allLinks.contains(currentUrl)) {
                    if (pageEntity == null) {
                        pageEntity = new PageEntity();
                        pageEntity.setPath(currentUrl);
                        pageEntity.setCode(statusCode);
                        pageEntity.setContent(body);
                        pageEntity.setSiteId(siteEntity);
                        pageRepositories.save(pageEntity);
                        SiteMapService siteMapService = new SiteMapService(currentUrl, allLinks, pageRepositories);
                        siteMapService.fork();
                        allTask.add(siteMapService);
                        allLinks.add(currentUrl);
                    }

                }
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        allTask.forEach(ForkJoinTask::join);
    }
}
