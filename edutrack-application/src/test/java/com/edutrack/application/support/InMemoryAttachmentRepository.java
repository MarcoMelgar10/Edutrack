package com.edutrack.application.support;

import com.edutrack.domain.attachment.Attachment;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.attachment.AttachmentRepository;
import com.edutrack.domain.record.RecordOwner;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class InMemoryAttachmentRepository implements AttachmentRepository {

    private final Map<AttachmentId, Attachment> attachments = new LinkedHashMap<>();
    private boolean failOnSave;

    @Override
    public Attachment save(Attachment attachment) {
        if (failOnSave) {
            throw new IllegalStateException("database is down");
        }
        attachments.put(attachment.id(), attachment);
        return attachment;
    }

    @Override
    public Optional<Attachment> findById(AttachmentId id) {
        return Optional.ofNullable(attachments.get(id));
    }

    @Override
    public void delete(AttachmentId id) {
        attachments.remove(id);
    }

    @Override
    public List<Attachment> findByOwner(RecordOwner owner) {
        return attachments.values().stream()
                .filter(attachment -> attachment.owner().equals(owner))
                .sorted(Comparator.comparing(Attachment::addedAt).reversed())
                .toList();
    }

    @Override
    public Set<String> allStoredPaths() {
        return attachments.values().stream().map(Attachment::storedPath).collect(Collectors.toSet());
    }

    /** Simulates the database refusing saves, to test cleanup of the copied file. */
    public void failOnSave(boolean fail) {
        this.failOnSave = fail;
    }

    public int size() {
        return attachments.size();
    }
}
