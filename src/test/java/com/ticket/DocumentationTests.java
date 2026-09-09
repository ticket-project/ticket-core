package com.ticket;

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
 * <p>{@link ApplicationModules}를 만드는 방식이 {@code com.ticket.ModularityTests}와 같아야 한다 —
 * 문서화 대상도 구조 검증 대상과 같아야 하기 때문이다. legacy package 제외 predicate는 두지 않는다
 * ({@code com.ticket.ModularityTests}의 클래스 javadoc 참고 — {@code core}/{@code bootstrap}/
 * {@code storage}/{@code support}는 {@code src/main/java}에 더 이상 존재하지 않는다).
 *
 * <p>결과물은 {@code build/spring-modulith-docs} 아래에 생성되는 CI artifact다. {@code build/}는
 * {@code .gitignore} 대상이라 source로 commit되지 않는다 — 매 실행마다 코드로부터 다시 만든다.
 */
class DocumentationTests {

    private static final String OUTPUT_FOLDER = "build/spring-modulith-docs";

    @Test
    void 전체_dependency_diagram과_module_canvas_exposed_beans_events를_생성한다() throws java.io.IOException {
        final ApplicationModules modules = ApplicationModules.of(TicketApplication.class);

        new Documenter(modules, Documenter.Options.defaults().withOutputFolder(OUTPUT_FOLDER))
                .writeDocumentation();

        final Path outputDir = Path.of(OUTPUT_FOLDER);
        assertThat(outputDir).as("문서 출력 폴더").isDirectory();

        // 전체 dependency diagram(모든 module을 한 번에 보여주는 PlantUML).
        assertThat(outputDir.resolve("components.puml")).exists();

        // module별 canvas(공개 API·의존·발행 이벤트를 표로 정리한 AsciiDoc)와 개별 diagram.
        // 파일명은 Documenter가 module identifier(소문자)로 만든다 — 대표로 아래 6개만 확인한다.
        for (final String moduleName : new String[] {
                "booking", "show", "venue", "like", "member", "payment"
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
}
