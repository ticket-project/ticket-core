package com.ticket.catalog.internal.application.show.query;

import com.ticket.catalog.internal.exception.UnsupportedShowSortException;

/**
 * Show 목록/검색 정렬 기준의 단일 typed contract다.
 *
 * <p>HTTP sort 문자열은 API/app 경계에서 {@link #from(String)}으로 한 번만 파싱한다. 이후
 * {@code ShowListReadRepository}와 infra Querydsl 구현은 이 타입만 주고받고, 문자열로 다시
 * 되돌아가지 않는다.
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
        throw new UnsupportedShowSortException("지원하지 않는 sort: " + apiValue);
    }

    public String apiValue() {
        return apiValue;
    }

    public String getDescription() {
        return description;
    }
}
