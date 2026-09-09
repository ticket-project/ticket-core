package com.ticket.like.domain;

import com.ticket.like.LikeType;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.Objects;

@Getter
@Entity
@Table(
        name = "LIKES",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_LIKES_MEMBER_TARGET", columnNames = {"member_id", "like_type", "target_id"})
        },
        indexes = {
                @Index(name = "IDX_LIKES_MEMBER_ID_ID", columnList = "member_id,id"),
                @Index(name = "IDX_LIKES_TARGET", columnList = "like_type,target_id")
        }
)
public class Like extends LikeAuditedEntity {

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
     * 찜 대상의 종류다. 지금은 {@link LikeType#SHOW} 하나뿐이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "like_type", nullable = false, length = 20)
    private LikeType likeType;

    /**
     * 대상(예: show)이 소유한 entity의 scalar 참조다. 원래는 공연에 대한 {@code @ManyToOne}이었지만,
     * 찜이 별도 module(like)로 분리되며 모듈을 넘나드는 JPA 연관관계를 금지하는 규칙(ADR 0003
     * §4)에 맞춰 scalar id column으로 바뀌었다. 대상 존재 확인은 이 module의 책임이 아니다 —
     * 호출자가 이미 확인했다는 전제다.
     */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    protected Like() {
    }

    public Like(final Long memberId, final LikeType likeType, final Long targetId) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.likeType = Objects.requireNonNull(likeType, "likeType must not be null");
        this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
    }

}
