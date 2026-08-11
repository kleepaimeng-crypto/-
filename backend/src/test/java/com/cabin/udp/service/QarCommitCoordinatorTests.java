package com.cabin.udp.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cabin.udp.entity.DataRecord;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class QarCommitCoordinatorTests {
    private final QarPostCommitService postCommitService = mock(QarPostCommitService.class);
    private final QarCommitCoordinator coordinator = new QarCommitCoordinator(postCommitService);

    @AfterEach
    void cleanUpTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void defersPublishingContextUntilTransactionCommits() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        DataRecord record = new DataRecord();
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-08-11T13:00:00+08:00");

        coordinator.afterCommit(record, sessionId, startedAt);

        verifyNoInteractions(postCommitService);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(postCommitService).handle(record, sessionId, startedAt);
    }
}
