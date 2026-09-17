# Member · Auth · Security 모듈 분리 구현 계획

상태: 구현 전 계획. 이 문서는 현재 구조를 설명하는 아키텍처 기준을 대체하지 않는다.

## 목표와 선택

회원 데이터와 생명주기는 `member`, 로그인 흐름과 토큰 수명은 `auth`, 요청 접근 제어와 인증
주체 전달은 `security`가 소유한다. 단일 Gradle 프로젝트 안에 닫힌 Spring Modulith 모듈 둘을
추가한다. 서비스 분리나 Gradle subproject 추가는 하지 않는다.

대안은 (1) 인증과 보안을 `auth` 하나로 묶는 것, (2) `auth`와 `security`를 독립시키는 것이다.
첫 번째는 공개 계약과 설정 작업이 적다. 두 번째는 이미 여러 업무 모듈과 HTTP·WebSocket이
사용하는 인증 계약을 로그인 흐름에서 분리해 검증할 수 있다. 합의한 책임 구분에 따라 두 번째를
선택한다. 자격증명 저장 자체를 독립시키는 Identity 분리는 이번 범위에 포함하지 않는다.

## 보존할 외부 동작

- URL·HTTP method·요청과 응답 JSON·Bean Validation·OpenAPI operation 계약.
- 오류 HTTP 상태, E-code, `message`, `data`. 오류의 이유가 현재 `data`에 들어가는 동작도 유지.
- JWT 서명·issuer·subject·role claim·만료, 기존에 발급된 토큰의 검증 가능 여부.
- Refresh Token 키·값·TTL·회전·원자적 폐기, OAuth 인증 코드의 일회성 소비.
- Refresh Token 쿠키의 이름·경로·보안 속성·수명과 로그아웃 실패 시에도 쿠키를 삭제하는 동작.
- OAuth 경로·state를 위한 세션·리다이렉트 허용 정책·성공과 실패 처리.
- CORS 허용 origin·method·header·credentials·노출 header·maxAge.
- 회원·소셜 계정 테이블과 JPA 연관관계, 기존 Flyway 파일과 이력, 시드 SQL.
- 설정 키·환경변수·비밀번호 해시 형식. 형제 저장소의 클라이언트와 토큰 생성기 변경이 필요하지
  않은 구조 변경으로 한정한다.
- JWT 검증은 기존처럼 토큰 자체를 검증한다. 매 요청 DB 조회나 탈퇴 시 Access Token 즉시 폐기
  정책을 새로 도입하지 않는다. 업무 유스케이스의 활성 회원 확인도 유지한다.
- 탈퇴 DB 트랜잭션을 끝낸 뒤 카카오 연동 해제를 호출하며, 외부 호출 실패가 탈퇴를 되돌리지
  않는 현재 동작을 유지한다. 비동기 이벤트·재시도 도입은 별도 변경이다.

## 최종 소유권

| 위치 | 소유할 구현과 계약 |
| --- | --- |
| `member` root | 기존 `MemberLookup`·`MemberStatus`·`MemberProfile`, 회원 검증·소셜 회원 연결용 최소 공개 계약 |
| `member.account` | 회원 가입·조회·탈퇴, 자격증명 확인, 소셜 회원 생성·연결·연동 해제, Member Aggregate와 저장소 |
| `auth.session` | 로그인·로그아웃·재발급, 토큰 발급·검증 구현, Refresh Token 저장소, 쿠키·인증 Controller |
| `auth.oauth` | 제공자 응답 해석, OAuth 로그인 조립·필터 체인, 인증 코드·교환·리다이렉트 |
| `auth.infrastructure` | session과 oauth가 실제로 함께 사용하는 UUID 공급 계약·빈 |
| `security` root | `AuthenticatedMember`, `AccessTokenReader`, `AccessTokenReadResult`, `AccessTokenAuthenticator` |
| `security.application` | 중립 토큰 읽기 결과를 인증 성공·실패로 변환하는 기존 `AccessTokenAuthenticatorService` |
| `security.infrastructure` | API 필터 체인, Bearer 필터, Principal resolver와 MVC 등록, CORS, EntryPoint·AccessDeniedHandler |
| `security.exception` | 인증·인가 오류와 E1000·E1001. 명시적인 공개 오류 계약으로 노출 |
| `member.exception` | 회원 오류와 E2000. 기존 공통 E400·E404·E500은 shared에 유지 |

`security`는 기술 모듈이므로 capability를 억지로 추가하지 않는다. `auth`는 로그인과 OAuth
capability를 가진 지원 기능 모듈로 취급한다. 각 위치에는 필요한 계층만 만든다.

