package io.github.jabhijeet.schema.parquet;

import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.fixtures.NumericLogicalPojo;
import io.github.jabhijeet.schema.fixtures.TemporalTypesPojo;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.DateLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimestampLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.junit.jupiter.api.Test;

import static io.github.jabhijeet.schema.parquet.ParquetTestSupport.primitive;
import static org.assertj.core.api.Assertions.assertThat;

class LogicalTypesParquetTest {

    private static final MessageType TEMPORAL = PojoSchemaGenerator.toParquet(TemporalTypesPojo.class);
    private static final MessageType NUMERIC = PojoSchemaGenerator.toParquet(NumericLogicalPojo.class);

    @Test
    void local_date_becomes_int32_with_date_logical_type() {
        PrimitiveType t = primitive(TEMPORAL, "localDate");

        assertThat(t.getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
        assertThat(t.getLogicalTypeAnnotation()).isInstanceOf(DateLogicalTypeAnnotation.class);
    }

    @Test
    void sql_date_also_becomes_int32_date() {
        PrimitiveType t = primitive(TEMPORAL, "sqlDate");

        assertThat(t.getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
        assertThat(t.getLogicalTypeAnnotation()).isInstanceOf(DateLogicalTypeAnnotation.class);
    }

    @Test
    void local_time_becomes_int64_with_time_micros() {
        PrimitiveType t = primitive(TEMPORAL, "localTime");

        assertThat(t.getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT64);
        TimeLogicalTypeAnnotation lt = (TimeLogicalTypeAnnotation) t.getLogicalTypeAnnotation();
        assertThat(lt.getUnit()).isEqualTo(TimeUnit.MICROS);
    }

    @Test
    void instant_becomes_int64_timestamp_adjusted_to_utc_in_millis() {
        PrimitiveType t = primitive(TEMPORAL, "instant");

        assertThat(t.getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT64);
        TimestampLogicalTypeAnnotation lt = (TimestampLogicalTypeAnnotation) t.getLogicalTypeAnnotation();
        assertThat(lt.isAdjustedToUTC()).isTrue();
        assertThat(lt.getUnit()).isEqualTo(TimeUnit.MILLIS);
    }

    @Test
    void local_datetime_becomes_int64_timestamp_not_adjusted_to_utc() {
        PrimitiveType t = primitive(TEMPORAL, "localDateTime");

        assertThat(t.getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT64);
        TimestampLogicalTypeAnnotation lt = (TimestampLogicalTypeAnnotation) t.getLogicalTypeAnnotation();
        assertThat(lt.isAdjustedToUTC()).isFalse();
        assertThat(lt.getUnit()).isEqualTo(TimeUnit.MILLIS);
    }

    @Test
    void offset_datetime_zoned_datetime_util_date_and_sql_timestamp_are_all_utc_timestamps() {
        for (String name : new String[]{"offsetDateTime", "zonedDateTime", "utilDate", "sqlTimestamp"}) {
            PrimitiveType t = primitive(TEMPORAL, name);
            assertThat(t.getPrimitiveTypeName()).as(name).isEqualTo(PrimitiveTypeName.INT64);
            TimestampLogicalTypeAnnotation lt = (TimestampLogicalTypeAnnotation) t.getLogicalTypeAnnotation();
            assertThat(lt.isAdjustedToUTC()).as(name).isTrue();
            assertThat(lt.getUnit()).as(name).isEqualTo(TimeUnit.MILLIS);
        }
    }

    @Test
    void bigdecimal_with_annotation_uses_annotation_precision_and_scale() {
        PrimitiveType t = primitive(NUMERIC, "price");

        assertThat(t.getPrimitiveTypeName()).isIn(PrimitiveTypeName.BINARY, PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY);
        DecimalLogicalTypeAnnotation lt = (DecimalLogicalTypeAnnotation) t.getLogicalTypeAnnotation();
        assertThat(lt.getPrecision()).isEqualTo(10);
        assertThat(lt.getScale()).isEqualTo(2);
    }

    @Test
    void bigdecimal_without_annotation_falls_back_to_options_defaults() {
        PrimitiveType t = primitive(NUMERIC, "defaultDecimal");

        DecimalLogicalTypeAnnotation lt = (DecimalLogicalTypeAnnotation) t.getLogicalTypeAnnotation();
        assertThat(lt.getPrecision()).isEqualTo(38);
        assertThat(lt.getScale()).isEqualTo(18);
    }

    @Test
    void biginteger_becomes_string() {
        PrimitiveType t = primitive(NUMERIC, "bigInt");

        assertThat(t.getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BINARY);
        assertThat(t.getLogicalTypeAnnotation())
                .isInstanceOf(LogicalTypeAnnotation.StringLogicalTypeAnnotation.class);
    }

    @Test
    void uuid_has_a_logical_annotation() {
        PrimitiveType t = primitive(NUMERIC, "id");

        assertThat(t.getLogicalTypeAnnotation()).isNotNull();
    }
}

