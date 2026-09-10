package com.ticket.seed;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code seed.*} 시스템 프로퍼티를 테스트 동안만 바꾼다.
 *
 * <p>{@link SeedSettings}는 실제 실행과 같은 경로(시스템 프로퍼티 → 로컬 프로파일 YAML)로 설정을
 * 읽는다. 테스트가 그 경로를 우회하면 실행 경로 자체를 검증하지 못하므로, 프로퍼티를 실제로
 * 세팅하고 끝나면 원래 값으로 되돌린다.
 */
final class SeedSystemProperties {

    private SeedSystemProperties() {
    }

    /** 세팅 전 값을 돌려준다(없었으면 {@code null}). {@link #restore(Map)}에 그대로 넘긴다. */
    static Map<String, String> set(final Map<String, String> properties) {
        final Map<String, String> previous = new HashMap<>();
        properties.forEach((key, value) -> {
            previous.put(key, System.getProperty(key));
            System.setProperty(key, value);
        });
        return previous;
    }

    static void restore(final Map<String, String> previous) {
        previous.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }
}
