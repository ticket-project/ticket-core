package com.ticket.show.application.show.query.model;

import com.ticket.show.application.show.query.GetShowDetailUseCase;
import com.ticket.show.domain.performance.policy.BookingEntryResolver;
import com.ticket.show.domain.show.BookingStatus;
import com.ticket.show.domain.show.Region;
import com.ticket.show.domain.show.SaleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ShowDetailView(
        Long id, String title, String subTitle, String info, LocalDate startDate, LocalDate endDate,
        Integer runningMinutes, long viewCount, long likeCount, BookingStatus bookingStatus, SaleType saleType,
        LocalDateTime saleStartDate, LocalDateTime saleEndDate, String image,
        GetShowDetailUseCase.VenueInfo venue, GetShowDetailUseCase.PerformerInfo performer,
        List<String> genreNames, GetShowDetailUseCase.PriceSummary priceSummary,
        List<GetShowDetailUseCase.PerformanceDateInfo> performanceDates
) {}
