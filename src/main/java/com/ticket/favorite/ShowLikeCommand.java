package com.ticket.favorite;

/**
 * 찜을 만들고 없애는 공개 계약이다.
 *
 * <p>show 존재 확인과 회원 활성 확인은 호출자(show)의 책임이다 — 이 계약을 부르기 전에 이미
 * 끝났다는 전제다.
 */
public interface ShowLikeCommand {

    /**
     * 이미 찜한 상태면 다시 저장하지 않고 현재 상태만 돌려준다(멱등). 처음 찜하는 사이 동시
     * 요청이 먼저 저장을 끝냈다면(unique 제약 위반)
     * {@link com.ticket.favorite.exception.ShowLikeAlreadyExistsException}(409, E7001)을 던진다.
     */
    ShowLikeInfo like(long memberId, long showId);

    /**
     * 찜하지 않은 상태로 불러도 예외 없이 현재 상태(liked=false)를 돌려준다(멱등).
     */
    ShowLikeInfo unlike(long memberId, long showId);
}
