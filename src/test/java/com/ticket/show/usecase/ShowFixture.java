package com.ticket.show.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.show.domain.show.SaleType;
import com.ticket.show.domain.show.Show;

/**
 * 조회 결과로 {@link Show} 엔티티를 받는 use case 단위 테스트가 쓰는 fixture다. id와 {@code createdAt}은 JPA가 채우는 필드라 생성자
 * 대신 reflection으로 심는다.
 */
final class ShowFixture {
    private ShowFixture() {}

    static Show show(final Long id, final String title, final @Nullable Long venueId) {
        return show(id, title, venueId, null, null, null, 0L, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    static Show show(
            final Long id,
            final String title,
            final @Nullable Long venueId,
            final @Nullable LocalDate startDate,
            final @Nullable LocalDate endDate,
            final @Nullable LocalDateTime displaySaleStartsAt,
            final long viewCount,
            final LocalDateTime createdAt) {
        return show(
                id,
                title,
                "subtitle",
                "image",
                venueId,
                startDate,
                endDate,
                displaySaleStartsAt,
                null,
                viewCount,
                createdAt);
    }

    static Show show(
            final Long id,
            final String title,
            final @Nullable String subTitle,
            final @Nullable String image,
            final @Nullable Long venueId,
            final @Nullable LocalDate startDate,
            final @Nullable LocalDate endDate,
            final @Nullable LocalDateTime displaySaleStartsAt,
            final @Nullable LocalDateTime displaySaleEndsAt,
            final long viewCount,
            final LocalDateTime createdAt) {
        final Show show =
                new Show(
                        title,
                        subTitle,
                        "info",
                        startDate,
                        endDate,
                        viewCount,
                        SaleType.GENERAL,
                        displaySaleStartsAt,
                        displaySaleEndsAt,
                        image,
                        venueId,
                        null,
                        null);
        ReflectionTestUtils.setField(show, "id", id);
        ReflectionTestUtils.setField(show, "createdAt", createdAt);
        return show;
    }
}
