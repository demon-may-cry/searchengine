package searchengine.model;

import jakarta.persistence.*;

@Entity
@Table(name = "page", indexes = @Index(columnList = "path", name = "path_index", unique = true))
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "site_id", columnDefinition = "INT", nullable = false)
    private Integer siteId;

    @Column(name = "path", columnDefinition = "VARCHAR(255)", nullable = false)
    private String path;

    @Column(name = "code", columnDefinition = "INT", nullable = false)
    private Integer code;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
