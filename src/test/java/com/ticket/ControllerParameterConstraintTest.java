package com.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jakarta.validation.Constraint;
import jakarta.validation.Valid;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * 요청 파라미터 제약은 문서 인터페이스 한곳에만 선언한다는 규칙을 고정한다.
 *
 * <p>Jakarta Bean Validation은 상위 타입 메서드의 파라미터 제약을 구현체가 다시 선언하면
 * ConstraintDeclarationException(HV000151)을 던진다. Controller가 문서 인터페이스를 구현하므로 같은 제약을 두 곳에 두면 method
 * validation 자체가 깨진다.
 *
 * <p>문서 인터페이스는 {@code endpoint} package를 가진 module이면 모두 {@code ..endpoint.docs}에 있다({@code
 * like}/{@code member}/{@code show}와 {@code booking}의 각 capability). {@code security}만 예외로
 * controller 옆 기능 폴더({@code security.auth})에 둔다 — 이 module은 계층 package를 아예 쓰지 않는다(ADR 0013). 그래서
 * {@code src/main/java/com/ticket} 전체를 훑되, <b>디렉터리 이름이 아니라 타입 자체로 찾는다</b> — controller는
 * {@code @RestController} 애노테이션으로, 문서 인터페이스는 {@code *ControllerDocs} 이름으로 찾는다. 디렉터리 이름으로 찾으면
 * package 배치가 바뀔 때 검사 대상이 조용히 줄어도 테스트가 통과해 버린다 (실제로 {@code web} -> {@code endpoint} 개명에서 그럴 뻔했고,
 * {@code docs} 디렉터리를 쓰지 않는 module의 문서 인터페이스는 한동안 검사 밖에 있었다).
 *
 * <p>상대 경로로 소스 디렉터리를 읽으므로 Gradle이 정해 주는 작업 디렉터리에서만 통과한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ControllerParameterConstraintTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/ticket");

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

        assertThat(controllerClasses())
                .as("검사 대상 controller가 0개면 이 테스트는 아무것도 보장하지 않는다")
                .isNotEmpty();
        assertThat(violations).as("제약과 @Valid는 controller.docs 인터페이스에만 선언한다").isEmpty();
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
        return allClassesUnder(SOURCE_ROOT).stream()
                .filter(type -> type.isAnnotationPresent(RestController.class))
                .toList();
    }

    /** 문서 인터페이스는 애노테이션으로 구분되지 않는다 — 배치가 module마다 달라 디렉터리 대신 이름으로 찾는다. */
    private List<Class<?>> docsInterfaces() throws IOException {
        return allClassesUnder(SOURCE_ROOT).stream()
                .filter(type -> type.isInterface())
                .filter(type -> type.getSimpleName().endsWith("ControllerDocs"))
                .toList();
    }

    /** {@code SOURCE_ROOT} 아래 모든 {@code .java}를 class로 읽는다. package 이름에 기대지 않는다. */
    private List<Class<?>> allClassesUnder(final Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException(root + "가 있어야 한다");
        }
        final List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> allDirs = Files.walk(root)) {
            for (final Path directory : allDirs.filter(Files::isDirectory).toList()) {
                classes.addAll(classesDirectlyIn(directory));
            }
        }
        return classes;
    }

    private List<Class<?>> classesDirectlyIn(final Path directory) throws IOException {
        final String packageName =
                Path.of("src/main/java")
                        .relativize(directory)
                        .toString()
                        .replace('\\', '.')
                        .replace('/', '.');
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().replace(".java", ""))
                    // package-info는 class 이름으로 로드할 수 없고, 애노테이션이 없으면 class 파일 자체가 없다.
                    .filter(name -> !"package-info".equals(name))
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
