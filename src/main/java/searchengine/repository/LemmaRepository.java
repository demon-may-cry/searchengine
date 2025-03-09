package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    int countBySiteId(SiteEntity siteEntity);
}