package com.ticket.core.domain.show.repository;

import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.show.model.Show;
import com.ticket.support.error.CoreException;

import java.util.Optional;

/**
 * 공연 aggregate의 복원을 담당하는 도메인 Repository다.
 *
 * <p>목록/상세 같은 읽기 전용 조회는 core-app의 read repository가 담당한다.
 */
public interface ShowRepository {

    Optional<Show> findById(Long showId);

    boolean existsById(Long showId);

    /**
     * 공연을 반환하고, 없으면 도메인 오류를 던진다.
     */
    default Show getById(final Long showId) {
        return findById(showId)
                .orElseThrow(() -> new CoreException(DomainErrorType.DATA_NOT_FOUND,
                        "공연을 찾을 수 없습니다. id=" + showId));
    }

    /**
     * 공연이 존재하는지 확인하고, 없으면 도메인 오류를 던진다.
     */
    default void requireExists(final Long showId) {
        if (!existsById(showId)) {
            throw new CoreException(DomainErrorType.DATA_NOT_FOUND,
                    "공연을 찾을 수 없습니다. id=" + showId);
        }
    }
}
