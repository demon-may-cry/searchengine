package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

@Repository
@Transactional
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {
    SiteEntity findByUrl(String url);

    void deleteByUrl(String url);
}
