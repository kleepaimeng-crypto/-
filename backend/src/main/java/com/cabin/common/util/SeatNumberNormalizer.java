package com.cabin.common.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SeatNumberNormalizer {
    private static final Pattern LETTER_FIRST = Pattern.compile("^([A-Z])([0-9]{1,3})$");
    private static final Pattern NUMBER_FIRST = Pattern.compile("^([0-9]{1,3})([A-Z])$");

    private SeatNumberNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String seatNo = value.trim().toUpperCase(Locale.ROOT);
        Matcher letterFirst = LETTER_FIRST.matcher(seatNo);
        if (letterFirst.matches()) {
            return letterFirst.group(1) + letterFirst.group(2);
        }
        Matcher numberFirst = NUMBER_FIRST.matcher(seatNo);
        if (numberFirst.matches()) {
            return numberFirst.group(2) + numberFirst.group(1);
        }
        return seatNo;
    }
}
