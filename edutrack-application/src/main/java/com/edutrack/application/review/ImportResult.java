package com.edutrack.application.review;

import java.util.List;

/** The outcome of importing cards from text: how many were created and why the other lines were not ("line 3: …"). */
public record ImportResult(int imported, List<String> skipped) {

    public ImportResult {
        skipped = List.copyOf(skipped);
    }
}
