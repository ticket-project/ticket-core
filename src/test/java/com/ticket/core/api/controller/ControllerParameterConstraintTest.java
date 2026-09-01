package com.ticket.core.api.controller;

import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요청 파라미터 제약은 문서 인터페이스 한곳에만 선언한다는 규칙을 고정한다.
 *
 * <p>Jakarta Bean Validation은 상위 타입 메서드의 파라미터 제약을 구현체가 다시 선언하면
 * ConstraintDeclarationException(HV000151)을 던진다. Controller가 문서 인터페이스를 구현하므로
 * 같은 제약을 두 곳에 두면 method validation 자체가 깨진다.
 *
 * <p>상대 경로로 소스 디렉터리를 읽으므로 Gradle이 정해 주는 작업 디렉터리에서만 통과한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ControllerParameterConstraintTest {

    @Test
    void controller_구현체는_파라미터_제약을_다시_선언하지_않는다() throws IOException {
        final List<String> violations = new ArrayList<>();

        for (final Class<?> controller : controllerClasses()) {
            for (final Method method : controller.getDeclaredMethods()) {
                if (!overridesInterfaceMethod(controller, method)) {
                    continue;
                }
                for (final Parameter parameter : method.getParameters()) {
                    if (hasConstraint(parameter)) {
                        violations.add(controller.getSimpleName() + "#" + method.getName());
                    }
                }
            }
        }

        assertThat(violations)
                .as("제약과 @Valid는 controller.docs 인터페이스에만 선언한다")
                .isEmpty();
    }

    @Test
    void 문서_인터페이스가_실제로_제약을_선언한다() throws IOException {
        final List<String> constrained = new ArrayList<>();

        for (final Class<?> docs : docsInterfaces()) {
            for (final Method method : docs.getDeclaredMethods()) {
                for (final Parameter parameter : method.getParameters()) {
                    if (hasConstraint(parameter)) {
                        constrained.add(docs.getSimpleName() + "#" + method.getName());
                    }
                }
            }
        }

        assertThat(constrained).isNotEmpty();
    }

    private boolean overridesInterfaceMethod(final Class<?> controller, final Method method) {
        for (final Class<?> candidate : controller.getInterfaces()) {
            try {
                candidate.getMethod(method.getName(), method.getParameterTypes());
                return true;
            } catch (final NoSuchMethodException ignored) {
                // 다음 인터페이스를 본다.
            }
        }
        return false;
    }

    private boolean hasConstraint(final Parameter parameter) {
        for (final Annotation annotation : parameter.getAnnotations()) {
            if (annotation.annotationType() == Valid.class) {
                return true;
            }
            if (annotation.annotationType().isAnnotationPresent(Constraint.class)) {
                return true;
            }
        }
        return false;
    }

    private List<Class<?>> controllerClasses() throws IOException {
        return classesIn(
                "src/main/java/com/ticket/core/api/controller",
                "com.ticket.core.api.controller"
        ).stream()
                .filter(type -> type.isAnnotationPresent(RestController.class))
                .toList();
    }

    private List<Class<?>> docsInterfaces() throws IOException {
        return classesIn(
                "src/main/java/com/ticket/core/api/controller/docs",
                "com.ticket.core.api.controller.docs"
        );
    }

    private List<Class<?>> classesIn(final String relativeDir, final String packageName) throws IOException {
        final Path directory = Path.of(relativeDir);
        assertThat(Files.isDirectory(directory)).as(relativeDir + "가 있어야 한다").isTrue();
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().replace(".java", ""))
                    .<Class<?>>map(name -> loadClass(packageName + "." + name))
                    .toList();
        }
    }

    private Class<?> loadClass(final String name) {
        try {
            return Class.forName(name);
        } catch (final ClassNotFoundException exception) {
            throw new IllegalStateException("클래스를 찾을 수 없습니다: " + name, exception);
        }
    }
}
