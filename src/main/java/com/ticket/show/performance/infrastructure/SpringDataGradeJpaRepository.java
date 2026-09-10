package com.ticket.show.performance.infrastructure;

import com.ticket.show.performance.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataGradeJpaRepository extends JpaRepository<Grade, Long> {

    List<Grade> findAllByOrderByCodeAsc();
}
