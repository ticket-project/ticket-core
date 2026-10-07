package com.ticket.shared.exception;

/** 어느 module의 것도 아닌 오류 코드다. 업무 의미가 있는 코드는 각 module의 {@code <Module>ErrorCode}가 소유한다. */
public enum CommonErrorCode implements ErrorCode {
    E400,
    E404,
    E500
}
