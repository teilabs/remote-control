package io.github.teilabs.remote.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import android.util.Base64;

/** Performs authenticated, encrypted HTTP operations against a Remote Control server. */
final class RemoteClient {
    private final String baseUrl;
    private final SigningKeyStore signingKeyStore;

    /** Creates a client for {@code baseUrl} using the device's persistent credentials. */
    RemoteClient(String baseUrl, SigningKeyStore signingKeyStore) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.signingKeyStore = signingKeyStore;
    }

    /** Fetches and parses the server's command and control metadata. */
    List<RemoteCommand> getCommands() throws Exception {
        JSONObject response = new JSONObject(request("GET", "/config", ""));
        JSONArray commands = response.getJSONArray("commands");
        List<RemoteCommand> result = new ArrayList<>();
        for (int i = 0; i < commands.length(); i++) {
            JSONObject command = commands.getJSONObject(i);
            String type = command.optString("type", "SIMPLE");
            boolean interactiveByDefault = type.equals("SIMPLE");
            JSONArray arguments = command.optJSONArray("arguments");
            List<RemoteArgument> parsedArguments = new ArrayList<>();
            if (arguments != null) {
                for (int j = 0; j < arguments.length(); j++) {
                    JSONObject argument = arguments.getJSONObject(j);
                    JSONArray options = argument.optJSONArray("options");
                    List<String> parsedOptions = new ArrayList<>();
                    if (options != null) {
                        for (int k = 0; k < options.length(); k++) {
                            parsedOptions.add(options.getString(k));
                        }
                    }
                    parsedArguments.add(new RemoteArgument(
                            argument.getString("name"),
                            argument.optString("label", argument.getString("name")),
                            argument.getString("type"),
                            argument.isNull("defaultValue")
                                    ? null
                                    : argument.optString("defaultValue", null),
                            (float) argument.optDouble("min", 0),
                            (float) argument.optDouble("max", 0),
                            (float) argument.optDouble("step", 0),
                            List.copyOf(parsedOptions)));
                }
            }
            result.add(new RemoteCommand(
                    command.getString("name"),
                    type,
                    List.copyOf(parsedArguments),
                    command.optBoolean("needConfirmation", interactiveByDefault),
                    command.optBoolean(
                            "needNotificationOnComplete", interactiveByDefault)));
        }
        return result;
    }

    /** Executes a configured command with the supplied argument values. */
    ExecutionResult execute(String commandName, Map<String, String> arguments) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("command", commandName);
        arguments.forEach((name, value) -> fields.put("arg." + name, value));
        JSONObject response = new JSONObject(request("POST", "/execute", formBody(fields)));
        return new ExecutionResult(response.getInt("exitCode"), response.optString("output"));
    }

    /** Reads the current value of a configured syncable command. */
    String getValue(String commandName) throws Exception {
        JSONObject response = new JSONObject(request(
                "POST", "/value", formBody(Map.of("command", commandName))));
        return response.getString("value");
    }

    private static String formBody(Map<String, String> fields) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8.name()))
                    .append('=')
                    .append(URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8.name()));
        }
        return body.toString();
    }

    private String request(String method, String path, String body) throws Exception {
        if (!path.equals("/key-exchange") && signingKeyStore.serverPublicKeyBase64().isEmpty()) {
            request("POST", "/key-exchange", "");
        }
        String clientKey = signingKeyStore.encryptionPublicKeyBase64();
        String wireBody = (method.equals("GET") || path.equals("/key-exchange")) ? body
                : Base64.encodeToString(crypt(Cipher.ENCRYPT_MODE,
                        signingKeyStore.encryptionKey(signingKeyStore.serverPublicKeyBase64()),
                        body.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        long timestamp = System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + path)
                .toURL().openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Timestamp", Long.toString(timestamp));
        connection.setRequestProperty("X-Client-Encryption-Key", clientKey);
        connection.setRequestProperty(
                "X-Signature", signingKeyStore.sign(timestamp + clientKey + wireBody));

        if (!wireBody.isEmpty() || method.equals("POST")) {
            connection.setDoOutput(true);
            connection.setRequestProperty(
                    "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.getOutputStream().write(wireBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        String response = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        if (!path.equals("/key-exchange")) {
            response = new String(crypt(Cipher.DECRYPT_MODE,
                    signingKeyStore.encryptionKey(signingKeyStore.serverPublicKeyBase64()),
                    Base64.decode(response, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } else {
            JSONObject exchange = new JSONObject(response);
            byte[] handshakeKey = signingKeyStore.encryptionKeyFromServerPublic(
                    exchange.getString("ephemeralKey"));
            String plain = new String(crypt(Cipher.DECRYPT_MODE, handshakeKey,
                    Base64.decode(exchange.getString("ciphertext"), Base64.NO_WRAP)), StandardCharsets.UTF_8);
            String encoded = new JSONObject(plain).getString("serverKey");
            signingKeyStore.saveServerPublicKey(encoded);
        }
        if (status >= 400) {
            String message = response;
            try {
                message = new JSONObject(response).optString("error", response);
            } catch (Exception ignored) {
                // Preserve the plain server response.
            }
            throw new IOException("Server returned " + status + ": " + message);
        }
        return response;
    }

    private static byte[] crypt(int mode, byte[] key, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        if (mode == Cipher.ENCRYPT_MODE) {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(input);
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return result;
        }
        byte[] iv = java.util.Arrays.copyOfRange(input, 0, 12);
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(input, 12, input.length - 12);
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
    }

    private static String normalizeBaseUrl(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }

    /** Plaintext result returned by a command execution request. */
    static final class ExecutionResult {
        private final int exitCode;
        private final String output;

        ExecutionResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        int exitCode() {
            return exitCode;
        }

        String output() {
            return output;
        }

        boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
