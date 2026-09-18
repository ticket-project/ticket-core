package com.ticket;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
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
import java.util.TreeSet;
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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

/**
 * 모든 module에 똑같이 적용되는 구조 관례를 한곳에서 강제한다.
 *
 * <p><b>규칙은 package 위치가 아니라 역할 이름을 본다.</b> 배치가 module마다 다르기 때문이다 — 작은 module은 {@code
 * member.usecase}처럼 역할을 module 바로 아래에서 드러내고, 큰 booking은 {@code booking.order.usecase}처럼 업무를 먼저
 * 드러낸다. 그래서 {@code com.ticket.<module>.<layer>..}라는 고정 경로 대신 {@code ..usecase..}/{@code
 * ..persistence..} 같은 역할 패턴으로 검사한다. 새 capability가 생겨도 규칙 목록을 고칠 일이 없고, 어느 깊이에 두든 같은 방향 규칙이 걸린다.
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
 * slices().beFreeOfCycles()}를 더해도 같은 사실을 두 번 확인할 뿐이라 두지 않는다.
 *
 * <p><b>역할 package가 없는 곳은 규칙 대상이 아니다.</b> {@code security}는 기능으로 나뉘고({@code auth}/{@code
 * jwt}/{@code oauth}/{@code token}/{@code http}), {@code booking.admission}과 {@code
 * member.password}도 파일이 적어 flat이다. 역할 이름이 없으면 방향 규칙이 말할 것도 없다 — 근거는 {@code
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

    /** 업무 module이다. {@code security}·{@code shared}는 기술 module이라 따로 다룬다. */
    private static final List<String> BUSINESS_MODULES =
            List.of("booking", "show", "venue", "like", "member", "payment");

    /** 기술 module이다. 업무 module을 역참조하면 안 된다. */
    private static final String SHARED = "shared";

    private static final Path MAIN_SOURCE_ROOT = Path.of("src", "main", "java", "com", "ticket");

    /** 규칙 회귀 검증용 fixture다. 운영 규칙 평가({@code @AnalyzeClasses})는 test class를 빼므로 여기에만 들어온다. */
    private static final JavaClasses ARCH_FIXTURES =
            new ClassFileImporter().importPackages("com.ticket.archfixture");

    // ---------------------------------------------------------------- 공개면 스냅샷

    /**
     * 의도적으로 공개한 named interface 목록이다.
     *
     * <p>{@code allowedDependencies}는 "무엇을 열어도 되는가"의 상한이고 이 목록은 "지금 실제로 무엇이 열려 있는가"다. 새
     * {@code @NamedInterface}가 PR에서 조용히 추가되면 여기서 실패한다 — 공개면이 늘어나는 것은 의식적인 결정이어야 한다.
     *
     * <p>{@link #다른_module의_공개면_밖을_참조하지_않는다}도 이 목록을 그대로 읽는다 — 공개면의 정의가 두 벌이 되지 않게 한다.
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

    // ---------------------------------------------------------------- 역할 이름

    // 역할 이름은 com.ticket 안에서만 뜻이 있다. 접두사를 빼면 jakarta.persistence처럼 같은 segment를 가진 외부
    // package까지 걸려 "domain이 persistence를 참조한다"는 거짓 위반이 나온다.

    /** 업무 규칙과 상태. */
    private static final String DOMAIN = "com.ticket..domain..";

    /** 요청 단위 조립과 트랜잭션 경계. */
    private static final String USECASE = "com.ticket..usecase..";

    /** 커밋 이후 후속 처리 조율. use case와 같은 쪽(안)이다. */
    private static final String EVENT = "com.ticket..event..";

    /** 자기 module의 local DB 조회 구현과 그 읽기 모델. */
    private static final String QUERY = "com.ticket..query..";

    /** 밖을 부르는 출력 계약. */
    private static final String PORT = "com.ticket..port..";

    /** 저장·조회 기술 구현. */
    private static final String PERSISTENCE = "com.ticket..persistence..";

    /** HTTP 진입점. */
    private static final String ENDPOINT = "com.ticket..endpoint..";

    /**
     * 규칙 대상에서 빼는 클래스다.
     *
     * <ol>
     *   <li><b>Querydsl Q-type</b>: entity와 같은 package에 생성되므로 {@code ..domain..}에 그대로 들어오지만 사람이 쓴
     *       코드가 아니고 당연히 Querydsl을 참조한다. {@code @Generated}는 SOURCE retention이라 bytecode에 남지 않아 이름으로
     *       가른다 — {@code Q} 다음이 대문자인 것만 잡으므로 {@code QueueLevel} 같은 실제 타입은 걸리지 않는다.
     *   <li><b>바깥 역할 package 안의 클래스</b>: {@code booking.event.persistence}처럼 안쪽 역할 package 아래에 구현이
     *       있으면, 그 구현이 자기 package를 참조한다며 규칙이 항상 실패한다. 구현은 구현 규칙으로 본다.
     * </ol>
     */
    private static final DescribedPredicate<JavaClass> GENERATED_OR_IMPLEMENTATION =
            describe(
                    "Querydsl Q-type이거나 persistence/endpoint 구현",
                    clazz ->
                            clazz.getSimpleName().matches("Q[A-Z].*")
                                    || resideInAnyPackage(PERSISTENCE, ENDPOINT).test(clazz));

    /**
     * {@code JPAQueryFactory}를 주입받는 class — 즉 DB를 직접 읽는 구체 Query다.
     *
     * <p>이름({@code *Query})이 아니라 실제로 무엇을 갖고 있는지로 가른다. 이름 규칙은 새 조회가 다른 이름을 달면 조용히 빠지지만, 조회를 하려면
     * {@code JPAQueryFactory}는 반드시 갖고 있어야 한다.
     */
    private static final DescribedPredicate<JavaClass> DB_QUERY =
            describe(
                    "JPAQueryFactory로 DB를 직접 읽는 Query",
                    clazz ->
                            clazz.getAllFields().stream()
                                    .anyMatch(
                                            field ->
                                                    field.getRawType()
                                                            .getName()
                                                            .equals(
                                                                    "com.querydsl.jpa.impl.JPAQueryFactory")));

    /** 규칙이 이름을 아는 역할이다. 하나라도 소스 트리에서 사라지면 규칙이 조용히 비어 버리므로 존재를 따로 확인한다. */
    private static final List<String> ROLE_DIRECTORY_NAMES =
            List.of("domain", "usecase", "event", "query", "port", "persistence", "endpoint");

    // ---------------------------------------------------------------- 계층 방향

    @ArchTest
    static final ArchRule domain은_조립_저장_HTTP를_모른다 =
            noClasses()
                    .that(resideInAPackage(DOMAIN).and(not(GENERATED_OR_IMPLEMENTATION)))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(USECASE, EVENT, QUERY, PERSISTENCE, ENDPOINT)
                    .because("domain은 업무 규칙과 상태만 안다 — 조립·조회 계약·저장·HTTP를 모른다");

    @ArchTest
    static final ArchRule usecase와_event는_저장_구현과_HTTP를_모른다 =
            noClasses()
                    .that(resideInAnyPackage(USECASE, EVENT).and(not(GENERATED_OR_IMPLEMENTATION)))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(PERSISTENCE, ENDPOINT)
                    .because(
                            "조립은 계약(domain repository·query port·output port)으로만 밖을 부른다 — 구현 선택은 persistence가 갖는다");

    @ArchTest
    static final ArchRule 출력_port는_구현과_HTTP를_모른다 =
            noClasses()
                    .that(resideInAPackage(PORT).and(not(GENERATED_OR_IMPLEMENTATION)))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(PERSISTENCE, ENDPOINT)
                    .because("출력 port는 밖을 부르는 계약이다 — 계약이 구현을 알면 계약과 구현을 나눈 이유가 사라진다");

    /**
     * {@code query}는 계약이 아니라 자기 module의 local DB 조회 <b>구현</b>이다.
     *
     * <p>그래서 {@link #출력_port는_구현과_HTTP를_모른다}와 규칙이 다르다 — Querydsl·JPA를 직접 쓰는 것은 허용하고(아래 {@link
     * #업무_코드는_Querydsl과_Redisson을_모른다}가 {@code query}를 대상에서 뺀 이유다), 대신 <b>방향</b>을 막는다. 조회가 조립(use
     * case·event)이나 HTTP를 거꾸로 부르면 읽기 경로가 업무 흐름에 묶이고, persistence의 RepositoryAdapter를 거치면 조회가
     * aggregate 복원 경로에 다시 얹힌다.
     */
    @ArchTest
    static final ArchRule query는_조립과_HTTP를_모른다 =
            noClasses()
                    .that(resideInAPackage(QUERY).and(not(GENERATED_OR_IMPLEMENTATION)))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(USECASE, EVENT, ENDPOINT, PERSISTENCE)
                    .because(
                            "query는 자기 module의 DB 조회만 한다 — 조립·HTTP를 거꾸로 부르거나 저장 adapter를 경유하지 않는다");

    @ArchTest
    static final ArchRule endpoint는_저장_구현을_모른다 =
            noClasses()
                    .that()
                    .resideInAPackage(ENDPOINT)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(PERSISTENCE)
                    .because("endpoint는 use case를 부른다 — 저장 기술을 직접 고르지 않는다");

    @ArchTest
    static final ArchRule endpoint는_Repository를_직접_부르지_않는다 =
            noClasses()
                    .that()
                    .resideInAPackage(ENDPOINT)
                    .should()
                    .dependOnClassesThat()
                    .haveNameMatching(".*(Repository|RepositoryAdapter|Query)")
                    .orShould()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.data.jpa..", "com.querydsl..")
                    .because(
                            "HTTP endpoint는 application use case를 부른다 — 조회를 직접 부르면 트랜잭션 경계와"
                                    + " 권한 판정이 함께 빠진다");

    /**
     * 업무 쪽 코드가 저장·락 <b>구현 라이브러리</b>를 직접 알지 않게 한다.
     *
     * <p>{@code jakarta.persistence}와 {@code org.springframework.data}는 일부러 뺐다. 이 프로젝트의 domain은 JPA
     * entity 자체이고 auditing도 Spring Data가 준다 — 여기서 막으면 지금 구조를 통째로 부정하게 된다. 반면 Querydsl과 Redisson은 조회
     * 표현과 락 임대 방식이라, 업무 코드가 알면 그 선택에 묶인다.
     *
     * <p><b>{@code query}는 대상이 아니다.</b> {@code query}는 local DB 조회 구현을 소유하므로 Querydsl·JPA를 직접 쓴다.
     * 여기까지 막으면 조회마다 port interface와 adapter를 한 쌍씩 만들어야 하고, 그 경유 지점은 기능을 이해하는 데 아무것도 보태지 않는다. 대신
     * {@link #query는_조립과_HTTP를_모른다}와 {@link #query는_다른_업무_module을_조합하지_않는다}가 방향을 막는다.
     */
    @ArchTest
    static final ArchRule 업무_코드는_Querydsl과_Redisson을_모른다 =
            noClasses()
                    .that(
                            resideInAnyPackage(DOMAIN, USECASE, EVENT, PORT)
                                    .and(not(GENERATED_OR_IMPLEMENTATION)))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.querydsl..", "org.redisson..")
                    .because("조회 표현과 락 임대 방식은 persistence가 고른다 — 업무 코드가 알면 바꿀 때 함께 바뀐다");

    // ---------------------------------------------------------------- module 경계

    /**
     * 다른 module은 공개면({@link #EXPOSED_NAMED_INTERFACES})으로만 부른다.
     *
     * <p>예전에는 "{@code <module>.domain}/{@code application}/{@code infrastructure}/{@code endpoint}를
     * 참조하지 않는다"로 썼다. 계층 이름이 module마다 달라진 지금은 그 목록이 곧 구멍이 된다 — 새 package 이름 하나가 규칙 밖으로 빠진다. 그래서 반대로
     * 뒤집어 "공개면 <b>밖</b>은 전부 금지"로 쓴다. 공개면 목록은 아래 스냅샷 하나가 원본이다.
     */
    @ArchTest
    static final ArchRule 다른_module의_공개면_밖을_참조하지_않는다 =
            combine(
                    modulesWithExposedSurface(),
                    module ->
                            noClasses()
                                    .that()
                                    .resideOutsideOfPackage("com.ticket." + module + "..")
                                    .should()
                                    .dependOnClassesThat(insideButNotExposed(module))
                                    .because(
                                            module
                                                    + "의 구현은 밖에서 보이지 않는다 — cross-module 호출은 "
                                                    + module
                                                    + "이 의도적으로 공개한 named interface로만 한다"));

    /**
     * {@code query}는 자기 module의 DB만 본다.
     *
     * <p>조회가 다른 module의 공개 API를 불러 결과를 합치기 시작하면 그 조합이 어디서 일어나는지가 조회 구현 안으로 숨는다. 표시값 조합은 use
     * case·service가 한다 — {@code GetShowDetailUseCase}가 venue 이름을, {@code
     * PerformanceSaleCatalogService}가 venue 좌석 주소를 붙이는 것이 그 자리다.
     */
    @ArchTest
    static final ArchRule query는_다른_업무_module을_조합하지_않는다 =
            combine(
                    BUSINESS_MODULES,
                    module ->
                            queryReadsOnlyOwnModule(
                                    module,
                                    BUSINESS_MODULES.stream()
                                            .filter(other -> !other.equals(module))
                                            .toList()));

    /**
     * {@link #query는_다른_업무_module을_조합하지_않는다}의 module 한 개짜리 규칙이다. 회귀 검증이 같은 factory를 쓴다.
     *
     * <p>대상은 {@code query} package 전체가 아니라 <b>DB를 직접 읽는 Query</b>다. 같은 package의 읽기 모델은 다른 module의
     * 공개 값 타입({@code venue.api.Region} 등)을 담을 수 있고 담아야 한다 — 막아야 하는 것은 조회 구현이 다른 module을 불러 결과를 합치는
     * 것이다.
     */
    private static ArchRule queryReadsOnlyOwnModule(
            final String module, final List<String> otherModules) {
        return noClasses()
                .that(resideInAPackage("com.ticket." + module + "..query..").and(DB_QUERY))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        otherModules.stream()
                                .map(other -> "com.ticket." + other + "..")
                                .toArray(String[]::new))
                .because(module + "의 query는 자기 DB만 조회한다 — 다른 module의 정보 조합은 use case가 한다")
                // member·payment처럼 query package가 없는 module도 목록에 있다. 없는 것은 위반이 아니다.
                .allowEmptyShould(true);
    }

    @ArchTest
    static final ArchRule shared는_업무_module을_모른다 =
            noClasses()
                    .that()
                    .resideInAPackage("com.ticket." + SHARED + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            BUSINESS_MODULES.stream()
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
                            PERSISTENCE,
                            ENDPOINT)
                    .because(
                            "api는 interface·record·enum 같은 계약만 갖는다 — 저장 기술이나 HTTP 타입이 새면 호출하는"
                                    + " module이 그 선택에 묶인다");

    // ---------------------------------------------------------------- 검사 대상이 비지 않았는지

    /**
     * 역할 이름이 소스 트리에 실제로 있는지 확인한다.
     *
     * <p>위 규칙들은 {@code ..usecase..} 같은 패턴을 쓴다. 그 이름의 package가 하나도 없으면 ArchUnit이 빈 집합을 검사하며 조용히 통과한다
     * — 검사되지 않는 것과 통과하는 것이 똑같아 보인다. 이름이 사라졌다면 규칙도 함께 고쳐야 한다는 뜻이다.
     */
    @Test
    void 규칙이_쓰는_역할_package가_실제로_존재한다() {
        final Set<String> present = roleDirectoryNames();

        assertThat(present)
                .as("규칙이 이름으로 가리키는 역할 package — 하나라도 없으면 그 규칙은 아무것도 검사하지 않는다")
                .containsAll(ROLE_DIRECTORY_NAMES);
        for (final String module : BUSINESS_MODULES) {
            assertThat(MAIN_SOURCE_ROOT.resolve(module)).as("%s module 디렉터리", module).isDirectory();
        }
        assertThat(MAIN_SOURCE_ROOT.resolve("member").resolve("api"))
                .as("api 공개면이 실제로 있어야 api 규칙이 의미를 갖는다")
                .isDirectory();
    }

    private static Set<String> roleDirectoryNames() {
        try (Stream<Path> directories = Files.walk(MAIN_SOURCE_ROOT)) {
            return directories
                    .filter(Files::isDirectory)
                    .map(directory -> directory.getFileName().toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

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

    // ---------------------------------------------------------------- 규칙 회귀 검증

    /**
     * 위 규칙들이 허용 사례와 위반 사례를 실제로 가르는지 확인한다.
     *
     * <p>구조 규칙은 조용히 아무것도 검사하지 않는 쪽으로 무너진다 — 패턴이 빗나가면 위반이 있어도 통과한다. 특히 {@code query}의 역할이 바뀌면서 "이름이
     * {@code QueryPort}인 것"을 보던 검사가 새 구체 {@code Query}를 놓칠 수 있었다. 그래서 {@code
     * com.ticket.archfixture}에 허용·위반 사례를 한 쌍씩 두고 규칙을 직접 평가한다.
     */
    @Test
    void 새_query_규칙이_허용_사례와_위반_사례를_구별한다() {
        assertThat(violationsOf(query는_조립과_HTTP를_모른다))
                .as("조회가 use case를 거꾸로 부르면 잡는다")
                .anyMatch(detail -> detail.contains("UseCaseCallingFixtureQuery"))
                .as("Querydsl로 자기 DB만 읽는 조회는 걸리지 않는다")
                .noneMatch(detail -> detail.contains("AllowedFixtureQuery"));

        assertThat(violationsOf(업무_코드는_Querydsl과_Redisson을_모른다))
                .as("use case의 Querydsl 직접 사용은 여전히 잡는다")
                .anyMatch(detail -> detail.contains("QuerydslUsingFixtureUseCase"))
                .as("query의 Querydsl 사용은 이제 허용한다")
                .noneMatch(detail -> detail.contains("AllowedFixtureQuery"));

        assertThat(
                        violationsOf(
                                queryReadsOnlyOwnModule(
                                        "archfixture.left", List.of("archfixture.right"))))
                .as("조회가 다른 module의 공개 API로 결과를 조합하면 잡는다")
                .anyMatch(detail -> detail.contains("CrossModuleFixtureQuery"))
                .noneMatch(detail -> detail.contains("AllowedFixtureQuery"));
    }

    @Test
    void endpoint_규칙이_구체_Query_직접_호출을_잡고_읽기_모델_참조는_허용한다() {
        assertThat(violationsOf(endpoint는_Repository를_직접_부르지_않는다))
                .as("endpoint가 use case를 건너뛰고 구체 Query를 부르면 잡는다 — 옛 이름(QueryPort)만 보면 놓친다")
                .anyMatch(detail -> detail.contains("QueryCallingFixtureController"))
                .as("endpoint가 query package의 Row/View를 응답으로 쓰는 것은 허용한다")
                .noneMatch(detail -> detail.contains("ReadModelFixtureController"));
    }

    /** 규칙을 fixture에 대해 평가해 위반 목록만 꺼낸다. 규칙 자체는 운영 코드에 대해 {@code @ArchTest}가 따로 돌린다. */
    private static List<String> violationsOf(final ArchRule rule) {
        return rule.evaluate(ARCH_FIXTURES).getFailureReport().getDetails();
    }

    // ---------------------------------------------------------------- helper

    /** 공개면을 하나라도 가진 module이다. 공개면이 없는 module은 "밖에서 아무것도 못 본다"가 아니라 아예 참조가 없어 규칙이 비어 버린다. */
    private static List<String> modulesWithExposedSurface() {
        return EXPOSED_NAMED_INTERFACES.stream()
                .map(entry -> entry.substring(0, entry.indexOf(" :: ")))
                .distinct()
                .sorted()
                .toList();
    }

    /** {@code com.ticket.<module>}의 내부이면서 공개 named interface package가 아닌 것. */
    private static com.tngtech.archunit.base.DescribedPredicate<JavaClass> insideButNotExposed(
            final String module) {
        final String[] exposedPackages =
                EXPOSED_NAMED_INTERFACES.stream()
                        .filter(entry -> entry.startsWith(module + " :: "))
                        .map(entry -> "com.ticket." + module + "." + entry.split(" :: ")[1] + "..")
                        .toArray(String[]::new);

        return resideInAPackage("com.ticket." + module + "..")
                .and(not(resideInAnyPackage(exposedPackages)))
                .as(module + "의 공개면 밖");
    }

    /**
     * module마다 같은 모양의 규칙을 만들어 하나로 합친다. 규칙을 module 수만큼 손으로 복사하면 module이 늘 때 조용히 빠진다 — 목록 상수 하나만 고치면
     * 되게 한다.
     */
    private static ArchRule combine(
            final List<String> modules, final Function<String, ArchRule> factory) {
        CompositeArchRule composite = null;
        for (final String module : new LinkedHashSet<>(modules)) {
            final ArchRule rule = factory.apply(module);
            composite = composite == null ? CompositeArchRule.of(rule) : composite.and(rule);
        }
        if (composite == null) {
            throw new IllegalStateException("검사할 module이 없다");
        }
        return composite;
    }
}
