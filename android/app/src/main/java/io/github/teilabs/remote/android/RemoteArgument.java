package io.github.teilabs.remote.android;

import java.util.List;

final class RemoteArgument {
    private final String name;
    private final String label;
    private final String type;
    private final String defaultValue;
    private final float min;
    private final float max;
    private final float step;
    private final List<String> options;

    RemoteArgument(
            String name,
            String label,
            String type,
            String defaultValue,
            float min,
            float max,
            float step,
            List<String> options) {
        this.name = name;
        this.label = label;
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
        this.options = options;
    }

    String name() {
        return name;
    }

    String label() {
        return label;
    }

    String type() {
        return type;
    }

    String defaultValue() {
        return defaultValue;
    }

    float min() {
        return min;
    }

    float max() {
        return max;
    }

    float step() {
        return step;
    }

    List<String> options() {
        return options;
    }
}
