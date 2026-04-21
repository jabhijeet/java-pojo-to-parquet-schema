package io.github.jabhijeet.schema.fixtures;

import io.github.jabhijeet.schema.annotation.SchemaField;

public class FlattenRenamedIntermediate {
    @SchemaField(name = "alias")
    public FlattenInner inner;
}
