package com.ticket.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    void worker_설정이_없으면_스케줄러를_활성화한다() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(SchedulingConfig.class));
    }

    @Test
    void worker_enabled가_true면_스케줄러를_활성화한다() {
        contextRunner.withPropertyValues("worker.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(SchedulingConfig.class));
    }

    @Test
    void worker_enabled가_false면_어떤_scheduled_트리거도_등록되지_않는다() {
        contextRunner.withPropertyValues("worker.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SchedulingConfig.class);
                    assertThat(context).doesNotHaveBean(
                            "org.springframework.context.annotation.internalScheduledAnnotationProcessor");
                });
    }
}
