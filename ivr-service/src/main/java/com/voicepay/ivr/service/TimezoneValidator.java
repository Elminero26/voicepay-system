package com.voicepay.ivr.service;
import com.voicepay.ivr.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimezoneValidator {

    private final AppProperties appProperties;

    public boolean isWithinCommercialHours(String timezoneStr) {
        String timezoneFallback = appProperties.getScheduler().getTimezoneFallback();
        String tz = (timezoneStr == null || timezoneStr.trim().isEmpty()) ? timezoneFallback : timezoneStr.trim();
        
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(tz);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}'. Falling back to default: {}", tz, timezoneFallback);
            try {
                zoneId = ZoneId.of(timezoneFallback);
            } catch (Exception ex) {
                zoneId = ZoneId.of("Europe/Madrid");
            }
        }

        ZonedDateTime localTimeNow = ZonedDateTime.now(zoneId);
        
        // 1. Day of week validation
        DayOfWeek currentDay = localTimeNow.getDayOfWeek();
        List<DayOfWeek> allowedDays;
        String allowedDaysConfig = appProperties.getScheduler().getAllowedDaysOfWeek();
        try {
            allowedDays = Arrays.stream(allowedDaysConfig.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .map(DayOfWeek::valueOf)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to parse allowed days config '{}'. Using Monday-Friday.", allowedDaysConfig);
            allowedDays = Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        
        if (!allowedDays.contains(currentDay)) {
            log.info("Call disallowed: current day '{}' in timezone '{}' is not in allowed days", currentDay, zoneId);
            return false;
        }

        // 2. Time window validation
        LocalTime currentTime = localTimeNow.toLocalTime();
        LocalTime startTime = LocalTime.parse(appProperties.getScheduler().getCommercialHours().getStart());
        LocalTime endTime = LocalTime.parse(appProperties.getScheduler().getCommercialHours().getEnd());

        if (currentTime.isBefore(startTime) || currentTime.isAfter(endTime)) {
            log.info("Call disallowed: current local time '{}' in timezone '{}' is outside commercial window [{} - {}]", 
                    currentTime, zoneId, startTime, endTime);
            return false;
        }

        log.debug("Call allowed: current local time '{}' in timezone '{}' matches rules", currentTime, zoneId);
        return true;
    }
}
