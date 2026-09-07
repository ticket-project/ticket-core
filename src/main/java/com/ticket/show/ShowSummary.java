package com.ticket.show;

import java.time.LocalDate;

/**
 * showlike 같은 다른 module이 응답을 조합할 때 필요한 show/venue 표시값만 담은 불변 snapshot이다.
 * venue가 없는 show는 {@code venueName}이 {@code null}이다.
 */
public record ShowSummary(
        long showId,
        String title,
        String image,
        LocalDate startDate,
        LocalDate endDate,
        String venueName
) {
}