### 모듈 간 계약

1. 기존 `AccessTokenReader`와 sealed `AccessTokenReadResult`를 그대로 security root로 옮긴다.
   결과의 성공·만료·무효 구분을 유지한다. 새 검증 전략 계층은 추가하지 않는다.
2. `auth.session.infrastructure.JwtAccessTokenCodec`가 `security.AccessTokenReader`를 구현한다.
   JWT 발급과 해석은 현재처럼 같은 codec에 둔다. security는 JWT 라이브러리·설정·키를 모른다.
3. `security.AccessTokenAuthenticator`는 WebSocket 등 만료·무효를 하나의 실패로 다루는 기존
   호출부의 계약으로 유지한다. 그 구현은 기존 읽기 계약을 조립하므로 security 안에 둔다.
4. 새 `member.MemberCredentialVerifier`는 이메일·비밀번호를 받아 성공 시 회원 ID·역할의
   `MemberIdentity`를, 불일치 시 빈 결과를 반환한다. 회원 존재 여부와 비밀번호 오류를 구분해
   노출하지 않는다. auth가 빈 결과를 기존 인증 오류로 변환한다.
5. 새 `member.SocialMemberProvisioner`는 중립 입력 `SocialMemberInput`을 받아 회원 ID·역할을
   반환한다. 입력은 제공자 식별자·소셜 ID·이메일·이름으로 한정한다. OAuth 원본 Map이나
   Spring OAuth 타입, member 내부 `SocialProvider` enum은 경계를 넘기지 않는다.
6. 토큰 재발급에는 기존 `MemberLookup`에 `findActiveStatus(long)`를 추가해
   `Optional<MemberStatus>`를 받는다. 기존 메서드는 유지한다. 현재 `getStatus()`로 단순 치환하면
   실패 응답의 상세 문구가 달라지므로, auth가 빈 결과에서 기존 `NotFoundException()`을 만든다.

공개 DTO에는 비밀번호 해시·토큰·JPA entity를 담지 않는다. 새 자격증명 입력 record를 만들지
않고, 비밀번호와 개인정보가 로그·자동 `toString()`에 추가 노출되지 않는지 확인한다.

### 인증·인가 오류 계약

E1000·E1001을 auth와 security 양쪽에 중복 정의하지 않는다. `security.exception`에 단일
`SecurityErrorCode`, `SecurityException`, `UnauthenticatedException`, `AuthorizationException`을
두고 `@NamedInterface("exception")`으로 이 패키지만 명시 공개한다. auth는
`security :: exception`에 의존한다. 하위 `handler`와 나머지 구현은 공개하지 않는다.

`SecurityExceptionHandler`는 자기 base 예외만 받아 기존 401·403 JSON을 만든다. 필터의
EntryPoint·AccessDeniedHandler도 같은 오류 계약을 사용한다. 기존 MemberExceptionHandler는
회원 오류 E2000을 처리한다. named interface는 실제 공유하는 실패 계약을 위한 것이며,
모듈을 OPEN으로 만들거나 내부 구현을 노출하는 용도로 사용하지 않는다. 이 공개 오류 계약
예외를 새 ADR과 architecture.md에 명시한다.

### 필터 체인과 CORS

- `auth.oauth.infrastructure.OAuth2SecurityConfig`: 기존 OAuth matcher·필터 순서·`@Order(1)`·
  `IF_REQUIRED` 세션 정책을 유지한다.
- `security.infrastructure.ApiSecurityConfig`: 기존 URL별 접근 정책·필터 순서·`@Order(2)`·
  `STATELESS` 정책과 나머지 요청의 인증 요구를 유지한다.
- `PasswordEncoder` 빈은 `member.account.infrastructure`에서 등록한다. 해싱을 사용하지 않는
  security가 회원가입의 빈을 제공하지 않도록 한다.
- `corsConfigurationSource`는 security에서 하나만 등록한다. 두 필터 체인 구성 메서드는
  Spring의 `CorsConfigurationSource`와 동일 빈 이름으로 주입받아 `.cors()`에 명시 연결한다.
  auth가 security의 설정 클래스를 import하지 않는다. 이 빈 연결은 통합 테스트에서 검증한다.
- 기존 `/api/**`·`/ws/**` CORS 등록을 유지한다. 현재 OAuth 경로 둘도 `/api/**` 아래이므로
  OAuth 상수 import와 중복 등록은 제거할 수 있다. 실제 두 OAuth 경로에 대한 CORS 검증을 둔다.

