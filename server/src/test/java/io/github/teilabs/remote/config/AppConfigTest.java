package io.github.teilabs.remote.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AppConfigTest {
    private static final String PUBLIC_KEY =
            "JdEqMtmtv6C2Nz6m4IkPnf/TNh2b6FzdaRUpVHEd3GI=";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadsSyncableCommandWithOneSliderAndReadCommand() throws Exception {
        AppConfig config = load("""
                {
                  "ttlMs": 30000,
                  "publicKeyBase64": "%s",
                  "commands": [{
                    "name": "volume",
                    "type": "SYNCABLE",
                    "executable": "set-volume",
                    "args": ["${volume}"],
                    "arguments": [{
                      "name": "volume",
                      "type": "SLIDER",
                      "min": 0,
                      "max": 100,
                      "step": 1
                    }],
                    "read": {
                      "executable": "get-volume",
                      "args": []
                    }
                  }]
                }
                """.formatted(PUBLIC_KEY));

        assertEquals(CommandType.SYNCABLE, config.commands().get(0).type());
        assertNotNull(config.commands().get(0).read());
    }

    @Test
    public void rejectsSyncableCommandWithoutReadCommand() throws Exception {
        try {
            load("""
                    {
                      "ttlMs": 30000,
                      "publicKeyBase64": "%s",
                      "commands": [{
                        "name": "volume",
                        "type": "SYNCABLE",
                        "executable": "set-volume",
                        "args": ["${volume}"],
                        "arguments": [{
                          "name": "volume",
                          "type": "SLIDER",
                          "min": 0,
                          "max": 100,
                          "step": 1
                        }]
                      }]
                    }
                    """.formatted(PUBLIC_KEY));
            fail("Expected config parsing to fail");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "SYNCABLE command 'volume' requires a read command",
                    expected.getMessage());
        }
    }

    private AppConfig load(String json) throws Exception {
        Path config = temporaryFolder.newFile("app.json").toPath();
        Files.writeString(config, json);
        String previous = System.getProperty("remote.config");
        System.setProperty("remote.config", config.toString());
        try {
            return AppConfig.load();
        } finally {
            if (previous == null) {
                System.clearProperty("remote.config");
            } else {
                System.setProperty("remote.config", previous);
            }
        }
    }
}
