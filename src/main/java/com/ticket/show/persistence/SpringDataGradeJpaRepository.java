package com.ticket.show.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.domain.Grade;

interface SpringDataGradeJpaRepository extends JpaRepository<Grade, Long> {}
