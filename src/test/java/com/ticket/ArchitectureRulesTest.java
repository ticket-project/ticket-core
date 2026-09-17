package com.ticket;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

/**
 * 모든 module에 똑같이 적용되는 구조 관례를 한곳에서 강제한다.
 *
 * <p><b>여기 두는 것과 두지 않는 것.</b> 여기 있는 규칙은 "어느 module에서든 같은 뜻인 것"뿐이다 — 계층 방향, 공개면 순수성, module 경계. 특정
 * BC의 사정을 아는 규칙은 그 BC의 테스트에 남는다({@code com.ticket.booking.BookingLayerDependencyTest}의 락 계약 규칙,
 * {@code com.ticket.shared.SharedModulePurityTest}의 shared 내부 배치 규칙). 같은 검사를 두 곳에 두지 않는다.
 *
 * <p><b>BC 사이 domain 격리는 여기 없다.</b> {@code com.ticket.DomainIsolationTest}가 이미 6개 BC 전부를 팩토리 하나로 덮고
 * 있다. 옮겨 오면 규칙이 아니라 파일만 움직이고, AGENTS.md와 {@code /verify} 스킬이 가리키는 이름만 깨진다.
 *
 * <p><b>package cycle 규칙도 여기 없다.</b> Spring Modulith {@code verify()}가 module 사이 순환을 이미 거부하고, 그 검증은
 * {@code com.ticket.ModularityTests.verifiesModuleStructure()}가 돌린다. ArchUnit {@code
 * slices().beFreeOfCycles()}를 더해도 같은 사실을 두 번 확인할 뿐이라 두지 않는다. Modulith가 보지 못하는 순환(module 안의 package
 * 사이)이 실제로 문제가 된 적이 아직 없다 — 생기면 그때 근거와 함께 추가한다.
 *
 * <p><b>{@code security}는 계층 규칙 대상이 아니다.</b> 이 module만 계층이 아니라 기능으로 나뉜다({@code auth}/{@code
 * jwt}/{@code oauth}/{@code token}/{@code http}). {@code domain}/{@code application} package가 아예 없어
 * 계층 방향 규칙이 말할 것이 없다. 근거는 {@code
 * docs/adr/0013-layer-first-package-layout-and-security-owns-authentication.md}다.
 *
 * <p><b>Querydsl Q-type 주의.</b> Q-type은 {@code build/generated/sources/annotationProcessor} 아래에
 * 생성되지만 package는 원본 entity와 같아서({@code com.ticket.booking.order.domain.QOrder}) 여기 분석 대상에 그대로 들어온다.
 * 지금 규칙들은 "무엇을 참조하면 안 되는가" 형태라 Q-type이 걸리지 않는다. 이름 관례나 "public 필드 금지" 같은 규칙을 뒤에 추가한다면 Q-type을 먼저
 * 제외해야 한다.
 */
@AnalyzeClasses(
        packages = "com.ticket",
        importOptions = {ImportOption.DoNotIncludeTests.class})
@SuppressWarnings("NonAsciiCharacters")
class ArchitectureRulesTest {

    /** 계층으로 나뉘는 업무 module이다. {@code security}는 기능으로 나뉘어 대상이 아니다(클래스 javadoc 참고). */
    private static final List<String> LAYERED_MODULES =
            List.of("booking", "show", "venue", "like", "member", "payment");

    /** 기술 module이다. 업무 module을 역참조하면 안 된다. */
    private static final String SHARED = "shared";

    private static final Path MAIN_SOURCE_ROOT = Path.of("src", "main", "java", "com", "ticket");

    // ---------------------------------------------------------------- 계층 방향

    @ArchTest
    static final ArchRule domain은_application_infrastructure_endpoint를_모른다 =
            forEachModuleWith(
                    "domain",
                    module ->
                            noClasses()
                                    .that()
                                    .resideInAPackage(pkg(module, "domain"))
                                    .should()
                                    .dependOnClassesThat()
                                    .resideInAnyPackage(
                                            pkg(module, "application"),
                                            pkg(module, "infrastructure"),
                                            pkg(module, "endpoint"))
                                    .because(module + ".domain은 업무 규칙과 상태만 안다 — 조립·저장·HTTP를 모른다"));

    @ArchTest
    static final ArchRule application은_infrastructure와_endpoint를_모른다 =
            forEachModuleWith(
                    "application",
                    module ->
                            noClasses()
                                    .that()
                                    .resideInAPackage(pkg(module, "application"))
                                    .should()
                                    .dependOnClassesThat()
                                    .resideInAnyPackage(
                                            pkg(module, "infrastructure"), pkg(module, "endpoint"))
                                    .because(
                                            module
                                                    + ".application은 포트로만 밖을 부른다 — 구현 선택은"
                                                    + " infrastructure가 갖는다"));

