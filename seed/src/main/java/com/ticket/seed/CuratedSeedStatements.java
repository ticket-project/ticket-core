package com.ticket.seed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code seed/sql/kopis-curated.sql}을 실행할 SQL 문 목록으로 나눈다. DB를 모르는 순수 변환 계층이라 파일만 있으면 단위 테스트할 수 있다.
 *
 * <p><b>문장 순서는 파일 그대로 유지한다.</b> 파일 끝의 {@code SEATS} 공연장별 복제와 {@code GRADES} / {@code PERFORMANCE_GRADES} /
 * {@code PERFORMANCE_SEATS}는 {@code INSERT ... SELECT}라 실행 시점에 존재하는 행만 대상으로 삼는다. 그 뒤에 선언된 공연장·회차는 좌석을 하나도 받지 못하므로
 * {@link #verifyStatementOrder()}가 파싱 직후에 막는다.
 */
final class CuratedSeedStatements {

    /** 리터럴 한 행 INSERT를 세는 패턴. {@code INSERT ... SELECT}는 걸러진다. */
    private static final Pattern LITERAL_INSERT_PATTERN =
            Pattern.compile("^INSERT INTO ([A-Z_]+) \\([^)]*\\) VALUES \\(", Pattern.DOTALL);

    private final List<String> statements;

    private CuratedSeedStatements(final List<String> statements) {
        this.statements = List.copyOf(statements);
    }

    static CuratedSeedStatements from(final Path sqlPath) {
        final CuratedSeedStatements parsed = new CuratedSeedStatements(parse(readLines(sqlPath)));
        parsed.verifyStatementOrder();
        return parsed;
    }

    /** 실제로 DB에 던지는 문장 목록이다. */
    List<String> statements() {
        return statements;
    }

    /**
     * 첫 집합 기반 {@code INSERT ... SELECT}보다 뒤에 선언된 리터럴 INSERT가 없는지 확인한다.
     *
     * <p>파일 끝에 새 수집분을 이어 붙이면 이 순서가 조용히 깨진다. 실제로 그렇게 추가된 공연장 179개가 좌석을 하나도 받지 못했고, 그 공연장의 공연 198개·회차 662개에 회차좌석이 생성되지
     * 않았다. 적재가 끝난 뒤 개수로 알아채는 것보다 여기서 먼저 막는 것이 낫다.
     */
    void verifyStatementOrder() {
        int firstSetInsert = -1;
        for (int index = 0; index < statements.size(); index++) {
            final String statement = statements.get(index);
            final boolean literal = LITERAL_INSERT_PATTERN.matcher(statement).find();
            if (!literal && statement.startsWith("INSERT INTO") && firstSetInsert < 0) {
                firstSetInsert = index;
            }
            if (literal && firstSetInsert >= 0) {
                throw new SeedFailure(
                        """
                        시드 SQL의 문장 순서가 어긋났습니다. 집합 기반 INSERT ... SELECT 뒤에 선언된 리터럴 INSERT가 있습니다.
                          문장: %s
                          -> 그 행은 좌석 복제·회차좌석 생성 대상에서 빠집니다. 해당 INSERT를 '-- @seed-splice' 마커 앞으로 옮기세요.
                        """.formatted(statement.length() > 120 ? statement.substring(0, 120) + "..." : statement));
            }
        }
        if (firstSetInsert < 0) {
            throw new SeedFailure("시드 SQL에서 집합 기반 INSERT ... SELECT를 찾지 못했습니다.");
        }
    }

    /** 리터럴 한 행 INSERT 개수다. 적재 완전성 판정의 기대값 원본이다. */
    long literalInsertCount(final String table) {
        return statements.stream()
                .filter(statement -> isLiteralInsertInto(statement, table))
                .count();
    }

    private static boolean isLiteralInsertInto(final String statement, final String table) {
        final Matcher matcher = LITERAL_INSERT_PATTERN.matcher(statement);
        return matcher.find() && matcher.group(1).equals(table);
    }

    private static List<String> readLines(final Path sqlPath) {
        try {
            return Files.readAllLines(sqlPath, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new SeedFailure("시드 SQL 파일을 읽을 수 없습니다: " + sqlPath, exception);
        }
    }

    private static List<String> parse(final List<String> lines) {
        final List<String> parsed = new ArrayList<>();
        final StringBuilder current = new StringBuilder();

        for (final String line : lines) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }

            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                parsed.add(current.toString().trim().replaceAll(";\\s*$", ""));
                current.setLength(0);
            }
        }

        if (!current.isEmpty()) {
            parsed.add(current.toString().trim());
        }

        return parsed;
    }
}
