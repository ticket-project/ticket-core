package com.ticket.show.performance.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.performance.domain.Grade;

interface SpringDataGradeJpaRepository extends JpaRepository<Grade, Long> {
    List<Grade> findAllByOrderByCodeAsc();
}
