package com.ticket.show.domain;

import java.util.Optional;

/** 출연자 aggregate를 식별자로 복원한다. 부재 시 응답은 호출하는 use case가 정한다. */
public interface PerformerRepository {
    Optional<Performer> findById(Long performerId);
}
