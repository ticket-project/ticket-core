package com.ticket.show.domain.showlike.model;

import com.ticket.show.domain.ShowAuditedEntity;
import com.ticket.show.domain.show.Show;
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
public class ShowLike extends ShowAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * member가 소유한 회원의 scalar 참조다. 모듈을 넘나드는 JPA 연관관계는 금지되므로
     * {@code @ManyToOne}이 아니라 id 컬럼만 갖는다(ADR 0003 §4).
     */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    protected ShowLike() {
    }

    public ShowLike(final Long memberId, final Show show) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.show = Objects.requireNonNull(show, "show must not be null");
    }

}
