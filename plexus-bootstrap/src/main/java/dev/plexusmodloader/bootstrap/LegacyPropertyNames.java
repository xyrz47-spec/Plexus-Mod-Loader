package dev.plexusmodloader.bootstrap;

import java.util.Properties;

/**
 * Keeps launch settings written for 1.4.x working: every system property {@code poly.<name>} is copied to {@code
 * plexus.<name>} unless that is set too. Runs first in {@link PlexusLauncherMain#main}, before anything reads a
 * property.
 */
final class LegacyPropertyNames {

    private static final String FORMER_PREFIX = "poly.";
    private static final String CURRENT_PREFIX = "plexus.";

    private LegacyPropertyNames() {}

    static void copyToCurrentNames(Properties properties) {
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(FORMER_PREFIX)) {
                String current = CURRENT_PREFIX + name.substring(FORMER_PREFIX.length());
                if (properties.getProperty(current) == null) {
                    properties.setProperty(current, properties.getProperty(name));
                }
            }
        }
    }
}
