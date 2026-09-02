package com.ticket.catalog;

import java.util.List;

/**
 * metadata module이 조합하는 코드/라벨 목록 중 catalog가 소유한 부분이다.
 * 어떤 module도 catalog internal enum·entity를 직접 import하지 않고 이 계약만 쓴다.
 */
public interface CatalogMetadata {

    List<CategoryCode> categories();

    List<GenreCode> genres();

    List<CodeLabel> bookingStatuses();

    List<CodeLabel> saleTypes();

    List<CodeLabel> regions();

    List<CodeLabel> showSortKeys();

    record CategoryCode(long id, String code, String name) {
    }

    record GenreCode(long id, String categoryCode, String code, String name) {
    }

    record CodeLabel(String code, String label) {
    }
}
