package com.ticket.error;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * module handler가 자기 module의 오류만 잡는지 강제한다.
 *
 * <p><b>왜 필요한가</b>: Spring은 {@code @ControllerAdvice} bean들을 order로 정렬한 뒤 매칭되는
 * 메서드를 가진 <i>첫</i> advice에서 멈춘다 — 그 안에서 더 구체적인 handler를 다른 advice가 갖고
 * 있어도 소용없다. module handler는 모두 {@code HIGHEST_PRECEDENCE}이므로, 그중 하나가
 * {@code RuntimeException}처럼 넓은 타입을 잡으면 <b>다른 module의 오류까지 삼켜</b> 엉뚱한 상태와
 * E-code로 응답한다. 컴파일과 기존 테스트는 전부 통과하고 운영에서만 드러나는 종류의 사고다.
 *
 * <p>{@code com.ticket.error.handler}의 전역 handler는 대상이 아니다 — 그쪽은 프레임워크 예외와
 * {@code Exception} fallback을 잡는 것이 역할이고, {@code LOWEST_PRECEDENCE}라 module handler를
 * 가리지 않는다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ExceptionHandlerScopeTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ticket");

    private static final String GLOBAL_HANDLER_PACKAGE = "com.ticket.error.handler";

    @Test
    void module_handler는_자기_module_package의_예외만_잡는다() {
        final List<String> violations = new ArrayList<>();
        int checkedHandlers = 0;

        for (final JavaClass advice : MAIN_CLASSES) {
            if (!advice.isMetaAnnotatedWith(RestControllerAdvice.class)
                    || advice.getPackageName().equals(GLOBAL_HANDLER_PACKAGE)) {
                continue;
            }
            checkedHandlers++;
            final String modulePackage = modulePackageOf(advice);

            for (final JavaMethod method : advice.getMethods()) {
                if (!method.isAnnotatedWith(ExceptionHandler.class)) {
                    continue;
                }
                for (final JavaClass parameter : method.getRawParameterTypes()) {
                    if (!parameter.getPackageName().startsWith(modulePackage)) {
                        violations.add("%s#%s가 %s를 잡는다".formatted(
                                advice.getSimpleName(), method.getName(), parameter.getName()));
                    }
                }
            }
        }

        assertThat(checkedHandlers)
                .as("module handler가 하나도 안 잡히면 이 테스트가 무의미하다")
                .isPositive();
        assertThat(violations)
                .as("module handler는 자기 module의 예외 package 안에 있는 타입만 잡아야 한다")
                .isEmpty();
    }

    /** {@code com.ticket.<module>.exception.handler} -> {@code com.ticket.<module>.exception} */
    private String modulePackageOf(final JavaClass advice) {
        final String packageName = advice.getPackageName();
        final int lastDot = packageName.lastIndexOf('.');

        return lastDot < 0 ? packageName : packageName.substring(0, lastDot);
    }
}
