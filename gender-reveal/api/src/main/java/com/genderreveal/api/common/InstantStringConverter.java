package com.genderreveal.api.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;

/**
 * Persists {@link Instant} columns as their ISO-8601 string representation
 * (e.g. {@code 2026-09-21T00:00:00Z}) instead of Hibernate's default epoch-millisecond
 * binding, so the underlying SQLite {@code TEXT} column stays comparable/sortable at
 * the SQL level and consistent with other TEXT-stored temporal columns (e.g. due_date).
 */
@Converter
public class InstantStringConverter implements AttributeConverter<Instant, String> {

    @Override
    public String convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public Instant convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Instant.parse(dbData);
    }
}
