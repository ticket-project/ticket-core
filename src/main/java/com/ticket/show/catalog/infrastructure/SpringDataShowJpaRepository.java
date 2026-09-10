package com.ticket.show.catalog.infrastructure;

import com.ticket.show.catalog.domain.Show;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataShowJpaRepository extends JpaRepository<Show, Long> {
}
