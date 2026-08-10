package com.cabin.udp.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.flighttrack.service.FlightSessionService;
import com.cabin.passenger.service.IfeCockrellCurrentStateCache;
import com.cabin.udp.dto.CurrentFlightContext;
import com.cabin.udp.mapper.UdpIngestMapper;
import com.cabin.udp.entity.DataRecord;
import com.cabin.udp.entity.DataTypeConfig;
import com.cabin.udp.dto.ParsedUdpPayload;
import com.cabin.udp.dto.UdpIngestOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class UdpIngestService {
    private final ObjectProvider<UdpIngestMapper> mapperProvider;
    private final ObjectMapper objectMapper;
    private final UdpPayloadParser parser;
    private final CurrentFlightContextService currentFlightContextService;
    private final FlightSessionService flightSessionService;
    private final IfeCockrellCurrentStateCache cockrellStateCache;

    public UdpIngestService(
            ObjectProvider<UdpIngestMapper> mapperProvider,
            ObjectMapper objectMapper,
            UdpPayloadParser parser,
            CurrentFlightContextService currentFlightContextService,
            FlightSessionService flightSessionService,
            IfeCockrellCurrentStateCache cockrellStateCache
    ) {
        this.mapperProvider = mapperProvider;
        this.objectMapper = objectMapper;
        this.parser = parser;
        this.currentFlightContextService = currentFlightContextService;
        this.flightSessionService = flightSessionService;
        this.cockrellStateCache = cockrellStateCache;
    }

    @Transactional
    public UdpIngestOutcome ingestDatagram(
            DataTypeConfig config,
            byte[] bytes,
            int length,
            OffsetDateTime receivedAt,
            String sourceHost,
            Integer sourcePort
    ) {
        ParsedUdpPayload parsed;
        try {
            String text = decodeUtf8(bytes, length);
            parsed = parseJsonPayload(config, text, receivedAt, sourceHost, sourcePort);
        } catch (CharacterCodingException exception) {
            parsed = parser.failedTextPayload(
                    config,
                    UdpPayloadParser.invalidUtf8Summary(bytes, length),
                    "Invalid UTF-8 datagram",
                    receivedAt,
                    sourceHost,
                    sourcePort
            );
        }
        persist(parsed);
        DataRecord record = parsed.record();
        return new UdpIngestOutcome(
                record.getId(),
                record.getDataTypeCode(),
                record.getParseStatus(),
                parsed.businessRows().size(),
                record.getParseError()
        );
    }

    private ParsedUdpPayload parseJsonPayload(
            DataTypeConfig config,
            String text,
            OffsetDateTime receivedAt,
            String sourceHost,
            Integer sourcePort
    ) {
        try {
            JsonNode root = objectMapper.readTree(text);
            return parser.parse(config, root, text, receivedAt, sourceHost, sourcePort);
        } catch (JsonProcessingException exception) {
            return parser.failedTextPayload(
                    config,
                    text,
                    "Invalid JSON: " + summarize(exception),
                    receivedAt,
                    sourceHost,
                    sourcePort
            );
        }
    }

    private void persist(ParsedUdpPayload parsed) {
        UdpIngestMapper mapper = mapper();
        DataRecord record = currentFlightContextService.applyTo(parsed.record());
        CurrentFlightContext contextBeforeInsert = currentFlightContextService.current();
        mapper.insertDataRecord(record);
        UUID qarSessionId = null;
        OffsetDateTime qarSessionStartedAt = null;
        for (Map<String, Object> row : parsed.businessRows()) {
            if ("QAR".equals(record.getDataTypeCode())) {
                qarSessionId = insertQarRow(mapper, record, row);
                qarSessionStartedAt = (OffsetDateTime) row.get("sampleAt");
            } else {
                insertBusinessRow(mapper, record.getDataTypeCode(), row);
            }
        }
        if ("QAR".equals(record.getDataTypeCode())) {
            CurrentFlightContext context = currentFlightContextService.updateFromQar(
                    record,
                    qarSessionId,
                    qarSessionStartedAt
            );
            if (context != null && context.hasRoute()) {
                mapper.backfillMissingFlightContext(
                        context.flightNo(),
                        context.origin(),
                        context.destination(),
                        context.airlineCode(),
                        currentFlightContextService.startedAt()
                );
            }
            CurrentFlightContext activeContext = context == null ? currentFlightContextService.current() : context;
            updateCacheAfterCommit(() -> cockrellStateCache.retainOnly(
                    activeContext == null ? null : activeContext.flightSessionId()
            ));
        } else if ("IFE_COCKRELL_BEHAVIOR".equals(record.getDataTypeCode())) {
            CurrentFlightContext context = contextBeforeInsert;
            parsed.businessRows().forEach(row -> updateCacheAfterCommit(
                    () -> cockrellStateCache.update(context, row, record.getReceivedAt())
            ));
        }
    }

    private UUID insertQarRow(
            UdpIngestMapper mapper,
            DataRecord record,
            Map<String, Object> row
    ) {
        UUID sessionId = flightSessionService.resolve(record, row);
        row.put("flightSessionId", sessionId);
        mapper.insertQarSample(row);
        flightSessionService.updateLatest(sessionId, record, row);
        return sessionId;
    }

    private void updateCacheAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void insertBusinessRow(UdpIngestMapper mapper, String dataTypeCode, Map<String, Object> row) {
        switch (dataTypeCode) {
            case "GROUND_TASK" -> mapper.insertSimulationTask(row);
            case "GROUND_TRAFFIC_RECORD" -> mapper.insertTrafficRecord(row);
            case "GROUND_SESSION_SUMMARY" -> mapper.insertSessionSummary(row);
            case "SMART_WINDOW_STATUS" -> mapper.insertSmartWindowStatus(row);
            case "IFE_633_BEHAVIOR" -> mapper.insertIfe633Behavior(row);
            case "IFE_COCKRELL_BEHAVIOR" -> mapper.insertIfeCockrellBehavior(row);
            default -> throw new BusinessException(ResponseCode.UNSUPPORTED_DATA_TYPE, "不支持的数据类型");
        }
    }

    private UdpIngestMapper mapper() {
        UdpIngestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }

    private String decodeUtf8(byte[] bytes, int length) throws CharacterCodingException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, length))
                .toString();
    }

    private String summarize(JsonProcessingException exception) {
        String message = exception.getOriginalMessage() != null
                ? exception.getOriginalMessage()
                : exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}

