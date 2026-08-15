public class AvroSchemaBuilderTest {
    @org.junit.jupiter.api.Test
    public void testBuildSchema() {
        // Basic test to ensure schema creation works
        Schema schema = new AvroSchemaBuilder(new SchemaOptions()).build(MyPojo.class);
        assertNotNull(schema);
    }
}

// Add additional test cases as needed