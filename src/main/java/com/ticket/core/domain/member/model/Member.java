package com.ticket.core.domain.member.model;

import com.ticket.core.domain.BaseEntity;
import com.ticket.core.domain.member.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "MEMBERS", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"email"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private Email email;

    @Embedded
    private EncodedPassword encodedPassword;

    private String name;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column
    private LocalDateTime deletedAt;

    public Member(final Email email, final EncodedPassword encodedPassword, final String name, final Role role) {
        this.email = email;
        this.encodedPassword = encodedPassword;
        this.name = name;
        this.role = role;
    }

    public static Member createSocialMember(
            final Email email,
            final String name,
            final Role role
    ) {
        return new Member(email, null, name, role);
    }

    public void withdraw(final LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        this.email = Email.create(buildWithdrawnEmail());
        this.encodedPassword = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private String buildWithdrawnEmail() {
        final String idPart = id == null ? "unknown" : id.toString();
        final String randomPart = UUID.randomUUID().toString().replace("-", "");
        return "deleted_" + idPart + "_" + randomPart + "@withdrawn.ticket";
    }
}
