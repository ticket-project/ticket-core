package com.ticket.booking.hold.domain;

import java.util.List;

import org.springframework.data.repository.Repository;

/** hold 이력의 저장과 복원을 담당하는 도메인 Repository다. 구현은 Spring Data JPA가 만든다. */
public interface HoldHistoryRepository extends Repository<HoldHistory, Long> {
    <S extends HoldHistory> List<S> saveAll(Iterable<S> holdHistories);
}
