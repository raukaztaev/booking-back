package org.example.bookingback.repository;

import org.example.bookingback.entity.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface ResourceRepository extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {

    @Override
    @EntityGraph(attributePaths = {"owner"})
    Page<Resource> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"owner"})
    Optional<Resource> findById(Long id);
}
