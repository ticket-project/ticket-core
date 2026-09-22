package com.ticket.show.domain.show;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.ticket.shared.jpa.AuditedEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공연-장르 연결 엔티티 - 공연은 0개 이상의 장르를 가질 수 있음 */
@Getter
@Entity
@Table(name = "SHOW_GENRES")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowGenre extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ShowGenre는 자기 aggregate root가 아니라 Show aggregate에 딸린 연결 entity다 — 전용 Repository가 없고 Show를 통해서만 만들어진다. Genre는
     * 다른 aggregate라 어느 쪽도 객체 참조로 들지 않고 식별자로만 참조한다(같은 BC 안이어도 aggregate 경계를 넘는 참조는 ID로 한다 —
     * {@code docs/architecture.md}의 참조 규칙). 컬럼명은 옛 {@code @ManyToOne} 매핑과 같은 {@code show_id}/{@code genre_id}를 그대로 쓴다.
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
