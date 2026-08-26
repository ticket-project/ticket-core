package com.ticket.support.error;

/**
 * 하나의 오류가 외부에 보이는 완성된 계약이다. 상태, 코드, 공개 메시지를 함께 제공하므로
 * API는 오류별 매핑 없이 그대로 직렬화한다.
 *
 * <p>실제 값은 오류의 발생 원인을 판단하는 모듈이 소유한다. core-domain은 DomainErrorType,
 * core-app은 ApplicationErrorType, core-api는 ApiErrorType을 가진다.
 */
public interface ErrorDefinition {

    ErrorStatus getStatus();

    ErrorCode getErrorCode();

    String getMessage();
}
