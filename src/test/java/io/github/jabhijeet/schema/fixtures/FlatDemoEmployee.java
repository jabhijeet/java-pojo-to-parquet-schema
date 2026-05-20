package io.github.jabhijeet.schema.fixtures;

import io.github.jabhijeet.schema.annotation.SchemaDecimal;
import io.github.jabhijeet.schema.annotation.SchemaField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Top-level POJO used by {@code FlattenJsonVisualDemoTest} to demonstrate flattening
 * of complex, deeply-nested objects into a single-level Avro/Parquet record.
 *
 * <p>Flatten behaviour showcased:
 * <ul>
 *   <li>{@code homeAddress} (2-level): street/city/state/zip/country lifted to top level.</li>
 *   <li>{@code dept} ({@code department} renamed via {@link SchemaField}; 3-level with nested
 *       {@code location}): code/name/location_building/location_floor lifted.</li>
 *   <li>{@code manager} (nullable intermediate): propagates nullability to manager_name/title.</li>
 *   <li>{@code projects} ({@code List}): flatten stops — elements remain as a nested record.</li>
 *   <li>{@code attributes} ({@code Map}): flatten stops — map value type is preserved.</li>
 * </ul>
 */
public class FlatDemoEmployee {
    public String employeeId;
    public String fullName;
    public String email;

    @SchemaDecimal(precision = 12, scale = 2)
    public BigDecimal salary;

    public LocalDate hireDate;

    /** Two-level flatten; zipCode inside is renamed to "zip" via @SchemaField. */
    public FlatDemoAddress homeAddress;

    /**
     * Renamed to "dept" — flattened leaves become dept_code, dept_name,
     * dept_location_building, dept_location_floor (three levels).
     */
    @SchemaField(name = "dept")
    public FlatDemoDepartment department;

    /** Nullable intermediate — manager_name and manager_title inherit nullability. */
    public FlatDemoManager manager;

    /** List boundary — flatten stops here; elements remain as nested FlatDemoProject records. */
    public List<FlatDemoProject> projects;

    /** Map boundary — flatten stops here; value type is preserved as String. */
    public Map<String, String> attributes;
}
