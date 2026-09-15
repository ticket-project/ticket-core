package com.ticket.show.application;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import com.ticket.venue.api.Region;

/**
 * show 상세에 쓰는 venue 표시값 조합 결과다. venue module의 {@code VenueSummary}를 이 use case의 응답 모양(좌석 배치 등 여기서
 * 쓰지 않는 필드는 뺀)으로 옮겨 담는다 — application 계층에서 조합한다({@code GetShowDetailUseCase}), infrastructure는 조합하지
 * 않는다.
 */
public record VenueInfo(
        Long id,
        @Nullable String name,
        @Nullable String address,
        @Nullable Region region,
        @Nullable BigDecimal latitude,
        @Nullable BigDecimal longitude,
        @Nullable String phone,
        @Nullable String imageUrl) {}