    @ArchTest
    static final ArchRule endpoint는_infrastructure를_모른다 =
            forEachModuleWith(
                    "endpoint",
                    module ->
                            noClasses()
                                    .that()
                                    .resideInAPackage(pkg(module, "endpoint"))
                                    .should()
                                    .dependOnClassesThat()
                                    .resideInAPackage(pkg(module, "infrastructure"))
                                    .because(
                                            module
                                                    + ".endpoint는 use case를 부른다 — 저장 기술을 직접 고르지"
                                                    + " 않는다"));

    @ArchTest
    static final ArchRule endpoint는_Repository를_직접_부르지_않는다 =
            noClasses()
                    .that()
                    .resideInAPackage("..endpoint..")
                    .should()
                    .dependOnClassesThat()
                    .haveNameMatching(".*(Repository|RepositoryAdapter|QueryPort)")
                    .orShould()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.data.jpa..", "com.querydsl..")
                    .because(
                            "HTTP endpoint는 application use case를 부른다 — 조회 계약을 건너뛰면 트랜잭션 경계와"
                                    + " 권한 판정이 함께 빠진다");

    // ---------------------------------------------------------------- module 경계

    @ArchTest
    static final ArchRule 다른_module의_구현을_참조하지_않는다 =
            forEachModule(
                    module ->
                            noClasses()
                                    .that()
                                    .resideOutsideOfPackage("com.ticket." + module + "..")
                                    .should()
                                    .dependOnClassesThat()
                                    .resideInAnyPackage(
                                            pkg(module, "domain"),
                                            pkg(module, "application"),
                                            pkg(module, "infrastructure"),
                                            pkg(module, "endpoint"),
                                            "com.ticket." + module + ".exception.handler..")
                                    .because(
                                            module
                                                    + "의 구현은 밖에서 보이지 않는다 — cross-module 호출은 "
                                                    + module
                                                    + ".api(와 의도적으로 공개한 named interface)로만 한다"));

    @ArchTest
    static final ArchRule shared는_업무_module을_모른다 =
            noClasses()
                    .that()
                    .resideInAPackage("com.ticket." + SHARED + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            LAYERED_MODULES.stream()
                                    .map(module -> "com.ticket." + module + "..")
                                    .toArray(String[]::new))
                    .because("shared는 아무 업무 module도 참조하지 않는 leaf다 — 역참조가 생기면 모든 module이 서로 묶인다");

    // ---------------------------------------------------------------- 공개면 순수성

    @ArchTest
    static final ArchRule api에는_구현_bean을_두지_않는다 =
            noClasses()
                    .that()
                    .resideInAPackage("..api..")
                    .should()
                    .beAnnotatedWith(Service.class)
                    .orShould()
                    .beAnnotatedWith(Component.class)
                    .orShould()
                    .beAnnotatedWith(Repository.class)
                    .orShould()
                    .beAnnotatedWith(Configuration.class)
                    .orShould()
                    .beAnnotatedWith(Controller.class)
                    .orShould()
                    .beAnnotatedWith(RestController.class)
                    .orShould()
                    .beAnnotatedWith(Entity.class)
                    .because("api는 다른 module이 읽는 계약면이다 — 구현 bean이 여기 있으면 계약과 구현의 경계가 사라진다");

    @ArchTest
    static final ArchRule api는_구현_기술을_모른다 =
            noClasses()
                    .that()
                    .resideInAPackage("..api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "jakarta.persistence..",
                            "org.springframework.data..",
                            "com.querydsl..",
                            "org.redisson..",
                            "org.springframework.web..",
                            "..infrastructure..",
                            "..endpoint..")
                    .because(
                            "api는 interface·record·enum 같은 계약만 갖는다 — 저장 기술이나 HTTP 타입이 새면 호출하는"
                                    + " module이 그 선택에 묶인다");

    // ---------------------------------------------------------------- 검사 대상이 비지 않았는지

    @Test
    void 계층_규칙의_검사_대상이_실제로_존재한다() {
        for (final String module : LAYERED_MODULES) {
            assertThat(MAIN_SOURCE_ROOT.resolve(module)).as("%s module 디렉터리", module).isDirectory();
        }
        // 규칙이 가리키는 계층이 하나도 없으면 위 ArchRule들은 빈 집합을 검사하며 조용히 통과한다.
        assertThat(MAIN_SOURCE_ROOT.resolve("booking").resolve("endpoint"))
                .as("endpoint 계층이 실제로 있어야 endpoint 규칙이 의미를 갖는다")
                .isDirectory();
        assertThat(MAIN_SOURCE_ROOT.resolve("member").resolve("api"))
                .as("api 공개면이 실제로 있어야 api 규칙이 의미를 갖는다")
                .isDirectory();
    }

    // ---------------------------------------------------------------- 공개면 스냅샷

