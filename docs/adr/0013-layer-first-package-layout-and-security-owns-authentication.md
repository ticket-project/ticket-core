# ADR 0013: 업무 모듈은 계층형으로 두고, 인증 조립은 security가 소유한다

## 상태

채택됨 (2026-09-14)

> 2026-09-22 갱신: 본문이 적은 `shared.config` → `shared.infrastructure` 개명은 되돌렸다
> (ADR 0011 머리말 참조). 계층형 배치와 security가 인증 조립을 소유한다는 결정은 그대로다.

> 2026-09-19 갱신: 아래 2026-09-17 갱신이 적은 `application` → `usecase`/`query`/`port` 중 `query`는
> [ADR 0017](0017-query-implementations-live-in-persistence.md) 이후 없어졌다 — 읽기 모델과 조회
> 타입도 `usecase`가 갖는다. 본문의 `MemberAccountOperations`는 `member.api.MemberAccountApi`,
> `AccessTokenAuthenticator`는 `security.api`에 있고(ADR 0014), 감사 base entity는 `booking.domain`이
> 아니라 `shared.jpa.AuditedEntity`다(ADR 0018).

> 2026-09-17 갱신: "모듈 → 계층" 배치는
> [ADR 0016](0016-capability-first-layout-inside-modules.md)이 대체했다. 계층 이름은 역할 이름이 되고
> (`application` → `usecase`/`query`/`port`, `infrastructure` → `persistence`), `booking`은 역할보다
> 업무(capability)를 먼저 드러낸다. **`security`만 기능으로 나눈다는 결정은 그대로 유효하다.**
>
> 2026-09-15 갱신: 계층 이름 `web`은 [ADR 0014](0014-module-public-contracts-live-in-api-packages.md)가
> `endpoint`로 바꿨다. 모듈 → 계층 배치와 `security`만 기능으로 나눈다는 결정은 그대로 유효하고,
> `shared.web`(응답 봉투)과 `security.http`(HTTP 보안 adapter)도 이름을 유지한다.

[ADR 0003](0003-spring-modulith-application-module-boundaries.md)의 capability 축 배치와
[ADR 0011](0011-shared-technical-package-layout.md)의 `shared.config`,
[ADR 0012](0012-separate-global-http-security-from-member.md)의 "인증 업무는 member가 소유한다"를
이 부분에 한해 대체한다. 그 ADR들의 나머지 결정(Application Module 경계, shared의 공개 계약 분리,
전역 HTTP 접근 정책을 member에서 떼어낸 것)은 그대로 유효하다.

## 배경

### 1. 폴더가 두 축으로 나뉘어 있었다

업무 모듈은 `<module>.<capability>.<layer>`였다(`booking.order.application`,
`show.catalog.domain`). 파일 하나를 찾으려면 "무슨 업무인가"와 "무슨 계층인가"를 둘 다 정해야
했고, booking은 업무 폴더가 일곱 개라 계층 폴더가 그만큼 반복됐다. 더 나쁜 것은 조립 코드였다 —
주문 생성은 좌석·hold·정책·show 표시값을 엮는데, 그 코드가 `order`에 있어야 할 이유는 "결과를
주문이 책임진다"는 규칙 하나였고 실제로는 자주 흔들렸다.

여러 업무가 함께 쓰는 기반도 갈 곳이 없어 `booking.common`이 생겼다. 이름이 계층을 말하지 않는
폴더는 시간이 지나면 무엇이든 받는다. 실제로 락 계약, 감사 base entity, 요청 좌석 값 객체,
Redisson 구현, Redis 만료 수신 배선이 한 폴더에 있었다.

### 2. member가 인증 업무와 회원 데이터를 함께 들고 있었다

ADR 0012는 전역 HTTP 접근 정책만 `security`로 떼고, JWT 발급·검증, 로그인·로그아웃·refresh,
OAuth2 provider 구현은 member에 남겼다. 그 결과 member는 "회원이 누구인가"와 "그 회원을 어떻게
인증하는가"를 함께 소유했고, 모듈 슬라이스 테스트를 띄우려면 JWT secret과 OAuth2 client 설정,
Redis가 모두 필요했다. 회원 데이터와 무관한 토큰 정책 변경이 member를 건드렸다.

