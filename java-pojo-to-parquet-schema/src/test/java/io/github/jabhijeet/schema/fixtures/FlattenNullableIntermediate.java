package io.github.jabhijeet.schema.fixtures;

import io.github.jabhijeet.schema.annotation.SchemaField;

public class FlattenNullableIntermediate {
    @SchemaField(nullability = SchemaField.Nullability.NULLABLE)
    public FlattenRequiredLeaf inner;
}
