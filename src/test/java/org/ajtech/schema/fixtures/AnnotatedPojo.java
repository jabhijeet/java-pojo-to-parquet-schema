package org.ajtech.schema.fixtures;

import org.ajtech.schema.annotation.SchemaDecimal;
import org.ajtech.schema.annotation.SchemaField;
import org.ajtech.schema.annotation.SchemaField.Nullability;
import org.ajtech.schema.annotation.SchemaIgnore;

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
