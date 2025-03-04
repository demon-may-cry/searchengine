package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * index — поисковый индекс
 */
@Getter
@Setter
@Entity
@Table(name = "index_entity")
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Идентификатор страницы
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", columnDefinition = "INT", nullable = false)
    private PageEntity pageId;

    /**
     * Идентификатор леммы
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lemma_id", columnDefinition = "INT", nullable = false)
    private LemmaEntity lemmaId;

    /**
     * Количество данной леммы для данной
     * страницы
     */
    @Column(name = "rank_count", columnDefinition = "FLOAT", nullable = false)
    private float rank;

}