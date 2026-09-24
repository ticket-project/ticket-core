# 코드 작성 기준

모듈·패키지의 소유권과 의존 방향은 [architecture.md](architecture.md), 테스트 작성은 [testing.md](testing.md), 커밋·PR은 [CONTRIBUTING.md](../CONTRIBUTING.md)를 따른다. 이 문서의 프로젝트 정책은 현재 합의된 선택이며, 구조 변경 제안은 ADR과 테스트 영향까지 검토한다.

## 클래스와 메서드

UseCase의 핵심 메서드는 검증, 업무 행위, 상태 변경, 실패 시 보상이 읽히도록 쓴다. Redis key, JPA 쿼리, 토큰 서명 같은 기술 구현은 해당 구현체에 둔다. 별도 클래스를 만들 때는 독립된 도메인 개념·생명주기·트랜잭션·모듈·외부 시스템 경계, 실제 재사용, 복잡한 정책, 또는 분명한 인지 부하 감소 중 근거를 확인한다. 메서드 길이와 역할 이름만으로는 충분하지 않다. 한 곳에서 쓰는 검증·매핑·조립은 먼저 같은 클래스의 private 메서드를 검토한다. 이것은 일률적인 클래스 삭제 규칙이 아니다.

도메인 규칙은 도메인에 둔다. 다만 분산락, 재시도, 멱등성, 트랜잭션 경계처럼 정확성을 지키는 추상화는 파일 수를 줄이려고 제거하지 않는다. 조회 helper에 DB 조회나 다른 모듈 호출을 숨기지 않는다. `region == null`(필터 없음)과 지역에 공연장이 없어 빈 ID 집합이 된 경우(결과 0건)를 구별한다.

## 이름과 계약

### Use case와 중간 타입

- Use case 클래스는 `<Verb><Target>UseCase`, 진입 메서드는 `execute(...)`를 기본으로 한다.
- `Request`는 `endpoint`의 HTTP 등 외부 adapter 입력에 쓴다.
- `Input`과 `Output`은 use case 호출 계약이다. 의미 있는 값 객체나 `void`를 억지로 감싸지 않는다.
- `Result`는 일반 출력 외에 cookie/token처럼 adapter가 별도로 소비할 값이 있을 때만 쓴다.
  `LoginUseCase.Result`가 그 예다.
- `Context`는 여러 application 단계 사이의 내부 처리 정보다. 외부 요청이 아닌 검증 결과에
  `Request`를 붙이지 않는다. 다만 한 use case 안에서만 오가는 값이면 `Context` 타입을 만들기 전에
  지역 변수로 충분한지 먼저 본다.
- 응답 항목은 그 use case의 중첩 record가 소유하고(`GetShowsUseCase.Item`), DB 집계 결과도
  마찬가지다(`GetShowDetailUseCase.PriceSummary`). 여러 use case가 함께 쓰는 조회 파라미터·커서·정렬은
  `usecase` package 최상위에 둔다(`ShowSearchCriteria`, `ShowCursor`, `ShowSort`). `Snapshot`은 특정
  시점에 고정한 상태다. `Criteria`는 검색·판정 조건, `Param`은 목록·커서 조회 실행 파라미터에 쓴다.
- **중간 타입을 계층마다 만들지 않는다.** 값을 담았다가 다른 응답 DTO로 그대로 복사하기만 하는
  타입은 두지 않고, 조회는 엔티티를 반환해 use case가 `Output`을 직접 만든다. 별도 타입은 최종 응답
  항목(그 use case의 중첩 record), DB 집계 결과, 모듈 공개 계약, 트랜잭션 snapshot일 때만 둔다.

UseCase는 `<Verb><Target>UseCase`, 진입 메서드는 `execute(...)`가 기본이다. 별도 클래스의 역할이 분명할 때 `Reader`, `Writer`, `Validator`, `Registrar`, `Authenticator` 등을 쓰고, 범용적인 `Manager`/`Helper` 이름은 피한다. `find...`는 부재 가능 조회, `findAll...`은 빈 목록 가능 조회, `require...`는 실패 가능한 필수 조건, `exists...`는 존재 판정이다. Boolean은 `is`/`has`/`can`으로 표현한다. 생성은 `create`, 변환은 `from`/`to...`, 단순 조립은 `of`가 기본이다.

예외 이름은 `[Subject][Condition]Exception`으로 읽히게 한다. `@ConfigurationProperties` 바인딩은 `Properties`, 검증을 끝낸 설정 값은 `Settings`다. 도메인 생명주기는 `State`, 외부 표시 상태는 `Status`를 쓴다. 이름 변경만으로 JSON·DB·오류 계약을 바꾸지 않는다. Production package마다 `package-info.java`의 `@NullMarked`가 필요하며 실제 nullable만 `@Nullable`로 표시한다([ADR 0015](adr/0015-null-contracts-are-explicit-and-enforced.md)).

## 조회 코드

Aggregate를 바꾸기 위한 저장·복원 계약은 domain `*Repository`에, 자기 모듈의 화면 조회 구현은 persistence에 둔다. 자기 모듈 DB 조회에 1:1 port/adapter를 기본으로 만들지 않는 것은 이 저장소의 현재 정책이다. 외부 시스템·모듈 공개 계약·도메인 보호 같은 실제 경계는 유지한다. 조회는 엔티티를 반환하고 use case가 최종 응답으로 변환하는 것이 기본이며, DB 집계·트랜잭션 snapshot·모듈 공개 계약에는 별도 타입이 필요할 수 있다. 계층마다 같은 값을 복사하는 DTO는 기본값이 아니다. 공개 JSON에 영향을 주는 DTO 분리는 미승인 구조 변경 제안이다.

단순 조회는 Spring Data 메서드, 짧은 고정 조회는 `@Query`, 동적 조건·복합 정렬·커서·집계는 이점이 분명할 때 Querydsl을 쓴다. 기존 Querydsl을 단순히 다른 문법으로 바꾸지 않는다. Querydsl의 실제 조건은 Repository에서 읽혀야 하며 LIKE escaping, 판매 상태 CASE, 커서 비교와 tie breaker 같은 정확성 로직은 보존한다. 리팩터링에서는 WHERE, join, GROUP BY/DISTINCT, ORDER BY, null, 결과 순서와 query 수를 테스트로 대조한다. 근거 없이 GROUP BY와 DISTINCT를 교환하지 않는다.

## 형식

코드 형식의 원본은 `build.gradle`의 Spotless 설정이다. `./gradlew spotlessApply`로 적용하고 `./gradlew spotlessCheck`로 검사한다. `.editorconfig`는 IDE 입력을 맞추는 보조 설정이다. 포맷·정적 검사의 실제 옵션은 설정 파일을 확인한다.
