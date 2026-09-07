package com.ticket.show;

/**
 * 다른 module이 show에 연결된 공연장의 좌석 맵 배치를 조회할 때 쓰는 공개 계약이다. JPA entity를
 * 노출하지 않는다.
 *
 * <p>이 계약은 임시다 — Venue BC가 별도 module로 분리되면 이 endpoint(그리고 이 interface)는
 * venue 소유 계약을 호출하는 show의 컨트롤러/use case로 흡수되며 사라질 예정이다.
 */
public interface VenueLayoutLookup {

    /**
     * show가 없거나 공연장이 연결되지 않았으면 {@code com.ticket.error.NotFoundException}을 던진다.
     */
    VenueLayout getVenueLayout(long showId);
}
