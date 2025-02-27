package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    /**
     * Статистика — GET /api/statistics
     * Метод возвращает статистику и другую служебную информацию о
     * состоянии поисковых индексов и самого движка.
     * Если ошибок индексации того или иного сайта нет, задавать ключ error не
     * нужно.
     * <p>Формат ответа:</p>
     *
     * @return {
     * 'result': true,
     * 'statistics': {
     * "total": {
     * "sites": 10,
     * "pages": 436423,
     * "lemmas": 5127891,
     * "indexing": true
     * },
     * "detailed": [
     * {
     * "url": "http://www.site.com",
     * "name": "Имя сайта",
     * "status": "INDEXED",
     * 5
     * "statusTime": 1600160357,
     * "error": "Ошибка индексации: главная
     * страница сайта недоступна",
     * "pages": 5764,
     * "lemmas": 321115
     * },
     * ...
     * ]
     * }
     */
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    /**
     * Запуск полной индексации — GET /api/startindexing
     * Метод запускает полную индексацию всех сайтов или полную
     * переиндексацию, если они уже проиндексированы.
     * Если в настоящий момент индексация или переиндексация уже
     * запущена, метод возвращает соответствующее сообщение об ошибке.
     * <p>Формат ответа в случае успеха:</p>
     *
     * @return true
     */
    @GetMapping("/startindexing")
    public ResponseEntity<IndexingResponse> startIndexing() throws IOException {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    /**
     * Остановка текущей индексации — GET /api/stopindexing
     * Метод останавливает текущий процесс индексации (переиндексации).
     * Если в настоящий момент индексация или переиндексация не происходит,
     * метод возвращает соответствующее сообщение об ошибке.
     * <p>Формат ответа в случае успеха:</p>
     *
     * @return true
     */
    @GetMapping("/stopindexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }
}