여러 필터 체인의 선택 순서와 CORS의 명시 연결 방식은
[Spring Security Java 설정 문서](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html)와
[CORS 문서](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)를 기준으로 한다.

### 최종 직접 의존성

| 모듈 | 직접 의존 모듈 |
| --- | --- |
| auth | member, security, shared |
| security | shared |
| member | security, shared |
| booking | show, member, security, shared |
| show | venue, like, member, security, shared |
| like | member, security, shared |
| venue | 없음 |
| payment | 없음 |
| shared | 없음 |

member의 security 참조는 웹 계층의 인증 주체 사용 때문이다. 회원 도메인과 자격증명 검증은
security를 참조하지 않는다. 업무 모듈의 Controller는 기존 resolver 방식으로
`security.AuthenticatedMember`를 사용한다. booking의 WebSocket adapter는 security 공개 계약을
사용하며, 업무 모듈은 auth를 참조하지 않는다. booking의 admission 검증은 계속 booking이 소유한다.

## 구현 순서와 단계별 통과 조건

### 1. 변경 전 기준선 확보

- 작업 시작 시 브랜치와 미커밋 변경을 확인하고 기존 사용자 변경을 보존한다.
- 현재 모듈 구조, 회원·인증 관련 테스트와 HTTP 오류 계약을 실행해 기존 실패를 구분한다.
- 기존 테스트를 재사용한다. 빠져 있는 회원가입 HTTP 계약, 두 필터 체인의 연결·CORS처럼
  분리로 영향을 받는 외부 동작에만 회귀 테스트를 보강한다.
- 기존 테스트 이름과 위치를 실제 소스에서 확인한 뒤 명령을 실행한다.

통과 조건: 변경 전 성공·실패·환경 제한을 기록하고, 이동 후 비교할 HTTP·토큰 계약이 확보된다.

### 2. member 내부의 소유권과 공개 계약 정리

- `CredentialAuthenticator`, `PasswordHasher`, `SpringSecurityPasswordHasher`, `RawPassword`를
  account 책임으로 모은다. 해시 형식과 미존재 회원의 더미 해싱을 유지한다.
- CredentialAuthenticator는 Member 반환·인증 예외 대신 공개 검증 계약의 결과를 반환하도록
  조정하고, 아직 member 내부에 있는 LoginUseCase가 그 계약을 사용하게 한다.
- 소셜 회원 생성·연결 구현을 account로 옮기고 중립 입력을 받도록 한다. OAuth 제공자 응답
  해석과 회원 조립 사이에 SocialMemberProvisioner를 연결한다. 회원 저장 트랜잭션은 유지한다.
- KakaoUnlinkService·클라이언트·관련 HTTP 빈 등록은 account로 모은다.
- 회원가입 endpoint는 member 소유 Controller로 분리한다. `/api/v1/auth/signup` 주소와
  요청 DTO·문서·검증·응답 형태를 유지하고 AuthController의 해당 메서드만 제거한다.
- MemberLookup의 선택적 활성 조회로 RefreshAuthTokenUseCase의 Repository 직접 접근을 없앤다.
- 시드 테스트의 PasswordHasher·RawPassword import와 관련 설명도 함께 수정한다.

통과 조건: 아직 모듈 집합은 기존 7개이고, 로그인·재발급·OAuth 조립이 회원 엔티티와 저장소를
직접 참조하지 않는다. 회원·인증 테스트와 시드 관련 참조 검증이 통과한다.

### 3. security를 먼저 추출

- security 공개 계약·읽기 결과·인증 결과 변환 구현·필터·resolver를 이동한다.
- SecurityConfig를 API와 OAuth 구성으로 분리한다. 이 단계에서 OAuth 구성은 아직
  `member.oauth`에 있으며, API 구성과 CORS는 security에 둔다.
- 공통 인증·인가 오류와 Handler를 security로 이동한다. 현재 member 내부에 남은 인증
  유스케이스가 공개된 `security :: exception`을 사용하도록 한다.
- 회원·공연·찜·예매 Controller와 문서, WebSocket adapter의 인증 계약 import를 함께 바꾼다.
- security 모듈 선언과 영향받는 allowedDependencies, ModularityTests의 8개 모듈 집합·실제 DAG를
  같은 변경 단위에서 갱신한다. 이 중간 상태에는 auth 모듈이 아직 없다.
- SecurityModuleTests를 추가한다. AccessTokenReader만 외부 구현의 대역으로 제공하고,
  필터·resolver·오류 처리·정책 구성은 실제 빈으로 기동한다.

