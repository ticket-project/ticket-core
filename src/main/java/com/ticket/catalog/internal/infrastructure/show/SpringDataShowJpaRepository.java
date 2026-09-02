package com.ticket.catalog.internal.infrastructure.show;

import com.ticket.catalog.internal.domain.show.Show;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataShowJpaRepository extends JpaRepository<Show, Long> {
}
