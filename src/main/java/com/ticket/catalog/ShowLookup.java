package com.ticket.catalog;

import java.util.Map;
import java.util.Set;

/**
 * 다른 module이 show 존재 확인과 표시값 조합에 쓰는 공개 계약이다. JPA entity를 노출하지 않는다.
 */
public interface ShowLookup {

    /**
     * show가 존재하지 않으면 {@code com.ticket.core.support.exception.CoreException}
     * ({@code ErrorType.NOT_FOUND_DATA})을 던진다. 존재하면 아무 것도 하지 않는다.
     */
    void requireExisting(long showId);

    /**
     * 빈 {@code showIds}는 빈 map을 반환한다. 존재하지 않는 ID는 결과 map에서 조용히 빠진다 —
     * 어떤 ID가 없었는지 의미를 부여하는 것은 호출자의 몫이다.
     */
    Map<Long, ShowSummary> getSummaries(Set<Long> showIds);
}
