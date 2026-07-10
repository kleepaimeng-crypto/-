package com.cabin.udp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cabin.config.UdpProperties;
import com.cabin.udp.dto.CurrentFlightContext;
import com.cabin.udp.entity.DataRecord;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CurrentFlightContextServiceTests {
    private final CurrentFlightContextService service =
            new CurrentFlightContextService(new UdpProperties(false, 0, 0, null, null, null, null));

    @Test
    void qarRecordUpdatesCompleteContext() {
        DataRecord record = record("QAR", "CA4732", "ZBAA", "ZSPD", "CA");

        CurrentFlightContext context = service.updateFrom(record);

        assertThat(context).isNotNull();
        assertThat(context.hasRoute()).isTrue();
        assertThat(context.flightNo()).isEqualTo("CA4732");
        assertThat(context.origin()).isEqualTo("ZBAA");
        assertThat(context.destination()).isEqualTo("ZSPD");
        assertThat(context.airlineCode()).isEqualTo("CA");
    }

    @Test
    void appliesCompleteContextToSmartWindowRecord() {
        service.updateFrom(record("QAR", "CA4732", "ZBAA", "ZSPD", "CA"));
        DataRecord smartWindow = record("SMART_WINDOW_STATUS", null, null, null, "CA");

        service.applyTo(smartWindow);

        assertThat(smartWindow.getFlightNo()).isEqualTo("CA4732");
        assertThat(smartWindow.getOrigin()).isEqualTo("ZBAA");
        assertThat(smartWindow.getDestination()).isEqualTo("ZSPD");
    }

    @Test
    void appliesRouteOnlyWhenFlightMatchesOrIsMissing() {
        service.updateFrom(record("QAR", "CA4732", "ZBAA", "ZSPD", "CA"));
        DataRecord otherFlight = record("GROUND_TASK", "CA9999", null, null, "CA");

        service.applyTo(otherFlight);

        assertThat(otherFlight.getFlightNo()).isEqualTo("CA9999");
        assertThat(otherFlight.getOrigin()).isNull();
        assertThat(otherFlight.getDestination()).isNull();
    }

    @Test
    void noContextDoesNotInventFlightOrRoute() {
        DataRecord smartWindow = record("SMART_WINDOW_STATUS", null, null, null, "CA");

        service.applyTo(smartWindow);

        assertThat(smartWindow.getFlightNo()).isNull();
        assertThat(smartWindow.getOrigin()).isNull();
        assertThat(smartWindow.getDestination()).isNull();
        assertThat(smartWindow.getAirlineCode()).isEqualTo("CA");
    }

    @Test
    void airlineOnlyRecordDoesNotCreateContext() {
        DataRecord record = record("SMART_WINDOW_STATUS", null, null, null, "CA");

        CurrentFlightContext context = service.updateFrom(record);

        assertThat(context).isNull();
        assertThat(service.current()).isNull();
    }

    private DataRecord record(
            String dataTypeCode,
            String flightNo,
            String origin,
            String destination,
            String airlineCode
    ) {
        DataRecord record = new DataRecord();
        record.setDataTypeCode(dataTypeCode);
        record.setFlightNo(flightNo);
        record.setOrigin(origin);
        record.setDestination(destination);
        record.setAirlineCode(airlineCode);
        record.setReceivedAt(OffsetDateTime.parse("2026-07-04T12:00:00+08:00"));
        return record;
    }
}
