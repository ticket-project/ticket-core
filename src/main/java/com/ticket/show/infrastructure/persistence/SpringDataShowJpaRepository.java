package com.ticket.show.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.domain.show.Show;

interface SpringDataShowJpaRepository extends JpaRepository<Show, Long> {}
