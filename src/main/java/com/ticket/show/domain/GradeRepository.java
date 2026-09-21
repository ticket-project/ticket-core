package com.ticket.show.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 좌석 등급 코드(Grade) aggregate의 복원을 담당하는 도메인 Repository다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다.
 */
public interface GradeRepository {
    Optional<Grade> findById(Long gradeId);

    List<Grade> findAllOrderByCodeAsc();

    /** 등급 id 집합으로 등급을 한 번에 복원한다. 빈 {@code gradeIds}는 빈 map을 반환한다. */
    Map<Long, Grade> findGradeNames(Collection<Long> gradeIds);
}