## 결정

### A. 업무 모듈은 모듈 → 계층, 묶음은 domain에만

업무 모듈 여섯(`booking`/`member`/`show`/`venue`/`like`/`payment`)은 모듈 바로 아래에
`web`/`application`/`domain`/`infrastructure`/`exception`을 둔다. **`application`,
`infrastructure`, `web` 아래에는 업무별 폴더를 만들지 않는다.** 그 계층에서 "무엇에 관한
코드인가"는 폴더가 아니라 클래스 이름이 말한다.

업무 묶음은 `domain` 아래에만 남긴다. 도메인 모델은 서로를 직접 참조하고 함께 읽히므로 묶음이
실제로 탐색을 돕는다. booking은 `domain.{order,hold,selection,seat,salespolicy,ticket}`, show는
`domain.{show,performance}`와 domain 직속, 나머지는 domain 직속이다.

**묶음은 Aggregate와 일대일이 아니다.** `domain.hold`와 `domain.selection`은 Redis 상태와 그
규칙의 묶음이고, hold 이력처럼 같은 묶음의 별도 영속 모델도 자기 Aggregate 경계를 유지한다.
경계는 폴더가 아니라 `com.ticket.AggregateAssociationTest`가 강제한다.

역할별 하위 폴더 `application.usecase`, `application.port`, `web.request`, `web.docs`,
`exception.handler`는 유지한다. 이것들은 업무가 아니라 역할을 말한다.

### B. 모듈 안의 `common`은 두지 않는다

여러 업무가 함께 쓰는 기반도 그 역할의 계층이 받는다. `booking.common`은 없애고 락 계약은
`booking.application`, 감사 base entity와 요청 좌석 값은 `booking.domain`, Redisson 구현과 Redis
만료 수신 배선은 `booking.infrastructure`가 가져갔다. `shared.config`도 같은 이유로
`shared.infrastructure`가 됐다.

평탄화로 폴더가 보장하던 것은 실행 가능한 규칙으로 옮겼다 —
`com.ticket.booking.BookingLayerDependencyTest`가 계층 방향과 락 계약의 기술 의존 금지를 고정하고,
`domain`으로 돌아간 타입은 `com.ticket.DomainIsolationTest`의 `..domain..` 패턴이 다시 덮는다.

### C. security가 인증을 소유하고, member는 계정 계약만 공개한다

인증 흐름의 조립은 전부 `security`로 옮긴다 — 가입·로그인·갱신·로그아웃·탈퇴 절차, JWT 발급·검증,
OAuth2 provider 통신과 응답 해석, refresh token 저장이 그렇다. **회원 테이블과 인증 데이터의
소유권은 member에 그대로 있다.** 이번 작업에서 DB를 나누거나 데이터를 옮기지 않았다.

security는 member가 공개한 `MemberAccountOperations`로만 계정을 만진다.

| 메서드 | 입력 | 반환 |
| --- | --- | --- |
| `register` | 이메일·비밀번호·이름 | 회원 번호 |
| `authenticate` | 이메일·비밀번호 | 활성 회원 번호와 역할(`MemberStatus`) |
| `requireActiveIdentity` | 회원 번호 | 현재 활성 회원의 `MemberStatus` |
| `resolveSocialAccount` | 정규화된 소셜 신원(`SocialIdentity`) | 회원의 `MemberStatus` |
| `withdraw` | 회원 번호 | 외부 unlink에 필요한 소셜 연결 목록 |

**비밀번호 해시를 security에 돌려주지 않는다.** 해싱과 일치 확인을 member가 직접 수행하므로
`member -> security` 의존이 생길 이유가 없다. 최종 방향은 `security -> member -> shared`다.

`security`만 계층 대신 기능으로 나눈다 — `auth`/`jwt`/`oauth`/`token`/`http`이며 각 폴더 안에
계층 폴더를 다시 만들지 않는다. 여기 있는 것은 업무가 아니라 인증 기술이라 "무엇에 관한
코드인가"가 더 나은 탐색 단위다.

