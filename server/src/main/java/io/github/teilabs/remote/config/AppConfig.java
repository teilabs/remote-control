package io.github.teilabs.remote.config;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record AppConfig(long ttl, Ed25519PublicKeyParameters publicKey, List<Command> commands) {
        private static final ObjectMapper JSON = new ObjectMapper();
        private static final Path DEFAULT_CONFIG_PATH = Path.of("config", "app.json");

        public static AppConfig load() {
            Path configPath = Path.of(System.getProperty("remote.config", DEFAULT_CONFIG_PATH.toString()));

            try {
                JsonNode config = JSON.readTree(configPath.toFile());
                long ttl = requiredLong(config, "ttlMs");
                String publicKey = requiredText(config, "publicKeyBase64");
                List<Command> commands = parseCommands(config.path("commands"));

                byte[] publicKeyBytes = Base64.getDecoder().decode(publicKey);
                return new AppConfig(ttl, new Ed25519PublicKeyParameters(publicKeyBytes, 0), commands);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load config file: " + configPath, e);
            }
        }

        private static List<Command> parseCommands(JsonNode commands) {
            if (commands.isMissingNode()) {
                return List.of();
            }
            if (!commands.isArray()) {
                throw new IllegalStateException("Config field 'commands' must be an array");
            }

            List<Command> parsedCommands = new ArrayList<>();
            for (JsonNode command : commands) {
                String name = requiredText(command, "name");
                CommandType type = CommandType.valueOf(requiredText(command, "type"));
                String executable = requiredText(command, "executable");
                List<String> args = optionalStringArray(command.path("args"), "args");
                List<CommandArgument> arguments = parseArguments(command.path("arguments"));
                ReadCommand read = parseReadCommand(command.path("read"), type);
                boolean interactiveByDefault = type == CommandType.SIMPLE;
                boolean needConfirmation = optionalBoolean(
                        command, "needConfirmation", interactiveByDefault);
                boolean needNotificationOnComplete = optionalBoolean(
                        command, "needNotificationOnComplete", interactiveByDefault);
                validatePlaceholders(name, args, arguments);
                validateCommandShape(name, type, arguments, read);
                parsedCommands.add(new Command(
                        name,
                        type,
                        executable,
                        args,
                        arguments,
                        read,
                        needConfirmation,
                        needNotificationOnComplete));
            }
            return List.copyOf(parsedCommands);
        }

        private static ReadCommand parseReadCommand(JsonNode read, CommandType type) {
            if (read.isMissingNode()) {
                return null;
            }
            if (!read.isObject()) {
                throw new IllegalStateException("Config field 'read' must be an object");
            }
            if (type != CommandType.SYNCABLE) {
                throw new IllegalStateException(
                        "Config field 'read' is only valid for SYNCABLE commands");
            }
            return new ReadCommand(
                    requiredText(read, "executable"),
                    optionalStringArray(read.path("args"), "read.args"));
        }

        private static void validateCommandShape(
                String name,
                CommandType type,
                List<CommandArgument> arguments,
                ReadCommand read) {
            if (type != CommandType.SYNCABLE) {
                return;
            }
            if (arguments.size() != 1 || arguments.get(0).type() != ArgumentType.SLIDER) {
                throw new IllegalStateException(
                        "SYNCABLE command '" + name + "' requires exactly one SLIDER argument");
            }
            if (read == null) {
                throw new IllegalStateException(
                        "SYNCABLE command '" + name + "' requires a read command");
            }
        }

        private static List<CommandArgument> parseArguments(JsonNode arguments) {
            if (arguments.isMissingNode()) {
                return List.of();
            }
            if (!arguments.isArray()) {
                throw new IllegalStateException("Config field 'arguments' must be an array");
            }

            List<CommandArgument> parsedArguments = new ArrayList<>();
            for (JsonNode argument : arguments) {
                String name = requiredText(argument, "name");
                String label = optionalText(argument, "label", name);
                ArgumentType type = ArgumentType.valueOf(requiredText(argument, "type"));
                String defaultValue = optionalValue(argument, "default");
                BigDecimal min = optionalDecimal(argument, "min");
                BigDecimal max = optionalDecimal(argument, "max");
                BigDecimal step = optionalDecimal(argument, "step");
                List<String> options = optionalStringArray(argument.path("options"), "options");

                if (parsedArguments.stream().anyMatch(existing -> existing.name().equals(name))) {
                    throw new IllegalStateException("Duplicate command argument: " + name);
                }
                if (type == ArgumentType.SLIDER) {
                    requireSliderFields(name, min, max, step);
                }
                if (type == ArgumentType.SELECT && options.isEmpty()) {
                    throw new IllegalStateException(
                            "SELECT argument '" + name + "' must define options");
                }
                if (defaultValue == null && type == ArgumentType.TOGGLE) {
                    defaultValue = "false";
                }

                CommandArgument parsed = new CommandArgument(
                        name, label, type, defaultValue, min, max, step, options);
                if (defaultValue != null) {
                    try {
                        parsed.validate(java.util.Map.of());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalStateException("Invalid default for argument '" + name + "'", e);
                    }
                }
                parsedArguments.add(parsed);
            }
            return List.copyOf(parsedArguments);
        }

        private static void requireSliderFields(
                String name, BigDecimal min, BigDecimal max, BigDecimal step) {
            if (min == null || max == null || step == null) {
                throw new IllegalStateException(
                        "SLIDER argument '" + name + "' requires min, max, and step");
            }
            if (min.compareTo(max) >= 0 || step.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException(
                        "SLIDER argument '" + name + "' requires min < max and step > 0");
            }
            if (max.subtract(min).remainder(step).compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalStateException(
                        "SLIDER argument '" + name + "' range must be divisible by its step");
            }
        }

        private static void validatePlaceholders(
                String commandName, List<String> args, List<CommandArgument> arguments) {
            for (CommandArgument argument : arguments) {
                String placeholder = "${" + argument.name() + "}";
                if (args.stream().noneMatch(value -> value.contains(placeholder))) {
                    throw new IllegalStateException(
                            "Command '" + commandName + "' does not use placeholder " + placeholder);
                }
            }
        }

        private static List<String> optionalStringArray(JsonNode values, String fieldName) {
            if (values.isMissingNode()) {
                return List.of();
            }
            if (!values.isArray()) {
                throw new IllegalStateException("Config field '" + fieldName + "' must be an array");
            }

            List<String> strings = new ArrayList<>();
            for (JsonNode value : values) {
                if (!value.isTextual()) {
                    throw new IllegalStateException("Config field '" + fieldName + "' must contain only strings");
                }
                strings.add(value.asText());
            }
            return List.copyOf(strings);
        }

        private static long requiredLong(JsonNode config, String fieldName) {
            JsonNode value = config.path(fieldName);
            if (!value.canConvertToLong()) {
                throw new IllegalStateException("Config field '" + fieldName + "' must be a number");
            }
            return value.asLong();
        }

        private static String optionalText(JsonNode config, String fieldName, String fallback) {
            JsonNode value = config.path(fieldName);
            return value.isMissingNode() ? fallback : requiredText(config, fieldName);
        }

        private static String optionalValue(JsonNode config, String fieldName) {
            JsonNode value = config.path(fieldName);
            if (value.isMissingNode()) {
                return null;
            }
            if (!value.isValueNode() || value.isNull()) {
                throw new IllegalStateException(
                        "Config field '" + fieldName + "' must be a string, number, or boolean");
            }
            return value.asText();
        }

        private static BigDecimal optionalDecimal(JsonNode config, String fieldName) {
            JsonNode value = config.path(fieldName);
            if (value.isMissingNode()) {
                return null;
            }
            if (!value.isNumber()) {
                throw new IllegalStateException("Config field '" + fieldName + "' must be a number");
            }
            return value.decimalValue();
        }

        private static boolean optionalBoolean(
                JsonNode config, String fieldName, boolean fallback) {
            JsonNode value = config.path(fieldName);
            if (value.isMissingNode()) {
                return fallback;
            }
            if (!value.isBoolean()) {
                throw new IllegalStateException(
                        "Config field '" + fieldName + "' must be a boolean");
            }
            return value.asBoolean();
        }

        private static String requiredText(JsonNode config, String fieldName) {
            JsonNode value = config.path(fieldName);
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new IllegalStateException("Config field '" + fieldName + "' must be a non-empty string");
            }
            return value.asText();
        }
    }
