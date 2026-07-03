package sibarum.dasum.gui.core.process;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AppProcessTest {

    @Test
    void runsAsJvmUnderTests() {
        assertFalse(AppProcess.isNativeImage(),
            "the test suite runs on a JVM, never as a native image");
    }

    @Test
    void selfCommandReExecsAClassOrJarWithTrailingArgs() {
        List<String> cmd = AppProcess.buildSelfCommand("workspace");

        assertTrue(cmd.get(0).contains("java"), "first token is the java binary: " + cmd.get(0));
        assertEquals("workspace", cmd.get(cmd.size() - 1), "passed args land at the end");
        assertTrue(cmd.contains("-cp") || cmd.contains("-jar"),
            "self re-exec uses either a classpath+main or a -jar entry: " + cmd);
    }

    @Test
    void selfCommandPropagatesNativeAccessFlagWhenParentHasIt() {
        // The parent's JVM flags must carry over so the Panama FFM downcalls
        // keep working in the child. Only assert it when the test JVM itself
        // was launched with the flag, so the test stays environment-robust.
        boolean parentHasFlag = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
            .anyMatch(f -> f.startsWith("--enable-native-access"));
        if (parentHasFlag) {
            assertTrue(AppProcess.buildSelfCommand().stream()
                    .anyMatch(f -> f.startsWith("--enable-native-access")),
                "child command should inherit --enable-native-access");
        }
    }
}
