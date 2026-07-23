package io.github.teilabs.remote.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class CommandArgumentTest {
    @Test
    public void sliderAcceptsAndNormalizesValidValues() {
        CommandArgument argument = slider();

        assertEquals("75", argument.validate(Map.of("volume", "75.0")));
    }

    @Test
    public void sliderRejectsValuesOutsideRange() {
        assertRejected(slider(), Map.of("volume", "101"));
    }

    @Test
    public void sliderRejectsValuesOutsideStep() {
        CommandArgument argument = new CommandArgument(
                "volume",
                "Volume",
                ArgumentType.SLIDER,
                "50",
                BigDecimal.ZERO,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(5),
                List.of());

        assertRejected(argument, Map.of("volume", "52"));
    }

    @Test
    public void selectAndToggleRejectUnknownValues() {
        CommandArgument select = new CommandArgument(
                "profile", "Profile", ArgumentType.SELECT, "music",
                null, null, null, List.of("music", "movie"));
        CommandArgument toggle = new CommandArgument(
                "muted", "Muted", ArgumentType.TOGGLE, "false",
                null, null, null, List.of());

        assertEquals("movie", select.validate(Map.of("profile", "movie")));
        assertRejected(select, Map.of("profile", "invalid"));
        assertRejected(toggle, Map.of("muted", "yes"));
    }

    private static CommandArgument slider() {
        return new CommandArgument(
                "volume",
                "Volume",
                ArgumentType.SLIDER,
                "50",
                BigDecimal.ZERO,
                BigDecimal.valueOf(100),
                BigDecimal.ONE,
                List.of());
    }

    private static void assertRejected(
            CommandArgument argument, Map<String, String> values) {
        try {
            argument.validate(values);
            fail("Expected argument validation to fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
