/**
 * member의 use case 조립과 트랜잭션 경계.
 *
 * <p>밖을 부르는 계약은 {@code application.port}에, 요청 단위 진입점은 {@code application.usecase}에 둔다.
 */
@NullMarked
package com.ticket.member.application;

import org.jspecify.annotations.NullMarked;
