package io.github.jabhijeet.schema.fixtures;

import io.github.jabhijeet.schema.annotation.SchemaDecimal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

public class NumericLogicalPojo {
    public UUID id;

    @SchemaDecimal(precision = 10, scale = 2)
    public BigDecimal price;

    public BigDecimal defaultDecimal;

    public BigInteger bigInt;
}

