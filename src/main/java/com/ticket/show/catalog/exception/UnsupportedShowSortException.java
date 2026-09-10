package com.ticket.show.catalog.exception;



import com.ticket.show.exception.ShowErrorCode;
import com.ticket.show.exception.ShowException;
import lombok.Getter;
/**
 * 지원하지 않는 공연 정렬 조건이다.
 *
 * <p>생성자는 정렬 <b>원문</b>을 받고 공개 상세 문구는 여기서 만든다 — 호출부가 문장을 조립해
 * 넘기면 접두어가 두 번 붙거나 경로마다 달라진다. 원문은 대소문자·공백을 정규화하지 않고
 * 그대로 {@code error.data}에 실린다(기존 응답 계약).
 */
@Getter
public class UnsupportedShowSortException extends ShowException {

    private static final String MESSAGE = "지원하지 않는 정렬 조건입니다.";

    private final String sortValue;

    public UnsupportedShowSortException(final String sortValue) {
        super(ShowErrorCode.E7002, MESSAGE, "지원하지 않는 sort: " + sortValue);
        this.sortValue = sortValue;
    }
}
