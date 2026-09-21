# 읽기 쉬운 코드 기준

이 문서는 **"기능 하나를 이해하는 데 드는 비용"을 줄이는 기준의 원본**이다. 이름·패키지 배치는
[code-conventions.md](code-conventions.md)가, 모듈 경계와 의존 방향은
[architecture.md](architecture.md)가 원본이다. 여기서는 **경계 안쪽을 어떻게 쓸 것인가**만 다룬다.

경계와 가독성이 부딪히면 경계가 이긴다. 읽기 쉽게 만들려고 트랜잭션·모듈·외부 시스템 경계를
허무는 것은 허용하지 않는다.

## 왜 이 문서가 생겼는가

이 저장소는 모듈 경계와 그 검증은 잘 잡혀 있었지만, 기능 하나를 읽으려면 파일을 계속 열어야 했다.
예매 시작 흐름이 대표적이었다.

```text
CreateOrderUseCase
├─ CreateOrderPreparer
│  └─ PendingOrderLocalValidator
│     └─ HoldSeatAvailabilityValidator
└─ CreatePendingOrderTransactionService
   └─ OrderCreator
```

"주문이 실제로 어디서 만들어지는가"를 알려면 파일 여섯 개를 지나야 했다. 각 클래스는 20~60줄로
짧았지만, **짧은 클래스가 여섯 개인 것은 단순한 코드가 아니다.**

## 원칙

### 1. UseCase는 업무 이야기를 보여준다

UseCase의 public method(또는 그 바로 아래 핵심 method)를 읽으면 다음이 이해돼야 한다.

- 무엇을 검증하는가
- 어떤 중요한 업무 행위를 하는가
- 어떤 상태가 바뀌는가
- 어떻게 성공하는가
- 실패하면 어떻게 되돌리는가

`StartBookingUseCase.startBooking`이 그 예다. 판매 가능 확인 → 회원 확인 → 좌석 확인 → 표시값
조회 → 좌석 선점 → 주문 생성 → 실패 시 선점 해제가 위에서 아래로 읽힌다.

### 2. 중요한 업무 행위는 숨기지 않는다

다음은 구현 detail이 아니라 업무 사실이다. 흐름에서 이름으로 드러나야 한다.

```text
좌석 선점    주문 생성    좌석 추가    결제 승인    티켓 발급    선점 보상 해제
```

반대로 다음은 숨긴다.

```text
Redis key 형식    Redisson API    Lua script    JPA/Querydsl 쿼리
Event Publication Registry 내부    토큰 서명 방식
```

> 업무 이야기는 보여주고, 기술 메커니즘은 숨긴다.

### 3. 필요한 abstraction만 만든다

새 클래스를 만들려면 아래 중 **하나 이상**의 근거가 있어야 한다.

- 독립적인 도메인 개념
- 독립적인 생명주기
- 트랜잭션 경계
- 모듈 경계
- 외부 시스템 경계
- 실제 재사용(호출자가 둘 이상)
- 독립적으로 복잡한 정책
- 분명한 인지 부하 감소

다음은 근거가 **아니다.**

- 메서드가 길다
- 이름을 붙일 수 있다
- `Validator`/`Creator`/`Service`라고 부를 수 있다
- 테스트하기 쉬워진다

특히 마지막 항목을 조심한다. 한 곳에서만 쓰는 collaborator를 만들어 mock으로 검증하면, 테스트가
업무 행동이 아니라 **구현 모양**을 고정하게 된다.

**interface도 같은 기준으로 만든다.** 구현이 하나뿐이고 시그니처가 같은 port는 계약이 아니라
경유 지점이다 — 자기 module DB를 읽는 조회에는 port를 두지 않고 `persistence`의 구체
`*QuerydslRepository`가 조회를 직접 갖는다(`show.persistence.ShowQuerydslRepository`). interface는 실제
계약·교체 지점·domain 보호처럼 근거가 있을 때 둔다. Redis, 분산락, JWT, WebSocket, 외부 API 같은
**의미 있는 외부 시스템 경계**와 다른 module에 공개하는 API가 그 자리다. 위임만 하는 service도
마찬가지다 — 단순 local 조회는 공개 API interface를 직접 구현해도 된다
(`venue.persistence.VenueRepositoryAdapter implements VenueLookupApi`).

### 4. 한 번만 쓰는 helper는 private method부터 검토한다

