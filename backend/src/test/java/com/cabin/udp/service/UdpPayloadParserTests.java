package com.cabin.udp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cabin.config.UdpProperties;
import com.cabin.udp.entity.DataRecord;
import com.cabin.udp.entity.DataTypeConfig;
import com.cabin.udp.dto.ParsedUdpPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class UdpPayloadParserTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UdpPayloadParser parser = new UdpPayloadParser(
            objectMapper,
            new UdpProperties(false, 0, 0, null, null, null, null)
    );
    private final OffsetDateTime receivedAt = OffsetDateTime.parse("2026-07-04T12:00:00+08:00");

    @Test
    void parsesEverySupportedPayloadType() throws Exception {
        assertParsed("QAR", "qar.frame", qarJson(), 1);
        assertParsed("GROUND_TASK", "ground.task", groundTaskJson(), 1);
        assertParsed("GROUND_TRAFFIC_RECORD", "ground.traffic_record", trafficJson(false), 2);
        assertParsed("GROUND_SESSION_SUMMARY", "ground.session_summary", sessionJson(), 1);
        assertParsed("SMART_WINDOW_STATUS", "smart_window.status", smartWindowJson(), 2);
        assertParsed("IFE_633_BEHAVIOR", "ife_633.behavior", ife633Json(), 1);
        assertParsed("IFE_COCKRELL_BEHAVIOR", "ife_cockrell.behavior", ifeCockrellJson(), 1);
    }

    @Test
    void qarUsesReceivedDateAndSourceTimeAsBusinessTime() throws Exception {
        ParsedUdpPayload parsed = parse("QAR", "qar.frame", qarJson());

        DataRecord record = parsed.record();
        assertThat(record.getParseStatus()).isEqualTo("PARSED");
        assertThat(record.getFlightNo()).isEqualTo("CA4732");
        assertThat(record.getOrigin()).isEqualTo("ZBAA");
        assertThat(record.getDestination()).isEqualTo("ZSPD");
        assertThat(record.getSentAt()).isEqualTo(OffsetDateTime.parse("2026-07-04T11:59:58+08:00"));
        assertThat(parsed.businessRows()).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("frameCount", 42L));
    }

    @Test
    void partialBatchKeepsSuccessfulItemsAndMarksRecordPartial() throws Exception {
        ParsedUdpPayload parsed = parse("GROUND_TRAFFIC_RECORD", "ground.traffic_record", trafficJson(true));

        assertThat(parsed.record().getParseStatus()).isEqualTo("PARTIAL");
        assertThat(parsed.record().getPayloadCount()).isEqualTo(2);
        assertThat(parsed.record().getParseError()).contains("item 2");
        assertThat(parsed.businessRows()).hasSize(1);
    }

    @Test
    void cockrellBusinessDetailRemovesCoverPayloadAndStoresCoverMetadata() throws Exception {
        ParsedUdpPayload parsed = parse("IFE_COCKRELL_BEHAVIOR", "ife_cockrell.behavior", ifeCockrellJson());

        assertThat(parsed.record().getParseStatus()).isEqualTo("PARSED");
        assertThat(parsed.businessRows()).singleElement().satisfies(row -> {
            assertThat(row.get("coverMimeType")).isEqualTo("image/png");
            assertThat(row.get("coverChecksum").toString()).hasSize(64);
            assertThat(row.get("behaviorDetail").toString()).doesNotContain("coverBase64");
            assertThat(row.get("behaviorDetail").toString()).doesNotContain("coverMimeType");
        });
    }

    private void assertParsed(String code, String messageType, String json, int rowCount) throws Exception {
        ParsedUdpPayload parsed = parse(code, messageType, json);
        assertThat(parsed.record().getParseStatus()).isEqualTo("PARSED");
        assertThat(parsed.businessRows()).hasSize(rowCount);
    }

    private ParsedUdpPayload parse(String code, String messageType, String json) throws Exception {
        return parser.parse(
                config(code, messageType),
                objectMapper.readTree(json),
                json,
                receivedAt,
                "127.0.0.1",
                51000
        );
    }

    private DataTypeConfig config(String code, String messageType) {
        DataTypeConfig config = new DataTypeConfig();
        config.setCode(code);
        config.setName(code);
        config.setMessageType(messageType);
        config.setUdpPort(8090);
        config.setSourceSystemCode("SIMULATOR");
        config.setSourceDeviceCode(switch (code) {
            case "QAR" -> "SIM-QAR";
            case "SMART_WINDOW_STATUS" -> "SIM-WINDOW";
            case "IFE_633_BEHAVIOR" -> "SIM-IFE-633";
            case "IFE_COCKRELL_BEHAVIOR" -> "SIM-IFE-COCKRELL";
            default -> "SIM-GROUND";
        });
        return config;
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

    private String groundTaskJson() {
        return """
                {
                  "messageType": "ground.task",
                  "sentAt": "2026-07-04T12:00:00+08:00",
                  "payload": {
                    "taskId": "CA4732-FLIGHT-20260704-001",
                    "flightNo": "CA4732",
                    "scenarioName": "北京 -> 上海 巡航模拟",
                    "status": "running",
                    "phase": "cruise",
                    "terminalCount": 320,
                    "startedAt": "2026-07-04T10:00:00+08:00",
                    "endedAt": null,
                    "downlinkTargetMbps": 600.0,
                    "statisticsWindowSeconds": 5,
                    "totalBytes": 9000000000,
                    "failureReason": null,
                    "rerunSourceTaskId": null,
                    "archived": false
                  }
                }
                """;
    }

    private String trafficJson(boolean withBadSecondItem) {
        String secondItem = withBadSecondItem
                ? """
                    {
                      "windowStart": "2026-07-04T12:00:00+08:00",
                      "windowEnd": "2026-07-04T12:00:00+08:00",
                      "taskId": "CA4732-FLIGHT-20260704-001",
                      "terminalId": "TERM-002"
                    }
                  """
                : """
                    {
                      "windowStart": "2026-07-04T12:00:00+08:00",
                      "windowEnd": "2026-07-04T12:00:00+08:00",
                      "taskId": "CA4732-FLIGHT-20260704-001",
                      "terminalId": "TERM-002",
                      "displayTerminalId": "D-002",
                      "seatLabel": "12B",
                      "application": "音乐",
                      "protocol": "UDP",
                      "direction": "downlink",
                      "bytesCount": 2048,
                      "packetCount": 24,
                      "throughputMbps": 1.5,
                      "peakMbps": 2.0,
                      "recordStatus": "recorded"
                    }
                  """;
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
                    },
                    %s
                  ]
                }
                """.formatted(secondItem);
    }

    private String sessionJson() {
        return """
                {
                  "messageType": "ground.session_summary",
                  "sentAt": "2026-07-04T12:00:00+08:00",
                  "items": [
                    {
                      "sessionId": "SES-000001",
                      "taskId": "CA4732-FLIGHT-20260704-001",
                      "terminalId": "TERM-001",
                      "displayTerminalId": "D-001",
                      "seatLabel": "12A",
                      "application": "网页浏览",
                      "protocol": "TCP",
                      "startedAt": "2026-07-04T10:00:00+08:00",
                      "durationSeconds": 7200,
                      "uplinkBytes": 1000,
                      "downlinkBytes": 2000,
                      "averageThroughputMbps": 3.2,
                      "peakThroughputMbps": 4.8,
                      "status": "active"
                    }
                  ]
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
                    },
                    {
                      "windowId": 2,
                      "zoneId": 1,
                      "brightnessLevel": 6,
                      "connectStatus": true,
                      "status": "TEST",
                      "timestamp": "2026-07-04 12:00:00.123"
                    }
                  ]
                }
                """;
    }

    private String ife633Json() {
        return """
                {
                  "messageType": "ife_633.behavior",
                  "sentAt": "2026-07-04T12:00:00+08:00",
                  "items": [
                    {
                      "sysInfo": {"timestamp": "2026-07-04 12:00:00.123", "flightId": "CA4732"},
                      "paxInfo": {
                        "pnr": "ABC123",
                        "seatNo": "12A",
                        "cabinClass": "ECONOMY",
                        "deviceId": "DEV-001",
                        "userId": "PAX-001"
                      },
                      "behaviorInfo": {
                        "behaviorType": "MOVIE_PLAY",
                        "contentId": "MOV-001",
                        "contentName": "星海远航"
                      },
                      "extInfo": {}
                    }
                  ]
                }
                """;
    }

    private String ifeCockrellJson() {
        return """
                {
                  "messageType": "ife_cockrell.behavior",
                  "sentAt": "2026-07-04T12:00:00+08:00",
                  "items": [
                    {
                      "sysInfo": {"timestamp": "2026-07-04 12:00:00.123", "flightId": "CA4732"},
                      "paxInfo": {
                        "pnr": "ABC123",
                        "seatNo": "12A",
                        "cabinClass": "ECONOMY",
                        "deviceId": "DEV-001",
                        "userId": "PAX-001"
                      },
                      "behaviorInfo": {
                        "behaviorType": "SHOPPING",
                        "orderList": [
                          {
                            "orderId": "ORDER-001",
                            "goodsList": [
                              {
                                "goodsId": "GOODS-001",
                                "coverBase64": "Zm9v",
                                "coverMimeType": "image/png"
                              }
                            ]
                          }
                        ]
                      },
                      "extInfo": {}
                    }
                  ]
                }
                """;
    }
}


