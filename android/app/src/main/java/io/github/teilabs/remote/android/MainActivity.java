package io.github.teilabs.remote.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = "remote_settings";
    private static final String SERVER_URL = "server_url";
    private static final String DEFAULT_SERVER_URL = "http://10.20.30.3:7000";
    private static final long SYNC_INTERVAL_MS = 2000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private final List<SyncableSlider> syncableSliders = new ArrayList<>();
    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            syncCurrentValues();
            syncHandler.postDelayed(this, SYNC_INTERVAL_MS);
        }
    };
    private LinearLayout commandList;
    private ProgressBar progress;
    private TextView statusText;
    private View statusDot;
    private ImageButton refreshButton;
    private SigningKeyStore signingKeyStore;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        signingKeyStore = new SigningKeyStore(this);
        commandList = findViewById(R.id.commandList);
        progress = findViewById(R.id.progress);
        statusText = findViewById(R.id.statusText);
        statusDot = findViewById(R.id.statusDot);
        refreshButton = findViewById(R.id.refreshButton);

        refreshButton.setOnClickListener(view -> loadCommands());
        findViewById(R.id.settingsButton).setOnClickListener(view -> showSettings());
        loadCommands();
    }

    @Override
    protected void onDestroy() {
        syncHandler.removeCallbacks(syncRunnable);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        syncHandler.removeCallbacks(syncRunnable);
        syncHandler.post(syncRunnable);
    }

    @Override
    protected void onStop() {
        syncHandler.removeCallbacks(syncRunnable);
        super.onStop();
    }

    private void loadCommands() {
        setLoading(true);
        setStatus("Connecting", R.color.remote_text_muted);
        executor.execute(() -> {
            try {
                List<RemoteCommand> commands = client().getCommands();
                runOnUiThread(() -> showCommands(commands));
            } catch (Exception e) {
                runOnUiThread(() -> showLoadError(e));
            }
        });
    }

    private void showCommands(List<RemoteCommand> commands) {
        setLoading(false);
        setStatus("Connected · " + commands.size() + " available", R.color.remote_accent);
        commandList.removeAllViews();
        syncableSliders.clear();
        if (commands.isEmpty()) {
            addMessage("No commands are configured on this server.");
            return;
        }
        for (int i = 0; i < commands.size(); i++) {
            View row = createCommandRow(commands.get(i));
            row.setAlpha(0f);
            row.setTranslationY(dp(12));
            commandList.addView(row);
            row.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 45L)
                    .setDuration(220L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        syncCurrentValues();
    }

    private View createCommandRow(RemoteCommand command) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(14), 0, dp(18));
        row.setBackgroundColor(ContextCompat.getColor(this, R.color.remote_background));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(header, matchWidth());

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        header.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(humanize(command.name()));
        name.setTextColor(ContextCompat.getColor(this, R.color.remote_text));
        name.setTextSize(17);
        labels.addView(name);

        TextView type = new TextView(this);
        type.setText(command.type().toLowerCase(Locale.ROOT));
        type.setTextColor(ContextCompat.getColor(this, R.color.remote_text_muted));
        type.setTextSize(12);
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        typeParams.topMargin = dp(4);
        labels.addView(type, typeParams);

        MaterialButton run = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        run.setText(R.string.run);
        run.setTextColor(ContextCompat.getColor(this, R.color.remote_accent));
        run.setStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.remote_accent_dark)));
        run.setCornerRadius(dp(6));
        run.setMinWidth(dp(76));
        header.addView(run, new LinearLayout.LayoutParams(dp(88), dp(48)));

        Map<String, ArgumentValue> argumentValues = new LinkedHashMap<>();
        if (command.type().equals("SYNCABLE")) {
            RemoteArgument argument = command.arguments().get(0);
            Slider slider = addSlider(row, argument, argumentValues);
            run.setVisibility(View.GONE);
            SyncableSlider syncable = new SyncableSlider(command, argument, slider);
            slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(Slider unused) {
                    syncable.tracking = true;
                }

                @Override
                public void onStopTrackingTouch(Slider unused) {
                    syncable.tracking = false;
                    requestSyncableUpdate(syncable);
                }
            });
            syncableSliders.add(syncable);
        } else {
            for (RemoteArgument argument : command.arguments()) {
                addArgumentControl(row, argument, argumentValues);
            }
            run.setOnClickListener(view ->
                    requestExecution(command, argumentValues, run));
        }

        return row;
    }

    private void addArgumentControl(
            LinearLayout row,
            RemoteArgument argument,
            Map<String, ArgumentValue> values) {
        switch (argument.type()) {
            case "SLIDER":
                addSlider(row, argument, values);
                break;
            case "SELECT":
                addSelect(row, argument, values);
                break;
            case "TOGGLE":
                addToggle(row, argument, values);
                break;
            case "TEXT":
                addTextInput(row, argument, values);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported argument type: " + argument.type());
        }
    }

    private Slider addSlider(
            LinearLayout row,
            RemoteArgument argument,
            Map<String, ArgumentValue> values) {
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams labelRowParams = matchWidth();
        labelRowParams.topMargin = dp(18);
        row.addView(labelRow, labelRowParams);

        TextView label = label(argument.label());
        labelRow.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = label("");
        value.setTextColor(ContextCompat.getColor(this, R.color.remote_accent));
        labelRow.addView(value);

        Slider slider = new Slider(this);
        slider.setValueFrom(argument.min());
        slider.setValueTo(argument.max());
        slider.setStepSize(argument.step());
        float initial = argument.defaultValue() == null
                ? argument.min()
                : Float.parseFloat(argument.defaultValue());
        slider.setValue(initial);
        value.setText(formatSliderValue(initial, argument.step()));
        slider.addOnChangeListener((unused, sliderValue, fromUser) ->
                value.setText(formatSliderValue(sliderValue, argument.step())));
        row.addView(slider, matchWidth());
        values.put(argument.name(),
                () -> formatSliderValue(slider.getValue(), argument.step()));
        return slider;
    }

    private void addSelect(
            LinearLayout row,
            RemoteArgument argument,
            Map<String, ArgumentValue> values) {
        TextView label = label(argument.label());
        LinearLayout.LayoutParams labelParams = matchWidth();
        labelParams.topMargin = dp(18);
        row.addView(label, labelParams);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, argument.options());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int initial = argument.options().indexOf(argument.defaultValue());
        spinner.setSelection(Math.max(initial, 0));
        row.addView(spinner, matchWidth());
        values.put(argument.name(), () -> (String) spinner.getSelectedItem());
    }

    private void addToggle(
            LinearLayout row,
            RemoteArgument argument,
            Map<String, ArgumentValue> values) {
        SwitchMaterial toggle = new SwitchMaterial(this);
        toggle.setText(argument.label());
        toggle.setTextColor(ContextCompat.getColor(this, R.color.remote_text));
        toggle.setChecked(Boolean.parseBoolean(argument.defaultValue()));
        LinearLayout.LayoutParams params = matchWidth();
        params.topMargin = dp(14);
        row.addView(toggle, params);
        values.put(argument.name(), () -> Boolean.toString(toggle.isChecked()));
    }

    private void addTextInput(
            LinearLayout row,
            RemoteArgument argument,
            Map<String, ArgumentValue> values) {
        TextView label = label(argument.label());
        LinearLayout.LayoutParams labelParams = matchWidth();
        labelParams.topMargin = dp(18);
        row.addView(label, labelParams);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(argument.defaultValue());
        input.setTextColor(ContextCompat.getColor(this, R.color.remote_text));
        input.setHintTextColor(ContextCompat.getColor(this, R.color.remote_text_muted));
        row.addView(input, matchWidth());
        values.put(argument.name(), () -> input.getText().toString());
    }

    private void requestExecution(
            RemoteCommand command,
            Map<String, ArgumentValue> argumentValues,
            MaterialButton button) {
        if (!command.needConfirmation()) {
            execute(command, readArguments(argumentValues), button);
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Run " + humanize(command.name()) + "?")
                .setMessage("This action will run immediately on the connected server.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Run", (dialog, which) ->
                        execute(command, readArguments(argumentValues), button))
                .show();
    }

    private void requestSyncableUpdate(SyncableSlider syncable) {
        if (!syncable.command.needConfirmation()) {
            updateSyncableValue(syncable);
            return;
        }

        syncable.pendingConfirmation = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Update " + humanize(syncable.command.name()) + "?")
                .setMessage("Apply this value immediately on the connected server?")
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    syncable.pendingConfirmation = false;
                    syncCurrentValues();
                })
                .setOnCancelListener(dialog -> {
                    syncable.pendingConfirmation = false;
                    syncCurrentValues();
                })
                .setPositiveButton("Apply", (dialog, which) -> {
                    syncable.pendingConfirmation = false;
                    updateSyncableValue(syncable);
                })
                .show();
    }

    private void execute(
            RemoteCommand command,
            Map<String, String> arguments,
            MaterialButton button) {
        button.setEnabled(false);
        button.setText(R.string.running);
        executor.execute(() -> {
            try {
                RemoteClient.ExecutionResult result = client().execute(command.name(), arguments);
                runOnUiThread(() -> {
                    resetRunButton(button);
                    if (command.needNotificationOnComplete()) {
                        showResult(command, result);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    resetRunButton(button);
                    showError("Could not run " + humanize(command.name()), e);
                });
            }
        });
    }

    private void syncCurrentValues() {
        for (SyncableSlider syncable : List.copyOf(syncableSliders)) {
            if (syncable.tracking || syncable.reading || syncable.pendingConfirmation) {
                continue;
            }
            syncable.reading = true;
            executor.execute(() -> {
                try {
                    String value = client().getValue(syncable.command.name());
                    runOnUiThread(() -> {
                        syncable.reading = false;
                        if (!syncable.tracking && syncableSliders.contains(syncable)) {
                            syncable.slider.setValue(Float.parseFloat(value));
                        }
                    });
                } catch (Exception ignored) {
                    runOnUiThread(() -> syncable.reading = false);
                }
            });
        }
    }

    private void updateSyncableValue(SyncableSlider syncable) {
        String value = formatSliderValue(
                syncable.slider.getValue(), syncable.argument.step());
        syncable.slider.setEnabled(false);
        executor.execute(() -> {
            try {
                RemoteClient.ExecutionResult result = client().execute(
                        syncable.command.name(),
                        Map.of(syncable.argument.name(), value));
                if (!result.isSuccess()) {
                    throw new IOException(
                            "Command exited with " + result.exitCode() + ": " + result.output());
                }
                runOnUiThread(() -> {
                    syncable.slider.setEnabled(true);
                    if (syncable.command.needNotificationOnComplete()) {
                        showResult(syncable.command, result);
                    }
                    syncCurrentValues();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    syncable.slider.setEnabled(true);
                    showError("Could not update " + humanize(syncable.command.name()), e);
                    syncCurrentValues();
                });
            }
        });
    }

    private void showResult(RemoteCommand command, RemoteClient.ExecutionResult result) {
        String output = result.output().isBlank() ? "Command returned no output." : result.output();
        new MaterialAlertDialogBuilder(this)
                .setTitle(result.isSuccess()
                        ? humanize(command.name()) + " completed"
                        : humanize(command.name()) + " failed")
                .setMessage(output)
                .setPositiveButton("Done", null)
                .setNeutralButton("Copy output", (dialog, which) -> copy("Command output", output))
                .show();
    }

    private void showSettings() {
        int padding = dp(24);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, dp(8), padding, 0);

        TextView urlLabel = label(getString(R.string.server_url));
        content.addView(urlLabel);
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setText(serverUrl());
        url.setTextColor(ContextCompat.getColor(this, R.color.remote_text));
        url.setHintTextColor(ContextCompat.getColor(this, R.color.remote_text_muted));
        content.addView(url, matchWidth());

        TextView keyLabel = label(getString(R.string.public_key));
        LinearLayout.LayoutParams keyLabelParams = matchWidth();
        keyLabelParams.topMargin = dp(24);
        content.addView(keyLabel, keyLabelParams);

        TextView key = new TextView(this);
        key.setText(signingKeyStore.publicKeyBase64());
        key.setTextColor(ContextCompat.getColor(this, R.color.remote_text_muted));
        key.setTextIsSelectable(true);
        key.setTextSize(13);
        content.addView(key, matchWidth());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Connection")
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.copy_public_key,
                        (unused, which) -> copy("Remote public key", signingKeyStore.publicKeyBase64()))
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = url.getText().toString().trim();
                    if (!isValidServerUrl(value)) {
                        url.setError("Enter an http:// or https:// URL");
                        return;
                    }
                    preferences.edit().putString(SERVER_URL, value).apply();
                    dialog.dismiss();
                    loadCommands();
                }));
        dialog.show();
    }

    private void showLoadError(Exception error) {
        setLoading(false);
        setStatus("Disconnected", R.color.remote_error);
        commandList.removeAllViews();
        addMessage("Unable to load commands.\n\n" + readableError(error)
                + "\n\nOpen settings to copy this device's public key and check the server URL.");
    }

    private void showError(String title, Exception error) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(readableError(error))
                .setPositiveButton("Done", null)
                .show();
    }

    private void addMessage(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(ContextCompat.getColor(this, R.color.remote_text_muted));
        view.setTextSize(15);
        view.setLineSpacing(0, 1.25f);
        view.setPadding(0, dp(28), 0, 0);
        commandList.addView(view, matchWidth());
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        refreshButton.setEnabled(!loading);
        if (loading) {
            refreshButton.animate().rotationBy(180f).setDuration(300).start();
        }
    }

    private void setStatus(String text, int color) {
        statusText.setText(text);
        statusDot.getBackground().setTint(ContextCompat.getColor(this, color));
    }

    private void resetRunButton(MaterialButton button) {
        button.setEnabled(true);
        button.setText(R.string.run);
    }

    private RemoteClient client() {
        return new RemoteClient(serverUrl(), signingKeyStore);
    }

    private String serverUrl() {
        return preferences.getString(SERVER_URL, DEFAULT_SERVER_URL);
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(this, R.color.remote_text));
        view.setTextSize(13);
        return view;
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void copy(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private static String humanize(String value) {
        if (value.isBlank()) {
            return value;
        }
        String normalized = value.replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static boolean isValidServerUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static Map<String, String> readArguments(
            Map<String, ArgumentValue> argumentValues) {
        Map<String, String> values = new LinkedHashMap<>();
        argumentValues.forEach((name, provider) -> values.put(name, provider.get()));
        return values;
    }

    private static String formatSliderValue(float value, float step) {
        int scale = Math.max(0, BigDecimal.valueOf(step).stripTrailingZeros().scale());
        return new BigDecimal(Float.toString(value))
                .setScale(scale, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String readableError(Exception error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ArgumentValue {
        String get();
    }

    private static final class SyncableSlider {
        private final RemoteCommand command;
        private final RemoteArgument argument;
        private final Slider slider;
        private boolean tracking;
        private boolean reading;
        private boolean pendingConfirmation;

        private SyncableSlider(
                RemoteCommand command, RemoteArgument argument, Slider slider) {
            this.command = command;
            this.argument = argument;
            this.slider = slider;
        }
    }
}
