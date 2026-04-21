package io.github.jabhijeet.schema.fixtures;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public class TemporalTypesPojo {
    public LocalDate localDate;
    public java.sql.Date sqlDate;
    public LocalTime localTime;
    public LocalDateTime localDateTime;
    public Instant instant;
    public OffsetDateTime offsetDateTime;
    public ZonedDateTime zonedDateTime;
    public java.util.Date utilDate;
    public java.sql.Timestamp sqlTimestamp;
}

