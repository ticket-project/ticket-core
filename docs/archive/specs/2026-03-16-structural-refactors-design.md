> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-16. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Structural Refactors Design

**목표**
- 테스트를 길게 만드는 구조적 결합을 줄이고, 오케스트레이션/시간/외부 저장소/조회 정책 책임을 분리한다.

**작업 단위**
1. 주문 시작/종료 오케스트레이션을 결과 객체 기반 도메인 서비스로 분리
2. 현재 시각/UUID 생성을 주입 가능한 공급자로 분리
3. Hold/SeatSelection의 Redis 직접 접근을 adapter 뒤로 이동
4. Show 조회의 조건/시간/커서 정책을 분리

**주문 오케스트레이션 방향**
- `StartOrderUseCase`는 입력 정규화와 접근 제어만 담당한다.
- hold 생성, 주문 생성, hold 이력 저장은 `OrderStartDomainService`가 수행하고 `OrderStartResult`를 반환한다.
- `TerminateOrderUseCase`는 주문 조회만 담당하고, 상태 전이와 좌석/hold history 로딩은 `OrderTerminationDomainService`로 이동한다.

**시간/UUID 방향**
- 공용 `Clock`, `UuidSupplier` 빈을 도입한다.
- `LocalDateTime.now()`와 `UUID.randomUUID()`가 테스트 취약성을 만드는 곳부터 우선 치환한다.

**Redis adapter 방향**
- `HoldStore`, `SeatSelectionStore` 인터페이스를 추가한다.
- 현재 Redisson 구현을 adapter로 이동하고, 서비스는 저장소 인터페이스에만 의존한다.

**Show 조회 방향**
- 조건 조립, 시간 창 정책, 커서 해석을 별도 컴포넌트로 분리한다.
- `ShowListQueryRepository`는 Querydsl 조회/조립에 집중한다.
