package com.ticket.venue;

import lombok.Getter;

/**
 * Venue의 행정 지역이다. show가 검색 조건("이 지역 공연장에서 하는 공연")과 표시값으로 함께
 * 쓰는 공용 어휘라, root 공개 계약의 "interface + record만" 원칙의 예외로 여기 둔다(package-info
 * 참고).
 */
@Getter
public enum Region {
    SEOUL("서울"),
    GYEONGGI("경기"),
    INCHEON("인천"),
    GANGWON("강원"),
    CHUNGCHEONG("충청"),
    JEOLLA("전라"),
    GYEONGSANG("경상"),
    JEJU("제주");

    private final String description;

    Region(String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

}
