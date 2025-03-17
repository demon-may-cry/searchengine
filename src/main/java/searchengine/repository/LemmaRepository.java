package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    int countBySiteId(SiteEntity siteEntity);

    List<LemmaEntity> findByLemmaInAndFrequencyLessThanOrderByFrequencyAsc(Set<String> lemmas, int threshold);

    List<LemmaEntity> findBySiteId(SiteEntity siteId);
}