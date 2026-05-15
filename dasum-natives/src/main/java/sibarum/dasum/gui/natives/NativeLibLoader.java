package sibarum.dasum.gui.natives;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Locates a native library either as a bundled classpath resource
 * ({@code /natives/windows-x64/<name>.dll}) extracted to a temp directory,
 * or via the system loader on PATH / {@code java.library.path}. Loading is
 * idempotent per library name.
 *
 * The returned Path is the resolved file path when the library came from a
 * bundled resource, or {@code null} when the system loader was used (in
 * which case callers must look up symbols by library name, not path).
 */
@SuppressWarnings("restricted")
public final class NativeLibLoader {

    private static final Map<String, Path> LOADED = new HashMap<>();

    private NativeLibLoader() {}

    public static synchronized Path load(String libName) {
        if (LOADED.containsKey(libName)) return LOADED.get(libName);

        Path resolved = null;
        String resource = "/natives/windows-x64/" + libName + ".dll";
        try (InputStream in = NativeLibLoader.class.getResourceAsStream(resource)) {
            if (in != null) {
                Path tmpDir = Files.createTempDirectory("dasum-natives-");
                tmpDir.toFile().deleteOnExit();
                Path dllPath = tmpDir.resolve(libName + ".dll");
                Files.copy(in, dllPath, StandardCopyOption.REPLACE_EXISTING);
                dllPath.toFile().deleteOnExit();
                System.load(dllPath.toAbsolutePath().toString());
                resolved = dllPath.toAbsolutePath();
            } else {
                System.loadLibrary(libName);
            }
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract " + libName + ": " + e.getMessage());
        }

        LOADED.put(libName, resolved);
        return resolved;
    }
}
