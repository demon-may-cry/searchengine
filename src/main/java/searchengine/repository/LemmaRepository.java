package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Set;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    int countBySiteId(SiteEntity siteEntity);

    List<LemmaEntity> findByLemmaInAndFrequencyLessThanOrderByFrequencyAsc(Set<String> lemmas, int threshold);

}