package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexEntity;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
}