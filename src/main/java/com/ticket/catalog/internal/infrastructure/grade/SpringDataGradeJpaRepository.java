package com.ticket.catalog.internal.infrastructure.grade;

import com.ticket.catalog.internal.domain.grade.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataGradeJpaRepository extends JpaRepository<Grade, Long> {

    List<Grade> findAllByOrderByCodeAsc();
}
