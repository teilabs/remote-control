package io.github.teilabs.remote;

import io.github.teilabs.remote.commands.CommandExecutor;
import io.github.teilabs.remote.config.AppConfig;

/** Application entry point for the Remote Control server. */
public final class App {
    private App() {}

    /** Loads server configuration and starts the HTTP service on port 7000. */
    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        CommandExecutor commandExecutor = new CommandExecutor(config);
        new RemoteServer(config, commandExecutor).start(7000);
    }
}
