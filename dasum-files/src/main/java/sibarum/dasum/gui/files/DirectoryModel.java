package sibarum.dasum.gui.files;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The browser's navigation state: which directory is shown, the listing of
 * its entries, and the toggles that shape that listing (show-hidden). The
 * model is the single source of truth — the UI is rebuilt from it after any
 * mutation, and {@link DirectoryTableSource} reads {@link #entries()} live.
 *
 * <p>Listing policy: a synthetic ".." row first (when a parent exists), then
 * directories before files, each group sorted case-insensitively by name.
 * Hidden entries (dot-prefixed, or the OS hidden attribute) are filtered out
 * unless {@link #showHidden} is set.
 *
 * <p>Scanning is defensive: a directory we can't read (permission denied,
 * vanished mid-scan) yields an empty listing and a non-null {@link #error()}
 * message rather than throwing — the browser stays usable and the status bar
 * reports the problem.
 */
public final class DirectoryModel {

    private Path dir;
    private boolean showHidden = false;
    private List<Entry> entries = List.of();
    private String error = null;

    public DirectoryModel(Path start) {
        this.dir = start.toAbsolutePath().normalize();
        refresh();
    }

    public Path dir()           { return dir; }
    public List<Entry> entries(){ return entries; }
    public boolean showHidden() { return showHidden; }
    /** Non-null when the last scan failed; cleared on the next successful scan. */
    public String error()       { return error; }

    // ---------- navigation ----------

    /** Switch to {@code target} (must be a directory) and rescan. No-op if null. */
    public void navigateTo(Path target) {
        if (target == null) return;
        this.dir = target.toAbsolutePath().normalize();
        refresh();
    }

    /** Go to the parent directory, if any. */
    public void up() {
        Path parent = dir.getParent();
        if (parent != null) navigateTo(parent);
    }

    /** Jump to the user's home directory. */
    public void home() {
        navigateTo(Path.of(System.getProperty("user.home", ".")));
    }

    public void setShowHidden(boolean v) {
        if (v == showHidden) return;
        showHidden = v;
        refresh();
    }

    /** Convenience accessor used for double-click / Enter activation. */
    public Entry entryAt(int row) {
        return (row >= 0 && row < entries.size()) ? entries.get(row) : null;
    }

    // ---------- scanning ----------

    /** Re-read the current directory from disk and rebuild the entry list. */
    public void refresh() {
        error = null;
        List<Entry> dirs  = new ArrayList<>();
        List<Entry> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (!showHidden && isHidden(p)) continue;
                String name = nameOf(p);
                FileTime mtime = null;
                long size = -1L;
                boolean isDir;
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    isDir = attrs.isDirectory();
                    mtime = attrs.lastModifiedTime();
                    if (!isDir) size = attrs.size();
                } catch (IOException unreadable) {
                    // Can't stat this entry (e.g. a dangling symlink) — still
                    // list it, just without size/mtime, treated as a file.
                    isDir = Files.isDirectory(p);
                }
                if (isDir) {
                    dirs.add(Entry.directory(p, name, mtime));
                } else {
                    files.add(Entry.file(p, name, size, mtime, typeOf(name)));
                }
            }
        } catch (IOException | RuntimeException e) {
            // SecurityException, AccessDeniedException, NoSuchFileException, …
            error = describe(e);
            entries = parentPrefixed(List.of());
            return;
        }

        Comparator<Entry> byName = Comparator.comparing(
            e -> e.name().toLowerCase(Locale.ROOT));
        dirs.sort(byName);
        files.sort(byName);

        List<Entry> combined = new ArrayList<>(dirs.size() + files.size());
        combined.addAll(dirs);
        combined.addAll(files);
        entries = parentPrefixed(combined);
    }

    /** Prepend the ".." row when the current dir has a parent. */
    private List<Entry> parentPrefixed(List<Entry> body) {
        Path parent = dir.getParent();
        if (parent == null) return List.copyOf(body);
        List<Entry> out = new ArrayList<>(body.size() + 1);
        out.add(Entry.parent(parent));
        out.addAll(body);
        return List.copyOf(out);
    }

    private static boolean isHidden(Path p) {
        String n = nameOf(p);
        if (n.startsWith(".")) return true;
        try { return Files.isHidden(p); }
        catch (IOException e) { return false; }
    }

    /** {@code getFileName()} is null for filesystem roots (e.g. {@code C:\}). */
    private static String nameOf(Path p) {
        Path fn = p.getFileName();
        return fn != null ? fn.toString() : p.toString();
    }

    private static String typeOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "File";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String describe(Exception e) {
        String msg = e.getMessage();
        String kind = e.getClass().getSimpleName();
        return (msg == null || msg.isBlank()) ? kind : kind + ": " + msg;
    }
}
