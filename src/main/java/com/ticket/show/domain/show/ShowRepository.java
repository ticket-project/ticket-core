package com.ticket.show.domain.show;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 공연 aggregate의 복원을 담당하는 도메인 Repository다.
 *
 * <p>동적 조건·커서 페이징이 필요한 목록/검색 조회는 {@code show.persistence}의 Querydsl 조회가 담당한다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다. 맥락에 따라 인증 실패일 수도, not-found일 수도, 멱등 성공일
 * 수도 있다.
 */
public interface ShowRepository {
    Optional<Show> findById(Long showId);

    boolean existsById(Long showId);

    /** 찜 목록처럼 id 집합으로 show를 한 번에 복원한다. 빈 {@code showIds}는 빈 map을 반환한다. */
    Map<Long, Show> findSummaries(Set<Long> showIds);

    /** 이 show에 붙은 장르 이름을 반환한다. */
    List<String> findGenreNames(Long showId);
}
