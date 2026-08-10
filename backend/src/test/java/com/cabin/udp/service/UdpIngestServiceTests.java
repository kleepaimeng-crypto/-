package com.cabin.udp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cabin.config.UdpProperties;
import com.cabin.flighttrack.service.FlightSessionService;
import com.cabin.passenger.service.IfeCockrellCurrentStateCache;
import com.cabin.udp.mapper.UdpIngestMapper;
import com.cabin.udp.entity.DataRecord;
import com.cabin.udp.entity.DataTypeConfig;
import com.cabin.udp.dto.UdpIngestOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class UdpIngestServiceTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UdpIngestMapper mapper = mock(UdpIngestMapper.class);
    private final UdpPayloadParser parser = new UdpPayloadParser(
            objectMapper,
            new UdpProperties(false, 0, 0, null, null, null, null)
    );
    private final CurrentFlightContextService currentFlightContextService =
            new CurrentFlightContextService(new UdpProperties(false, 0, 0, null, null, null, null));
    private final FlightSessionService flightSessionService = mock(FlightSessionService.class);
    private final IfeCockrellCurrentStateCache cockrellStateCache = new IfeCockrellCurrentStateCache(objectMapper);
    private final UdpIngestService service = new UdpIngestService(
            provider(mapper),
            objectMapper,
            parser,
            currentFlightContextService,
            flightSessionService,
            cockrellStateCache
    );
    private final OffsetDateTime receivedAt = OffsetDateTime.parse("2026-07-04T12:00:00+08:00");

    UdpIngestServiceTests() {
        when(flightSessionService.resolve(
                org.mockito.ArgumentMatchers.any(DataRecord.class),
                anyMap()
        )).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void invalidJsonIsStoredAsFailedRawTextRecord() {
        when(mapper.insertDataRecord(org.mockito.ArgumentMatchers.any(DataRecord.class))).thenReturn(1);

        UdpIngestOutcome outcome = service.ingestDatagram(
                config(),
                "{bad json".getBytes(StandardCharsets.UTF_8),
                "{bad json".length(),
                receivedAt,
                "127.0.0.1",
                51000
        );

        ArgumentCaptor<DataRecord> captor = ArgumentCaptor.forClass(DataRecord.class);
        verify(mapper).insertDataRecord(captor.capture());
        assertThat(outcome.parseStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getRawPayload()).isNull();
        assertThat(captor.getValue().getRawText()).isEqualTo("{bad json");
        assertThat(captor.getValue().getParseError()).startsWith("Invalid JSON");
    }

    @Test
    void invalidUtf8IsStoredAsFailedSummaryWithoutThrowing() {
        when(mapper.insertDataRecord(org.mockito.ArgumentMatchers.any(DataRecord.class))).thenReturn(1);

        byte[] bytes = new byte[]{(byte) 0xC3, 0x28};
        UdpIngestOutcome outcome = service.ingestDatagram(
                config(),
                bytes,
                bytes.length,
                receivedAt,
                "127.0.0.1",
                51000
        );

        ArgumentCaptor<DataRecord> captor = ArgumentCaptor.forClass(DataRecord.class);
        verify(mapper).insertDataRecord(captor.capture());
        assertThat(outcome.parseStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getRawText()).startsWith("INVALID_UTF8");
        assertThat(captor.getValue().getParseError()).isEqualTo("Invalid UTF-8 datagram");
    }

    @Test
    void validQarPersistsRecordAndBusinessRow() {
        when(mapper.insertDataRecord(org.mockito.ArgumentMatchers.any(DataRecord.class))).thenReturn(1);
        when(mapper.insertQarSample(anyMap())).thenReturn(1);
        String json = """
                {
                  "AIR GND ON GND": "AIR",
                  "BARO COR ALT NO. 1": "35000",
                  "COMPUTED AIRSPEED": "285",
                  "DESTINATION": "ZSPD",
                  "DESTINATION ETA": "20:00.0",
                  "DISTANCE TO GO": "180",
                  "FLIGHT NUMBER": "CA4732",
                  "GROUNDSPEED": "470",
                  "ORIGIN": "ZBAA",
                  "PRES POSN LAT - FMC": "36.411113024",
                  "PRES POSN LONG - FMC": "120.09225375",
                  "TRACK ANGLE TRUE - FMC": "180.100",
                  "CAPT DISPLAY HEADING": "181.100",
                  "BODY PITCH RATE": "1.20",
                  "BODY ROLL RATE": "0.30",
                  "LT MAIN FUEL QTY": "11000",
                  "RT MAIN FUEL QTY": "11100",
                  "CENTER MAIN FUEL QTY": "6000",
                  "LOW FUEL QTY TANK1/2": "false",
                  "frameCount": 42,
                  "time": "11:59:58"
                }
                """;

        UdpIngestOutcome outcome = service.ingestDatagram(
                config(),
                json.getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8).length,
                receivedAt,
                "127.0.0.1",
                51000
        );

        assertThat(outcome.parseStatus()).isEqualTo("PARSED");
        assertThat(outcome.businessRowCount()).isEqualTo(1);
        verify(mapper).insertQarSample(anyMap());
    }

    @Test
    void smartWindowAfterQarUsesCurrentFlightContext() {
        when(mapper.insertDataRecord(org.mockito.ArgumentMatchers.any(DataRecord.class))).thenReturn(1);
        when(mapper.insertQarSample(anyMap())).thenReturn(1);
        when(mapper.insertSmartWindowStatus(anyMap())).thenReturn(1);

        ingest(config("QAR"), qarJson());
        ingest(config("SMART_WINDOW_STATUS"), smartWindowJson());

        ArgumentCaptor<DataRecord> captor = ArgumentCaptor.forClass(DataRecord.class);
        verify(mapper, times(2)).insertDataRecord(captor.capture());
        DataRecord smartWindow = captor.getAllValues().get(1);
        assertThat(smartWindow.getDataTypeCode()).isEqualTo("SMART_WINDOW_STATUS");
        assertThat(smartWindow.getFlightNo()).isEqualTo("CA4732");
        assertThat(smartWindow.getOrigin()).isEqualTo("ZBAA");
        assertThat(smartWindow.getDestination()).isEqualTo("ZSPD");
        assertThat(smartWindow.getAirlineCode()).isEqualTo("CA");
    }

    @Test
    void trafficAfterQarKeepsOwnFlightAndUsesCurrentRoute() {
        when(mapper.insertDataRecord(org.mockito.ArgumentMatchers.any(DataRecord.class))).thenReturn(1);
        when(mapper.insertQarSample(anyMap())).thenReturn(1);
        when(mapper.insertTrafficRecord(anyMap())).thenReturn(1);

        ingest(config("QAR"), qarJson());
        ingest(config("GROUND_TRAFFIC_RECORD"), trafficJson());

        ArgumentCaptor<DataRecord> captor = ArgumentCaptor.forClass(DataRecord.class);
        verify(mapper, times(2)).insertDataRecord(captor.capture());
        DataRecord traffic = captor.getAllValues().get(1);
        assertThat(traffic.getDataTypeCode()).isEqualTo("GROUND_TRAFFIC_RECORD");
        assertThat(traffic.getFlightNo()).isEqualTo("CA4732");
        assertThat(traffic.getOrigin()).isEqualTo("ZBAA");
        assertThat(traffic.getDestination()).isEqualTo("ZSPD");
    }

    @Test
    void windowBeforeQarTriggersBackfillWhenQarProvidesRoute() {
        when(mapper.insertDataRecord(org.mockito.ArgumentMatchers.any(DataRecord.class))).thenReturn(1);
        when(mapper.insertSmartWindowStatus(anyMap())).thenReturn(1);
        when(mapper.insertQarSample(anyMap())).thenReturn(1);

        ingest(config("SMART_WINDOW_STATUS"), smartWindowJson());
        ingest(config("QAR"), qarJson());

        verify(mapper).backfillMissingFlightContext(
                org.mockito.ArgumentMatchers.eq("CA4732"),
                org.mockito.ArgumentMatchers.eq("ZBAA"),
                org.mockito.ArgumentMatchers.eq("ZSPD"),
                org.mockito.ArgumentMatchers.eq("CA"),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)
        );
    }

    @Test
    void unmatchedCockrellEventIsPersistedButDoesNotEnterCurrentFlightCache() {
        when(mapper.insertDataRecord(org.mockito.ArgumentMatchers.any(DataRecord.class))).thenReturn(1);
        when(mapper.insertQarSample(anyMap())).thenReturn(1);
        when(mapper.insertIfeCockrellBehavior(anyMap())).thenReturn(1);

        ingest(config("QAR"), qarJson());
        ingest(config("IFE_COCKRELL_BEHAVIOR"), cockrellJson("MU9999"));

        ArgumentCaptor<DataRecord> captor = ArgumentCaptor.forClass(DataRecord.class);
        verify(mapper, times(2)).insertDataRecord(captor.capture());
        DataRecord cockrell = captor.getAllValues().get(1);
        assertThat(cockrell.getFlightNo()).isEqualTo("MU9999");
        assertThat(cockrell.getAirlineCode()).isEqualTo("MU");
        assertThat(cockrell.getOrigin()).isNull();
        assertThat(cockrell.getDestination()).isNull();
        verify(mapper).insertIfeCockrellBehavior(anyMap());
        assertThat(currentFlightContextService.current().flightNo()).isEqualTo("CA4732");
        assertThat(cockrellStateCache.snapshot(currentFlightContextService.current().flightSessionId())).isEmpty();
    }

    private DataTypeConfig config() {
        return config("QAR");
    }

    private DataTypeConfig config(String code) {
        DataTypeConfig config = new DataTypeConfig();
        config.setCode(code);
        config.setName(code);
        config.setMessageType(switch (code) {
            case "SMART_WINDOW_STATUS" -> "smart_window.status";
            case "GROUND_TRAFFIC_RECORD" -> "ground.traffic_record";
            case "IFE_COCKRELL_BEHAVIOR" -> "ife_cockrell.behavior";
            default -> "qar.frame";
        });
        config.setUdpPort(switch (code) {
            case "SMART_WINDOW_STATUS" -> 8094;
            case "GROUND_TRAFFIC_RECORD" -> 8092;
            case "IFE_COCKRELL_BEHAVIOR" -> 8096;
            default -> 8090;
        });
        config.setSourceSystemCode("SIMULATOR");
        config.setSourceDeviceCode(switch (code) {
            case "SMART_WINDOW_STATUS" -> "SIM-WINDOW";
            case "GROUND_TRAFFIC_RECORD" -> "SIM-GROUND";
            case "IFE_COCKRELL_BEHAVIOR" -> "SIM-IFE-COCKRELL";
            default -> "SIM-QAR";
        });
        return config;
    }

    private UdpIngestOutcome ingest(DataTypeConfig config, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return service.ingestDatagram(config, bytes, bytes.length, receivedAt, "127.0.0.1", 51000);
    }

    private String qarJson() {
        return """
                {
                  "AIR GND ON GND": "AIR",
                  "BARO COR ALT NO. 1": "35000",
                  "COMPUTED AIRSPEED": "285",
                  "DESTINATION": "ZSPD",
                  "DESTINATION ETA": "20:00.0",
                  "DISTANCE TO GO": "180",
                  "FLIGHT NUMBER": "CA4732",
                  "GROUNDSPEED": "470",
                  "ORIGIN": "ZBAA",
                  "PRES POSN LAT - FMC": "36.411113024",
                  "PRES POSN LONG - FMC": "120.09225375",
                  "TRACK ANGLE TRUE - FMC": "180.100",
                  "CAPT DISPLAY HEADING": "181.100",
                  "BODY PITCH RATE": "1.20",
                  "BODY ROLL RATE": "0.30",
                  "LT MAIN FUEL QTY": "11000",
                  "RT MAIN FUEL QTY": "11100",
                  "CENTER MAIN FUEL QTY": "6000",
                  "LOW FUEL QTY TANK1/2": "false",
                  "frameCount": 42,
                  "time": "11:59:58"
                }
                """;
    }

    private String smartWindowJson() {
        return """
                {
                  "messageType": "smart_window.status",
                  "sentAt": "2026-07-04T12:00:00+08:00",
                  "items": [
                    {
                      "windowId": 1,
                      "zoneId": 1,
                      "brightnessLevel": 5,
                      "connectStatus": true,
                      "status": "NORMAL",
                      "timestamp": "2026-07-04 12:00:00.123"
                    }
                  ]
                }
                """;
    }

    private String cockrellJson(String flightId) {
        return """
                {
                  "sysInfo": {"timestamp": "2026-07-04 12:00:00.123", "flightId": "%s"},
                  "paxInfo": {
                    "pnr": "PNR-001",
                    "seatNo": "F42",
                    "cabinClass": "ECONOMY",
                    "deviceId": "SEAT-F42",
                    "userId": "PAX-001"
                  },
                  "behaviorInfo": {
                    "behaviorType": "MOVIE_PLAY",
                    "contentId": "VIDEO-001",
                    "contentName": "测试影片",
                    "contentType": "剧情",
                    "playAction": "PLAY"
                  },
                  "extInfo": {"errorCode": "0000", "errorDesc": ""}
                }
                """.formatted(flightId);
    }

    private String trafficJson() {
        return """
                {
                  "messageType": "ground.traffic_record",
                  "sentAt": "2026-07-04T12:00:00+08:00",
                  "items": [
                    {
                      "windowStart": "2026-07-04T12:00:00+08:00",
                      "windowEnd": "2026-07-04T12:00:00+08:00",
                      "taskId": "CA4732-FLIGHT-20260704-001",
                      "terminalId": "TERM-001",
                      "displayTerminalId": "D-001",
                      "seatLabel": "12A",
                      "application": "高清视频",
                      "protocol": "TCP",
                      "direction": "downlink",
                      "bytesCount": 1024,
                      "packetCount": 12,
                      "throughputMbps": 6.5,
                      "peakMbps": 7.2,
                      "recordStatus": "recorded"
                    }
                  ]
                }
                """;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<UdpIngestMapper> provider(UdpIngestMapper mapper) {
        ObjectProvider<UdpIngestMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}


