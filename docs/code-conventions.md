# 코드 관례

이 문서는 현재 코드에서 반복되는 이름과 배치 규칙의 단일 기준이다. 새 접미사를 기계적으로
붙이지 않고, 같은 책임은 같은 어휘로 표현한다. 모듈 경계와 의존 방향은
[architecture.md](architecture.md)가 원본이다.

## 패키지와 역할

업무 코드는 `com.ticket.<module>.<capability>.<layer>` 순서로 둔다. 예를 들어
`booking.order.application`, `show.catalog.domain`, `member.auth.web`처럼 읽는다.
`web`은 HTTP 계약, `application`은 use case와 조합, `domain`은 상태와 업무 규칙,
`infrastructure`는 DB·Redis·외부 client 구현을 소유한다. 같은 모듈의 여러 capability가 함께 쓰는
도메인 기반 타입은 `show.domain.ShowAuditedEntity`처럼 module-level `domain`에 둘 수 있다.

## Application 계약

- Use case 클래스는 `<Verb><Target>UseCase`, 진입 메서드는 `execute(...)`를 기본으로 한다.
- `Request`는 `web`의 HTTP 등 외부 adapter 입력에 쓴다.
- `Input`과 `Output`은 use case 호출 계약이다. 의미 있는 값 객체나 `void`를 억지로 감싸지 않는다.
- `Result`는 일반 출력 외에 cookie/token처럼 adapter가 별도로 소비할 값이 있을 때만 쓴다.
  `LoginUseCase.Result`가 그 예다.
- `Context`는 여러 application 단계 사이의 내부 처리 정보다. `ValidatedOrderContext`처럼 외부 요청이
  아닌 검증 결과에 `Request`를 붙이지 않는다.
- `Row`는 persistence/query projection, `View`는 응답용 조합 모델, `Snapshot`은 특정 시점에 고정한
  상태다. `Criteria`는 검색·판정 조건, `Param`은 목록·커서 조회 실행 파라미터에 쓴다.

## Component와 메서드

- Aggregate 저장 계약은 `Repository`, 읽기 전용 projection 계약은 `QueryPort`, 그 구현은 기술을
  드러내는 이름(`QuerydslSeatStateQueryPort`)을 쓴다.
- `Reader`/`Writer`는 읽기·쓰기 한쪽 책임, `Validator`는 검증, `Registrar`는 등록,
  `Authenticator`는 자격 증명 확인을 뜻한다. 책임이 더 좁으면 `Service`보다 이 이름을 우선한다.
- `find...`는 없을 수 있는 조회, `findAll...`은 빈 컬렉션이 가능한 복수 조회, `exists...`는 존재
  여부, `require...`는 실패 가능한 필수 조건에 쓴다. boolean 판단은 `is`/`has`/`can`을 쓴다.
- 새 객체·리소스 생성은 `create`, 다른 표현에서 변환은 `from`, 단순 값 조립은 `of`, 반대 방향
  변환은 `to...`를 기본으로 한다. 기존 Repository의 absence 처리 정책은 application에 유지한다.

## 이름 세부 규칙

- 예외는 `[Subject][Condition]Exception` 형태로 읽히게 한다. 예: `SeatAlreadyHeldException`,
  `HoldLimitExceededException`. Java 클래스명 교정으로 오류 코드·상태·공개 메시지를 바꾸지 않는다.
- `@ConfigurationProperties` binding 타입은 `XxxProperties`, 검증을 끝낸 별도 설정 값은
  `XxxSettings`로 구분한다.
- 도메인 lifecycle은 `State`를 기본으로 하고, 외부 조회/응답에 단순화한 표현은 `Status`를 허용한다.
  JSON 필드나 DB 저장값에 영향을 주는 일괄 rename은 하지 않는다.
- acronym은 변수에서 `oauth2`, `jwt`, `url`, `uri`처럼 일반 camelCase로 쓴다.

## 테스트와 형식

- 개별 단위·구조 테스트는 `XxxTest`, HTTP/API 계약은 `XxxContractTest`, Spring Modulith slice는
  `XxxModuleTests`를 쓴다. 데이터 생성은 `XxxFixture`, test double은 `FakeXxx` 또는 호출을 기록하는
  `RecordingXxx`로 역할을 드러낸다.
- 여러 모듈의 JPA/Querydsl 테스트가 공유하는 기반 코드는 `com.ticket.testsupport.persistence`에 둔다.
- Java와 seed source는 Spotless가 4칸 들여쓰기, 120자 줄 길이, 명시적 import, import 순서,
  trailing whitespace와 EOF newline을 관리한다. `./gradlew spotlessApply`로 수정하고
  `./gradlew spotlessCheck`로 검사한다. wildcard import는 허용하지 않는다.
