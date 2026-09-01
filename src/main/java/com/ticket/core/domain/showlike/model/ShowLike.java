package com.ticket.core.domain.showlike.model;

import com.ticket.core.domain.BaseEntity;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.show.model.Show;
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
public class ShowLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    protected ShowLike() {
    }

    public ShowLike(final Member member, final Show show) {
        this.member = Objects.requireNonNull(member, "member must not be null");
        this.show = Objects.requireNonNull(show, "show must not be null");
    }

}
