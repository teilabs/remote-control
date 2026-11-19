package io.github.teilabs.remote;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.teilabs.remote.commands.CommandExecutor;
import io.github.teilabs.remote.commands.CommandExecutor.ExecutionResult;
import io.github.teilabs.remote.commands.CommandExecutor.UnknownCommandException;
import io.github.teilabs.remote.config.AppConfig;
import io.github.teilabs.remote.config.Command;
import io.github.teilabs.remote.config.CommandArgument;
import io.javalin.Javalin;
import io.javalin.http.Context;

/** Registers and serves the Remote Control HTTP API. */
public final class RemoteServer {
    private final AppConfig config;
    private final CommandExecutor commandExecutor;
    private final RequestSecurity requestSecurity;

    /** Creates the API server using the supplied configuration and command executor. */
    public RemoteServer(AppConfig config, CommandExecutor commandExecutor) {
        this.config = config;
        this.commandExecutor = commandExecutor;
        this.requestSecurity = new RequestSecurity(config);
    }

    /** Starts listening for HTTP requests on {@code port}. */
    public void start(int port) {
        Javalin app = Javalin.create(javalin -> {
            javalin.http.defaultContentType = "application/json";
            javalin.routes.before(requestSecurity::verifySignature);
            javalin.routes.before(requestSecurity::decryptRequest);
            javalin.routes.after(requestSecurity::encryptResponse);

            javalin.routes.get("/health", ctx -> ctx.json(Map.of("status", "ok")));
            javalin.routes.post("/key-exchange", requestSecurity::sendServerKey);
            javalin.routes.get("/config", ctx -> ctx.json(Map.of("commands", commandDescriptors())));
            javalin.routes.post("/value", this::readValue);
            javalin.routes.post("/execute", this::execute);
        }).start(port);

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }

    private void readValue(Context ctx) {
        String command = requiredCommand(ctx);
        if (command == null) return;

        try {
            ctx.json(Map.of("value", commandExecutor.readValue(command)));
        } catch (UnknownCommandException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            ctx.status(502).json(Map.of("error", e.getMessage()));
        }
    }

    private void execute(Context ctx) {
        String command = requiredCommand(ctx);
        if (command == null) return;

        try {
            ExecutionResult result = commandExecutor.execute(command, requestArguments(ctx));
            ctx.json(Map.of("exitCode", result.exitCode(), "output", result.output()));
        } catch (UnknownCommandException e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            ctx.status(502).json(Map.of("error", e.getMessage()));
        }
    }

    private List<CommandDescriptor> commandDescriptors() {
        return config.commands().stream().map(CommandDescriptor::from).toList();
    }

    private Map<String, String> requestArguments(Context ctx) {
        Map<String, String> form = formParams(ctx);
        return config.commands().stream()
                .flatMap(command -> command.arguments().stream())
                .map(CommandArgument::name)
                .distinct()
                .filter(name -> form.containsKey("arg." + name))
                .collect(java.util.stream.Collectors.toMap(
                        name -> name,
                        name -> form.get("arg." + name)));
    }

    private String requiredCommand(Context ctx) {
        String command = formParams(ctx).get("command");
        if (command == null || command.isBlank()) {
            ctx.status(400).json(Map.of("error", "Missing command"));
            return null;
        }
        return command;
    }

    private static Map<String, String> formParams(Context ctx) {
        String body = ctx.attribute(RequestSecurity.FORM_ATTRIBUTE);
        if (body == null) body = ctx.body();
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            String[] parts = pair.split("=", 2);
            params.put(java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(
                            parts.length == 1 ? "" : parts[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    private record CommandDescriptor(
            String name,
            String type,
            List<CommandArgument> arguments,
            boolean runInBackground,
            boolean needConfirmation,
            boolean needNotificationOnComplete) {
        private static CommandDescriptor from(Command command) {
            return new CommandDescriptor(
                    command.name(),
                    command.type().name(),
                    command.arguments(),
                    command.runInBackground(),
                    command.needConfirmation(),
                    command.needNotificationOnComplete());
        }
    }
}
