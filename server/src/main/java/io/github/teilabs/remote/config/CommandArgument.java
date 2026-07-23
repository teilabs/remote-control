package io.github.teilabs.remote.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CommandArgument(
        String name,
        String label,
        ArgumentType type,
        String defaultValue,
        BigDecimal min,
        BigDecimal max,
        BigDecimal step,
        List<String> options) {

    public String validate(Map<String, String> values) {
        String value = values.getOrDefault(name, defaultValue);
        if (value == null) {
            throw new IllegalArgumentException("Missing argument: " + name);
        }

        return switch (type) {
            case SLIDER -> validateSlider(value);
            case SELECT -> validateSelect(value);
            case TOGGLE -> validateToggle(value);
            case TEXT -> value;
        };
    }

    private String validateSlider(String value) {
        final BigDecimal number;
        try {
            number = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Argument '" + name + "' must be a number");
        }

        if (number.compareTo(min) < 0 || number.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                    "Argument '" + name + "' must be between " + min + " and " + max);
        }
        if (number.subtract(min).remainder(step).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                    "Argument '" + name + "' must use increments of " + step);
        }
        return number.stripTrailingZeros().toPlainString();
    }

    private String validateSelect(String value) {
        if (!options.contains(value)) {
            throw new IllegalArgumentException("Invalid value for argument: " + name);
        }
        return value;
    }

    private String validateToggle(String value) {
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException("Argument '" + name + "' must be true or false");
        }
        return value;
    }
}
