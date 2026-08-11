package com.cabin.udp.service;

import com.cabin.udp.entity.DataRecord;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class QarCommitCoordinator {
    private final QarPostCommitService postCommitService;

    public QarCommitCoordinator(QarPostCommitService postCommitService) {
        this.postCommitService = postCommitService;
    }

    public void afterCommit(
            DataRecord record,
            UUID flightSessionId,
            OffsetDateTime sessionStartedAt
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            postCommitService.handle(record, flightSessionId, sessionStartedAt);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                postCommitService.handle(record, flightSessionId, sessionStartedAt);
            }
        });
    }
}
