package io.github.teilabs.remote.config;

import java.util.List;

/** Executable and arguments used to read the current value of a syncable command. */
public record ReadCommand(String executable, List<String> args) {
}