`AccessTokenAuthenticator`는 `security` root의 공개 계약이 된다. booking의 WebSocket 인증이 이
계약을 쓰므로 `booking -> security` edge를 새로 승인했다 — STOMP CONNECT는 HTTP filter chain을
타지 않아 좌석 상태 구독 인터셉터가 토큰을 직접 검증해야 한다.

`DELETE /api/v1/members`는 `security.auth`로 옮긴다. 탈퇴는 DB 처리로 끝나지 않고 커밋 뒤 외부
provider 연결 해제와 SecurityContext 정리가 이어지는 인증 조립이다. member에 남겨 두면 member가
security의 조립을 참조해 모듈 순환이 생긴다. `GET /api/v1/members`는 member에 남으므로 같은 URL을
두 모듈이 HTTP 메서드로 나눠 갖는다. URL·인증 요구·응답은 바뀌지 않았다.

## 검토한 대안

**(1) capability 축을 유지한다.** 폴더가 업무 경계를 눈에 보이게 한다는 장점이 있다. 그러나 실제
경계를 강제하는 것은 폴더가 아니라 Modulith와 ArchUnit이었고, 폴더는 탐색 비용만 더했다. 조립
코드의 자리를 매번 논쟁하게 되는 것도 그대로였다.

**(2) capability를 각각 Application Module로 올린다.** ADR 0012가 이미 기각했다. 지금의 양방향
협력이 모듈 순환이나 과도한 공개 계약으로 바뀐다.

**(3) 인증까지 member에 그대로 둔다(ADR 0012 유지).** member 슬라이스가 JWT·OAuth2·Redis 설정
없이는 뜨지 않는 상태가 계속된다. 회원 데이터와 무관한 토큰 정책 변경이 member를 건드린다.

**(4) 회원 데이터까지 security로 옮긴다.** 인증과 회원이 한 모듈이 되어 (3)의 문제를 반대 방향으로
반복한다. 회원 프로필·찜·주문이 security를 참조하게 된다.

**(5) 계정 계약을 `shared`에 둔다.** shared는 기술 모듈이고 업무 계약을 담지 않는다는 ADR 0011의
결정과 어긋난다.

## 비용과 감수한 것

- **큰 diff.** 거의 모든 파일의 package 선언과 import가 바뀌었다. 대신 클래스 내용 변경은 C의
  계약 도입 부분으로 한정했고, 패키지 이동과 호출 구조 변경을 별도 커밋으로 나눴다.
- **Querydsl Q-타입 경로가 바뀐다.** entity와 같은 패키지에 생성되므로 import를 함께 고쳤다.
  빌드 설정 변경은 필요 없었다.
- **`booking -> security` edge가 새로 생긴다.** WebSocket 인증 하나 때문이며, 그 사실을 booking의
  `package-info`와 `ModularityTests.APPROVED_DEPENDENCY_DAG`에 명시했다.
- **같은 URL을 두 모듈이 나눠 갖는다.** `/api/v1/members`의 GET과 DELETE다. 매핑이 겹치지 않고,
  대안(member가 security를 참조)은 모듈 순환이라 이 비용을 택했다.
- **`MemberAccountService`는 얇은 위임 계층이다.** 업무 규칙은 기존 구현이 그대로 소유하고, 이
  클래스는 결과를 entity가 아닌 공개 값으로 바꾸는 일만 한다. 공개 계약을 두려면 피할 수 없는
  비용으로 봤다.

## 보존한 것

JWT claim·키 설정·만료 정책, Redis 키·TTL·소유권 확인·일회용 소비 의미, 물리 좌석 `seatId`와 공연
좌석 `performanceSeatId`의 의미, 공개 이벤트의 타입 경로와 payload, `BookingEventListeners`의 고정
listener id, 기존 Aggregate 연관관계와 트랜잭션·락·보상·재시도 경계, 모든 HTTP 경로·상태 코드·
요청/응답 필드.

`BookingEventListeners`의 listener id는 특히 주의한다. 재편으로 실제 package가 옛 값과 다시 같아
졌지만, 이 값은 `EVENT_PUBLICATION.listener_id`와 맞추기 위한 고정 문자열이라 계속 명시한다 —
package 경로 일괄 치환 대상이 아니다. `BookingEventListenerIdContractTest`가 이를 고정한다.
