package io.mcpm.core.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Simple internationalization support for mcpm messages.
 * <p>
 * Loads translations from {@code i18n/mcpm_XX.properties} files.
 * Default language is English; Chinese (zh) is bundled.
 * <p>
 * Usage:
 * <pre>
 *   String msg = Messages.get("install.complete");
 *   String msg = Messages.get("install.complete", "my-pkg");
 *   Messages.setLocale(Locale.CHINESE);
 * </pre>
 */
public class Messages {

    private static final String BUNDLE_BASE = "i18n.mcpm";
    private static Locale locale = detectLocale();
    private static ResourceBundle bundle = loadBundle();

    private Messages() {}

    /**
     * Set the locale for all subsequent message lookups.
     */
    public static void setLocale(Locale newLocale) {
        locale = newLocale;
        bundle = loadBundle();
    }

    /**
     * Get the current locale.
     */
    public static Locale getLocale() {
        return locale;
    }

    /**
     * Get a translated message by key.
     *
     * @return the translated message, or the key if not found
     */
    public static String get(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            if (args.length > 0) {
                return MessageFormat.format(pattern, args);
            }
            return pattern;
        } catch (MissingResourceException e) {
            // Fall back to English bundle
            try {
                ResourceBundle fallback = ResourceBundle.getBundle(
                        BUNDLE_BASE, Locale.ENGLISH);
                String pattern = fallback.getString(key);
                if (args.length > 0) {
                    return MessageFormat.format(pattern, args);
                }
                return pattern;
            } catch (MissingResourceException e2) {
                return key;
            }
        }
    }

    /**
     * Quick check if a key exists.
     */
    public static boolean hasKey(String key) {
        return bundle.containsKey(key);
    }

    private static ResourceBundle loadBundle() {
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE, locale);
        } catch (MissingResourceException e) {
            return ResourceBundle.getBundle(BUNDLE_BASE, Locale.ENGLISH);
        }
    }

    private static Locale detectLocale() {
        // Check mcpm config first, then system locale
        try {
            var settings = new io.mcpm.core.settings.SettingsManager();
            String lang = settings.get("lang");
            if (lang != null) {
                return Locale.forLanguageTag(lang);
            }
        } catch (Exception ignored) {}
        return Locale.getDefault();
    }
}
