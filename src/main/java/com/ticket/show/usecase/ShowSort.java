package com.ticket.show.usecase;

import java.util.Set;

import com.ticket.show.exception.UnsupportedShowSortException;

/**
 * Show 목록/검색 정렬 기준의 단일 typed contract다.
 *
 * <p>HTTP sort 문자열은 API/app 경계에서 {@link #from(String)}으로 한 번만 파싱한다. 이후 {@code ShowQuerydslRepository}와 infra Querydsl
 * 구현은 이 타입만 주고받고, 문자열로 다시 되돌아가지 않는다.
 */
public enum ShowSort {
    POPULAR("popular", "인기순"),
    LATEST("latest", "최신순"),
    SHOW_START_APPROACHING("showStartApproaching", "공연 임박순"),
    SALE_START_APPROACHING("saleStartApproaching", "판매 오픈 임박순");
    private final String apiValue;
    private final String description;

    ShowSort(final String apiValue, final String description) {
        this.apiValue = apiValue;
        this.description = description;
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
        throw new UnsupportedShowSortException(apiValue);
    }

    /**
     * 이 정렬을 {@code supported} 안에서만 받는다. 각 목록 use case가 자기 조회에서 정렬 키가 null이 될 수 없는 정렬만 넘긴다 — null 키 행이 페이지 끝에 오면 다음 커서를
     * 만들지 못해 500이 된다({@code ShowQuerydslRepository#resolveLastValue}).
     *
     * @throws UnsupportedShowSortException 이 조회가 받지 않는 정렬일 때. {@code error.data}에는 {@link #apiValue()}가 실린다.
     */
    public ShowSort requireOneOf(final Set<ShowSort> supported) {
        if (!supported.contains(this)) {
            throw new UnsupportedShowSortException(apiValue);
        }
        return this;
    }

    public String apiValue() {
        return apiValue;
    }

    public String getDescription() {
        return description;
    }
}
