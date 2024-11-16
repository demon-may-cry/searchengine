package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;

@Repository
public interface PageRepositories extends JpaRepository<PageEntity, Long> {
    PageEntity findByPath(String path);
}
