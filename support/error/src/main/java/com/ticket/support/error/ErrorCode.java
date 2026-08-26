package com.ticket.support.error;

/**
 * 클라이언트에 노출하는 오류 코드다. 코드 값은 각 모듈이 소유하고, 이 인터페이스는 형식만 정한다.
 *
 * <p>{@code description}은 백엔드 개발자가 코드의 의미를 찾기 위한 내부 설명이다. 클라이언트에
 * 보여줄 문구는 {@link ErrorDefinition#getMessage()}이며, 클라이언트는 메시지가 아니라 코드로 분기한다.
 */
public interface ErrorCode {

    String getCode();

    String getDescription();
}
