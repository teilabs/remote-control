package io.github.teilabs.remote.android;

import java.util.List;

final class RemoteCommand {
    private final String name;
    private final String type;
    private final List<RemoteArgument> arguments;
    private final boolean needConfirmation;
    private final boolean needNotificationOnComplete;

    RemoteCommand(
            String name,
            String type,
            List<RemoteArgument> arguments,
            boolean needConfirmation,
            boolean needNotificationOnComplete) {
        this.name = name;
        this.type = type;
        this.arguments = arguments;
        this.needConfirmation = needConfirmation;
        this.needNotificationOnComplete = needNotificationOnComplete;
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

    boolean needConfirmation() {
        return needConfirmation;
    }

    boolean needNotificationOnComplete() {
        return needNotificationOnComplete;
    }
}