통과 조건: security는 member·미래 auth를 참조하지 않는다. 8개 모듈 구조 검증, 독립 기동,
HTTP 접근 정책·오류 응답·Principal 주입·WebSocket 인증 테스트가 통과한다.

### 4. auth 추출

- member에 남은 로그인·로그아웃·재발급·토큰·쿠키·인증 Controller를 auth.session으로 옮긴다.
- OAuth 응답 해석·로그인 조립·인증 코드·리다이렉트·전용 필터 체인을 auth.oauth로 옮긴다.
- 두 capability가 사용하는 UUID 공급 구현은 auth.infrastructure로 옮긴다.
- JwtAccessTokenCodec가 security의 공개 읽기 계약을 구현하도록 한다. member와 security의
  내부 구현을 auth에서 import하지 않는다.
- auth 모듈 선언과 관련 경계 테스트·문서 생성 대상 목록을 갱신한다. member의 임시
  `security :: exception` 의존이 없어졌는지 확인하고 제거한다.
- AuthModuleTests를 추가한다. member 공개 계약과 security의 CORS 빈 연결만 대역으로
  제공하고 auth 자체의 빈은 실제로 기동한다. MemberModuleTests에서 JWT·OAuth 로그인 설정을
  제거하되, 회원 탈퇴 연동 해제에 실제로 필요한 설정은 남긴다.

통과 조건: 9개 모듈이 위 최종 DAG와 일치한다. member는 auth를 참조하지 않고, security는
두 모듈 모두 참조하지 않는다. 각 모듈 독립 기동과 로그인·토큰·OAuth 회귀 검증이 통과한다.

### 5. 전체 연결과 외부 계약 검증

- 실제 전체 Spring 컨텍스트에서 로그인 → 토큰 발급 → 회원 API 인증 흐름을 관통한다.
- OAuth 체인이 자기 경로를 먼저 처리하고 API 체인이 나머지를 보호하는지, 양쪽 경로에서
  CORS preflight와 허용·비허용 origin 처리가 보존되는지 검증한다.
- 정상·만료·무효 JWT, 필수 claim 누락·잘못된 issuer, 인증 header 없는 요청, 공개 URL에
  잘못된 토큰을 붙인 경우의 기존 동작을 검증한다. 새 정책을 임의로 적용하지 않는다.
- 기존 HTTP·WebSocket 검증 계약과 JPA 감사자의 회원 ID 기록을 검증한다.
- 실제 Redis로 Refresh Token 회전·TTL·소유자 검증·일회성 OAuth 코드 소비를 검증한다.
- 미존재·탈퇴 회원 재발급의 E404 `message/data`, 중복 이메일 E2000,
  인증·인가 E1000·E1001, 로그아웃 실패 시 쿠키 삭제를 검증한다.
- 실제 토큰 구현과 보안 필터를 동시에 대역으로 바꿔 연결 결함을 가리는 테스트는 만들지 않는다.
  외부 OAuth 제공자 HTTP만 통제하고 애플리케이션 내부 연결은 실제로 조립한다.
- DomainPurityTest에 auth의 도메인 격리와 업무 domain의 security 참조 금지를 반영한다.
  빈 domain 패키지에 대한 무의미한 규칙은 만들지 않는다.
- API Controller 이동에 따른 ControllerParameterConstraintTest, Handler 범위,
  E-code 유일성, 시드 프로그램과 배포 산출물을 함께 확인한다.

통과 조건: 관련 구조·기능·Redis·전체 연결 테스트 및 seedTest와 bootJar가 통과한다.
Docker나 환경 문제로 실행하지 못한 검증은 미완료로 명시한다.

### 6. 현재 기준 문서 반영

- 구현 결과를 기준으로 다음 번호의 ADR을 추가한다. 세 책임, 공개 계약, 공개 오류 계약,
  빈 연결, 자격증명 저장을 member에 유지한 이유를 기록한다.
- architecture.md·모듈 package-info·testing.md·필요한 운영 설명과 시드 설명을 갱신한다.
- DocumentationTests의 고정된 모듈 목록과 생성 대상에 auth·security를 포함한다.
- 종전 이름이 현재 구조 설명이나 main/test/seed import에 남지 않았는지 검색한다. 과거 ADR의
  역사적 본문은 일괄 치환하지 않고 새 결정의 링크로 연결한다.
- 계획 문서의 각 통과 조건에 실제 결과를 기록한다. 커밋·PR·배포는 이번 계획 수립에 포함하지 않는다.

## 검증 실행 묶음

Windows PowerShell, `ticket` 저장소 루트 기준이다. 새 클래스 이름은 해당 단계에서 생성한 뒤
확인한다. 결과 보고에는 실행 건수와 실패·스킵·미실행 범위를 구분한다.

