package io.github.jabhijeet.schema.fixtures;

import io.github.jabhijeet.schema.annotation.SchemaDecimal;
import io.github.jabhijeet.schema.annotation.SchemaField;
import io.github.jabhijeet.schema.annotation.SchemaField.Nullability;
import io.github.jabhijeet.schema.annotation.SchemaIgnore;

import java.math.BigDecimal;

public class AnnotatedPojo {

    @SchemaField(name = "user_name", doc = "Username in the external system")
    public String username;

    @SchemaField(nullability = Nullability.REQUIRED)
    public String tenantId;

    @SchemaField(nullability = Nullability.NULLABLE)
    public Integer retryCount;

    @SchemaIgnore
    public String secret;

    @SchemaDecimal(precision = 18, scale = 4)
    public BigDecimal amount;
}

