package com.ticket.show.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.domain.Grade;

interface SpringDataGradeJpaRepository extends JpaRepository<Grade, Long> {
    List<Grade> findAllByOrderByCodeAsc();
}
