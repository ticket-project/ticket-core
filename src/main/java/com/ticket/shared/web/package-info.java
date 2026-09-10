/**
 * 이 앱의 공통 HTTP 응답 형식이다.
 *
 * <p>모든 REST 응답을 감싸는 공통 봉투({@link com.ticket.shared.web.ApiResponse}/
 * {@link com.ticket.shared.web.ErrorMessage}/{@link com.ticket.shared.web.ResultType})와 무한스크롤 응답
 * 형식({@link com.ticket.shared.web.SliceResponse})이 여기 있다.
 */
@org.springframework.modulith.NamedInterface("web")
package com.ticket.shared.web;
