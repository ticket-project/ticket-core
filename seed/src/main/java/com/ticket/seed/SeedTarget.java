package com.ticket.seed;

/**
 * 시드를 어느 DB에 적재하는가다. 접속 설정의 원본과 기본값이 여기서 갈린다.
 *
 * <ul>
 *   <li>{@link #LOCAL} — {@code src/main/resources/application-local.yml}의 {@code
 *       spring.datasource.*}를 읽는다. 부하 테스트 회원·회차를 기본으로 함께 만든다.
 *   <li>{@link #PROD} — {@code SPRING_DATASOURCE_URL} / {@code SPRING_DATASOURCE_USERNAME} / {@code
 *       SPRING_DATASOURCE_PASSWORD} 환경변수만 쓴다. <b>로컬 설정으로 대체하지 않는다.</b> 기본 적재 대상은 공연 데이터뿐이고, 테스트
 *       회원·부하 테스트 회차는 옵션을 명시할 때만 만든다.
 * </ul>
 */
enum SeedTarget {
    LOCAL("로컬"),
    PROD("운영");

    private final String label;

    SeedTarget(final String label) {
        this.label = label;
    }

    String label() {
        return label;
    }
}
