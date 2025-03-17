package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Lemma — леммы, встречающиеся в текстах
 */
@Getter
@Setter
@Entity
@Table(name = "lemma")
public class LemmaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /**
     * ID веб-сайта из таблицы site
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", columnDefinition = "INT", nullable = false)
    private SiteEntity siteId;

    /**
     * Нормальная форма слова (лемма)
     */
    @Column(name = "lemma", columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;

    /**
     * Количество страниц, на которых слово
     * встречается хотя бы один раз. Максимальное значение не может
     * превышать общее количество слов на сайте.
     */
    @Column(name = "frequency", columnDefinition = "INT", nullable = false)
    private int frequency;

    @OneToMany(mappedBy = "lemmaId", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<IndexEntity> indexLemmas;
}