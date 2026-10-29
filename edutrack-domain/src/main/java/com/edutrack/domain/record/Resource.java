package com.edutrack.domain.record;

import com.edutrack.domain.shared.Validation;
import com.edutrack.domain.shared.WebLinks;

import java.util.Objects;

/** A source used to learn a step: a course, article, video, book… with an optional link. */
public final class Resource {

    public static final int TITLE_MAX_LENGTH = 160;

    private final ResourceId id;
    private String title;
    private String url;
    private ResourceType type;
    private boolean reviewed;

    private Resource(ResourceId id, ResourceDetails details, boolean reviewed) {
        this.id = Objects.requireNonNull(id, "id");
        update(details);
        this.reviewed = reviewed;
    }

    static Resource create(ResourceDetails details) {
        return new Resource(ResourceId.newId(), details, false);
    }

    public static Resource restore(ResourceId id, String title, String url, ResourceType type, boolean reviewed) {
        return new Resource(id, new ResourceDetails(title, url, type), reviewed);
    }

    /** All-or-nothing: every value is validated before any field changes. */
    void update(ResourceDetails details) {
        Objects.requireNonNull(details, "details");
        String validTitle = Validation.requiredText(details.title(), "Title", TITLE_MAX_LENGTH);
        String validUrl = WebLinks.normalize(details.url(), "Link");
        ResourceType validType = Validation.required(details.type(), "Type");
        this.title = validTitle;
        this.url = validUrl;
        this.type = validType;
    }

    void markReviewed(boolean reviewed) {
        this.reviewed = reviewed;
    }

    public ResourceId id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** Normalized http(s) link, or {@code null} for sources without one (e.g. a printed book). */
    public String url() {
        return url;
    }

    public ResourceType type() {
        return type;
    }

    public boolean reviewed() {
        return reviewed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Resource resource && id.equals(resource.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
