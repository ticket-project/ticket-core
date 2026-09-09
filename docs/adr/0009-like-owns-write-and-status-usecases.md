# 찜하기·찜 해제·찜 상태 조회는 like가 소유하고, "내 찜 목록"만 show에 남는다

## 상태(2026-09-09): 채택·구현됨. ADR 0008 §4를 supersede

## 배경

ADR 0008 §4는 "찜의 HTTP endpoint·use case는 여전히 show module에 있다"고 결정했다 —
`show -> like`(찜 개수·조회 위임)를 지울 수 없는데, 존재·회원 확인 때문에 `like -> show`가
새로 생기면 순환이 된다는 이유였다.

다시 살펴보니 그 순환의 원인은 "존재 확인"과 "회원 확인" 둘 다가 아니라 **존재 확인 하나뿐**이다.

- **존재 확인**("이 공연이 있는가")은 show가 아는 사실이다. `like`가 이걸 직접 확인하려면
  `like -> show`가 생긴다.
- **회원 활성 확인**("이 회원이 탈퇴하지 않았는가")은 `member`가 아는 사실이고, `member`는
  어떤 업무 module도 참조하지 않는 leaf다. `like -> member`는 새로 생겨도 순환을 만들지
  않는다 — 아무도 `member -> like`를 갖지 않기 때문이다.

즉 **"존재 확인을 포기하면" 찜하기·찜 해제·찜 상태 조회를 통째로 like로 옮길 수 있다.** 남는
질문은 하나였다: 존재 확인을 포기해도 되는가. JWT 인증만으로 이 확인을 대체할 수 있는지
조사했더니 안 된다는 결론이 나왔다(아래 "회원 확인은 포기하지 않는 이유" 참고) — 그래서 회원
확인은 `like`가 직접 재확인하도록 그대로 옮긴다.

## 결정

1. **찜하기·찜 해제·찜 상태 조회는 like가 소유한다.** `AddShowLikeUseCase`/
   `RemoveShowLikeUseCase`/`GetShowLikeStatusUseCase`(show)를 각각
   `AddLikeUseCase`/`RemoveLikeUseCase`/`GetLikeStatusUseCase`(like)로 옮기고, `LikeType`을
   받는 일반 형태로 바꾼다(`Input(memberId, LikeType, targetId)`). `ShowLikeController`도
   `like.web.LikeController`로 옮긴다. HTTP 계약(`/api/v1/likes/shows/{showId}`)과 오류
   코드(`E7001`)는 바뀌지 않는다 — controller가 내부적으로 `LikeType.SHOW`를 고정해 넘긴다.
2. **대상 존재 확인을 뺀다.** 옮겨간 세 use case는 더 이상 대상(show) 존재를 확인하지
   않는다 — 존재하지 않는 targetId를 찜해도 조용히 저장된다. `like` package-info의
   "이 module은 다른 BC의 entity 존재 여부를 자기 invariant로 잡지 않는다"는 서술이 이제
   실제로 강제된다(예전에는 그 서술이 like 자신에 대한 얘기였을 뿐, 검증은 호출자인 show가
   대신 해줬다).
3. **회원 활성 확인은 포기하지 않는다.** JWT 인증 필터(`AccessTokenAuthenticationFilter` +
   `JwtAccessTokenCodec`)는 서명·만료만 검사하고 DB를 재조회하지 않는다. 회원 탈퇴는 이미
   발급된 access token을 무효화하지 않는다(블랙리스트도, 강제 만료도 없다) — 그래서 탈퇴
   직후에도 토큰 만료 전까지는 인증이 통과된다. `GetOrderStatusUseCase`/`GetOrderDetailUseCase`
   /`CancelOrderUseCase`도 같은 이유로 `MemberLookup.requireActive`/`getProfile`을 재확인한다 —
   "탈퇴한 회원은 자기 데이터를 못 본다"는 규칙을 지키기 위해서다. 옮긴 세 use case도 같은
   규칙을 그대로 지킨다 — `like`가 `MemberLookup.requireActive`를 직접 부른다
   (`like -> member`, 새 edge지만 순환 없음).
4. **"내 찜 목록"(`GetMyShowLikesUseCase`, `MyShowLikesController`)은 show에 남는다.** 이
   화면은 각 찜 항목에 공연 제목·이미지·공연장 이름까지 채워야 한다 — 단순 존재 확인이
   아니라 실제 표시 데이터 조립이다. `like`로 옮기면 그 조립 때문에 `like -> show`가 생겨
   3번이 막으려 한 순환이 다른 경로로 되살아난다. `GetShowDetailUseCase`가 공연 상세에 찜
   개수를 조합하는 것과 정확히 같은 모양(show가 다른 BC의 표시값을 끌어와 화면을 완성하는
   조합)이라 show 소유가 맞다.

## 결정하지 않는 것

**"내 찜 목록"이 영구히 show 소유라고 결정하지 않는다.** 이 use case는 사실 "찜"보다 "내
정보"에 더 가깝다 — 회원 한 명의 여러 BC 데이터(주문 내역, 찜 목록, 프로필 등)를 한 화면에
모으는 성격이다. 지금은 그런 조합을 전담하는 module이 없어서 가장 가까운 BC(show)에 둔다.
여러 BC의 "내 것"을 모으는 module(가칭 mypage)이 생기면 `GetMyShowLikesUseCase`와
`MyShowLikesController`는 그쪽으로 옮길 첫 번째 후보다. 지금 그 module을 만들지 않는 이유는
이 화면 하나만으로는 새 BC를 둘 근거(여러 use case가 반복해서 같은 조합 패턴을 필요로 하는가)가
아직 없기 때문이다 — ADR 0005 §2·ADR 0008 "결정하지 않는 것"과 같은 판단 방식이다.

## 근거

- `docs/architecture.md`의 Repository vs Query Port 절: "행동시키기 위해 Aggregate를 가져오면
  Domain Repository를, 보여주기 위해 데이터를 가져오면 Application Query Port를 쓴다"는
  기준과 같은 방식으로, "이 use case가 실제로 필요로 하는 게 다른 BC의 예/아니오(존재)인지,
  다른 BC의 실제 데이터(표시값)인지"로 소유 module을 갈랐다.
- `member/package-info.java`: "찜하기/찜 해제/찜 상태 조회 시 회원 활성 확인은 like가 이
  module의 공개 계약을 직접 호출해 수행한다."

## 영향

- `show.application.usecase`에서 `AddShowLikeUseCase`/`RemoveShowLikeUseCase`/
  `GetShowLikeStatusUseCase`가 없어지고, `like.application.usecase`에
  `AddLikeUseCase`/`RemoveLikeUseCase`/`GetLikeStatusUseCase`가 생긴다.
- `like`의 `allowedDependencies`가 `{}`에서 `{"member"}`로 바뀐다(`ModularityTests.
  APPROVED_DEPENDENCY_DAG`도 함께 갱신).
- API 문서(`LikeControllerDocs`)에서 "404 공연 없음" 응답 설명을 뺀다 — 이제 존재하지 않는
  공연 id로 찜을 요청해도 성공한다.
