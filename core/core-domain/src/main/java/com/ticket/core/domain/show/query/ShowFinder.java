package com.ticket.core.domain.show.query;

import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.repository.ShowJpaRepository;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShowFinder {

    private final ShowJpaRepository showJpaRepository;

    public Show findById(final Long showId) {
        return showJpaRepository.findById(showId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA,
                        "공연을 찾을 수 없습니다. id=" + showId));
    }

    public void validateShowExists(final Long showId) {
        if (!showJpaRepository.existsById(showId)) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA,
                    "공연을 찾을 수 없습니다. id=" + showId);
        }
    }

}
