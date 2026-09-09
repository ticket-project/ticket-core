package com.ticket.show.application;

import java.math.BigDecimal;

/**
 * ADR 0005: show-level 가격표(과거 ShowGrade)는 폐기됐다. 등급·가격은 회차(Performance)마다
 * 다를 수 있어 show 상세는 그 회차들의 PerformanceGrade.price 중 최소/최대만 요약해 보여준다.
 * 정확한 가격은 회차를 고른 뒤 그 회차의 등급 API로 확인한다. 이 show에 등급이 하나도 없으면
 * {@code null}이다.
 */
public record PriceSummary(BigDecimal minPrice, BigDecimal maxPrice) {
}
