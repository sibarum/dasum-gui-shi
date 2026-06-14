package sibarum.dasum.gui.files;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * One row in a directory listing — a snapshot of a filesystem entry taken
 * at scan time. Immutable; {@link DirectoryModel} rebuilds the whole list
 * on navigation / refresh rather than mutating entries in place.
 *
 * <p>{@code parentLink} marks the synthetic ".." row prepended to a listing
 * when the current directory has a parent. It carries the parent's path so
 * activating it navigates up without special-casing the name string.
 *
 * @param path        absolute path to the entry (the parent dir for ".."")
 * @param name        display name (the file name, or ".." for the parent link)
 * @param dir         whether the entry is a directory
 * @param parentLink  whether this is the synthetic ".." row
 * @param size        size in bytes; {@code -1} for directories / unknown
 * @param modified    last-modified time; {@code null} if unreadable
 * @param type        short type label ("Folder", extension, or "File")
 */
public record Entry(
    Path path, String name, boolean dir, boolean parentLink,
    long size, FileTime modified, String type
) {

    /** A directory row. */
    public static Entry directory(Path path, String name, FileTime modified) {
        return new Entry(path, name, true, false, -1L, modified, "Folder");
    }

    /** A regular-file row. */
    public static Entry file(Path path, String name, long size, FileTime modified, String type) {
        return new Entry(path, name, false, false, size, modified, type);
    }

    /** The synthetic ".." row pointing at {@code parent}. */
    public static Entry parent(Path parent) {
        return new Entry(parent, "..", true, true, -1L, null, "Parent");
    }
}
