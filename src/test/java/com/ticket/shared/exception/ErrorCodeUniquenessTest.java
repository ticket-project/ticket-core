package com.ticket.shared.exception;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-code의 전역 유일성을 지킨다.
 *
 * <p>오류 코드를 module별 {@code <Module>ErrorCode} enum으로 분산 소유하면 컴파일러가 중복을
 * 잡아주지 못한다 — 서로 다른 module이 같은 코드를 쓰면 클라이언트는 두 오류를 구분할 수 없다.
 * 코드 값은 외부 계약이므로({@code gatling-test}가 E4001·E6000 등을 하드코딩한다) 이 유일성이
 * 깨지는 것은 계약 파손이다.
 *
 * <p>{@code core.support.exception.ErrorCatalogContractTest}를 대체한다 — 그 테스트는 전역 enum
 * 하나를 검사했고, 지금은 검사 대상이 여러 module에 흩어져 있다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ErrorCodeUniquenessTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ticket");

    @Test
    void 모든_module의_E_code는_전역에서_유일하다() {
        final Map<String, List<String>> owners = new LinkedHashMap<>();

        for (final ErrorCode errorCode : allErrorCodes()) {
            owners.computeIfAbsent(errorCode.getCode(), key -> new ArrayList<>())
                    .add(errorCode.getClass().getName());
        }

        assertThat(owners)
                .as("두 곳 이상이 같은 E-code를 쓰면 클라이언트가 오류를 구분할 수 없다")
                .allSatisfy((code, declaringTypes) -> assertThat(declaringTypes)
                        .as("중복된 E-code %s", code)
                        .hasSize(1));
    }

    @Test
    void 모든_E_code는_code와_description을_갖는다() {
        final List<ErrorCode> errorCodes = allErrorCodes();

        assertThat(errorCodes)
                .as("ErrorCode 구현이 하나도 안 잡히면 이 테스트가 무의미하다")
                .isNotEmpty();

        assertThat(errorCodes).allSatisfy(errorCode -> {
            assertThat(errorCode.getCode()).isNotBlank();
            assertThat(errorCode.getDescription()).isNotBlank();
        });
    }

    private List<ErrorCode> allErrorCodes() {
        final List<ErrorCode> errorCodes = new ArrayList<>();

        for (final JavaClass candidate : MAIN_CLASSES) {
            if (!candidate.isEnum() || !candidate.isAssignableTo(ErrorCode.class)) {
                continue;
            }
            for (final Object constant : candidate.reflect().getEnumConstants()) {
                errorCodes.add((ErrorCode) constant);
            }
        }

        return errorCodes;
    }
}
