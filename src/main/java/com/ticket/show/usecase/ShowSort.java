package com.ticket.show.usecase;

import java.util.Set;

import com.ticket.show.exception.ShowErrorCode;
import com.ticket.show.exception.ShowException;

/**
 * Show 목록/검색 정렬 기준의 단일 typed contract다.
 *
 * <p>HTTP sort 문자열은 API/app 경계에서 {@link #from(String)}으로 한 번만 파싱한다. 이후 {@code ShowQuerydslRepository}와 infra Querydsl
 * 구현은 이 타입만 주고받고, 문자열로 다시 되돌아가지 않는다.
 */
public enum ShowSort {
    POPULAR("popular"),
    LATEST("latest"),
    SHOW_START_APPROACHING("showStartApproaching"),
    SALE_START_APPROACHING("saleStartApproaching");

    /**
     * 판매 시작 시각으로 거르지 않는 목록(전체 목록·검색)이 받는 정렬이다. 판매 시작 임박순은 판매 시작 시각이 없는 공연의 정렬 키가 null이라 받지 않는다. 공연 임박순은 정렬할 때 시작일이 오늘
     * 이후인 공연만 남기므로 키가 있다.
     */
    static final Set<ShowSort> WITHOUT_SALE_START = Set.of(POPULAR, LATEST, SHOW_START_APPROACHING);

    private final String apiValue;

    ShowSort(final String apiValue) {
        this.apiValue = apiValue;
    }

    public static ShowSort from(final String apiValue) {
        if (apiValue == null || apiValue.isBlank()) {
            return POPULAR;
        }
        for (final ShowSort sort : values()) {
            if (sort.apiValue.equalsIgnoreCase(apiValue)) {
                return sort;
            }
        }
        throw unsupported(apiValue);
    }

    /**
     * 이 정렬을 {@code supported} 안에서만 받는다. 각 목록 use case가 자기 조회에서 정렬 키가 null이 될 수 없는 정렬만 넘긴다 — null 키 행이 페이지 끝에 오면 다음 커서를
     * 만들지 못해 500이 된다({@code ShowQuerydslRepository#resolveLastValue}).
     *
     * @throws ShowException E7002, 이 조회가 받지 않는 정렬일 때. {@code error.data}에는 이 정렬의 API 값이 실린다.
     */
    public ShowSort requireOneOf(final Set<ShowSort> supported) {
        if (!supported.contains(this)) {
            throw unsupported(apiValue);
        }
        return this;
    }

    /** 정렬 <b>원문</b>을 대소문자·공백 정규화 없이 {@code error.data}에 싣는다(기존 응답 계약). 접두어를 여기 한 곳에서만 붙인다. */
    private static ShowException unsupported(final String apiValue) {
        return new ShowException(ShowErrorCode.E7002, "지원하지 않는 sort: " + apiValue);
    }
}
