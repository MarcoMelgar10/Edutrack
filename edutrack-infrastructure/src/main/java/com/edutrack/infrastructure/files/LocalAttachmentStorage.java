package com.edutrack.infrastructure.files;

import com.edutrack.application.attachment.AttachmentStorage;
import com.edutrack.domain.attachment.AttachmentId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keeps attached files on the local disk at {@code <root>/<attachmentId>/<sanitized name>}. The root
 * is the {@code edutrack.files-dir} property (default {@code ~/.edutrack/files}). Every path handed
 * in is resolved against the root and refused if it would leave it.
 */
@Component
public class LocalAttachmentStorage implements AttachmentStorage {

    static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_MIME_LENGTH = 120;
    private static final int MAX_EXTENSION_LENGTH = 20;
    private static final Pattern INVALID_CHARACTERS = Pattern.compile("[<>:\"/\\\\|?*\\p{Cntrl}]");
    private static final Pattern RESERVED_NAME = Pattern.compile("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");

    private final Path root;

    @Autowired
    public LocalAttachmentStorage(@Value("${edutrack.files-dir:${user.home}/.edutrack/files}") String root) {
        this(Path.of(root));
    }

    public LocalAttachmentStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(Path source, AttachmentId id, String originalName) {
        Path directory = inside(root.resolve(id.toString()));
        Path target = inside(directory.resolve(sanitize(originalName)));
        try {
            Files.createDirectories(directory);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(relative(target), Files.size(target), mimeTypeOf(target));
        } catch (IOException failure) {
            deleteQuietly(target);
            throw new UncheckedIOException("Cannot store " + source, failure);
        }
    }

    @Override
    public Path resolve(String storedPath) {
        return inside(root.resolve(storedPath));
    }

    @Override
    public void delete(String storedPath) {
        Path file = resolve(storedPath);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + file, e);
        }
        removeIfEmpty(file.getParent());
    }

    @Override
    public Set<String> listStored() {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).map(this::relative).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + root, e);
        }
    }

    /**
     * Makes a name safe on every file system: no path parts, no characters Windows rejects, no
     * reserved device names, no trailing dots or spaces, at most 120 characters with the extension kept.
     */
    static String sanitize(String originalName) {
        String name = originalName == null ? "" : originalName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = INVALID_CHARACTERS.matcher(name).replaceAll("_").strip();
        name = name.replaceAll("[. ]+$", "");
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
            name = "file";
        }
        if (RESERVED_NAME.matcher(name).matches()) {
            name = "_" + name;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 && name.length() - dot <= MAX_EXTENSION_LENGTH ? name.substring(dot) : "";
            name = name.substring(0, MAX_NAME_LENGTH - extension.length()) + extension;
        }
        return name;
    }

    private Path inside(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IllegalArgumentException("Path is outside the files folder: " + path);
        }
        return normalized;
    }

    private String relative(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String mimeTypeOf(Path file) {
        try {
            String type = Files.probeContentType(file);
            return type == null || type.length() > MAX_MIME_LENGTH ? null : type.toLowerCase(Locale.ROOT);
        } catch (IOException | RuntimeException unknown) {
            return null;
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException | RuntimeException ignored) {
            // best effort: a leftover partial copy is removed as an orphan at the next start
        }
        removeIfEmpty(file.getParent());
    }

    /** Removes the per-attachment folder once its file is gone; never the root itself. */
    private void removeIfEmpty(Path directory) {
        if (directory == null || directory.equals(root) || !directory.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(directory);
        } catch (DirectoryNotEmptyException stillUsed) {
            // other files remain
        } catch (IOException ignored) {
            // an empty folder left behind is harmless
        }
    }
}
