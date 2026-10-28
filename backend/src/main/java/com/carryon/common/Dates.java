package com.carryon.common;

import java.time.LocalDate;
import java.time.ZoneId;

/** "Today" for business rules is the traveller's calendar day (India), not the server's zone (UTC on most hosts). */
public final class Dates {
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private Dates() {}

    public static LocalDate today() { return LocalDate.now(ZONE); }
}
