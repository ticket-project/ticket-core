package com.ticket.like.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.ticket.shared.jpa.AuditedEntity;

import lombok.Getter;

@Getter
@Entity
@Table(name = "LIKES")
public class Like extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** member가 소유한 회원의 scalar 참조다. 모듈을 넘나드는 JPA 연관관계는 금지되므로 {@code @ManyToOne}이 아니라 id 컬럼만 갖는다(ADR 0003 §4). */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 찜 대상의 종류다. 대상이 공연뿐이라 항상 {@link LikeType#SHOW}로 저장한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "like_type", nullable = false, length = 20)
    private LikeType likeType = LikeType.SHOW;

    /**
     * 대상(예: show)이 소유한 entity의 scalar 참조다. 원래는 공연에 대한 {@code @ManyToOne}이었지만, 찜이 별도 module(like)로 분리되며 모듈을 넘나드는 JPA
     * 연관관계를 금지하는 규칙(ADR 0003 §4)에 맞춰 scalar id column으로 바뀌었다. 대상 존재 확인은 이 module의 책임이 아니다 — 호출자가 이미 확인했다는 전제다.
     */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    protected Like() {}

    public Like(final Long memberId, final Long targetId) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
    }
}
