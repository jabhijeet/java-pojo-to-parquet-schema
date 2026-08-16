package io.github.jabhijeet.schema.avro;

import io.github.jabhijeet.schema.SchemaOptions;
import io.github.jabhijeet.schema.fixtures.Person;
import org.apache.avro.Schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AvroSchemaBuilderTest {
    @org.junit.jupiter.api.Test
    void testBuildSchema() {
        // Basic test to ensure schema creation works
        Schema schema = new AvroSchemaBuilder(SchemaOptions.defaults()).build(Person.class);
        assertNotNull(schema);
    }
}
