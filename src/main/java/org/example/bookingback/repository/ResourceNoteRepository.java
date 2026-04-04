package org.example.bookingback.repository;

import org.example.bookingback.entity.ResourceNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceNoteRepository extends JpaRepository<ResourceNote, Long> {
}
