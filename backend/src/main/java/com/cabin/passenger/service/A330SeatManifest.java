package com.cabin.passenger.service;

import java.util.ArrayList;
import java.util.List;

final class A330SeatManifest {
    private static final List<Seat> SEATS = buildSeats();

    private A330SeatManifest() {
    }

    static List<Seat> seats() {
        return SEATS;
    }

    private static List<Seat> buildSeats() {
        List<Seat> seats = new ArrayList<>(237);
        for (int row = 11; row <= 15; row++) {
            addRow(seats, row, "ACDHJL", "BUSINESS");
        }
        addRow(seats, 31, "ACDEHJL", "ECONOMY");
        for (int row = 32; row <= 53; row++) {
            addRow(seats, row, "ACDEFHJL", "ECONOMY");
        }
        for (int row = 54; row <= 56; row++) {
            addRow(seats, row, "ACDEHJL", "ECONOMY");
        }
        addRow(seats, 57, "DEH", "ECONOMY");
        if (seats.size() != 237) {
            throw new IllegalStateException("A330-200 seat manifest must contain 237 seats");
        }
        return List.copyOf(seats);
    }

    private static void addRow(List<Seat> seats, int row, String letters, String cabinClass) {
        letters.chars().forEach(letter -> seats.add(new Seat((char) letter + Integer.toString(row), cabinClass)));
    }

    record Seat(String seatNo, String cabinClass) {
    }
}
