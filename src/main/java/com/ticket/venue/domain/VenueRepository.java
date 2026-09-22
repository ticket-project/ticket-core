package com.ticket.venue.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ticket.venue.api.Region;

/**
 * Venue aggregate의 복원을 담당하는 도메인 Repository다.
 *
 * <p>엔티티를 그대로 돌려준다 — 공개 계약({@code venue.api})으로의 변환은 그것을 구현하는 use case가 한다({@code docs/readability-guidelines.md}
 * §10-1).
 */
public interface VenueRepository {
    Optional<Venue> findById(Long venueId);

    List<Venue> findAllById(Collection<Long> venueIds);

    /** 이 단계는 엔티티가 아직 필요 없고 id 집합만 쓰이므로 scalar로 읽는다. */
    List<Long> findIdsByRegion(Region region);
}
