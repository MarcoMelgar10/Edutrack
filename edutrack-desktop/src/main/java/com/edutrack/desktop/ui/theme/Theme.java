package com.edutrack.desktop.ui.theme;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;

import java.net.URL;
import java.util.Objects;

/**
 * The two appearances of the single EduTrack identity.
 *
 * <p>Each theme pairs an AtlantaFX base (control rendering) with a token stylesheet that only
 * sets the {@code -et-*} colors. Everything else — layout, shapes, typography, icons — comes from
 * {@link #BASE_CSS} and is identical in both themes.
 */
public enum Theme {

    LIGHT(new PrimerLight().getUserAgentStylesheet(), "/theme/tokens-light.css"),
    DARK(new PrimerDark().getUserAgentStylesheet(), "/theme/tokens-dark.css");

    /** Shared component styles; maps AtlantaFX variables onto the {@code -et-*} tokens. */
    public static final String BASE_CSS = resource("/theme/base.css");

    /** Styles of the features built on top of the base components; loaded right after {@link #BASE_CSS}. */
    public static final String FEATURES_CSS = resource("/theme/features.css");

    private final String userAgentStylesheet;
    private final String tokensCss;

    Theme(String userAgentStylesheet, String tokensPath) {
        this.userAgentStylesheet = userAgentStylesheet;
        this.tokensCss = resource(tokensPath);
    }

    public String userAgentStylesheet() {
        return userAgentStylesheet;
    }

    public String tokensCss() {
        return tokensCss;
    }

    public Theme opposite() {
        return this == LIGHT ? DARK : LIGHT;
    }

    private static String resource(String path) {
        URL url = Objects.requireNonNull(Theme.class.getResource(path), () -> "Missing stylesheet " + path);
        return url.toExternalForm();
    }
}
