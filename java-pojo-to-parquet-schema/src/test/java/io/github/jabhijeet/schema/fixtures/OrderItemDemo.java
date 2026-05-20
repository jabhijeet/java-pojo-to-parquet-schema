package io.github.jabhijeet.schema.fixtures;

import io.github.jabhijeet.schema.annotation.SchemaDecimal;

import java.math.BigDecimal;

public class OrderItemDemo {
    public String sku;
    public String productName;
    public int quantity;

    @SchemaDecimal(precision = 10, scale = 2)
    public BigDecimal unitPrice;
}
