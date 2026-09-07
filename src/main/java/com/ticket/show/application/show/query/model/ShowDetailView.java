package com.ticket.show.application.show.query.model;

import com.ticket.show.application.show.query.GetShowDetailUseCase;
import com.ticket.show.domain.show.BookingStatus;
import com.ticket.venue.Region;
import com.ticket.show.domain.show.SaleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code likeCount}는 담지 않는다 — 찜 개수는 favorite module 소유라 {@link GetShowDetailUseCase}가
 * 별도로 조회해 응답에 합친다.
 */
public record ShowDetailView(
        Long id, String title, String subTitle, String info, LocalDate startDate, LocalDate endDate,
        Integer runningMinutes, long viewCount, BookingStatus bookingStatus, SaleType saleType,
        LocalDateTime saleStartDate, LocalDateTime saleEndDate, String image,
        GetShowDetailUseCase.VenueInfo venue, GetShowDetailUseCase.PerformerInfo performer,
        List<String> genreNames, GetShowDetailUseCase.PriceSummary priceSummary,
        List<GetShowDetailUseCase.PerformanceDateInfo> performanceDates
) {}
