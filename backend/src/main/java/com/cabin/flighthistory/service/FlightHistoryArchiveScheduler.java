package com.cabin.flighthistory.service;

import com.cabin.flighthistory.mapper.FlightHistoryArchiveMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "cabin.flight-history.enabled", havingValue = "true", matchIfMissing = true)
public class FlightHistoryArchiveScheduler {
    private static final int JOBS_PER_RUN = 10;
    private final FlightSessionClosingService closingService;
    private final FlightHistoryArchiveWorker archiveWorker;
    private final FlightHistoryArchiveMapper archiveMapper;
    private final Clock clock;

    @Autowired
    public FlightHistoryArchiveScheduler(
            FlightSessionClosingService closingService,
            FlightHistoryArchiveWorker archiveWorker,
            FlightHistoryArchiveMapper archiveMapper
    ) {
        this(closingService, archiveWorker, archiveMapper, Clock.systemDefaultZone());
    }

    FlightHistoryArchiveScheduler(
            FlightSessionClosingService closingService,
            FlightHistoryArchiveWorker archiveWorker,
            FlightHistoryArchiveMapper archiveMapper,
            Clock clock
    ) {
        this.closingService = closingService;
        this.archiveWorker = archiveWorker;
        this.archiveMapper = archiveMapper;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *")
    public void run() {
        closingService.closeDueSessions();
        for (int index = 0; index < JOBS_PER_RUN; index += 1) {
            try {
                if (!archiveWorker.archiveNext()) {
                    return;
                }
            } catch (RuntimeException exception) {
                // The worker transaction has rolled back; record a retry state separately.
                markFailedJob();
                return;
            }
        }
    }

    @Transactional
    void markFailedJob() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        var job = archiveMapper.lockNextJob(now);
        if (job == null) {
            return;
        }
        int minutes = switch (job.getAttemptCount()) {
            case 0 -> 1;
            case 1 -> 5;
            case 2 -> 15;
            case 3 -> 30;
            default -> 60;
        };
        archiveMapper.markFailed(job.getId(), now.plusMinutes(minutes), "历史轨迹归档失败", now);
    }
}
