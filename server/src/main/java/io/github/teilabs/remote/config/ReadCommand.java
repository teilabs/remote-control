package io.github.teilabs.remote.config;

import java.util.List;

public record ReadCommand(String executable, List<String> args) {
}
