package com.edutrack.application.support;

import com.edutrack.application.attachment.AttachmentStorage;
import com.edutrack.domain.attachment.AttachmentId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Remembers what was "stored" (path and size) without copying bytes. */
public class InMemoryAttachmentStorage implements AttachmentStorage {

    private final Path root = Path.of("files-root");
    private final Map<String, Long> stored = new LinkedHashMap<>();

    @Override
    public StoredFile store(Path source, AttachmentId id, String originalName) {
        try {
            long size = Files.size(source);
            String storedPath = id + "/" + originalName;
            stored.put(storedPath, size);
            return new StoredFile(storedPath, size, "text/plain");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Path resolve(String storedPath) {
        return root.resolve(storedPath);
    }

    @Override
    public void delete(String storedPath) {
        stored.remove(storedPath);
    }

    @Override
    public Set<String> listStored() {
        return Set.copyOf(stored.keySet());
    }

    /** A file that is on disk but that no row knows about (left over from a deleted goal). */
    public void plantOrphan(String storedPath) {
        stored.put(storedPath, 1L);
    }

    public int count() {
        return stored.size();
    }

    public boolean contains(String storedPath) {
        return stored.containsKey(storedPath);
    }
}
