package io.github.teilabs.remote.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.teilabs.remote.config.AppConfig;
import io.github.teilabs.remote.config.Command;
import io.github.teilabs.remote.config.CommandArgument;
import io.github.teilabs.remote.config.CommandType;

public class CommandExecutor {
    private final Map<String, Command> commands = new HashMap<>();

    public CommandExecutor(AppConfig config) {
        config.commands().forEach((command) -> {
            commands.put(command.name(), command);
        });
    }

    public ExecutionResult execute(String commandName, Map<String, String> suppliedArguments) {
        Command command = commands.get(commandName);
        if (command == null) {
            throw new UnknownCommandException("Unknown command: " + commandName);
        }

        switch (command.type()) {
            case SIMPLE, SYNCABLE:
                Map<String, String> argumentValues = command.arguments().stream()
                        .collect(Collectors.toMap(
                                argument -> argument.name(),
                                argument -> argument.validate(suppliedArguments)));
                List<String> commandList = new ArrayList<>();
                commandList.add(command.executable());
                command.args().stream()
                        .map(value -> replacePlaceholders(value, argumentValues))
                        .forEach(commandList::add);

                return run(commandName, commandList);
            default:
                    throw new RuntimeException("Unsupported command type: " + command.type());
        }
    }

    public String readValue(String commandName) {
        Command command = commands.get(commandName);
        if (command == null) {
            throw new UnknownCommandException("Unknown command: " + commandName);
        }
        if (command.type() != CommandType.SYNCABLE) {
            throw new IllegalArgumentException("Command is not syncable: " + commandName);
        }

        List<String> commandList = new ArrayList<>();
        commandList.add(command.read().executable());
        commandList.addAll(command.read().args());
        ExecutionResult result = run(commandName + " read", commandList);
        if (!result.isSuccess()) {
            throw new RuntimeException(
                    "Read command exited with " + result.exitCode() + ": " + result.output());
        }

        String value = result.output().trim();
        CommandArgument argument = command.arguments().get(0);
        return argument.validate(Map.of(argument.name(), value));
    }

    private static ExecutionResult run(String commandName, List<String> commandList) {
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            String output;
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            return new ExecutionResult(exitCode, output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Command was interrupted: " + commandName, e);
        } catch (IOException e) {
            throw new RuntimeException("Could not start command '" + commandName
                    + "' with executable '" + commandList.get(0) + "': " + e.getMessage(), e);
        }
    }

    private static String replacePlaceholders(String value, Map<String, String> arguments) {
        String result = value;
        for (Map.Entry<String, String> argument : arguments.entrySet()) {
            result = result.replace("${" + argument.getKey() + "}", argument.getValue());
        }
        return result;
    }

    public static final class UnknownCommandException extends IllegalArgumentException {
        public UnknownCommandException(String message) {
            super(message);
        }
    }

    public record ExecutionResult(int exitCode, String output) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
