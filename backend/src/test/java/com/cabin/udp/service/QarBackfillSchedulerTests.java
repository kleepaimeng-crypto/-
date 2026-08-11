package com.cabin.udp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

class QarBackfillSchedulerTests {
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final QarBackfillTaskService taskService = mock(QarBackfillTaskService.class);
    private final QarBackfillScheduler scheduler = new QarBackfillScheduler(taskScheduler, taskService);

    @Test
    void schedulesWithoutRunningBackfillOnCallingThreadAndDeduplicatesSession() {
        QarBackfillRequest request = request();

        boolean first = scheduler.schedule(request);
        boolean duplicate = scheduler.schedule(request);

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        verifyNoInteractions(taskService);

        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(runnable.capture(), any(Instant.class));
        runnable.getValue().run();
        verify(taskService).backfill(request);
    }

    private QarBackfillRequest request() {
        return new QarBackfillRequest(
                UUID.randomUUID(),
                "CA4732",
                "ZBAA",
                "ZSPD",
                "CA",
                OffsetDateTime.parse("2026-08-11T13:00:00+08:00")
        );
    }
}
