package com.ticket.support.error;

/**
 * 여러 모듈이 함께 쓰는 공용 오류 코드다. 같은 코드 문자열을 모듈마다 새로 발급하면 이미 공개된
 * 코드가 바뀌므로, 공용으로 쓸 코드만 여기서 한 번 소유한다.
 *
 * <p>업무 오류 코드는 여기에 두지 않는다. 회원·공연·좌석·주문·홀드·대기열 코드는 각각
 * DomainErrorCode, ApplicationErrorCode, ApiErrorCode가 소유한다.
 */
public enum CommonErrorCode implements ErrorCode {
    E400("잘못된 요청"),
    E404("데이터 없음"),
    E500("내부 서버 오류"),
    E1000("인증 오류"),
    E1001("인가 오류");

    private final String description;

    CommonErrorCode(final String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
