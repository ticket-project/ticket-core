/**
 * member의 application이 요구하는 출력 계약이다.
 *
 * <p>지금은 {@link com.ticket.member.application.port.PasswordHasher} 하나뿐이다. 파일 하나를 위한 package는 보통 만들지
 * 않지만, {@code application.port}는 모든 업무 module에서 같은 자리를 뜻하는 역할 이름이라 예외로 둔다 — "이 module이 밖에 요구하는 것이
 * 무엇인가"를 module마다 다른 곳에서 찾게 하지 않는다.
 */
@NullMarked
package com.ticket.member.application.port;

import org.jspecify.annotations.NullMarked;
