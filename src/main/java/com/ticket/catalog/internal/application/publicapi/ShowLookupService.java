package com.ticket.catalog.internal.application.publicapi;

import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.ShowSummary;
import com.ticket.catalog.internal.application.show.query.ShowSummaryBatchReadRepository;
import com.ticket.catalog.internal.domain.show.repository.ShowRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * {@link ShowLookup}의 catalog 소유 구현이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowLookupService implements ShowLookup {

    private final ShowRepository showRepository;
    private final ShowSummaryBatchReadRepository showSummaryBatchReadRepository;

    @Override
    public void requireExisting(final long showId) {
        if (!showRepository.existsById(showId)) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA, "공연을 찾을 수 없습니다. id=" + showId);
        }
    }

    @Override
    public Map<Long, ShowSummary> getSummaries(final Set<Long> showIds) {
        return showSummaryBatchReadRepository.findSummaries(showIds);
    }
}
