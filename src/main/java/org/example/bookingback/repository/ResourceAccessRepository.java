package org.example.bookingback.repository;

import org.example.bookingback.entity.ResourceAccess;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceAccessRepository extends JpaRepository<ResourceAccess, Long> {

    boolean existsByResourceIdAndUserId(Long resourceId, Long userId);
}
