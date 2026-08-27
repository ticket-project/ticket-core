package com.ticket.core.domain.show.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShowFinder {

    private final ShowRepository showRepository;

    public Show findById(final Long showId) {
        return showRepository.findById(showId)
                .orElseThrow(() -> new CoreException(DomainErrorType.DATA_NOT_FOUND,
                        "공연을 찾을 수 없습니다. id=" + showId));
    }

    public void validateShowExists(final Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new CoreException(DomainErrorType.DATA_NOT_FOUND,
                    "공연을 찾을 수 없습니다. id=" + showId);
        }
    }

}
