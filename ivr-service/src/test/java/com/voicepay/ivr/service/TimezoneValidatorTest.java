package com.voicepay.ivr.service;

import com.voicepay.ivr.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class TimezoneValidatorTest {

    private TimezoneValidator timezoneValidator;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getScheduler().getCommercialHours().setStart("09:00");
        properties.getScheduler().getCommercialHours().setEnd("21:00");
        properties.getScheduler().setAllowedDaysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY");
        properties.getScheduler().setTimezoneFallback("Europe/Madrid");
        
        timezoneValidator = new TimezoneValidator(properties);
    }

    @Test
    void testIsWithinCommercialHours_ExtremeTimezones() {
        // Test two timezones that are 25 hours apart. At any time, at least one of them must be outside 9 AM - 9 PM.
        boolean resultMidway = timezoneValidator.isWithinCommercialHours("Pacific/Midway");
        boolean resultKiritimati = timezoneValidator.isWithinCommercialHours("Pacific/Kiritimati");
        
        assertFalse(resultMidway && resultKiritimati, "At least one of the extreme timezones must be outside legal hours");
    }

    @Test
    void testIsWithinCommercialHours_FallbackOnInvalidTimezone() {
        // Verify it fallback-validates without exception.
        timezoneValidator.isWithinCommercialHours("Invalid/Timezone");
    }
}
