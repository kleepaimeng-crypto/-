package com.cabin.udp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cabin.config.UdpProperties;
import com.cabin.udp.mapper.UdpIngestMapper;
import com.cabin.udp.entity.DataRecord;
import com.cabin.udp.entity.DataTypeConfig;
import com.cabin.udp.dto.UdpIngestOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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
    private final UdpIngestService service = new UdpIngestService(
            provider(mapper),
            objectMapper,
            parser
    );
    private final OffsetDateTime receivedAt = OffsetDateTime.parse("2026-07-04T12:00:00+08:00");

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

    private DataTypeConfig config() {
        DataTypeConfig config = new DataTypeConfig();
        config.setCode("QAR");
        config.setName("QAR");
        config.setMessageType("qar.frame");
        config.setUdpPort(8090);
        config.setSourceSystemCode("SIMULATOR");
        config.setSourceDeviceCode("SIM-QAR");
        return config;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<UdpIngestMapper> provider(UdpIngestMapper mapper) {
        ObjectProvider<UdpIngestMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}


