package io.github.teilabs.remote;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.bouncycastle.crypto.signers.Ed25519Signer;

public class App {
    private static final AppConfig CONFIG = AppConfig.load();

    private static final CommandExecutor commandExecutor = new CommandExecutor(CONFIG);

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
            config.routes.before(App::verifySignature);
            config.routes.get("/health", ctx -> ctx.json(Map.of("status", "ok")));
            config.routes.get("/config", ctx -> ctx.json(Map.of("commands", commandDescriptors())));
            config.routes.post("/value", ctx -> {
                String command = requiredCommand(ctx);
                if (command == null) {
                    return;
                }
                try {
                    ctx.json(Map.of("value", commandExecutor.readValue(command)));
                } catch (UnknownCommandException e) {
                    ctx.status(404).json(Map.of("error", e.getMessage()));
                } catch (IllegalArgumentException e) {
                    ctx.status(400).json(Map.of("error", e.getMessage()));
                } catch (RuntimeException e) {
                    ctx.status(502).json(Map.of("error", e.getMessage()));
                }
            });
            config.routes.post("/execute", ctx -> {
                String command = requiredCommand(ctx);
                if (command == null) {
                    return;
                }

                ExecutionResult result;
                try {
                    result = commandExecutor.execute(command, requestArguments(ctx));
                } catch (UnknownCommandException e) {
                    ctx.status(404).json(Map.of("error", e.getMessage()));
                    return;
                } catch (IllegalArgumentException e) {
                    ctx.status(400).json(Map.of("error", e.getMessage()));
                    return;
                } catch (RuntimeException e) {
                    ctx.status(502).json(Map.of("error", e.getMessage()));
                    return;
                }

                ctx.json(Map.of("exitCode", result.exitCode(), "output", result.output()));
            });
        }).start(7000);

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }

    private static void verifySignature(Context ctx) {
        if (ctx.path().equals("/health")) {
            return;
        }

        String timestamp = ctx.header("X-Timestamp");
        String signature = ctx.header("X-Signature");

        if (timestamp == null || signature == null) {
            reject(ctx, "Missing timestamp or signature");
            return;
        }

        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            reject(ctx, "Invalid timestamp");
            return;
        }

        long now = System.currentTimeMillis();
        if (requestTime < now - CONFIG.ttl() || requestTime > now + CONFIG.ttl()) {
            reject(ctx, "Timestamp expired");
            return;
        }

        String body = ctx.body();
        String message = timestamp + body;
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            reject(ctx, "Invalid signature encoding");
            return;
        }

        Ed25519Signer signer = new Ed25519Signer();
        signer.init(false, CONFIG.publicKey());
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        signer.update(messageBytes, 0, messageBytes.length);

        if (!signer.verifySignature(signatureBytes)) {
            reject(ctx, "Invalid signature");
        }
    }

    private static List<CommandDescriptor> commandDescriptors() {
        return CONFIG.commands().stream()
                .map(CommandDescriptor::from)
                .toList();
    }

    private static Map<String, String> requestArguments(Context ctx) {
        return CONFIG.commands().stream()
                .flatMap(command -> command.arguments().stream())
                .map(CommandArgument::name)
                .distinct()
                .filter(name -> ctx.formParam("arg." + name) != null)
                .collect(java.util.stream.Collectors.toMap(
                        name -> name,
                        name -> ctx.formParam("arg." + name)));
    }

    private static String requiredCommand(Context ctx) {
        String command = ctx.formParam("command");
        if (command == null || command.isBlank()) {
            ctx.status(400).json(Map.of("error", "Missing command"));
            return null;
        }
        return command;
    }

    private static void reject(Context ctx, String message) {
        ctx.status(401).json(Map.of("error", message)).skipRemainingHandlers();
    }

    private record CommandDescriptor(
            String name,
            String type,
            List<CommandArgument> arguments,
            boolean needConfirmation,
            boolean needNotificationOnComplete) {
        private static CommandDescriptor from(Command command) {
            return new CommandDescriptor(
                    command.name(),
                    command.type().name(),
                    command.arguments(),
                    command.needConfirmation(),
                    command.needNotificationOnComplete());
        }
    }
}
