package com.ticket.booking.hold.domain;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * 주문 키·선점 키를 시간순 UUID(RFC 9562 버전 7)로 만든다. 문자열은 접두어 + 하이픈 없는 소문자 32글자로, 무작위 UUID를 쓰던 때와 형식·길이가 같다.
 *
 * <p><b>왜 시간순인가.</b> {@code orders}의 유일 인덱스 두 개({@code uk_orders_order_key}, {@code uk_orders_hold_key})에 무작위 키를 넣으면 새
 * 키가 인덱스의 아무 페이지에나 들어가 RDS가 페이지를 디스크에서 읽고 WAL에 페이지 전체를 쓴다. 시간순 키는 늘 인덱스 끝 근처에 들어가 메모리에 있는 페이지를 쓴다(로컬 2026-11-06:
 * 2,000건에 인덱스 디스크 읽기 1,933 → 7번). docs/plans/core-cpu-per-user.md 1. F-1.
 *
 * <p>상위 64비트는 주입한 {@link Clock}의 epoch 밀리초 48비트, 버전 {@code 0111}, 무작위 12비트다. 하위 64비트는 variant {@code 10}과 무작위 62비트다.
 * 무작위는 {@link java.util.UUID#randomUUID()}와 같은 {@link SecureRandom}에서 온다. 같은 밀리초 안의 순서는 맞추지 않는다 — 인덱스는 밀리초 단위로만 모여도
 * 충분하다. 대가로 키에서 생성 시각(밀리초)이 보이고 무작위 비트는 122 → 74비트다.
 */
@Component
public class TimeOrderedKeys {
    private static final HexFormat HEX = HexFormat.of();

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TimeOrderedKeys(final Clock clock) {
        this.clock = clock;
    }

    /** {@code prefix}에 시간순 UUID 32글자를 붙여 돌려준다. */
    public String next(final String prefix) {
        final long high = (clock.millis() << 16) | 0x7000L | (random.nextInt() & 0x0FFFL);
        final long low = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return prefix + HEX.toHexDigits(high) + HEX.toHexDigits(low);
    }
}
