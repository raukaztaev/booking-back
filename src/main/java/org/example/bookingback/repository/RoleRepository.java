package org.example.bookingback.repository;

import java.util.Optional;
import org.example.bookingback.entity.Role;
import org.example.bookingback.entity.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
