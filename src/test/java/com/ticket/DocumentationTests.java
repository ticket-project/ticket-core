package com.ticket;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * module 구조 문서를 생성한다.
 *
 * <p>{@code spring-modulith-docs}는 {@code spring-modulith-starter-test}가 test classpath로 이미
 * 끌어오므로 {@code build.gradle}에 별도 의존을 추가하지 않는다.
 *
 * <p>{@link ApplicationModules#of(Class)}(predicate 없는 기본 overload)를 그대로 쓰면
 * {@code com.ticket.core}/{@code bootstrap}/{@code storage}/{@code support} legacy 코드가 서로 얽힌
 * 참조 때문에 module 구성 자체에서 막힌다 — {@code Documenter}는 생성자로 받은
 * {@link ApplicationModules} 인스턴스를 그대로 문서화할 뿐이므로, {@code com.ticket.ModularityTests}가
 * 쓰는 것과 같은 legacy 제외 predicate로 만든 {@link ApplicationModules}를 넘기면 legacy 코드는
 * 애초에 분석 대상에서 빠져 문제가 되지 않는다.
 *
 * <p>결과물은 {@code build/spring-modulith-docs} 아래에 생성되는 CI artifact다. {@code build/}는
 * {@code .gitignore} 대상이라 source로 commit되지 않는다 — 매 실행마다 코드로부터 다시 만든다.
 */
class DocumentationTests {

    private static final String OUTPUT_FOLDER = "build/spring-modulith-docs";

    /**
     * com.ticket.ModularityTests와 같은 legacy 제외 predicate다. 어긋나면 두 테스트가 따로 깨진다.
     * {@code bootstrap}이 "아직 이동하지 않은 legacy"가 아니라 영구 composition-root 예외인 이유는
     * {@code ModularityTests}의 클래스 javadoc 참고.
     */
    private static final DescribedPredicate<JavaClass> LEGACY_PACKAGES = DescribedPredicate.describe(
            "com.ticket.core, com.ticket.storage, com.ticket.support 아래의 아직 이동하지 않은 legacy "
                    + "코드, 그리고 com.ticket.bootstrap의 영구 composition-root 코드",
            DocumentationTests::isLegacy);

    @Test
    void 전체_dependency_diagram과_module_canvas_exposed_beans_events를_생성한다() throws java.io.IOException {
        final ApplicationModules modules = ApplicationModules.of(TicketApplication.class, LEGACY_PACKAGES);

        new Documenter(modules, Documenter.Options.defaults().withOutputFolder(OUTPUT_FOLDER))
                .writeDocumentation();

        final Path outputDir = Path.of(OUTPUT_FOLDER);
        assertThat(outputDir).as("문서 출력 폴더").isDirectory();

        // 전체 dependency diagram(모든 module을 한 번에 보여주는 PlantUML).
        assertThat(outputDir.resolve("components.puml")).exists();

        // module별 canvas(공개 API·의존·발행 이벤트를 표로 정리한 AsciiDoc)와 개별 diagram.
        // 파일명은 Documenter가 module identifier(소문자)로 만든다 — legacy를 뺀 5개 각각
        // (ticketing은 booking으로 흡수돼 더 이상 없다. payment는 ticket-domain-module-redesign
        // Phase 5에서 신설된 entity-only module이다. catalog는 BC 재편으로 show로 개명됐고, 찜은
        // show에서 다시 별도 module favorite로 분리됐다).
        for (final String moduleName : new String[] {
                "booking", "show", "favorite", "member", "payment"
        }) {
            assertThat(outputDir.resolve("module-" + moduleName + ".adoc"))
                    .as("%s module canvas", moduleName)
                    .exists();
            assertThat(outputDir.resolve("module-" + moduleName + ".puml"))
                    .as("%s module diagram", moduleName)
                    .exists();
        }

        assertThat(outputDir.resolve("all-docs.adoc")).as("종합 문서").exists();

        // canvas가 실제로 exposed bean과 이벤트를 담는지, 빈 표가 아닌지 내용까지 확인한다.
        // booking은 show/member의 공개 bean을 참조하고 자기 이벤트를 듣는다.
        final String bookingCanvas = Files.readString(outputDir.resolve("module-booking.adoc"));
        assertThat(bookingCanvas)
                .as("booking module canvas")
                .contains("Bean references")
                .contains("MemberLookup")
                .contains("Events listened to")
                .contains("OrderStarted");
    }

    private static boolean isLegacy(final JavaClass javaClass) {
        final String packageName = javaClass.getPackageName();
        return packageName.equals("com.ticket.core") || packageName.startsWith("com.ticket.core.")
                || packageName.equals("com.ticket.bootstrap") || packageName.startsWith("com.ticket.bootstrap.")
                || packageName.equals("com.ticket.storage") || packageName.startsWith("com.ticket.storage.")
                || packageName.equals("com.ticket.support") || packageName.startsWith("com.ticket.support.");
    }
}
