package io.github.jabhijeet.schema.fixtures;

public class FlatDemoDepartment {
    public String code;
    public String name;
    /** Three-level nest: dept → location → building/floor. */
    public FlatDemoLocation location;
}
