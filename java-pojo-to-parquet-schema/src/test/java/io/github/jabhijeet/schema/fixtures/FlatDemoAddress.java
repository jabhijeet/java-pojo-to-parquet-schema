package io.github.jabhijeet.schema.fixtures;

import io.github.jabhijeet.schema.annotation.SchemaField;

public class FlatDemoAddress {
    public String street;
    public String city;
    public String state;
    /** Renamed via @SchemaField — flattens to homeAddress_zip, not homeAddress_zipCode. */
    @SchemaField(name = "zip")
    public String zipCode;
    public String country;
}
