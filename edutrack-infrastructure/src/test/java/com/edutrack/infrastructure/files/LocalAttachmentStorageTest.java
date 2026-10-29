package com.edutrack.infrastructure.files;

import com.edutrack.application.attachment.AttachmentStorage.StoredFile;
import com.edutrack.domain.attachment.AttachmentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAttachmentStorageTest {

    @TempDir
    Path tmp;

    private Path root;
    private LocalAttachmentStorage storage;

    @BeforeEach
    void createStorage() {
        root = tmp.resolve("files");
        storage = new LocalAttachmentStorage(root);
    }

    private Path source(String name, String content) throws IOException {
        Path folder = Files.createDirectories(tmp.resolve("source"));
        return Files.writeString(folder.resolve(name), content);
    }

    @Test
    void storeCopiesTheFileUnderTheAttachmentId() throws IOException {
        AttachmentId id = AttachmentId.newId();
        Path original = source("certificate.txt", "hello");

        StoredFile stored = storage.store(original, id, "certificate.txt");

        assertThat(stored.storedPath()).isEqualTo(id + "/certificate.txt");
        assertThat(stored.sizeBytes()).isEqualTo(5);
        assertThat(Files.readString(storage.resolve(stored.storedPath()))).isEqualTo("hello");
        assertThat(original).exists();
        assertThat(storage.resolve(stored.storedPath())).startsWith(root.toAbsolutePath());
    }

    @Test
    void mimeTypeIsKnownForCommonFilesAndNeverBlowsUpOtherwise() throws IOException {
        StoredFile text = storage.store(source("a.txt", "x"), AttachmentId.newId(), "a.txt");
        StoredFile unknown = storage.store(source("b.zzzunknown", "x"), AttachmentId.newId(), "b.zzzunknown");

        assertThat(text.mimeType()).isIn("text/plain", null);
        assertThat(unknown.mimeType()).isNull();
    }

    @Test
    void theStoredNameIsSanitized() throws IOException {
        AttachmentId id = AttachmentId.newId();

        StoredFile stored = storage.store(source("x.txt", "x"), id, "..\\..\\evil:name?.txt");

        assertThat(stored.storedPath()).isEqualTo(id + "/evil_name_.txt");
        assertThat(storage.resolve(stored.storedPath())).exists();
    }

    @Test
    void sanitizeHandlesAwkwardNames() {
        assertThat(LocalAttachmentStorage.sanitize("C:\\Users\\me\\report.pdf")).isEqualTo("report.pdf");
        assertThat(LocalAttachmentStorage.sanitize("a/b/c.png")).isEqualTo("c.png");
        assertThat(LocalAttachmentStorage.sanitize("what*is|this<>.txt")).isEqualTo("what_is_this__.txt");
        assertThat(LocalAttachmentStorage.sanitize("notes.txt. . ")).isEqualTo("notes.txt");
        assertThat(LocalAttachmentStorage.sanitize("..")).isEqualTo("file");
        assertThat(LocalAttachmentStorage.sanitize("  ")).isEqualTo("file");
        assertThat(LocalAttachmentStorage.sanitize(null)).isEqualTo("file");
        assertThat(LocalAttachmentStorage.sanitize("CON.txt")).isEqualTo("_CON.txt");
        assertThat(LocalAttachmentStorage.sanitize("nul")).isEqualTo("_nul");
        assertThat(LocalAttachmentStorage.sanitize("tab\there.txt")).isEqualTo("tab_here.txt");
    }

    @Test
    void longNamesAreShortenedKeepingTheExtension() {
        String sanitized = LocalAttachmentStorage.sanitize("x".repeat(300) + ".pdf");

        assertThat(sanitized).hasSize(120).endsWith(".pdf");
    }

    @Test
    void resolveRefusesPathsOutsideTheRoot() {
        assertThatThrownBy(() -> storage.resolve("../outside.txt")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolve("a/../../outside.txt")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("../outside.txt")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRemovesTheFileAndItsFolder() throws IOException {
        AttachmentId id = AttachmentId.newId();
        StoredFile stored = storage.store(source("a.txt", "a"), id, "a.txt");

        storage.delete(stored.storedPath());

        assertThat(storage.resolve(stored.storedPath())).doesNotExist();
        assertThat(root.resolve(id.toString())).doesNotExist();
        assertThat(root).exists();
    }

    @Test
    void deletingAMissingFileIsFine() {
        storage.delete(AttachmentId.newId() + "/ghost.txt");
    }

    @Test
    void listStoredReturnsRelativePathsWithForwardSlashes() throws IOException {
        assertThat(storage.listStored()).isEmpty();
        AttachmentId first = AttachmentId.newId();
        AttachmentId second = AttachmentId.newId();
        storage.store(source("a.txt", "a"), first, "a.txt");
        storage.store(source("b.txt", "b"), second, "b.txt");

        assertThat(storage.listStored()).containsExactlyInAnyOrder(first + "/a.txt", second + "/b.txt");
    }

    @Test
    void storingFromAMissingSourceFailsWithoutLeavingFiles() {
        AttachmentId id = AttachmentId.newId();

        assertThatThrownBy(() -> storage.store(tmp.resolve("missing.txt"), id, "missing.txt"))
                .isInstanceOf(java.io.UncheckedIOException.class);

        assertThat(storage.listStored()).isEmpty();
        assertThat(root.resolve(id.toString())).doesNotExist();
    }
}
