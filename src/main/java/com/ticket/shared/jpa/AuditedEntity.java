package com.ticket.shared.jpa;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Getter;

/**
 * 모든 module의 entity가 함께 쓰는 감사(auditing) 공통 필드다.
 *
 * <p>테이블을 갖지 않는 {@code @MappedSuperclass}라 상속해도 네 컬럼이 각 테이블에 그대로 인라인된다 — 상속으로 묶이는 것은 컬럼 정의뿐이고,
 * aggregate·트랜잭션·조회는 module마다 독립이다.
 *
 * <p>값을 채우는 쪽은 {@code shared.config.JpaAuditingConfig}이고, 감사자 ID는 {@link
 * com.ticket.shared.api.AuditorPrincipal}에서 읽는다.
 *
 * <p><b>업무 의미를 가진 필드를 여기 추가하지 않는다.</b> 한 module에만 필요한 필드(낙관적 락 {@code @Version}, 소프트 삭제 표시 등)를 여기
 * 올리면 21개 테이블 전부가 그 컬럼을 갖게 된다. 그런 필드는 그것을 필요로 하는 entity가 직접 갖는다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public class AuditedEntity {
    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate private LocalDateTime updatedAt;

    @CreatedBy
    @Column(updatable = false, nullable = false)
    private String createdBy;

    @LastModifiedBy private String updatedBy;
}
