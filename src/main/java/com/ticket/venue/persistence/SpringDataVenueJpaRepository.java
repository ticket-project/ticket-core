package com.ticket.venue.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.venue.api.Region;
import com.ticket.venue.domain.Venue;

/**
 * venue 조회다. 상속받은 {@code findById}·{@code findAllById}가 엔티티를 그대로 주므로 별도 조회를 두지 않는다 — 공개 계약 타입으로의
 * 변환은 {@link VenueRepositoryAdapter}가 한다({@code docs/readability-guidelines.md} §10-1).
 *
 * <p>지역별 id 조회만 남는다. 이 단계는 엔티티가 아직 필요 없고 id 집합만 쓰이므로 scalar로 읽는다.
 */
interface SpringDataVenueJpaRepository extends JpaRepository<Venue, Long> {
    @Query("SELECT v.id FROM Venue v WHERE v.region = :region")
    List<Long> findIdsByRegion(@Param("region") Region region);
}
