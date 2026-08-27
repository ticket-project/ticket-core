package com.ticket.core.domain.show.repository;

import com.ticket.core.domain.show.model.Show;

import java.util.Optional;

/**
 * 공연 aggregate의 복원을 담당하는 도메인 Repository다.
 *
 * <p>목록/상세 같은 읽기 전용 조회는 core-app의 read repository가 담당한다.
 */
public interface ShowRepository {

    Optional<Show> findById(Long showId);

    boolean existsById(Long showId);
}
