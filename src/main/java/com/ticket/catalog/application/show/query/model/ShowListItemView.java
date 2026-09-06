package com.ticket.catalog.application.show.query.model;

import com.ticket.catalog.domain.show.Region;
import com.ticket.catalog.domain.show.SaleType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ShowListItemView(
        Long id,
        String title,
        String subTitle,
        String image,
        List<String> genreNames,
        LocalDate startDate,
        LocalDate endDate,
        long viewCount,
        SaleType saleType,
        LocalDateTime saleStartDate,
        LocalDateTime saleEndDate,
        LocalDateTime createdAt,
        Region region,
        String venue
) {
}
