package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.StatusType;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.morphology.LemmaMorphology;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final static int FREQUENCY_THRESHOLD = 200;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final LemmaMorphology lemmaMorphology;

    @Override
    public SearchResponse findByLemma(String query, String site, int offset, int limit) {
        if (site != null && !isIndexed(site)) {
            return new SearchResponse(false, "Индекс для сайта " + site + " не готов или отсутствует");
        }
        return executeSearch(query, site, offset, limit);
    }

    private SearchResponse executeSearch(String query, String site, int offset, int limit) {
        HashMap<String, Integer> lemmaMap = lemmaMorphology.collectLemmas(query);

        List<LemmaEntity> filteredLemmas = filterLemmasByFrequency(lemmaMap);

        List<PageEntity> relevantPages = findSequentiallyRelevantPages(filteredLemmas, site);

        relevantPages = filterPagesByProximity(relevantPages, query);

        Map<PageEntity, Double> relevanceScores = calculateRelevanceScores(relevantPages, filteredLemmas);

        List<Map.Entry<PageEntity, Double>> sortedPages = new ArrayList<>(relevanceScores.entrySet());

        sortedPages.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        int totalResult = sortedPages.size();

        offset = Math.max(offset, 0);
        limit = Math.max(limit, 1);

        int start = Math.min(offset, totalResult);
        int end = Math.min(start + limit, totalResult);

        List<Map.Entry<PageEntity, Double>> paginatedPages = sortedPages.subList(start, end);

        List<SearchData> result = new ArrayList<>();
        for (var entry : paginatedPages) {
            PageEntity page = entry.getKey();
            double relevance = entry.getValue();
            String snippet = generateSnippet(page.getContent(), query);

            SearchData searchData = new SearchData();
            searchData.setSite(page.getSiteId().getUrl());
            searchData.setSiteName(page.getSiteId().getName());
            searchData.setUri(page.getPath());
            searchData.setTitle(extractTitle(page.getContent()));
            searchData.setSnippet(snippet);
            searchData.setRelevance(relevance);
            result.add(searchData);
        }
        SearchResponse searchResponse = new SearchResponse(true);
        searchResponse.setResult(result);
        searchResponse.setCount(totalResult);
        return searchResponse;
    }

    private String extractTitle(String content) {
        Document doc = Jsoup.parse(content);
        return doc.title();
    }

    private String generateSnippet(String content, String query) {
        Document doc = Jsoup.parse(content);
        String textContent = doc.text();

        String[] queryWords = query.split("\\s+");

        StringBuilder regexBuilder = new StringBuilder();
        for (var word : queryWords) {
            if (!regexBuilder.isEmpty()) {
                regexBuilder.append("|");
            }
            regexBuilder.append(Pattern.quote(word));
        }
        String regex = "(?i)(" + regexBuilder + ")";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(textContent);
        int pos = matcher.find() ? matcher.start() : -1;

        int start = Math.max(pos - 100, 0);
        int end = Math.min(pos + 100, textContent.length());

        String snippet = textContent.substring(start, end) + "...";

        snippet = snippet.replaceAll(regex, "<b>$1</b>");

        return snippet;
    }

    private Map<PageEntity, Double> calculateRelevanceScores(List<PageEntity> relevantPages, List<LemmaEntity> filteredLemmas) {
        Map<PageEntity, Double> relevanceScores = new LinkedHashMap<>();

        for (var page : relevantPages) {
            double absoluteRelevance = filteredLemmas.stream()
                    .flatMap(lemma -> lemma.getIndexLemmas().stream())
                    .filter(index -> index.getPageId().equals(page))
                    .mapToDouble(IndexEntity::getRank)
                    .sum();
            relevanceScores.put(page, absoluteRelevance);
        }

        if (relevanceScores.isEmpty()) {
            log.info("No relevant pages found or no relevance calculated.");
            return relevanceScores;
        }

        double maxScore = Collections.max(relevanceScores.values());

        if (maxScore == 0) {
            log.info("Maximum relevance is 0, possibly due to no relevant pages or ranks.");
            return relevanceScores;
        }

        Map<PageEntity, Double> relativeRelevanceScores = new LinkedHashMap<>();

        for (var entry : relevanceScores.entrySet()) {
            double relativeRelevance = entry.getValue() / maxScore;
            BigDecimal roundedRelevance = BigDecimal.valueOf(relativeRelevance)
                    .setScale(4, RoundingMode.HALF_UP);
            relativeRelevanceScores.put(entry.getKey(), roundedRelevance.doubleValue());
        }
        return relativeRelevanceScores;
    }

    private List<PageEntity> filterPagesByProximity(List<PageEntity> pages, String query) {
        List<PageEntity> filteredPages = new ArrayList<>();
        String[] queryWords = query.toLowerCase().split("\\s+");

        for (var page : pages) {
            String content = Jsoup.parse(page.getContent()).text().toLowerCase();
            if (areWordsInProximity(content, queryWords)) {
                filteredPages.add(page);
            }
        }
        return filteredPages;
    }

    private boolean areWordsInProximity(String content, String[] queryWords) {
        List<Integer> positions = new ArrayList<>();
        for (var word : queryWords) {
            int position = content.indexOf(word);
            if (position == -1) {
                return false;
            }
            positions.add(position);
        }
        Collections.sort(positions);
        for (int i = 0; i < positions.size() - 1; i++) {
            if (positions.get(i + 1) - positions.get(i) > SearchServiceImpl.FREQUENCY_THRESHOLD + queryWords[i].length()) {
                return false;
            }
        }
        return true;
    }

    private List<PageEntity> findSequentiallyRelevantPages(List<LemmaEntity> filteredLemmas, String site) {
        Map<String, Set<Integer>> pagesBySite = new HashMap<>();
        List<PageEntity> result = new ArrayList<>();
        for (var lemma : filteredLemmas) {
            for (var index : lemma.getIndexLemmas()) {
                String siteUrl = index.getPageId().getSiteId().getUrl().toLowerCase();
                pagesBySite.computeIfAbsent(siteUrl, V -> new HashSet<>())
                        .add(index.getPageId().getId());
            }
        }
        for (var currentSite : pagesBySite.keySet()) {
            if (site == null || site.equalsIgnoreCase(currentSite)) {
                List<LemmaEntity> lemmasForSite = filterLemmasForSite(filteredLemmas, currentSite);
                Set<Integer> relevantPageIdsForSite = findPagesForLemmasOnSite(lemmasForSite, currentSite);
                result.addAll(pageRepository.findAllById(relevantPageIdsForSite));
            }
        }
        return result;
    }

    private Set<Integer> findPagesForLemmasOnSite(List<LemmaEntity> lemmas, String siteUrl) {
        Set<Integer> relevantPageIds = new HashSet<>();
        for (int i = 0; i < lemmas.size(); i++) {
            LemmaEntity lemmaEntity = lemmas.get(i);
            Set<Integer> currentPagesIds = lemmaEntity.getIndexLemmas().stream()
                    .filter(index -> index.getPageId().getSiteId().getUrl().equalsIgnoreCase(siteUrl))
                    .map(index -> index.getPageId().getId())
                    .collect(Collectors.toSet());
            if (i == 0) {
                relevantPageIds.addAll(currentPagesIds);
            } else {
                relevantPageIds.retainAll(currentPagesIds);
                if (relevantPageIds.isEmpty()) {
                    break;
                }
            }
        }
        return relevantPageIds;
    }

    private List<LemmaEntity> filterLemmasForSite(List<LemmaEntity> lemmas, String siteUrl) {
        return lemmas.stream()
                .filter(lemma -> lemma
                        .getIndexLemmas()
                        .stream()
                        .anyMatch(index -> index
                                .getPageId()
                                .getSiteId()
                                .getUrl()
                                .equalsIgnoreCase(siteUrl)))
                .collect(Collectors.toList());
    }

    /**
     * Возвращает сортированный список лемм в порядке увеличения частоты встречаемости
     * (по возрастанию значения поля frequency) — от самых редких до
     * самых частых.
     *
     * @param lemmaMap коллекция лемм
     * @return List
     */
    private List<LemmaEntity> filterLemmasByFrequency(HashMap<String, Integer> lemmaMap) {
        Set<String> allLemmas = lemmaMap.keySet();
        return lemmaRepository.findByLemmaInAndFrequencyLessThanOrderByFrequencyAsc(allLemmas, FREQUENCY_THRESHOLD);
    }

    /**
     * Проверяет статус индексации
     *
     * @param siteUrl адрес сайта
     * @return boolean
     */
    private boolean isIndexed(String siteUrl) {
        return siteRepository.findByUrl(siteUrl).getStatus().equals(StatusType.INDEXED);
    }
}
