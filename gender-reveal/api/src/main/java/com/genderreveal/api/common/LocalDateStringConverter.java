package com.genderreveal.api.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Persists {@link LocalDate} columns as an ISO-8601 {@code yyyy-MM-dd} string instead of
 * Hibernate's default binding, which the SQLite JDBC driver (xerial) stores as an
 * epoch-millisecond number by default — silently corrupting round-trips through a real
 * (non-cached) read, since the column is declared {@code TEXT} and later parsing expects
 * a date string, not a millisecond count.
 */
@Converter
public class LocalDateStringConverter implements AttributeConverter<LocalDate, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : FORMATTER.format(attribute);
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LocalDate.parse(dbData, FORMATTER);
    }
}
