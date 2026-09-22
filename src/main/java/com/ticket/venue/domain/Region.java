package com.ticket.venue.domain;

import com.ticket.shared.exception.InvalidRequestException;

import lombok.Getter;

/**
 * Venue의 행정 지역이다. Venue entity의 필드이자 venue가 값 집합을 소유하는 도메인 어휘다.
 *
 * <p>다른 module에는 이 enum을 노출하지 않는다 — 나갈 때는 {@code VenueSnapshot.RegionView}(코드·표시명 쌍)로, 들어올 때는 {@link #from(String)}이 받는
 * 코드 문자열로 오간다. 값 집합을 아는 쪽이 판정도 해야 한다.
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

    /**
     * 지역 코드 문자열을 enum으로 바꾼다. 앞뒤 공백은 지운다 — 이전에 Spring의 enum 변환기가 하던 관용을 그대로 유지한다.
     *
     * @throws InvalidRequestException 값이 지역 코드가 아니면 던진다(400)
     */
    public static Region from(final String code) {
        try {
            return Region.valueOf(code.trim());
        } catch (final IllegalArgumentException exception) {
            throw new InvalidRequestException("region 값이 올바르지 않습니다: " + code);
        }
    }
}
