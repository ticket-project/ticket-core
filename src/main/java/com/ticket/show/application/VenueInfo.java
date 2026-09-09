package com.ticket.show.application;

import com.ticket.show.application.usecase.GetShowDetailUseCase;

import com.ticket.venue.Region;

import java.math.BigDecimal;

/**
 * show 상세에 쓰는 venue 표시값 조합 결과다. venue module의 {@code VenueSummary}를 이 use case의
 * 응답 모양(좌석 배치 등 여기서 쓰지 않는 필드는 뺀)으로 옮겨 담는다 — application 계층에서
 * 조합한다({@code GetShowDetailUseCase}), infrastructure는 조합하지 않는다.
 */
public record VenueInfo(
        Long id,
        String name,
        String address,
        Region region,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        String imageUrl
) {
}
