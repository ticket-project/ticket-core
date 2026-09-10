package com.ticket.seed;

/**
 * 시드 프로그램의 출력 창구다.
 *
 * <p>로깅 프레임워크를 쓰지 않는다. 이 프로그램은 사람이 손으로 한 번 실행하고 결과를 읽는 CLI라
 * 표준 출력이 곧 결과 보고다. 또 logging backend를 하나 더 끌어오면 "DB 접속과 적재에 필요한
 * 의존성만 쓴다"는 제약이 흐려진다.
 *
 * <p><b>접속 비밀번호·토큰을 여기에 넘기지 않는다.</b> 접속 정보를 출력할 때는
 * {@link #maskedUrl(String)}로 자격증명 조각을 지운 뒤 넘긴다.
 */
final class SeedConsole {

    private SeedConsole() {
    }

    static void info(final String message) {
        System.out.println(message);
    }

    static void error(final String message) {
        System.err.println(message);
    }

    /**
     * JDBC URL에서 자격증명으로 보이는 조각을 지운다. H2 파일 URL은 비밀번호를 담지 않지만,
     * 설정 원본이 바뀌어 URL에 자격증명이 섞여도 로그로 새지 않게 한다.
     */
    static String maskedUrl(final String jdbcUrl) {
        if (jdbcUrl == null) {
            return "(없음)";
        }
        return jdbcUrl.replaceAll("(?i)((?:password|pwd|user|username)\\s*=)[^;&]*", "$1***");
    }
}
