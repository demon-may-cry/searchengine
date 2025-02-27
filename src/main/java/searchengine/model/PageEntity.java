package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Page — проиндексированные страницы сайта
 */
@Setter
@Getter
@Entity
@Table(name = "page", indexes = @Index(columnList = "path", name = "path_index"))
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * ID веб-сайта из таблицы site
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "site_id", columnDefinition = "INT", nullable = false)
    private SiteEntity siteId;

    /**
     * Адрес страницы от корня сайта
     */
    @Column(name = "path", columnDefinition = "VARCHAR(255)", nullable = false)
    private String path;

    /**
     * Код HTTP-ответа, полученный при запросе
     * страницы (например, 200, 404, 500 или другие)
     */
    @Column(name = "code", columnDefinition = "INT", nullable = false)
    private int code;

    /**
     * Контент страницы (HTML-код)
     */
    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
