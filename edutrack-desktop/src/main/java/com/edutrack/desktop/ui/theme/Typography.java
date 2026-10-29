package com.edutrack.desktop.ui.theme;

import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Registers the bundled Inter font files with JavaFX so stylesheets can refer to them by family name. */
public final class Typography {

    private static final Logger log = LoggerFactory.getLogger(Typography.class);
    private static final List<String> FONT_FILES =
            List.of("Inter-Regular.ttf", "Inter-Medium.ttf", "Inter-SemiBold.ttf", "Inter-Italic.ttf");

    private Typography() {
    }

    public static void loadBundledFonts() {
        for (String file : FONT_FILES) {
            try (InputStream in = Typography.class.getResourceAsStream("/fonts/" + file)) {
                if (in == null || Font.loadFont(in, 13) == null) {
                    log.warn("Could not load bundled font {}; falling back to the system font", file);
                }
            } catch (IOException e) {
                log.warn("Could not read bundled font {}", file, e);
            }
        }
    }
}
