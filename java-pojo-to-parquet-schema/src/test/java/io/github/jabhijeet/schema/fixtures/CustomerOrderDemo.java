package io.github.jabhijeet.schema.fixtures;

import io.github.jabhijeet.schema.annotation.SchemaDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CustomerOrderDemo {
    public UUID orderId;
    public String customerName;
    public String customerEmail;

    @SchemaDecimal(precision = 12, scale = 2)
    public BigDecimal totalAmount;

    public Instant placedAt;
    public LocalDate expectedDelivery;

    public Address shippingAddress;
    public Address billingAddress;

    public List<OrderItemDemo> items;

    public Map<String, String> tags;
    public Map<String, List<String>> metadata;
}
