package io.github.teilabs.remote.config;

import java.util.List;

/** Immutable configuration for one command exposed through the API. */
public record Command(
        String name,
        CommandType type,
        String executable,
        List<String> args,
        List<CommandArgument> arguments,
        ReadCommand read,
        boolean runInBackground,
        boolean needConfirmation,
        boolean needNotificationOnComplete) {

}