특정 UseCase 하나에서만 쓰는 검증·매핑·변환·조립은 별도 Spring bean보다 같은 클래스의 private
method를 먼저 검토한다. bean으로 만들 이유는 위 3의 목록에 있어야 한다.

**여러 `Input`이 문구까지 똑같이 반복하던 "null 검사 → 양수 검사"는 `com.ticket.shared.api.InputChecks`
의 정적 메서드 `requirePositiveId`/`requireProvided`로 모은다.** 그 이상으로 나가지 않는다 — Spring
bean, 검증 인터페이스, `Input`별 Validator, Bean Validation 교체, 커스텀 애너테이션은 만들지 않는다.
오류 문구와 `null`/0 이하의 구분, 검사 순서는 공개 계약이라 그대로 보존한다. 조회별 `size` 상한처럼
`Input`마다 다른 규칙과 도메인이 소유한 규칙은 각자의 자리에 남는다.

### 5. 실제 경계는 유지한다

다음은 클래스를 나눌 강한 이유다. 가독성을 이유로 없애지 않는다.

```text
트랜잭션    Redis    데이터베이스    모듈    외부 API    분산락    aggregate 생명주기
```

예매 시작에서 `BookingAvailabilityChecker`와 `PendingOrderCreator`가 별도 bean인 이유는
**오직 트랜잭션 경계 하나**다. 같은 클래스의 private method로 부르면 Spring proxy가 적용되지 않아
`@Transactional`이 아예 걸리지 않는다. 그 이유를 각 클래스의 javadoc에 적는다.

**다만 "경계를 지킨다"와 "경계를 감싼 클래스를 남긴다"는 다른 말이다.** 트랜잭션 경계는 유지해야
하지만, **이미 있는 다른 public bean이나 adapter가 같은 경계를 안전하게 소유할 수 있다면 경계만
감싸는 wrapper 클래스는 없앨 수 있다.** 옮길 곳이 Spring이 관리하는 다른 빈이면 proxy는 그대로
적용되므로 경계가 사라지지 않는다.

판단은 **그 경계 안에서 하는 일이 몇 개인가**로 한다.

| 경계 안의 일 | 어디가 소유하는가 |
| --- | --- |
| DB 접근 하나 | 그 조회를 실행하는 `*QuerydslRepository`나 `*RepositoryAdapter`가 직접 갖는다 |
| DB 접근 둘 이상을 한 시점으로 묶어야 한다 | 별도 bean이 필요하다 — 묶는 것 자체가 그 bean의 일이다 |
| 트랜잭션 없는 listener에서 lazy 연관을 읽어야 한다 | 별도 bean이 필요하다 — proxy가 없으면 초기화에 실패한다 |
| 공개 계약 구현이 자기 연산마다 경계를 갖는다 | 그 구현이 직접 갖는다 — 밖에서 인터페이스로 부르므로 self-invocation이 아니다 |

`SeatStateSnapshotReader`가 첫 줄에 해당해 사라졌고
(`PerformanceSeatRepositoryAdapter.findSeatStates`가 경계를 가져갔다),
`SeatAvailabilitySnapshotReader`와 `OrderHoldSnapshotReader`는 둘째·셋째 줄에 해당해 남았다.
`MemberAccountService`는 넷째 줄이라 협력자 셋을 흡수하면서 각 연산이 자기 `@Transactional`을 갖게 됐다.

**옮기기 전에 경계가 실제로 적용되는지 테스트로 확인한다.** 결과 값은 경계가 무너져도 그대로라
행동 테스트로는 드러나지 않는다 — 실제 컨텍스트에서 Spring의 `TransactionAttributeSource`에 물어
"누가 무엇에 트랜잭션 advice를 적용하는가"를 고정한다(`SeatStatusTransactionBoundaryTest`).

`HoldManager`, `LockManager`, Repository 계약, cross-module API도 합치지 않는다. Hold는 독립적인
생명주기와 TTL, Redis 일관성 경계를 갖는다. UseCase가 알아야 하는 것은 "좌석을 선점한다"이지
"Redis에 어떻게 저장하는가"가 아니다.

### 6. 도메인 규칙은 도메인에 남긴다

`Order.addOrderSeat()`, `PerformanceSalesPolicy.ensureAcceptingOrders()`, `Hold.create()` 같은
불변식은 도메인이 소유한다. UseCase가 그 판정을 다시 구현하지 않는다. UseCase는 **언제 부를지**를
정하고, **무엇이 맞는지**는 도메인이 정한다.

### 7. 줄 수보다 인지 부하, 그리고 파일 이동 횟수

