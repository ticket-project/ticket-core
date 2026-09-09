package com.ticket.show.infrastructure;

import com.ticket.show.domain.Show;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataShowJpaRepository extends JpaRepository<Show, Long> {
}
