package com.ticket.show.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.domain.show.Show;

interface SpringDataShowJpaRepository extends JpaRepository<Show, Long> {}
