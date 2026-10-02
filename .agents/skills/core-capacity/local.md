# 로컬 측정

대상은 `java -jar`로 띄운 로컬 Core와 파일 H2다. Gatling은 같은 PC에서 돈다. 로컬 숫자는 병목을 찾는 데 쓴다.
확정한 C도 "로컬 후보"로 적는다. Queue 입장률은 운영에서 다시 잰다.

명령은 모두 `../gatling-test`(gatling-test 저장소 루트)에서 PowerShell로 실행한다. 아래 상대 경로도 그곳 기준이다.

## 준비

1. **8080을 누가 쓰는지 본다.**
   - `distributed-results-join/capacity/core-local.json`의 `pid`가 8080을 잡고 있고, 그 `commit`이 ticket HEAD와 같다
     → 그대로 쓴다. 4(예열)로 간다.
   - 재측정이다 → `core-local.json`의 `pid`만 `Stop-Process -Id <pid>`로 끄고 3으로 간다.
   - 다른 프로세스가 잡고 있다 → 끄지 않는다. PID와 명령줄을 보여주고 사용자에게 묻는다. 다른 세션의 서버일 수 있다.
2. **측정할 코드를 확인한다.** ticket의 브랜치, commit, 미커밋 변경 여부. `reset-local.ps1`은 그 시점의 코드를 빌드한다.
3. **Core를 띄운다.** 기존 DB를 다시 쓰는 것이 기본이다. 규모는 큰 예매 사이트를 가정한다(회원 100만, 주문 500만,
   15,000석 회차. 사용자 결정 2026-10-01).
   - `core-local.json`의 `seed`가 이 규모이고 `~/ticket-local.mv.db`가 있다 → `-KeepData`로 띄운다. 새 코드를 빌드하고,
     새 migration은 기동할 때 적용된다. 재측정도 이 경우다.

     ```powershell
     .\scripts\capacity\reset-local.ps1 -KeepData
     ```

   - DB가 없거나 규모가 다르다 → 새로 만든다. 옛 DB 파일은 지워지지 않고 `.bak-<시각>`으로 남는다(지금 규모면 약 4GB).
     그래서 새로 만들기 전에 사용자에게 이유를 말하고 확인을 받는다.

     ```powershell
     .\scripts\capacity\reset-local.ps1 -LargePerformanceCount 30 -Members 1000000 -BackgroundOrders 5000000
     ```

   - 빌드와 적재가 10분을 넘을 수 있다. 백그라운드로 돌리고 `ready:` 줄을 기다린다. health가 안 올라오면 출력에 나온 Core 로그를 본다.
4. **예열한다.** 안 쓴 회차 하나로 `-UsersPerSecond 10 -Label warmup`. Core를 다시 띄울 때마다 한다(P-005).

## 회차

대형 회차는 `920000001`부터 1씩 늘어난다(회차마다 15,000석). 실행마다 다음 번호를 쓴다. 쓴 번호는 steps.md의 회차 칸에 있다.
`-KeepData`로 다시 띄워도 쓴 회차는 다시 못 쓴다. 남은 회차가 5개 아래로 내려가면 Core를 띄운 채로 회차만 더한다.
seed는 모자란 회차만 더하고 이미 있는 회원·주문은 건너뛴다. 좌석 행이 늘어나므로 일지 조건에 적는다.

```powershell
cd ..\ticket; .\gradlew.bat seedLocal -q "-Dseed.load-test-fixture.large-performance-count=<지금 개수 + 30>"
```

## 실행

```powershell
.\scripts\capacity\run-step.ps1 -UsersPerSecond 20 -PerformanceId 920000003 -Label ladder
```

- 라벨: `warmup`, `ladder`, `repro`(재현), `diag`(진단), `remeasure`(고친 뒤), `narrow`(한계 좁히기).
- 진단 실행은 같은 부하, 새 회차, `-ThreadDumps 3 -Label diag`다.
- 한 번에 2~3분 걸린다. 출력 끝의 `verdict:` 줄과 `run dir:` 경로를 읽는다.

## 스레드 덤프 읽기

요청 스레드(이름이 `http-nio-8080-exec-`로 시작)만 센다. 스택을 위에서부터 읽어 처음 걸리는 것으로 분류한다.

| 스택에 보이는 것 | 뜻 |
| --- | --- |
| `HikariPool.getConnection` | DB 연결 차례를 기다린다 |
| `org.redisson`, `RedissonLock`, `tryLock` | 좌석 락을 기다린다 |
| `org.h2.` | DB 작업 중이다(쿼리 실행, H2 내부 락) |
| 맨 위가 `com.ticket.`이고 `RUNNABLE` | 앱 코드가 계산 중이다(JSON 직렬화 포함) |
| `NioSocketImpl.read`, `SocketDispatcher.read` | Redis 같은 네트워크 응답을 기다린다 |
| `TaskQueue.take` | 할 일이 없어 쉬는 중이다. 세지 않는다 |

분류마다 개수와, `com.ticket.` 프레임이 들어간 대표 스택 하나를 근거로 붙인다. 덤프 세 장의 분포가 비슷하면 잠깐이 아니라 계속되는 병목이다.

## 같은 PC와 H2라서 다른 점

- Gatling과 Core가 CPU를 나눠 쓴다. Gatling이 멈춘 실행은 INVALID로 걸러지지만, CPU를 나눠 쓴 만큼 Core가 느려지는 것은
  Core 쪽 숫자에도 들어간다. `core-metrics.tsv`의 `system_cpu`가 0.9 이상이면 "PC CPU가 찼다. Core만 돌 때보다 낮게 나왔을 수
  있다"고 보고에 적는다.
- H2는 Core JVM 안에서 돈다. Core의 CPU, 힙, GC에 DB 일이 섞여 있다. 쿼리에 쓴 시간은 덤프의 `org.h2.` 프레임으로 가른다.
- H2의 실행 계획과 락은 Oracle과 다르다. 로컬에서 찾은 쿼리 문제는 "Oracle에서 같은지 미확인"으로 적는다.
- 실행 계획은 `run-step.ps1`이 주문 수를 셀 때 쓰는 H2 Shell 명령에 `EXPLAIN ANALYZE <SQL>`을 넣어 본다.
