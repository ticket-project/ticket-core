package com.ticket.show.infrastructure;

import com.ticket.show.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataGradeJpaRepository extends JpaRepository<Grade, Long> {

    List<Grade> findAllByOrderByCodeAsc();
}
