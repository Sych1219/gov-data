package com.gov.app.util;

import java.time.LocalDateTime;

public final class DateUtils {

    private DateUtils() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}

