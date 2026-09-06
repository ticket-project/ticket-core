package com.ticket.catalog.infrastructure.show;

import com.ticket.catalog.domain.show.Show;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataShowJpaRepository extends JpaRepository<Show, Long> {
}