메서드가 조금 길어도 다음이면 허용한다.

- 업무 흐름이 위에서 아래로 읽힌다
- 분기와 중첩이 얕다
- 다른 파일로 이동할 일이 줄어든다

**기계적인 메서드 줄 수 제한을 두지 않는다.** 대신 다음 모양을 경계한다.

```text
UseCase → Coordinator → Preparer → Validator → Creator → Writer
```

각 클래스가 20줄이어도 단순한 코드가 아니다. **기능 하나를 이해하기 위해 열어야 하는 파일 수**를
복잡도로 본다.

### 8. 일반적인 이름을 남발하지 않는다

```text
Manager    Processor    Coordinator    Preparer    Helper    Util    Service    Handler
```

업무 어휘가 있으면 그것을 먼저 쓴다. 역할 접미사는 **별도 클래스를 만들기로 결정한 뒤** 가장
구체적인 이름을 고르는 단계에서 쓴다 — 접미사가 클래스를 만들 이유가 되면 순서가 거꾸로다.

### 9. 테스트는 구현 모양이 아니라 행동을 고정한다

가능하면 업무 행동, 외부 계약, 아키텍처 경계, 도메인 불변식을 테스트한다. 내부 helper 클래스
이름이나 정확한 메서드 시그니처를 불필요하게 고정하지 않는다 — 그러면 구조를 고칠 때마다 테스트가
깨지고, 테스트가 리팩터링을 막는다.

## 조회 코드

### 10. Querydsl은 기본 선택이 아니다

**Querydsl을 조회의 기본값으로 두지 않는다.** 단순 조회는 Spring Data 파생 메서드로, 짧은 고정
쿼리는 `@Query`로 단순화한다. Querydsl은 그 이점이 분명한 곳에만 남긴다 — 동적 조건과 optional
filter 조합, 복합 정렬, 커서 페이징, 집계와 복잡한 join이 그 자리다.

| 조회의 성격 | 쓸 것 |
| --- | --- |
| 단일 id 조회, `exists`, 고정 조건 | Spring Data 파생 메서드 |
| 짧은 고정 projection·고정 join | `@Query`(필요하면 생성자 표현식) |
| 동적 조건, 복합 정렬, 커서 페이징, 집계·복합 join | Querydsl |

**단, 대체가 오히려 읽기 어려워지면 기존 Querydsl을 유지한다.** 긴 파생 메서드 이름
(`findAllByShowIdOrderByStartTimeAscPerformanceNoAsc`보다 더 긴 것)이나 복잡한 문자열 쿼리가
생긴다면 바꾸지 않는다. 바꾸더라도 projection·정렬·null 처리·join·query 개수는 그대로여야
한다(§14).

#### 10-1. 조회는 엔티티를 반환한다

Querydsl을 유지하든 아니든, **조회는 엔티티를 반환하고 최종 응답으로 한 번만 변환한다.** 값을 한 번
담았다가 그대로 다시 옮기는 단계를 만들지 않는다. show 목록·검색·오픈예정도, booking의 좌석 조회도
엔티티를 돌려준다 — 목록 전건에 `Show.info`(CLOB)가, 좌석 상태 조회에 회차 전 좌석이 실리는 비용은
그 대가로 받아들인다.

projection이 남는 자리는 **엔티티로 표현되지 않는 조회 결과**다.

```text
DB 집계(min/max/count)    여러 테이블을 한 값으로 접는 복합 JOIN 결과
모듈 공개 계약(snapshot)    트랜잭션 snapshot
```

`PriceSummary`가 그 예다 — 회차 전체 가격의 최소·최대를 DB가 계산한 결과라, 가격을 전부 메모리로
읽어 계산하지 않는다. 반대로 커서 페이징의 1단계 id 조회처럼 엔티티가 아직 필요 없는 단계는 그
단계에서만 scalar를 읽고, 2단계에서 엔티티를 한 번에 읽는다.

#### 10-2. "계층마다 DTO"는 타입을 남길 이유가 아니다

**값을 담았다가 다른 응답 DTO로 그대로 복사하기만 하는 중간 타입은 없앤다.** 계층마다 DTO가 있어야
한다는 것은 근거가 아니다. 다음은 남긴다.

```text
최종 응답 항목    집계·복합 JOIN 결과    모듈 공개 계약    트랜잭션 snapshot
```

