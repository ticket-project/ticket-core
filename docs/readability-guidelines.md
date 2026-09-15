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

### 4. 한 번만 쓰는 helper는 private method부터 검토한다

특정 UseCase 하나에서만 쓰는 검증·매핑·변환·조립은 별도 Spring bean보다 같은 클래스의 private
method를 먼저 검토한다. bean으로 만들 이유는 위 3의 목록에 있어야 한다.

### 5. 실제 경계는 유지한다

다음은 클래스를 나눌 강한 이유다. 가독성을 이유로 없애지 않는다.

```text
트랜잭션    Redis    데이터베이스    모듈    외부 API    분산락    aggregate 생명주기
```

예매 시작에서 `BookingAvailabilityChecker`와 `PendingOrderCreator`가 별도 bean인 이유는
**오직 트랜잭션 경계 하나**다. 같은 클래스의 private method로 부르면 Spring proxy가 적용되지 않아
`@Transactional`이 아예 걸리지 않는다. 그 이유를 각 클래스의 javadoc에 적는다.

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
`OrderHoldHistoryRecorder`(주문 취소 흐름도 쓴다 — 실제 재사용), `AdmissionVerifier`(외부 토큰 검증
경계, 세 use case가 쓴다).

**모든 UseCase를 이 모양에 억지로 맞추라는 뜻은 아니다.** 판단 기준은 위 원칙이고, 이것은 그 기준을
적용한 예다.

## 이 기준으로 코드를 볼 때 묻는 것

- 이 기능을 이해하려고 파일을 몇 개 열었는가?
- UseCase만 읽고 업무 흐름을 설명할 수 있는가?
- 이 클래스가 존재하는 이유를 한 문장으로 말할 수 있는가? 그 이유가 원칙 3의 목록에 있는가?
- 이 클래스를 지우면 무엇이 깨지는가? "테스트"만 깨진다면 왜 있는가?
- 중요한 업무 행위가 helper 뒤에 숨어 있지 않은가?
