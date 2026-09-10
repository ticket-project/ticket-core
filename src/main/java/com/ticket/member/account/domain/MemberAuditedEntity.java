package com.ticket.member.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * member module이 소유하는 entity의 감사(auditing) 공통 필드다.
 *
 * <p>여러 module이 감사 필드 하나를 상속해 공유하면 module 간 JPA 상속 결합이 생기므로, 6개 업무
 * module(booking/show/venue/member/like/payment) 각각이 이 작은 {@code @MappedSuperclass}를
 * module-local로 복제해서 쓴다. 필드와 동작은 module마다 동일하다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public class MemberAuditedEntity {

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(updatable = false, nullable = false)
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}
