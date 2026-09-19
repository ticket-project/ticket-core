/**
 * {@link com.ticket.ArchitectureRulesTest}의 구조 규칙이 "허용"과 "위반"을 실제로 가르는지 확인하는 fixture다.
 *
 * <p>규칙은 {@code ..persistence..}/{@code ..usecase..}처럼 역할 이름만 보므로, 여기 있는 가짜 module 둘({@code
 * left}/{@code right})도 같은 규칙에 걸린다. 운영 규칙 평가는 {@code ImportOption.DoNotIncludeTests}로 test class를
 * 빼므로 이 fixture가 운영 검사를 오염시키지 않는다 — 회귀 검증만 이 package를 직접 import해 평가한다.
 *
 * <p>실행되지 않는 코드다. bean도 아니고 어디서도 호출하지 않는다.
 */
package com.ticket.archfixture;
