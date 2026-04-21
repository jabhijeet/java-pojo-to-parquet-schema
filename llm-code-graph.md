# CODE_GRAPH
MISSION: COMPACT PROJECT MAP FOR LLM AGENTS.
PROTOCOL: Follow llm-agent-rules.md
MEMORY: See llm-agent-project-learnings.md

> Legend: * core, (↑out ↓in deps), s: symbols, d: desc

- src/main/java/org/ajtech/schema/annotation/SchemaDecimal.java (0↑ 0↓) | d: Declares precision and scale for a {@link java.math.BigDecimal} field. <p>Avro r
  - s: [@interfac SchemaDecimal [<p>Avro requires both values to be known at schema-generation time. Without this annotation the generator falls back to the defaults configured on {@code SchemaOptions}. /], SchemaDecimal [<p>Avro requires both values to be known at schema-generation time. Without this annotation the generator falls back to the defaults configured on {@code SchemaOptions}. /]]
- src/main/java/org/ajtech/schema/annotation/SchemaField.java (0↑ 0↓) | d: Customises how a POJO field appears in the generated schema. <p>Field name, docu
  - s: [@interfac SchemaField [{@link Nullability#AUTO} defers to the generator's default rule: primitives are required, reference types follow {@code SchemaOptions.nullableByDefault()}. /], Nullability, SchemaField [{@link Nullability#AUTO} defers to the generator's default rule: primitives are required, reference types follow {@code SchemaOptions.nullableByDefault()}. /]]
- src/main/java/org/ajtech/schema/annotation/SchemaIgnore.java (0↑ 0↓) | d: Excludes a field from the generated schema.
  - s: [@interfac SchemaIgnore [Excludes a field from the generated schema.], SchemaIgnore [Excludes a field from the generated schema.]]
- src/main/java/org/ajtech/schema/avro/AvroSchemaBuilder.java (0↑ 0↓) | d: Contains 13 symbols.
  - s: [AvroSchemaBuilder [(they maintain a record cache for shared/self-referential types). Use a new instance per call — or the facade {@code PojoSchemaGenerator}, which does this. /], build [(Class<?> pojoClass)], buildRecord [(Class<?> type)], collectFields [(Class<?> type)], decimalSchema [(Field context)], determineNullability [(Field field, SchemaField ann, boolean optionalWrapped)], enumSchema [(Class<?> enumType)], fieldLabel [(Field f)], instanceof [Class<?> raw)], makeNullable [(Schema schema)], mapClass [(Class<?> cls, Field context)], mapType [(Type type, Field context)], unwrapOptional [(Type genericType)]]
- src/main/java/org/ajtech/schema/parquet/ParquetSchemaBuilder.java (0↑ 0↓) | d: Produces a Parquet {@link MessageType} by first generating an Avro {@link Schema
  - s: [ParquetSchemaBuilder [finite tree). They are detected before conversion and surface as a {@link SchemaGenerationException} rather than a native {@code StackOverflowError}. /], build [(Class<?> pojoClass)], detectCycle [(Schema schema, Set<String> stack, Class<?> rootType)]]
- src/main/java/org/ajtech/schema/PojoSchemaGenerator.java (0↑ 0↓) | d: Entry point for converting Java POJOs into Avro and Parquet schemas. <p>Each cal
  - s: [Builder [builder()], PojoSchemaGenerator [.namespace("com.example.schemas") .nullableByDefault(false) .build(); Schema schema = gen.generateAvro(Person.class); }</pre> /], build [()], builder [()], defaultDecimal [(int precision, int scale)], generateAvro [(Class<?> pojoClass)], generateAvroJson [(Class<?> pojoClass, boolean pretty)], generateParquet [(Class<?> pojoClass)], generateParquetString [(Class<?> pojoClass)], namespace [(String namespace)], nullableByDefault [(boolean nullable)], toAvro [(Class<?> pojoClass)], toParquet [(Class<?> pojoClass)]]
- src/main/java/org/ajtech/schema/SchemaGenerationException.java (0↑ 0↓) | d: Thrown when a POJO cannot be mapped to a schema.
  - s: [SchemaGenerationException [Thrown when a POJO cannot be mapped to a schema.]]
- src/main/java/org/ajtech/schema/SchemaOptions.java (0↑ 0↓) | d: Immutable configuration for {@link PojoSchemaGenerator}. <p>Instances are create
  - s: [@Override equals [(Object o)], @Override hashCode [()], Builder [builder()], SchemaOptions [Immutable configuration for {@link PojoSchemaGenerator}. <p>Instances are created through {@link #builder()} or obtained as {@link #defaults()}. /], build [()], builder [()], defaultDecimal [(int precision, int scale)], defaultDecimalPrecision, defaultDecimalScale, defaults [()], equals [(Object o)], hashCode [()], namespace [(String namespace)], namespaceOverride, nullableByDefault]
- src/test/java/org/ajtech/schema/fixtures/Address.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [Address]
- src/test/java/org/ajtech/schema/fixtures/AllPrimitivesPojo.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [AllPrimitivesPojo]
- src/test/java/org/ajtech/schema/fixtures/AnnotatedPojo.java (0↑ 0↓) | d: Contains 6 symbols.
  - s: [@SchemaDecimal(precision = 18, scale = 4) amount, @SchemaField(name = , doc = ) username, @SchemaField(nullability = Nullability.NULLABLE) retryCount, @SchemaField(nullability = Nullability.REQUIRED) tenantId, @SchemaIgnore secret, AnnotatedPojo]
- src/test/java/org/ajtech/schema/fixtures/ArrayTypesPojo.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [ArrayTypesPojo]
- src/test/java/org/ajtech/schema/fixtures/BasePojo.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [BasePojo]
- src/test/java/org/ajtech/schema/fixtures/CollectionsPojo.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [CollectionsPojo]
- src/test/java/org/ajtech/schema/fixtures/Color.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [Color]
- src/test/java/org/ajtech/schema/fixtures/DerivedPojo.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [DerivedPojo [extends BasePojo]]
- src/test/java/org/ajtech/schema/fixtures/Employee.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [Employee [extends Person]]
- src/test/java/org/ajtech/schema/fixtures/LeafPojo.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [LeafPojo]
- src/test/java/org/ajtech/schema/fixtures/MidPojo.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [MidPojo]
- src/test/java/org/ajtech/schema/fixtures/NestedCollectionsPojo.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [NestedCollectionsPojo]
- src/test/java/org/ajtech/schema/fixtures/Node.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [Node]
- src/test/java/org/ajtech/schema/fixtures/NonStringKeyMap.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [NonStringKeyMap]
- src/test/java/org/ajtech/schema/fixtures/NumericLogicalPojo.java (0↑ 0↓) | d: Contains 2 symbols.
  - s: [@SchemaDecimal(precision = 10, scale = 2) price, NumericLogicalPojo]
- src/test/java/org/ajtech/schema/fixtures/OuterPojo.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [OuterPojo]
- src/test/java/org/ajtech/schema/fixtures/Person.java (0↑ 0↓) | d: Contains 4 symbols.
  - s: [@SchemaDecimal(precision = 12, scale = 2) balance, @SchemaField(name = , doc = ) email, @SchemaIgnore internalNote, Person]
- src/test/java/org/ajtech/schema/fixtures/TemporalTypesPojo.java (0↑ 0↓) | d: Contains 1 symbols.
  - s: [TemporalTypesPojo]
- src/test/java/org/ajtech/schema/parquet/AnnotationBehaviourParquetTest.java (0↑ 0↓) | d: Contains 15 symbols.
  - s: [@Test nullability_nullable_on_boxed_type_is_optional [()], @Test nullability_required_on_reference_type_produces_required_repetition [()], @Test nullable_by_default_off_forces_reference_fields_to_required [()], @Test renamed_field_is_still_optional_by_default [()], @Test schema_decimal_precision_and_scale_flow_into_parquet [()], @Test schema_field_rename_changes_parquet_field_name [()], @Test schema_ignore_removes_the_field_entirely [()], AnnotationBehaviourParquetTest, nullability_nullable_on_boxed_type_is_optional [()], nullability_required_on_reference_type_produces_required_repetition [()], nullable_by_default_off_forces_reference_fields_to_required [()], renamed_field_is_still_optional_by_default [()], schema_decimal_precision_and_scale_flow_into_parquet [()], schema_field_rename_changes_parquet_field_name [()], schema_ignore_removes_the_field_entirely [()]]
- src/test/java/org/ajtech/schema/parquet/ArrayTypesParquetTest.java (0↑ 0↓) | d: Contains 15 symbols.
  - s: [@Test boolean_array_is_list_of_boolean [()], @Test byte_array_is_special_cased_to_binary_not_a_list [()], @Test double_array_is_list_of_double [()], @Test int_array_is_list_of_int32 [()], @Test long_array_is_list_of_int64 [()], @Test record_array_is_list_of_group_with_record_fields [()], @Test string_array_is_list_of_string [()], ArrayTypesParquetTest, boolean_array_is_list_of_boolean [()], byte_array_is_special_cased_to_binary_not_a_list [()], double_array_is_list_of_double [()], int_array_is_list_of_int32 [()], long_array_is_list_of_int64 [()], record_array_is_list_of_group_with_record_fields [()], string_array_is_list_of_string [()]]
- src/test/java/org/ajtech/schema/parquet/CollectionsParquetTest.java (0↑ 0↓) | d: Contains 19 symbols.
  - s: [@Test any_collection_subtype_maps_to_list_group [()], @Test list_of_list_preserves_both_levels [()], @Test list_of_map_keeps_list_outside_and_map_inside [()], @Test list_of_records_produces_group_inside_list_element [()], @Test list_of_string_is_list_group_of_binary_string [()], @Test map_of_list_keeps_map_outside_and_list_inside [()], @Test map_of_map_nests_correctly [()], @Test map_with_string_keys_is_map_group_with_string_key_and_long_value [()], @Test set_maps_to_list_group_just_like_list [()], CollectionsParquetTest, any_collection_subtype_maps_to_list_group [()], list_of_list_preserves_both_levels [()], list_of_map_keeps_list_outside_and_map_inside [()], list_of_records_produces_group_inside_list_element [()], list_of_string_is_list_group_of_binary_string [()], map_of_list_keeps_map_outside_and_list_inside [()], map_of_map_nests_correctly [()], map_with_string_keys_is_map_group_with_string_key_and_long_value [()], set_maps_to_list_group_just_like_list [()]]
- src/test/java/org/ajtech/schema/parquet/LogicalTypesParquetTest.java (0↑ 0↓) | d: Contains 21 symbols.
  - s: [@Test bigdecimal_with_annotation_uses_annotation_precision_and_scale [()], @Test bigdecimal_without_annotation_falls_back_to_options_defaults [()], @Test biginteger_becomes_string [()], @Test instant_becomes_int64_timestamp_adjusted_to_utc_in_millis [()], @Test local_date_becomes_int32_with_date_logical_type [()], @Test local_datetime_becomes_int64_timestamp_not_adjusted_to_utc [()], @Test local_time_becomes_int64_with_time_micros [()], @Test offset_datetime_zoned_datetime_util_date_and_sql_timestamp_are_all_utc_timestamps [()], @Test sql_date_also_becomes_int32_date [()], @Test uuid_has_a_logical_annotation [()], LogicalTypesParquetTest, bigdecimal_with_annotation_uses_annotation_precision_and_scale [()], bigdecimal_without_annotation_falls_back_to_options_defaults [()], biginteger_becomes_string [()], instant_becomes_int64_timestamp_adjusted_to_utc_in_millis [()], local_date_becomes_int32_with_date_logical_type [()], local_datetime_becomes_int64_timestamp_not_adjusted_to_utc [()], local_time_becomes_int64_with_time_micros [()], offset_datetime_zoned_datetime_util_date_and_sql_timestamp_are_all_utc_timestamps [()], sql_date_also_becomes_int32_date [()], uuid_has_a_logical_annotation [()]]
- src/test/java/org/ajtech/schema/parquet/NestedRecordsParquetTest.java (0↑ 0↓) | d: Contains 15 symbols.
  - s: [@Test inheritance_includes_fields_from_base_and_derived [()], @Test inherited_primitive_retains_required_repetition [()], @Test list_of_mids_inside_outer_descends_to_leaf_list [()], @Test list_of_records_inside_nested_record [()], @Test outer_contains_mid_group_with_its_fields [()], @Test self_referential_records_are_rejected_because_parquet_is_acyclic [()], @Test three_level_nesting_preserves_leaf_primitive_types [()], NestedRecordsParquetTest, inheritance_includes_fields_from_base_and_derived [()], inherited_primitive_retains_required_repetition [()], list_of_mids_inside_outer_descends_to_leaf_list [()], list_of_records_inside_nested_record [()], outer_contains_mid_group_with_its_fields [()], self_referential_records_are_rejected_because_parquet_is_acyclic [()], three_level_nesting_preserves_leaf_primitive_types [()]]
- src/test/java/org/ajtech/schema/parquet/ParquetTestSupport.java (0↑ 0↓) | d: Navigation helpers for Parquet MessageType trees.
  - s: [ParquetTestSupport [Navigation helpers for Parquet MessageType trees.], group [(GroupType parent, String name)], listElement [Element of a parquet-avro LIST group. The default encoding is 2-level, where the repeated field named {@code array} IS the element (primitive) or IS the element group (record). /], mapKey [Key of a MAP group: {@code <map> -> key_value -> key}.], mapValue [Value of a MAP group: {@code <map> -> key_value -> value}.], primitive [(GroupType group, String name)]]
- src/test/java/org/ajtech/schema/parquet/PrimitiveTypesParquetTest.java (0↑ 0↓) | d: Contains 17 symbols.
  - s: [@Test boolean_primitive_and_boxed_map_to_boolean [()], @Test byte_short_int_and_boxed_map_to_int32 [()], @Test char_primitive_and_boxed_map_to_binary_string [()], @Test double_primitive_and_boxed_map_to_double [()], @Test every_expected_field_is_present [()], @Test float_primitive_and_boxed_map_to_float [()], @Test long_primitive_and_boxed_map_to_int64 [()], @Test primitives_are_required_and_boxed_are_optional [()], PrimitiveTypesParquetTest, boolean_primitive_and_boxed_map_to_boolean [()], byte_short_int_and_boxed_map_to_int32 [()], char_primitive_and_boxed_map_to_binary_string [()], double_primitive_and_boxed_map_to_double [()], every_expected_field_is_present [()], float_primitive_and_boxed_map_to_float [()], long_primitive_and_boxed_map_to_int64 [()], primitives_are_required_and_boxed_are_optional [()]]
- src/test/java/org/ajtech/schema/PojoSchemaGeneratorTest.java (0↑ 0↓) | d: Contains 37 symbols.
  - s: [@Test bigdecimal_uses_schema_decimal_annotation [()], @Test date_and_instant_map_to_avro_logical_types [()], @Test enum_becomes_avro_enum [()], @Test enum_is_not_a_valid_top_level_target [()], @Test generates_record_with_namespace_and_name_from_package [()], @Test inheritance_includes_superclass_fields_with_superclass_first [()], @Test list_becomes_array_and_map_becomes_avro_map [()], @Test namespace_override_replaces_derived_namespace [()], @Test nested_pojo_becomes_nested_record [()], @Test non_string_map_keys_are_rejected [()], @Test nullable_by_default_off_makes_reference_fields_required [()], @Test optional_field_is_always_nullable [()], @Test parquet_message_type_is_generated_and_matches_field_names [()], @Test primitives_are_required_and_reference_types_are_nullable_by_default [()], @Test schema_field_overrides_name_and_doc [()], @Test schema_ignore_excludes_field_and_transient_is_skipped [()], @Test self_referencing_record_is_supported_via_name_reference [()], PojoSchemaGeneratorTest, bigdecimal_uses_schema_decimal_annotation [()], date_and_instant_map_to_avro_logical_types [()], enum_becomes_avro_enum [()], enum_is_not_a_valid_top_level_target [()], fieldType [(Schema record, String name)], generates_record_with_namespace_and_name_from_package [()], inheritance_includes_superclass_fields_with_superclass_first [()], list_becomes_array_and_map_becomes_avro_map [()], namespace_override_replaces_derived_namespace [()], nested_pojo_becomes_nested_record [()], non_string_map_keys_are_rejected [()], nullable_by_default_off_makes_reference_fields_required [()], optional_field_is_always_nullable [()], parquet_message_type_is_generated_and_matches_field_names [()], primitives_are_required_and_reference_types_are_nullable_by_default [()], schema_field_overrides_name_and_doc [()], schema_ignore_excludes_field_and_transient_is_skipped [()], self_referencing_record_is_supported_via_name_reference [()], unwrapNullable [(Schema s)]]

## EDGES
[DerivedPojo] -> [inherits] -> [BasePojo]
[Employee] -> [inherits] -> [Person]
[SchemaGenerationException] -> [inherits] -> [RuntimeException]