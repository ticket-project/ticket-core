package com.ticket.show.domain;

import com.ticket.show.domain.ShowAuditedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공연-장르 연결 엔티티
 * - 공연은 0개 이상의 장르를 가질 수 있음
 */
@Getter
@Entity
@Table(name = "SHOW_GENRES")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowGenre extends ShowAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Show·Genre는 각각 ShowGenre와 다른 aggregate라 식별자로만 참조한다(같은 BC 안이어도 aggregate
     * 경계를 넘는 참조는 ID로 한다 — {@code docs/architecture.md}의 참조 규칙). 컬럼명은 옛
     * {@code @ManyToOne} 매핑과 같은 {@code show_id}/{@code genre_id}를 그대로 쓴다.
     */
    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "genre_id", nullable = false)
    private Long genreId;

    public ShowGenre(Long showId, Long genreId) {
        this.showId = showId;
        this.genreId = genreId;
    }

}
