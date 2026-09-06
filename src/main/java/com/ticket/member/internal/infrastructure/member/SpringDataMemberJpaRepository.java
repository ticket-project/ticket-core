package com.ticket.member.internal.infrastructure.member;

import com.ticket.member.internal.domain.member.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataMemberJpaRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail_EmailAndDeletedAtIsNull(String email);

    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);
}
