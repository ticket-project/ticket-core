# 코드 작성 기준

코드와 이름을 정하는 규칙이다. 각 규칙 끝의 대괄호는 그 규칙을 자동으로 잡는 검사이고, `[검사 없음]`은
사람이 지킨다. 이 문서의 정책은 현재 합의된 선택이며, 바꾸려면 영향과 테스트까지 검토한다.

## 클래스와 메서드

- UseCase의 핵심 메서드는 검증, 업무 행위, 상태 변경, 실패 시 보상이 읽히도록 쓴다. Redis key, JPA
  쿼리, 토큰 서명 같은 기술 구현은 해당 구현체에, 도메인 규칙은 도메인에 둔다.
  [검사 없음. 계층 의존 방향과 업무 코드의 Querydsl·Redisson 참조 금지는 `ArchitectureRulesTest`]
- 한 곳에서 쓰는 검증·매핑·조립은 같은 클래스의 private 메서드로 충분한지 먼저 본다. 메서드 길이와 역할
  이름만으로 별도 클래스를 만들지 않는다. 분리할 근거는 독립된 도메인 개념·생명주기, 트랜잭션·모듈·외부
  시스템 경계, 실제 재사용, 복잡한 정책 또는 분명한 인지 부하 감소다. [검사 없음]
- 분산락·재시도·멱등성·트랜잭션처럼 정확성을 지키는 추상화는 파일 수를 줄이려고 제거하지 않는다. 조회
  helper에 DB 조회나 다른 모듈 호출을 숨기지 않는다. [검사 없음]
- 조회 조건에서 `region == null`(필터 없음)과 지역에 공연장이 없어 빈 ID 집합이 된 경우(결과 0건)를
  구별한다. [검사 없음]

## 이름과 계약

### Use case와 중간 타입

- Use case 클래스는 `<Verb><Target>UseCase`, 진입 메서드는 `execute(...)`를 기본으로 한다. [검사 없음]
- `Request`는 `endpoint`의 HTTP 등 외부 adapter 입력에 쓴다. [검사 없음]
- `Input`과 `Output`은 use case 호출 계약이다. 의미 있는 값 객체나 `void`를 억지로 감싸지 않는다. [검사 없음]
- `Result`는 일반 출력 외에 cookie/token처럼 adapter가 별도로 소비할 값이 있을 때만 쓴다. [검사 없음]
- `Context`는 여러 application 단계 사이의 내부 처리 정보다. 외부 요청이 아닌 검증 결과에 `Request`를
  붙이지 않는다. 한 use case 안에서만 오가는 값이면 타입을 만들기 전에 지역 변수로 충분한지 먼저 본다.
  [검사 없음]
- 응답 항목은 그 use case의 중첩 record가 소유하고 이름은 `<Target>Response`로 짓는다. 엔티티와 같은
  이름(`Seat`)이나 `Item`은 쓰지 않는다. DB 집계 결과도 마찬가지다. 여러 use case가 함께 쓰는 조회
  파라미터·커서·정렬은 `usecase` package 최상위에 둔다. `Snapshot`은 특정 시점에 고정한 상태, `Criteria`는
  검색·판정 조건, `Param`은 목록·커서 조회 실행 파라미터에 쓴다. [검사 없음]
- **중간 타입을 계층마다 만들지 않는다.** 같은 값을 다른 응답 DTO로 그대로 복사하기만 하는 타입은 두지
  않는다. 별도 타입은 최종 응답 항목, DB 집계 결과, 모듈 공개 계약, 트랜잭션 snapshot처럼 구별되는 역할이
  있을 때 둔다. 일반 조회는 엔티티를 반환하고 use case가 응답을 조립한다. [검사 없음]
- 모듈 공개 interface는 `<module>.api`에 두고 `Api` 접미사를 붙인다. 모듈 밖으로 내보내는 조회 값은
  `*Snapshot`이다. [검사 없음. `api`에 구현 bean·구현 기술 금지는 `ArchitectureRulesTest`]

### 그 밖의 이름

- 별도 클래스의 역할이 분명할 때 `Reader`, `Writer`, `Validator`, `Registrar`, `Authenticator` 등을 쓰고,
  범용적인 `Manager`/`Helper` 이름은 피한다. [검사 없음]
- `find...`는 부재 가능 조회, `findAll...`은 빈 목록 가능 조회, `require...`는 실패 가능한 필수 조건,
  `exists...`는 존재 판정이다. Boolean은 `is`/`has`/`can`으로 표현한다. 생성은 `create`, 변환은
  `from`/`to...`, 단순 조립은 `of`가 기본이다. [검사 없음]
- 예외 이름은 `[Subject][Condition]Exception`으로 읽히게 한다. `@ConfigurationProperties` 바인딩은
  `Properties`, 검증을 끝낸 설정 값은 `Settings`다. [검사 없음]
- 도메인 생명주기는 `State`, 외부 표시 상태는 `Status`를 쓴다. [검사 없음]
- 이름 변경만으로 JSON·DB·오류 계약을 바꾸지 않는다. 오류 코드(`E`-code)는 외부 계약이라 공개 code·status·
  message를 이름 정리를 이유로 바꾸지 않는다. [검사 없음. E-code 전역 유일성은 `ErrorCodeUniquenessTest`]
- Production package마다 `package-info.java`의 `@NullMarked`가 필요하며 실제 nullable만 `@Nullable`로
  표시한다. [강제: `ArchitectureRulesTest`(package 선언), `compileJava`의 NullAway(null 계약)]

## 조회 코드

- Aggregate를 바꾸기 위한 저장·복원 계약은 domain `*Repository`에, 자기 모듈의 화면 조회 구현은
  persistence에 둔다. 자기 모듈 DB 조회에 1:1 port/adapter를 기본으로 만들지 않는다. 외부 시스템·모듈
  공개 계약·도메인 보호 같은 실제 경계는 유지한다. [강제: `ArchitectureRulesTest`(use case가 부를 수 있는
  persistence 타입과 승인된 조회 Repository 목록). 1:1 port를 만들지 않는 것은 검사 없음]
- 조회 결과와 중간 DTO의 기준은 위 "Use case와 중간 타입"을 따른다. 계층별 HTTP DTO를 일괄 도입하는
  제안은 [#249](https://github.com/ticket-project/ticket-core/issues/249)에서 검토 중이며 현재 정책을 바꾼
  결정은 아니다. 공개 JSON 계약을 변경할 때는 별도로 영향과 호환성을 검토한다. [검사 없음]
- 단순 조회는 Spring Data 메서드, 짧은 고정 조회는 `@Query`, 동적 조건·복합 정렬·커서·집계는 이점이
  분명할 때 Querydsl을 쓴다. 기존 Querydsl을 단순히 다른 문법으로 바꾸지 않는다. [검사 없음]
- Querydsl의 실제 조건은 Repository에서 읽혀야 하며 LIKE escaping, 판매 상태 CASE, 커서 비교와 tie
  breaker 같은 정확성 로직은 보존한다. 리팩터링에서는 WHERE, join, GROUP BY/DISTINCT, ORDER BY, null, 결과
  순서와 query 수를 테스트로 대조한다. 근거 없이 GROUP BY와 DISTINCT를 교환하지 않는다. [검사 없음]

## 형식

코드 형식의 원본은 `build.gradle`의 Spotless 설정이다. `./gradlew spotlessApply`로 적용한다.
[강제: CI의 `spotlessCheck`] `.editorconfig`는 IDE 입력을 맞추는 보조 설정이다.