**파일 이동이나 내부 record 재배치는 정리가 아니다.** 실제로 없어지는 타입과 사라지는 변환 단계가
있어야 한다. 그 자리를 메우는 새 `Mapper`/`Assembler`/wrapper나 MapStruct는 만들지 않는다.

**최종 응답 항목은 그 use case의 중첩 record가 소유한다.** 응답 모양 하나에 파일 하나를 두지 않는다
— `GetShowsUseCase.Item`, `GetSeatStatusUseCase.Seat`, `GetShowDetailUseCase.VenueInfo`가 그 예다.

공연 상세가 그 예다. show 조회는 19필드짜리 `ShowDetailView`를 조립하는 대신
`findShow`(엔티티)·`findGenreNames`·`findRepresentativePerformanceGrades`·`findGradeNames`·
`findPriceSummary`·`findPerformances`·`findPerformer` 조각만 주고, `GetShowDetailUseCase`가 Output을
직접 만든다. 주문 상세·상태도 Querydsl projection 대신 `OrderRepository`의
`@Query`(`join fetch o.orderSeats`)로 `Order`를 받아 use case가 Output을 만든다.

### 11. Querydsl 자체와 Querydsl 주변의 포장을 구분한다

Querydsl은 그 자체로 읽을 수 있는 코드다.

```java
queryFactory.select(...).from(show).leftJoin(...).where(...).orderBy(...).limit(...).fetch();
```

이것을 숨기려고 `JoinBuilder`/`ConditionBuilder`/`QueryExecutor`/`ProjectionBuilder` 같은 계층을
쌓지 않는다. **실제 query의 의미가 `*QuerydslRepository`에서 보여야 한다.**

`var where = conditionBuilder.build(criteria);` 한 줄 때문에 검색 조건이 keyword·category·genre·
region·기간·판매 상태라는 사실이 전혀 보이지 않는다면, 몇 줄 늘어나더라도 조건을 늘어놓는 편이 낫다.

**새 범용 query framework를 만들지 않는다** — `BaseQuerydslRepository`, `QueryExecutor`,
`PredicatePipeline`, `CursorStrategyFactory` 같은 것들이다. 지금 문제는 abstraction 부족이 아니다.

### 12. 다만 correctness abstraction은 보존한다

반대로 다음은 별도 helper의 값어치가 높다. 틀리기 쉽고, 틀리면 조용히 틀리기 때문이다.

```text
LIKE escaping    대소문자 무시 검색    판매 상태 CASE expression
커서 비교 조건    tie breaker    복합 정렬 정책    페이징 correctness
```

> wrapper는 걷어내되 correctness abstraction은 보존한다.

정렬 정의와 커서 비교 조건처럼 **같은 규칙이 두 곳에 복제되면** 언젠가 어긋난다. 한쪽을 고치고
다른 쪽을 잊는 순간 페이지 사이에 중복이나 누락이 생기고, 그것은 테스트 없이는 보이지 않는다.
가능하면 같은 정의를 공유하게 만든다 — 그러자고 새 framework를 만들지는 않는다.

### 13. 조회 helper 안에 I/O를 숨기지 않는다

조건을 만드는 class 안에서 다른 module API를 부르거나 DB를 조회하면, 이름이 말하는 것과 실제로
하는 일이 달라진다. 가능하면 application이 그 조회를 먼저 하고 결과 값을 `*QuerydslRepository`에
넘긴다.

```text
region -> VenueLookupApi.findIdsByRegion(...) -> venueIds -> ShowQuerydslRepository.findAllBySearch(..., venueIds)
```

그러면 Querydsl 코드는 `show.venueId.in(venueIds)`라는 자기 DB query에 집중한다. 단 **module
경계와 결과 semantics가 완전히 같을 때만** 옮긴다 — 빈 집합일 때의 동작까지 확인한다.

**"조건 없음"과 "조건은 있는데 해당하는 것이 없음"을 같은 값으로 표현하지 않는다.** 위 예에서
`region == null`(지역 필터 없음)과 `region`은 있는데 그 지역에 공연장이 없어 `venueIds`가 빈 집합인
경우는 결과가 정반대다 — 앞은 전체, 뒤는 0건이다. 그래서 넘기는 타입이 `Set<Long>`이 아니라
`@Nullable Set<Long>`이어야 한다. 둘을 뭉개면 "제주에 공연장이 없다"가 "전체 목록"으로 바뀌고,
이 회귀는 조용하다. 옮기기 전에 두 경우를 각각 고정하는 테스트를 먼저 쓴다.

### 14. 리팩터링은 동작을 바꾸지 않는다

