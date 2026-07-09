CREATE INDEX IF NOT EXISTS idx_ife_633_flight_passenger_event
    ON ife_633_behavior (flight_no, passenger_id, event_at DESC);

CREATE INDEX IF NOT EXISTS idx_ife_cockrell_flight_passenger_event
    ON ife_cockrell_behavior (flight_no, passenger_id, event_at DESC);

CREATE INDEX IF NOT EXISTS idx_traffic_task_seat_window
    ON traffic_record (task_id, seat_label, window_end DESC)
    WHERE seat_label IS NOT NULL;
