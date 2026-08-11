package com.cabin.passenger.service;

import java.util.ArrayList;
import java.util.List;

final class C929SeatManifest {
    private static final List<Seat> SEATS = buildSeats();

    private C929SeatManifest() {
    }

    static List<Seat> seats() {
        return SEATS;
    }

    private static List<Seat> buildSeats() {
        List<Seat> seats = new ArrayList<>(282);
        for (int row = 11; row <= 12; row++) {
            addRow(seats, row, "ADGK", "BUSINESS");
        }
        for (int row = 13; row <= 17; row++) {
            addRow(seats, row, "ACDGHK", "BUSINESS");
        }
        for (int row = 31; row <= 43; row++) {
            addRow(seats, row, "ABCDEFGHK", "ECONOMY");
        }
        addRow(seats, 44, "DEF", "ECONOMY");
        addRow(seats, 45, "ACDEFHK", "ECONOMY");
        for (int row = 46; row <= 58; row++) {
            addRow(seats, row, "ABCDEFGHK", "ECONOMY");
        }
        if (seats.size() != 282) {
            throw new IllegalStateException("C929-700 seat manifest must contain 282 seats");
        }
        return List.copyOf(seats);
    }

    private static void addRow(List<Seat> seats, int row, String letters, String cabinClass) {
        letters.chars().forEach(letter -> seats.add(new Seat((char) letter + Integer.toString(row), cabinClass)));
    }

    record Seat(String seatNo, String cabinClass) {
    }
}
