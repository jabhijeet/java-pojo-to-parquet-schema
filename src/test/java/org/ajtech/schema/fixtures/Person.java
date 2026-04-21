package org.ajtech.schema.fixtures;

import org.ajtech.schema.annotation.SchemaDecimal;
import org.ajtech.schema.annotation.SchemaField;
import org.ajtech.schema.annotation.SchemaIgnore;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Person {
    public UUID id;
    public String name;
    public int age;
    public long createdAtMs;
    public double score;
    public boolean active;
    public LocalDate dob;
    public Instant updatedAt;
    public Color favouriteColor;

    @SchemaDecimal(precision = 12, scale = 2)
    public BigDecimal balance;

    public Address primaryAddress;
    public List<Address> addresses;
    public Map<String, String> tags;
    public Optional<String> nickname;

    @SchemaField(name = "email_address", doc = "Contact email")
    public String email;

    @SchemaIgnore
    public String internalNote;

    public transient String cachedDisplay;
}
