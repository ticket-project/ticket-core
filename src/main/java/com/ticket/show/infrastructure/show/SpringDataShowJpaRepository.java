package com.ticket.show.infrastructure.show;

import com.ticket.show.domain.show.Show;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataShowJpaRepository extends JpaRepository<Show, Long> {
}
