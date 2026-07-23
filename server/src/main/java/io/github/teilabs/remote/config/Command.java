package io.github.teilabs.remote.config;

import java.util.List;

public record Command(
        String name,
        CommandType type,
        String executable,
        List<String> args,
        List<CommandArgument> arguments,
        ReadCommand read) {

}
