package com.qinglian.fitness.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Recommended spring groups for each color; zero means that color is not used. */
public record SpringCounts(
    @NotNull @Min(0) @Max(20) @JsonDeserialize(using = CountDeserializer.class) Integer red,
    @NotNull @Min(0) @Max(20) @JsonDeserialize(using = CountDeserializer.class) Integer green,
    @NotNull @Min(0) @Max(20) @JsonDeserialize(using = CountDeserializer.class) Integer yellow,
    @NotNull @Min(0) @Max(20) @JsonDeserialize(using = CountDeserializer.class) Integer blue
) {
    /** Reject fractions and strings instead of silently coercing a physical spring count. */
    public static final class CountDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Integer.class, "弹簧组数必须为 0 至 20 的整数");
            }
            return parser.getIntValue();
        }
    }
}