```powershell
# 변경 전 구조·회원 기준선
.\gradlew.bat test --tests "com.ticket.ModularityTests" --tests "com.ticket.member.*" -x seedTest

# 9개 모듈 분리 후 구조·격리·오류·Controller 계약
.\gradlew.bat test --tests "com.ticket.ModularityTests" --tests "com.ticket.DomainPurityTest" --tests "com.ticket.AggregateAssociationTest" --tests "com.ticket.ControllerParameterConstraintTest" --tests "com.ticket.shared.exception.*" -x seedTest

# 세 모듈 기능과 모든 모듈 독립 기동, WebSocket·감사자 영향 범위
.\gradlew.bat test --tests "com.ticket.member.*" --tests "com.ticket.auth.*" --tests "com.ticket.security.*" --tests "com.ticket.*.*ModuleTests" --tests "com.ticket.booking.seat.infrastructure.WebSocketAuthInterceptorTest" --tests "com.ticket.shared.config.SecurityContextAuditorAwareTest" -x seedTest

# 전체 컨텍스트 및 구조 문서; 새 인증 연결 테스트는 구현 시 실제 FQCN을 추가
.\gradlew.bat test --tests "com.ticket.bootstrap.ApplicationContextLoadTest" --tests "com.ticket.DocumentationTests" -x seedTest

# 시드와 배포 산출물
.\gradlew.bat seedTest bootJar verifySeedNotInBootJar
```

Redis 통합 테스트와 전체 컨텍스트 검증에는 Docker가 필요하다. 모듈 패키지 테스트만으로
Controller·보안 필터·토큰 구현의 연결 검증을 대체하지 않는다. 이후 PR 직전에는 저장소의
verify 스킬에 따라 `clean test bootJar verifySeedNotInBootJar`를 실행한다.

## 완료 조건과 주요 위험

- 모듈 root와 명시한 공개 오류 계약만 참조하고 순환·OPEN 모듈·다른 모듈 Repository 참조가 없다.
- 세 모듈과 기존 업무 모듈의 독립 기동, 실제 인증 연결, 외부 계약·Redis·시드 검증이 통과한다.
- DB·토큰·쿠키·설정 계약의 마이그레이션이 필요하지 않다. 예상 밖의 직렬화된 Java 패키지명
  의존이 발견되면 이동 전에 호환 전략을 추가한다.
- 위험은 필터 체인 우선순위·빈 등록, 공개/보호 URL과 CORS, 오류 상세 응답, Redis 소비 순서에
  집중된다. 해당 항목을 서로 다른 모듈의 단위 테스트 결과만으로 완료 처리하지 않는다.
- 실패 중인 구조 검증을 무시하거나 공개 범위를 무조건 넓히지 않는다. 각 단계의 통과 조건을
  만족시킨 후 다음 단계로 진행한다.

## 계획 수립 시 확인한 근거

- `src/main/java/com/ticket/member/security/infrastructure/SecurityConfig.java`: API·OAuth·CORS·해싱 빈이 함께 있음.
- `src/main/java/com/ticket/member/auth/application/AccessTokenReader.java` 및 `AccessTokenReadResult.java`: 재사용할 중립 읽기 계약이 이미 있음.
- `src/main/java/com/ticket/member/auth/infrastructure/JwtAccessTokenCodec.java`: 발급과 검증 구현 및 읽기 계약의 기존 구현.
- `src/main/java/com/ticket/member/auth/application/usecase/RefreshAuthTokenUseCase.java`와 `account/application/MemberLookupService.java`: 활성 조회 치환 시 오류 상세 차이.
- `src/main/java/com/ticket/member/account/application/usecase/WithdrawCurrentMemberUseCase.java`: 커밋 이후 연동 해제·실패 허용 흐름.
- `src/test/java/com/ticket/ModularityTests.java`: 모듈 집합·직접 의존 DAG·닫힌 모듈 검증.
- `src/test/java/com/ticket/shared/exception/ErrorCodeUniquenessTest.java`와 `ExceptionHandlerScopeTest.java`: 오류 중복과 Handler 범위 제약.
- `docs/adr/0006-bounded-context-module-boundaries.md`: Member에서 인증 분리를 후속 후보로 기록.
- `.agents/skills/codebase-design/SKILL.md`, `.agents/skills/verify/SKILL.md`: 작은 공개 계약과 변경 범위별 검증 절차.

계획 작성 시 애플리케이션 코드 변경이나 Gradle 테스트 실행은 하지 않았다.
