package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.io.IOException;
import java.net.MalformedURLException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    /**
     * Статистика — GET /api/statistics
     * <p>
     * Метод возвращает статистику и другую служебную информацию о
     * состоянии поисковых индексов и самого движка.
     * Если ошибок индексации того или иного сайта нет, задавать ключ error не
     * нужно.
     * @return boolean
     */
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    /**
     * Запуск полной индексации — GET /api/startIndexing
     * <p>
     * Метод запускает полную индексацию всех сайтов или полную
     * переиндексацию, если они уже проиндексированы.
     * Если в настоящий момент индексация или переиндексация уже
     * запущена, метод возвращает соответствующее сообщение об ошибке.
     * @return boolean
     */
    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() throws IOException {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    /**
     * Остановка текущей индексации — GET /api/stopIndexing
     * <p>
     * Метод останавливает текущий процесс индексации (переиндексации).
     * Если в настоящий момент индексация или переиндексация не происходит,
     * метод возвращает соответствующее сообщение об ошибке.
     * @return boolean
     */
    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    /**
     * Добавление или обновление отдельной страницы — POST /api/indexPage
     * <p>
     * Метод добавляет в индекс или обновляет отдельную страницу, адрес
     * которой передан в параметре.
     * Если адрес страницы передан неверно, метод должен вернуть
     * соответствующую ошибку.
     * @param url адрес страницы, которую нужно переиндексировать
     * @return boolean
     */
    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam String url) throws MalformedURLException {
        return ResponseEntity.ok(indexingService.indexPage(url));
    }

    /**
     * Получение данных по поисковому запросу — GET /api/search
     * <p>
     * Метод осуществляет поиск страниц по переданному поисковому запросу.
     * @param query поисковый запрос
     * @return boolean
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam String query,
                                                 @RequestParam(required = false) String site,
                                                 @RequestParam(required = false, defaultValue = "0") int offset,
                                                 @RequestParam(required = false, defaultValue = "20") int limit) {
        return ResponseEntity.ok(searchService.findByLemma(query, site, offset, limit));
    }
}
