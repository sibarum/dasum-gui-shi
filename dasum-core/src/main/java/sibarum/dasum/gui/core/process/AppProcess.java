package sibarum.dasum.gui.core.process;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Spawns a fresh DasumGUIshi window as its own independent OS process — the
 * "separate process" answer to multi-window. Each spawned process runs its
 * own {@code glfwInit} / event loop / GL context and outlives its parent: the
 * framework stays {@linkplain sibarum.dasum.gui.core.window.Window single-window}
 * <em>per process</em>, and the OS is the only thing coordinating the windows.
 *
 * <p>{@link #spawnSelf} re-launches the current program, reconstructing the
 * launch command for whichever runtime we're in:
 * <ul>
 *   <li><b>Native image</b> — re-exec the current binary
 *       ({@link ProcessHandle#info()}), which has a single fixed entry point.</li>
 *   <li><b>JVM (development)</b> — re-invoke {@code java} with the parent's
 *       JVM flags (so {@code --enable-native-access=ALL-UNNAMED} and any
 *       {@code -D}/{@code -X} options carry over), the parent's classpath, and
 *       the parent's main class.</li>
 * </ul>
 *
 * <p>{@link #spawn} launches a <em>different</em> executable (e.g. a separate
 * CLI binary), forwarding program arguments; {@link #spawnDetached} is the raw
 * command-line escape hatch both build on.
 *
 * <p>Spawned processes are independent — there is no IPC here. A debug-port
 * connection between processes is a planned, separate layer.
 */
public final class AppProcess {

    private AppProcess() {}

    /** True when running as a GraalVM native image rather than on a JVM. */
    public static boolean isNativeImage() {
        // Set to "runtime" by the image at execution time; absent on a JVM.
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    /**
     * Re-launch this same program as a new, independent process — its own
     * top-level window. {@code args} are passed to the new process's
     * {@code main} (e.g. a start path), <em>replacing</em> this process's
     * original arguments rather than appending to them.
     *
     * <p>The child is detached: it keeps running after this process exits, and
     * its stdout/stderr inherit this console so its logs stay visible during
     * development.
     *
     * @return the started process; callers may ignore it
     */
    public static Process spawnSelf(String... args) {
        return spawnDetached(buildSelfCommand(args));
    }

    /**
     * Launch a different executable — e.g. a separate CLI binary — as its own
     * detached, independent process, forwarding {@code args} as program
     * arguments. Unlike {@link #spawnSelf}, this does not re-exec the current
     * program: {@code executable} is the path/name of the binary to run.
     *
     * @return the started process; callers may ignore it
     */
    public static Process spawn(String executable, String... args) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        addArgs(command, args);
        return spawnDetached(command);
    }

    /**
     * Start a fully-detached child from a raw command line. stdout/stderr/stdin
     * inherit this console so the child's logs stay visible during development;
     * on every supported OS the child keeps running after this process exits.
     */
    public static Process spawnDetached(List<String> command) {
        try {
            return new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to spawn process: " + command, e);
        }
    }

    // --- command construction (package-visible for tests) ---

    static List<String> buildSelfCommand(String... args) {
        if (isNativeImage()) {
            String exe = ProcessHandle.current().info().command().orElseThrow(
                () -> new IllegalStateException("Cannot determine current executable path for re-exec"));
            List<String> cmd = new ArrayList<>();
            cmd.add(exe);
            addArgs(cmd, args);
            return cmd;
        }
        // Reconstruct how this JVM was entered — either `-jar X.jar` or a bare
        // main class on the classpath — from sun.java.command (its first token).
        String javaCommand = System.getProperty("sun.java.command");
        String entry = (javaCommand == null) ? null : javaCommand.split("\\s+", 2)[0];
        if (entry == null || entry.isBlank()) {
            throw new IllegalStateException(
                "Cannot determine the current main class or jar (sun.java.command is unavailable).");
        }
        List<String> entryArgs = entry.endsWith(".jar")
            ? List.of("-jar", entry)
            : List.of("-cp", System.getProperty("java.class.path"), entry);

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBinary());
        // Carry over the parent's JVM flags (--enable-native-access, -X, -D, ...).
        // getInputArguments() excludes -cp and the main class, so no duplication
        // with the entry args below.
        cmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        cmd.addAll(entryArgs);
        addArgs(cmd, args);
        return cmd;
    }

    private static String javaBinary() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return System.getProperty("java.home") + File.separator + "bin"
             + File.separator + (windows ? "java.exe" : "java");
    }

    private static void addArgs(List<String> cmd, String... args) {
        if (args != null) {
            for (String a : args) cmd.add(a);
        }
    }
}
