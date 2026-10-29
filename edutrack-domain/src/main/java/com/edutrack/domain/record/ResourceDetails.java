package com.edutrack.domain.record;

/** A resource as typed in the Resource dialog. Validated by {@link Resource}. */
public record ResourceDetails(String title, String url, ResourceType type) {
}
