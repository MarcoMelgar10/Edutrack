package com.edutrack.desktop.ui.common;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The language the interface currently speaks, reachable from static helpers such as {@link Formats}.
 * {@link Messages} is the injectable face of the same state. English until {@link #use(Language)} is
 * called, which keeps unit tests independent of the system language.
 */
public final class I18n {

    private static final String BUNDLE = "i18n.messages";

    private static volatile Language language = Language.ENGLISH;
    private static volatile ResourceBundle bundle = load(Language.ENGLISH);

    private I18n() {
    }

    public static void use(Language next) {
        bundle = load(next);
        language = next;
    }

    /**
     * {@link #use(Language)} for the running application: also makes the language the JVM default, which
     * is what JavaFX's own controls (date picker months, standard button names) read.
     */
    public static void activate(Language next) {
        use(next);
        Locale.setDefault(next.locale());
    }

    public static Language language() {
        return language;
    }

    public static Locale locale() {
        return language.locale();
    }

    public static ResourceBundle bundle() {
        return bundle;
    }

    public static boolean has(String key) {
        return bundle.containsKey(key);
    }

    /**
     * The text for {@code key}. Without arguments the text is returned as written; with arguments it is a
     * {@link MessageFormat} pattern ({@code {0}}, and {@code ''} for a literal apostrophe).
     * A missing key shows the key itself, so a gap is visible instead of fatal.
     */
    public static String get(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException missing) {
            return key;
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale()).format(args);
    }

    /** "1 resource" / "3 resources": picks {@code key.one} or {@code key.other}; {0} is the count. */
    public static String plural(String key, long count, Object... more) {
        Object[] args = new Object[more.length + 1];
        args[0] = count;
        System.arraycopy(more, 0, args, 1, more.length);
        return get(key + (count == 1 ? ".one" : ".other"), args);
    }

    /** Never falls back to the system language: an English request must not pick up the Spanish file. */
    private static ResourceBundle load(Language language) {
        return ResourceBundle.getBundle(BUNDLE, language.locale(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }
}
