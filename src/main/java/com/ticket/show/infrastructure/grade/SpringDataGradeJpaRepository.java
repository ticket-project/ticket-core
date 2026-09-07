package com.ticket.show.infrastructure.grade;

import com.ticket.show.domain.grade.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataGradeJpaRepository extends JpaRepository<Grade, Long> {

    List<Grade> findAllByOrderByCodeAsc();
}
