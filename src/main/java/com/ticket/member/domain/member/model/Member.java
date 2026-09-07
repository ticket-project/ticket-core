package com.ticket.member.domain.member.model;

import com.ticket.member.domain.MemberAuditedEntity;
import com.ticket.member.domain.member.model.Role;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
@Entity
@Table(name = "MEMBERS", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"email"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends MemberAuditedEntity {

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

    /**
     * 소셜 계정은 회원 없이 존재할 수 없는 같은 aggregate의 자식이라 Root가 컬렉션으로 소유한다.
     *
     * <p><b>{@code orphanRemoval}을 쓰지 않는다.</b> {@link MemberSocialAccount}는 {@code deletedAt}으로
     * soft delete하므로, 컬렉션에서 빼는 순간 실제 row가 지워지면 연결 이력이 사라진다. 같은 이유로
     * cascade도 {@code PERSIST}/{@code MERGE}로 제한한다 — {@code REMOVE}는 회원 삭제가 곧 물리
     * 삭제가 되어 soft delete 규칙과 어긋난다.
     *
     * <p>컬렉션에는 탈퇴 처리된 계정도 함께 담기므로 원본을 그대로 노출하지 않는다. 활성 계정만
     * 걸러 주는 {@link #activeSocialAccounts()}/{@link #findActiveSocialAccount(SocialProvider)}로만
     * 접근한다.
     */
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "member", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<MemberSocialAccount> socialAccounts = new ArrayList<>();

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

    /**
     * 소셜 계정을 연결한다. 양방향 연관관계를 Root가 한곳에서 맞춘다 — 자식을 직접 만들어 컬렉션에
     * 넣지 않는다.
     */
    public MemberSocialAccount addSocialAccount(final SocialProvider socialProvider, final String socialId) {
        final MemberSocialAccount socialAccount = MemberSocialAccount.create(this, socialProvider, socialId);
        socialAccounts.add(socialAccount);
        return socialAccount;
    }

    /**
     * 탈퇴 처리되지 않은 소셜 계정만 돌려준다. 반환된 목록을 바꿔도 aggregate에 반영되지 않는다.
     */
    public List<MemberSocialAccount> activeSocialAccounts() {
        return socialAccounts.stream()
                .filter(socialAccount -> !socialAccount.isDeleted())
                .toList();
    }

    public Optional<MemberSocialAccount> findActiveSocialAccount(final SocialProvider socialProvider) {
        return activeSocialAccounts().stream()
                .filter(socialAccount -> socialAccount.getSocialProvider() == socialProvider)
                .findFirst();
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
