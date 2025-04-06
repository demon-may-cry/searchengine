package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Set;

@Repository
@Transactional
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    int countBySiteId(SiteEntity siteEntity);

    List<LemmaEntity> findByLemmaInAndFrequencyGreaterThanOrderByFrequencyAsc(Set<String> lemmas, int threshold);

    List<LemmaEntity> findBySiteId(SiteEntity siteId);

    @Modifying
    @Query("UPDATE LemmaEntity l SET l.frequency = l.frequency - 1 WHERE l IN :lemmas AND l.frequency > 0")
    void decrementFrequencyForLemmas(@Param("lemmas") List<LemmaEntity> lemmas);

    @Modifying
    @Query("DELETE FROM LemmaEntity l WHERE l.frequency <= 0")
    void deleteWhereFrequencyZero();
}