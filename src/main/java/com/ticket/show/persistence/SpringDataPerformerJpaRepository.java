package com.ticket.show.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.domain.Performer;

interface SpringDataPerformerJpaRepository extends JpaRepository<Performer, Long> {}
