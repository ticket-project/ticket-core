package com.ticket.show.application;

import java.math.BigDecimal;

/** 기존 공연 상세 응답에서 사용하는 대표 회차의 등급별 가격이다. */
public record ShowGradeView(Long id, String gradeName, BigDecimal price) {}
