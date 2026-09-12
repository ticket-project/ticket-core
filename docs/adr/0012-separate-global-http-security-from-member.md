# ADR 0012: 전역 HTTP security를 member에서 분리한다

## 상태

채택됨 (2026-09-13)

## 배경

`member.security.infrastructure.SecurityConfig`가 회원·인증 업무와 무관한 공연·예매·Actuator·
WebSocket URL의 접근 정책까지 알고 있었다. 같은 설정이 OAuth2 로그인 filter chain, stateless API
filter chain, CORS, 비밀번호 인코더 빈도 함께 만들었다. 공연 API 공개 범위를 바꾸거나 HTTP 인증
주체 전달 방식을 고칠 때 member를 수정해야 하는 구조였다.

반대로 `account`, `auth`, `oauth`를 각각 Application Module로 만들면 현재의 양방향 업무 협력이
모듈 간 순환 또는 과도한 공개 계약으로 바뀐다. 회원가입은 비밀번호 해싱을, credential 인증은
Member repository를, OAuth 가입·탈퇴는 Member aggregate를 사용한다. 이 셋은 아직 독립 BC가
아니라 member 안에서 함께 변경되는 capability다.

## 결정

### Application Module과 의존 방향

전역 HTTP 보안 인프라만 `com.ticket.security` 닫힌 기술 Application Module로 분리한다.

```text
security -> member -> shared
```

- `member`는 `account`/`auth`/`oauth` 내부 capability를 유지한다.
- `member -> security`는 금지한다.
- `security`는 member root의 `AuthenticatedMember`, `AccessTokenReader`,
  `AccessTokenReadResult`를 사용한다.
- 기존 E1000/E1001 계약을 보존하기 위해 `member :: exception`만 named interface로 공개한다.
- security가 사용하는 shared 계약은 root의 `CorsProperties`, `shared :: web`,
  `shared :: exception`으로 한정한다.

`AccessTokenAuthenticator`는 만료/무효를 하나의 인증 실패로 처리하는 WebSocket용 공개 계약으로
유지한다. HTTP는 응답 상세를 보존해야 하므로 만료/무효를 구분하는 `AccessTokenReader`를 별도
공개한다. 두 의미를 하나로 합치지 않는다.

### filter chain과 빈 소유권

- `member.oauth.infrastructure.OAuth2SecurityConfig`가 OAuth2 matcher, `@Order(1)`,
  `IF_REQUIRED` 세션, OAuth 전용 handler와 capture filter를 소유한다.
- `security.infrastructure.ApiSecurityConfig`가 일반 API의 `@Order(2)`, `STATELESS`, CORS,
  URL별 permitAll/authenticated 정책, 401/403 handler와 access token filter를 소유한다.
- `member.auth.infrastructure.PasswordHashingConfig`가 `PasswordEncoder`를 제공한다.

기존 matcher, endpoint, JWT claim, cookie, 오류 코드와 응답 형식은 변경하지 않는다.

`AccessTokenAuthenticationFilter`는 token 추출·검증만 `IllegalArgumentException` 처리 범위에 둔다.
downstream `filterChain.doFilter()`는 catch 범위 밖에서 정확히 한 번 호출한다. 하위 controller나
filter의 `IllegalArgumentException`을 token 오류로 오인해 chain을 두 번 실행하지 않는다.

### OAuth provider 응답과 계정 연결

Google/Kakao raw attribute 해석은 domain이 아니라
`member.oauth.infrastructure.OAuth2UserInfoMapper`가 담당한다. application에는 provider,
providerId, email, emailVerified, name만 담은 정규화된 `OAuth2UserInfo`를 넘긴다.

동일 이메일 자동 연결은 provider가 이메일 검증을 명시한 경우에만 허용한다. Google은
`email_verified=true`, Kakao는 `is_email_valid=true`와 `is_email_verified=true`를 모두 요구한다.
검증되지 않은 이메일은 provider ID 기반 대체 주소를 사용해 기존 계정과 연결하지 않는다.
Google도 email 대신 `sub`를 안정적인 식별자로 쓰라고 명시하며, Kakao도 이메일 유효성과 검증
상태를 별도 제공한다.

- [Google OpenID Connect API reference](https://developers.google.com/identity/openid-connect/reference)
- [Kakao Login REST API](https://developers.kakao.com/docs/en/kakaologin/rest-api)

### 회원 탈퇴

`WithdrawCurrentMemberUseCase`는 Kakao 구현 대신 `SocialAccountUnlinker`를 의존한다. 이 포트의
infrastructure adapter인 `ProviderSocialAccountUnlinker`가 provider별 프로토콜과 설정을 소유한다.
현재 구현은 Kakao만 외부 unlink를 호출하고 Google은 no-op다. DB 탈퇴 transaction이 완료된 뒤
외부 호출을 수행하고 실패를 기록한 뒤 계속하는 기존 원칙은 유지한다.

Member aggregate가 탈퇴하면 활성 MemberSocialAccount도 같은 시각에 탈퇴해야 하는 invariant는
`Member.withdraw(now)`가 직접 보장한다. 외부 provider 호출은 aggregate에 넣지 않는다.

### 감사 주체 계약

`AuthenticatedMember`가 `java.security.Principal`을 구현하면 Spring MVC의 기본 Principal
argument resolver가 애플리케이션 resolver보다 먼저 선택되어 controller 호출이 500이 된다.
따라서 shared의 기술 중립 계약 `AuditorPrincipal`을 구현하고,
`SecurityContextAuditorAware`가 그 식별자를 읽는다. shared가 member를 참조하지 않는 방향은
유지한다.

## 검토한 대안

1. 기존 SecurityConfig를 통째로 security로 이동: OAuth 내부 구현을 외부에 공개하거나
   security가 member 내부 패키지를 참조하므로 선택하지 않았다.
2. account/auth/oauth를 각각 Application Module로 분리: 현재 상호 협력 때문에 순환과 공개
   계약이 급증하므로 선택하지 않았다.
3. security를 shared.config에 배치: member 공개 계약을 참조하는 전역 정책이 shared leaf 성격과
   충돌하므로 선택하지 않았다.
4. `AccessTokenAuthenticator` 하나만 HTTP에도 사용: 만료/무효 응답 구분이 사라져 기존 API
   동작이 바뀌므로 선택하지 않았다.

## 결과

공연·예매 API 접근 정책 변경은 security만 수정한다. JWT 발급·검증 기술과 로그인·refresh는
member.auth에 남고, OAuth provider JSON 변경은 member.oauth.infrastructure mapper에 국한된다.
Modulith 검증은 8개 module과 `security -> member`, `security -> shared` 직접 edge를 고정한다.

DB schema와 migration은 바뀌지 않는다. 검증되지 않은 provider 이메일을 사용하던 신규 소셜
회원은 앞으로 provider ID 기반 대체 이메일로 생성되므로 이 부분만 의도적인 보안 동작 변경이다.
