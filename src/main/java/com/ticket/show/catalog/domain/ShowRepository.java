package com.ticket.show.catalog.domain;

import com.ticket.show.catalog.application.port.ShowDetailQueryPort;
import com.ticket.show.catalog.application.port.ShowListQueryPort;

import com.ticket.show.catalog.domain.Show;

import java.util.Optional;

/**
 * 공연 aggregate의 복원을 담당하는 도메인 Repository다.
 *
 * <p>목록/상세 같은 읽기 전용 조회는 {@code show.catalog.application.port}의 `*QueryPort`
 * 인터페이스(예: {@code ShowListQueryPort}, {@code ShowDetailQueryPort})가 담당한다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다.
 * 맥락에 따라 인증 실패일 수도, not-found일 수도, 멱등 성공일 수도 있다.
 */
public interface ShowRepository {

    Optional<Show> findById(Long showId);

    boolean existsById(Long showId);
}