조회 코드를 고칠 때 다음이 하나라도 달라지면 그것은 리팩터링이 아니라 변경이다.

```text
WHERE    join type과 ON 조건    GROUP BY / DISTINCT    ORDER BY와 ASC/DESC    tie breaker
LIMIT    커서 비교    null 처리    projection    결과 순서    query 개수(N+1)
```

특히 `GROUP BY`를 `DISTINCT`로(또는 반대로) 바꾸지 않는다 — join 행 중복, dialect 차이,
`SELECT DISTINCT` + `ORDER BY` expression 제약 같은 이유가 있을 수 있다. 확신이 없으면 고치지 않고
후보로만 남긴다.

"같아 보인다"로 판단하지 않는다. 같은 입력에 같은 결과·같은 예외·같은 순서·같은 query 개수인지를
테스트로 확인하고, 확인할 테스트가 없으면 **먼저 현재 동작을 고정하는 테스트를 쓴다.**

## 대표 예: 예매 시작(Start Booking)

### Before

```text
CreateOrderUseCase
├─ CreateOrderPreparer            판매 정책·입장·회원·좌석·표시값을 전부 감쌈
│  └─ PendingOrderLocalValidator  @Transactional(readOnly) 짧은 읽기
│     └─ HoldSeatAvailabilityValidator
└─ CreatePendingOrderTransactionService   @Transactional, 저장/이력/이벤트
   └─ OrderCreator                        Order 조립
```

### After

```text
StartBookingUseCase
├─ (private) 판매 정책 확인 · 입장 확인 · 회원 활성 확인
├─ BookingAvailabilityChecker   @Transactional(readOnly) -- 트랜잭션 경계
├─ HoldManager                  Redis 선점 -- 외부 시스템 경계
└─ PendingOrderCreator          @Transactional -- 트랜잭션 경계, 주문 생성 전부
```

- `StartBookingUseCase` = 예매 시작이라는 workflow를 조율한다. Redis 선점과 DB 주문이라는 서로 다른
  일관성 경계를 순서대로 엮고, 뒤가 실패하면 앞을 보상한다.
- `PendingOrderCreator` = 한 DB 트랜잭션 안에서 실제 Order를 조립하고 저장한다. 이 파일을 열면
  `new Order` → `addOrderSeat` → `save` → `HoldHistory` → `OrderStarted`가 바로 보인다. 여기서 또
  다른 Creator/Assembler로 들어갈 필요가 없다.

없앤 abstraction과 이유:

| 없앤 것 | 어떻게 | 왜 |
| --- | --- | --- |
| `CreateOrderPreparer` | UseCase의 private method로 | 한 곳에서만 쓰였고, 중요한 업무 검증을 전부 가리고 있었다 |
| `ValidatedOrderContext` | 지역 변수로 | preparer가 사라지자 값을 묶어 넘길 이유가 없어졌다 |
| `PendingOrderLocalValidator` | `BookingAvailabilityChecker`로 흡수 | 트랜잭션 경계 하나에 검증 둘이 있으면 충분하다 |
| `HoldSeatAvailabilityValidator` | 같이 흡수 | 호출자가 그 validator 하나뿐이었다 |
| `OrderCreator` | `PendingOrderCreator`로 흡수 | 트랜잭션 경계 뒤에 실제 생성 구현을 다시 숨기고 있었다 |

남긴 것과 이유: `HoldManager`(Redis 일관성 경계), `LockManager`(분산락),
`OrderHoldHistoryRecorder`(주문 취소 흐름도 쓴다 — 실제 재사용), `AdmissionGuard`(대기열 입장 검증
경계, 세 use case가 쓴다 — 외부 토큰 검증 자체는 그 안의 `AdmissionVerifier`).

**모든 UseCase를 이 모양에 억지로 맞추라는 뜻은 아니다.** 판단 기준은 위 원칙이고, 이것은 그 기준을
적용한 예다.

## 이 기준으로 코드를 볼 때 묻는 것

- 이 기능을 이해하려고 파일을 몇 개 열었는가?
- UseCase만 읽고 업무 흐름을 설명할 수 있는가?
- 이 클래스가 존재하는 이유를 한 문장으로 말할 수 있는가? 그 이유가 원칙 3의 목록에 있는가?
- 이 클래스를 지우면 무엇이 깨지는가? "테스트"만 깨진다면 왜 있는가?
- 중요한 업무 행위가 helper 뒤에 숨어 있지 않은가?
