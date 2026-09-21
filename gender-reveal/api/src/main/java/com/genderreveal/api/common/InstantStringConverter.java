package com.genderreveal.api.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Persists {@link Instant} columns as a FIXED-WIDTH ISO-8601 string representation
 * (e.g. {@code 2026-09-21T00:00:00.000000Z}, always six fractional digits) instead of
 * Hibernate's default epoch-millisecond binding, so the underlying SQLite {@code TEXT}
 * column stays comparable/sortable at the SQL level and consistent with other
 * TEXT-stored temporal columns (e.g. due_date).
 *
 * <p>The fixed width matters: {@link Instant#toString()} emits a variable number of
 * fractional-second digits (0, 3, 6, or 9) depending on the value, and lexicographic
 * string ordering does not agree with chronological ordering across mixed widths
 * (e.g. {@code "...:00Z"} vs {@code "...:00.500Z"}). Always emitting six digits keeps
 * string order and chronological order in agreement.
 */
@Converter
public class InstantStringConverter implements AttributeConverter<Instant, String> {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    @Override
    public String convertToDatabaseColumn(Instant attribute) {
        if (attribute == null) {
            return null;
        }
        return FORMATTER.format(attribute.truncatedTo(ChronoUnit.MICROS));
    }

    @Override
    public Instant convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return Instant.from(FORMATTER.parse(dbData));
    }
}
