package com.ticket.show.domain;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** 출연자 aggregate를 식별자로 복원한다. 부재 시 응답은 호출하는 use case가 정한다. 구현은 Spring Data JPA가 만든다. */
public interface PerformerRepository extends Repository<Performer, Long> {
    Optional<Performer> findById(Long performerId);
}
