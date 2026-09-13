package com.ticket.show.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.catalog.domain.Show;

interface SpringDataShowJpaRepository extends JpaRepository<Show, Long> {}
