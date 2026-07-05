package com.cabin.udp.service;

import com.cabin.config.UdpProperties;
import com.cabin.udp.dto.CurrentFlightContext;
import com.cabin.udp.entity.DataRecord;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CurrentFlightContextService {
    private static final Logger log = LoggerFactory.getLogger(CurrentFlightContextService.class);

    private final AtomicReference<CurrentFlightContext> currentContext = new AtomicReference<>();
    private final OffsetDateTime startedAt;

    public CurrentFlightContextService(UdpProperties properties) {
        this.startedAt = OffsetDateTime.now(properties.zone());
    }

    public OffsetDateTime startedAt() {
        return startedAt;
    }

    public DataRecord applyTo(DataRecord record) {
        CurrentFlightContext context = currentContext.get();
        if (context == null) {
            enrichAirline(record);
            return record;
        }

        if (isBlank(record.getFlightNo()) && context.hasRoute()) {
            record.setFlightNo(context.flightNo());
        }
        if (context.hasRoute() && sameFlightOrMissing(record.getFlightNo(), context.flightNo())) {
            if (isBlank(record.getOrigin())) {
                record.setOrigin(context.origin());
            }
            if (isBlank(record.getDestination())) {
                record.setDestination(context.destination());
            }
        }
        if (isBlank(record.getAirlineCode()) && !isBlank(context.airlineCode())) {
            record.setAirlineCode(context.airlineCode());
        }
        enrichAirline(record);
        return record;
    }

    public CurrentFlightContext updateFrom(DataRecord record) {
        CurrentFlightContext candidate = candidateFrom(record);
        if (candidate == null) {
            return null;
        }

        CurrentFlightContext before;
        CurrentFlightContext after;
        do {
            before = currentContext.get();
            after = merge(before, candidate);
            if (Objects.equals(before, after)) {
                return null;
            }
        } while (!currentContext.compareAndSet(before, after));

        if (after.hasRoute() && !sameRoute(before, after)) {
            log.info("Current flight context updated: {} {} -> {}",
                    after.flightNo(),
                    after.origin(),
                    after.destination());
        }
        return after;
    }

    public CurrentFlightContext current() {
        return currentContext.get();
    }

    private CurrentFlightContext merge(CurrentFlightContext before, CurrentFlightContext candidate) {
        if (candidate.hasRoute()) {
            return candidate;
        }
        if (before == null) {
            return candidate.hasFlightNo() ? candidate : null;
        }
        if (before.hasRoute()) {
            if (candidate.hasFlightNo() && !sameFlightOrMissing(candidate.flightNo(), before.flightNo())) {
                return candidate;
            }
            return before;
        }
        if (candidate.hasFlightNo()) {
            return candidate;
        }
        return before;
    }

    private CurrentFlightContext candidateFrom(DataRecord record) {
        String flightNo = trimToNull(record.getFlightNo());
        String origin = trimToNull(record.getOrigin());
        String destination = trimToNull(record.getDestination());
        String airlineCode = trimToNull(record.getAirlineCode());
        if (isBlank(airlineCode) && !isBlank(flightNo) && flightNo.length() >= 2) {
            airlineCode = flightNo.substring(0, 2).toUpperCase(Locale.ROOT);
        }
        if (isBlank(flightNo) && isBlank(origin) && isBlank(destination)) {
            return null;
        }
        return new CurrentFlightContext(flightNo, origin, destination, airlineCode, record.getReceivedAt());
    }

    private boolean sameRoute(CurrentFlightContext left, CurrentFlightContext right) {
        return left != null
                && Objects.equals(left.flightNo(), right.flightNo())
                && Objects.equals(left.origin(), right.origin())
                && Objects.equals(left.destination(), right.destination());
    }

    private boolean sameFlightOrMissing(String left, String right) {
        return isBlank(left) || isBlank(right) || left.equalsIgnoreCase(right);
    }

    private void enrichAirline(DataRecord record) {
        String flightNo = record.getFlightNo();
        if (isBlank(record.getAirlineCode()) && !isBlank(flightNo) && flightNo.length() >= 2
                && Character.isLetter(flightNo.charAt(0))
                && Character.isLetter(flightNo.charAt(1))) {
            record.setAirlineCode(flightNo.substring(0, 2).toUpperCase(Locale.ROOT));
        }
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
