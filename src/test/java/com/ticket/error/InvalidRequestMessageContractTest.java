package com.ticket.error;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 400 응답 {@code error.data}에 실리는 필수 입력 위반 문구를 고정한다.
 *
 * <p>이 판정은 예전에 {@code com.ticket.shared.RequiredInput} 한 곳이 소유했다. 그 클래스가
 * {@code shared -> error} 순환을 만들어(봉투가 shared에 있고 handler가 error에 있다) 지워지고
 * 판정이 각 {@code UseCase.Input}의 compact constructor로 인라인됐다. 소유자가 없어졌으므로
 * 문구가 파일마다 갈라지는 것을 막을 것이 필요하다 — 그것이 이 테스트다.
 *
 * <p>대표 몇 개를 호출해 보는 방식으로는 나머지 호출부의 표류를 잡지 못하므로, {@code src/main}
 * 전체에서 문자열 리터럴을 인자로 받는 {@code new InvalidRequestException("...")}를 모아 검사한다.
 * {@code InvalidRequestException}은 필수 입력 위반 외에도 쓰이므로(예: "orderSeats는 빈 order를
 * 만들어야 합니다.") 전부를 승인 목록에 묶을 수는 없다. 그래서 <b>필수 입력 계열로 보이는 문구</b>
 * (필수·양수·1 이상 중 하나를 담은 것)만 골라 승인된 네 형태와 정확히 일치하는지 본다.
 *
 * <p><b>이 테스트가 잡지 못하는 것</b>: 같은 규칙을 완전히 다른 어휘로 쓴 경우
 * (예: "memberId가 필요합니다.")는 계열 필터에 걸리지 않아 통과한다. 다섯 규칙의 문구를 바꿀 때는
 * 이 파일의 표를 먼저 고치는 것이 원본이다.
 */
@SuppressWarnings("NonAsciiCharacters")
class InvalidRequestMessageContractTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** 리터럴 인자만 걸린다 — handler가 넘기는 필드 오류 문자열 같은 변수 인자는 대상이 아니다. */
    private static final Pattern LITERAL_THROW =
            Pattern.compile("new InvalidRequestException\\((\"[^\"]*\"(?: \\+ [^)]+)?)\\)");

    /** 필수 입력 계열로 보이는 문구만 검사 대상으로 삼는다. */
    private static final Pattern REQUIRED_INPUT_FAMILY = Pattern.compile("필수|양수|1 이상");

    private static final List<Pattern> APPROVED = List.of(
            Pattern.compile("^\"[^\"]+는 필수입니다\\.\"$"),
            Pattern.compile("^\"[^\"]+는 양수여야 합니다\\.\"$"),
            Pattern.compile("^\"[^\"]+는 1 이상이어야 합니다\\.\"$"),
            Pattern.compile("^\"[^\"]+는 1 이상 \" \\+ .+ \\+ \" 이하여야 합니다\\.\"$"));

    @Test
    void 필수_입력_위반_문구는_승인된_네_형태만_쓴다() {
        final List<String> candidates = collectLiteralArguments().stream()
                .filter(argument -> REQUIRED_INPUT_FAMILY.matcher(argument).find())
                .toList();

        assertThat(candidates)
                .as("필수 입력 계열 문구가 하나도 안 걸리면 정규식이나 계열 필터가 낡은 것이다")
                .isNotEmpty();

        assertThat(candidates)
                .allSatisfy(argument -> assertThat(APPROVED)
                        .as("승인되지 않은 필수 입력 문구: %s", argument)
                        .anyMatch(approved -> approved.matcher(argument).matches()));
    }

    private List<String> collectLiteralArguments() {
        final List<String> arguments = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(MAIN)) {
            sources.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> {
                        final Matcher matcher = LITERAL_THROW.matcher(read(path));
                        while (matcher.find()) {
                            arguments.add(matcher.group(1));
                        }
                    });
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return arguments;
    }

    private String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
