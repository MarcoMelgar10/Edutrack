package com.edutrack.domain.record;

/** The kind of source a resource is. Stored by name. */
public enum ResourceType {
    COURSE,
    ARTICLE,
    VIDEO,
    BOOK,
    DOCUMENTATION,
    PODCAST,
    REPOSITORY,
    TOOL,
    COMMUNITY,
    /** A mentor or teacher; the link may be a profile page or empty. */
    PERSON,
    OTHER
}
