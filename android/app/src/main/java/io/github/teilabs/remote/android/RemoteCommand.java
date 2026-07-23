package io.github.teilabs.remote.android;

import java.util.List;

final class RemoteCommand {
    private final String name;
    private final String type;
    private final List<RemoteArgument> arguments;

    RemoteCommand(String name, String type, List<RemoteArgument> arguments) {
        this.name = name;
        this.type = type;
        this.arguments = arguments;
    }

    String name() {
        return name;
    }

    String type() {
        return type;
    }

    List<RemoteArgument> arguments() {
        return arguments;
    }
}
