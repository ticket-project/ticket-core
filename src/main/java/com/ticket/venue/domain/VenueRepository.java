package com.ticket.venue.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Venue aggregate의 복원을 담당하는 도메인 Repository다. 구현은 Spring Data JPA가 만든다.
 *
 * <p>엔티티를 돌려주고 공개 계약 변환은 use case가 한다({@code docs/coding-guidelines.md}).
 */
public interface VenueRepository extends Repository<Venue, Long> {
    Optional<Venue> findById(Long venueId);

    List<Venue> findAllById(Iterable<Long> venueIds);

    /** 이 단계는 엔티티가 아직 필요 없고 id 집합만 쓰이므로 scalar로 읽는다. */
    @Query("SELECT v.id FROM Venue v WHERE v.region = :region")
    List<Long> findIdsByRegion(@Param("region") Region region);
}
