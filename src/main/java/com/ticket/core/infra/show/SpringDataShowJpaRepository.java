package com.ticket.core.infra.show;

import com.ticket.core.domain.show.model.Show;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataShowJpaRepository extends JpaRepository<Show, Long> {
}
