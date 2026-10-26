package com.ticket.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@SuppressWarnings("NonAsciiCharacters")
class SchedulingConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(SchedulingConfig.class)
            // ShedLock 락 저장소(JdbcTemplateLockProvider)가 DataSource를 받는다. 이 테스트는 활성화 조건만 보므로 실제 DB는 필요 없다.
            .withBean(DataSource.class, () -> mock(DataSource.class));

    @Test
    void worker_설정이_없으면_스케줄러를_활성화한다() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(SchedulingConfig.class));
    }

    @Test
    void worker_enabled가_true면_스케줄러를_활성화한다() {
        contextRunner
                .withPropertyValues("worker.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(SchedulingConfig.class));
    }

    @Test
    void worker_enabled가_false면_어떤_scheduled_트리거도_등록되지_않는다() {
        contextRunner.withPropertyValues("worker.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(SchedulingConfig.class);
            assertThat(context)
                    .doesNotHaveBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor");
        });
    }
}
