package com.ticket.show.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.repository.Repository;

/**
 * 좌석 등급 코드(Grade) aggregate의 복원을 담당하는 도메인 Repository다. 구현은 Spring Data JPA가 만든다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다.
 */
public interface GradeRepository extends Repository<Grade, Long> {
    /** 등급 id 집합으로 등급을 한 번에 복원한다. 빈 {@code gradeIds}는 빈 map을 반환한다. */
    default Map<Long, Grade> findGradeNames(final Collection<Long> gradeIds) {
        if (gradeIds.isEmpty()) {
            return Map.of();
        }
        return findAllById(gradeIds).stream().collect(Collectors.toMap(Grade::getId, grade -> grade));
    }

    List<Grade> findAllById(Iterable<Long> gradeIds);
}
