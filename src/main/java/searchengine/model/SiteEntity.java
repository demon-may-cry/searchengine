package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;
import java.util.List;

/**
 * Site — информация о сайтах и статусах их индексации
 */
@Setter
@Getter
@Entity
@Table(name = "site")
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /**
     * Текущий статус полной индексации сайта, отражающий готовность поискового
     * движка осуществлять поиск по сайту — индексация или переиндексация
     * в процессе, сайт полностью проиндексирован (готов к поиску) либо его не
     * удалось проиндексировать (сайт не готов к поиску и не будет до
     * устранения ошибок и перезапуска индексации)
     */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private StatusType status;

    /**
     * Дата и время статуса (в случае статуса INDEXING дата и время должны
     * обновляться регулярно при добавлении каждой новой страницы в индекс)
     */
    @CreationTimestamp
    @Column(name = "status_time", columnDefinition = "DATETIME", nullable = false)
    private Date statusTime;

    /**
     * Текст ошибки индексации или NULL, если её не было
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Адрес главной страницы сайта
     */
    @Column(name = "url", columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;

    /**
     * Имя сайта
     */
    @Column(name = "name", columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;

    @OneToMany(mappedBy = "siteId", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<PageEntity> pages;

    @OneToMany(mappedBy = "siteId", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<LemmaEntity> lemmas;
}