    /**
     * 의도적으로 공개한 named interface 목록이다.
     *
     * <p>{@code allowedDependencies}는 "무엇을 열어도 되는가"의 상한이고 이 목록은 "지금 실제로 무엇이 열려 있는가"다. 새
     * {@code @NamedInterface}가 PR에서 조용히 추가되면 여기서 실패한다 — 공개면이 늘어나는 것은 의식적인 결정이어야 한다.
     */
    private static final Set<String> EXPOSED_NAMED_INTERFACES =
            Set.of(
                    "like :: api",
                    "member :: api",
                    "member :: exception",
                    "security :: api",
                    "shared :: api",
                    "shared :: exception",
                    "shared :: web",
                    "show :: api",
                    "venue :: api");

    @Test
    void 공개된_named_interface는_승인된_목록과_일치한다() {
        final Set<String> exposed =
                ApplicationModules.of(TicketApplication.class).stream()
                        .flatMap(
                                module ->
                                        module.getNamedInterfaces().stream()
                                                .filter(named -> !named.isUnnamed())
                                                .map(
                                                        named ->
                                                                module.getIdentifier()
                                                                        + " :: "
                                                                        + named.getName()))
                        .collect(Collectors.toSet());

        assertThat(exposed)
                .as("공개면이 늘거나 줄면 이 목록도 함께 바꾼다 — 왜 바꾸는지는 PR이 설명한다")
                .containsExactlyInAnyOrderElementsOf(EXPOSED_NAMED_INTERFACES);
    }

    // ---------------------------------------------------------------- null 정책

    /**
     * 모든 first-party production package가 null 정책을 명시하는지 확인한다.
     *
     * <p>{@code @NullMarked}는 하위 package로 전파되지 않는다. package 하나를 새로 만들면서 {@code package-info.java}를
     * 빼먹으면 그 package만 조용히 검사 대상에서 빠지고, NullAway는 아무 말도 하지 않는다 — 검사되지 않는 것과 통과하는 것이 똑같아 보인다. 그래서 소스
     * 트리를 직접 읽어 확인한다.
     *
     * <p>파싱하지 않고 문자열로 확인한다. 정확한 파서를 직접 만드는 쪽이 이 검사가 잡으려는 실수보다 깨지기 쉽다.
     */
    @Test
    void 모든_production_package가_null_정책을_선언한다() {
        final List<String> missing = new ArrayList<>();

        try (Stream<Path> directories = Files.walk(MAIN_SOURCE_ROOT)) {
            for (final Path directory : directories.filter(Files::isDirectory).toList()) {
                if (!hasJavaSource(directory)) {
                    continue;
                }
                final Path packageInfo = directory.resolve("package-info.java");
                if (!Files.exists(packageInfo)) {
                    missing.add(directory + " (package-info.java 없음)");
                    continue;
                }
                final String source = Files.readString(packageInfo, StandardCharsets.UTF_8);
                if (!source.contains("@NullMarked")) {
                    missing.add(directory + " (@NullMarked 없음)");
                }
            }
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }

        assertThat(missing)
                .as("production package는 package-info.java에 @NullMarked를 선언한다")
                .isEmpty();
    }

    private static boolean hasJavaSource(final Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(
                    file ->
                            file.getFileName().toString().endsWith(".java")
                                    && !file.getFileName().toString().equals("package-info.java"));
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // ---------------------------------------------------------------- helper

    private static String pkg(final String module, final String layer) {
        return "com.ticket." + module + "." + layer + "..";
    }

    /**
     * 해당 계층을 <b>실제로 가진</b> module에 대해서만 규칙을 만든다. 계층이 없는 module까지 규칙을 만들면 ArchUnit이 "검사한 클래스가 0개"라며
     * 실패한다 — payment에는 {@code application}이 없고 venue·payment에는 {@code endpoint}가 없다. 없는 계층을 미리 만들지
     * 않는 것은 배치 규칙 그대로다(docs/architecture.md).
     */
    private static ArchRule forEachModuleWith(
            final String layer, final Function<String, ArchRule> factory) {
        return combine(modulesWith(layer), factory);
    }

    private static List<String> modulesWith(final String layer) {
        return LAYERED_MODULES.stream()
                .filter(
                        module ->
                                Files.isDirectory(MAIN_SOURCE_ROOT.resolve(module).resolve(layer)))
                .toList();
    }

    private static ArchRule forEachModule(final Function<String, ArchRule> factory) {
        final Set<String> all = new LinkedHashSet<>(LAYERED_MODULES);
        all.add("security");
        return combine(List.copyOf(all), factory);
    }

    /**
     * module마다 같은 모양의 규칙을 만들어 하나로 합친다. 규칙을 module 수만큼 손으로 복사하면 module이 늘 때 조용히 빠진다 — 목록 상수 하나만 고치면
     * 되게 한다.
     */
    private static ArchRule combine(
            final List<String> modules, final Function<String, ArchRule> factory) {
        CompositeArchRule composite = null;
        for (final String module : modules) {
            final ArchRule rule = factory.apply(module);
            composite = composite == null ? CompositeArchRule.of(rule) : composite.and(rule);
        }
        if (composite == null) {
            throw new IllegalStateException("검사할 module이 없다");
        }
        return composite;
    }
}
