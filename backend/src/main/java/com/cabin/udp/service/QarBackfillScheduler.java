package com.cabin.udp.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class QarBackfillScheduler {
    private static final Logger log = LoggerFactory.getLogger(QarBackfillScheduler.class);
    private static final long INITIAL_DELAY_SECONDS = 1;

    private final TaskScheduler taskScheduler;
    private final QarBackfillTaskService taskService;
    private final Set<UUID> scheduledSessions = ConcurrentHashMap.newKeySet();

    public QarBackfillScheduler(
            @Qualifier("qarBackfillTaskScheduler") TaskScheduler taskScheduler,
            QarBackfillTaskService taskService
    ) {
        this.taskScheduler = taskScheduler;
        this.taskService = taskService;
    }

    public boolean schedule(QarBackfillRequest request) {
        UUID sessionId = request.flightSessionId();
        if (!scheduledSessions.add(sessionId)) {
            return false;
        }
        try {
            taskScheduler.schedule(
                    () -> run(request),
                    Instant.now().plusSeconds(INITIAL_DELAY_SECONDS)
            );
            return true;
        } catch (RuntimeException exception) {
            scheduledSessions.remove(sessionId);
            log.warn(
                    "Unable to schedule QAR post-commit backfill for session {}: {}",
                    sessionId,
                    safeMessage(exception)
            );
            return false;
        }
    }

    private void run(QarBackfillRequest request) {
        try {
            taskService.backfill(request);
        } catch (RuntimeException exception) {
            log.warn(
                    "QAR post-commit backfill failed for session {}: {}",
                    request.flightSessionId(),
                    safeMessage(exception)
            );
        } finally {
            scheduledSessions.remove(request.flightSessionId());
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
