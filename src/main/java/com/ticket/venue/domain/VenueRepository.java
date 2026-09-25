package com.ticket.venue.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Venue aggregate의 복원을 담당하는 도메인 Repository다.
 *
 * <p>엔티티를 돌려주고 공개 계약 변환은 use case가 한다({@code docs/coding-guidelines.md}).
 */
public interface VenueRepository {
    Optional<Venue> findById(Long venueId);

    List<Venue> findAllById(Collection<Long> venueIds);

    /** 이 단계는 엔티티가 아직 필요 없고 id 집합만 쓰이므로 scalar로 읽는다. */
    List<Long> findIdsByRegion(Region region);
}
