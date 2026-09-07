package com.ticket.favorite.domain.showlike.model;

import com.ticket.favorite.domain.FavoriteAuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.Objects;

@Getter
@Entity
@Table(
        name = "SHOW_LIKES",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_SHOW_LIKES_MEMBER_SHOW", columnNames = {"member_id", "show_id"})
        },
        indexes = {
                @Index(name = "IDX_SHOW_LIKES_MEMBER_ID_ID", columnList = "member_id,id"),
                @Index(name = "IDX_SHOW_LIKES_SHOW_ID", columnList = "show_id")
        }
)
public class ShowLike extends FavoriteAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * member가 소유한 회원의 scalar 참조다. 모듈을 넘나드는 JPA 연관관계는 금지되므로
     * {@code @ManyToOne}이 아니라 id 컬럼만 갖는다(ADR 0003 §4).
     */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /**
     * show가 소유한 공연의 scalar 참조다. 원래는 {@code @ManyToOne Show}였지만, 찜이 별도
     * module(favorite)로 분리되며 모듈을 넘나드는 JPA 연관관계를 금지하는 규칙(ADR 0003 §4)에
     * 맞춰 scalar id column으로 바뀌었다. show 존재 확인은 이 module의 책임이 아니다 — 호출자가
     * 이미 확인했다는 전제다.
     */
    @Column(name = "show_id", nullable = false)
    private Long showId;

    protected ShowLike() {
    }

    public ShowLike(final Long memberId, final Long showId) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.showId = Objects.requireNonNull(showId, "showId must not be null");
    }

}
