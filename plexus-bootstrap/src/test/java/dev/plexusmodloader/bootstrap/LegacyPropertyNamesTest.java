package dev.plexusmodloader.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class LegacyPropertyNamesTest {

    @Test
    void formerNamesAreCopiedToTheCurrentOnes() {
        Properties properties = new Properties();
        properties.setProperty("poly.modsDir", "old-mods");
        properties.setProperty("poly.earlyWindow", "false");

        LegacyPropertyNames.copyToCurrentNames(properties);

        assertEquals("old-mods", properties.getProperty("plexus.modsDir"));
        assertEquals("false", properties.getProperty("plexus.earlyWindow"));
    }

    @Test
    void aCurrentNameWins() {
        Properties properties = new Properties();
        properties.setProperty("poly.modsDir", "old-mods");
        properties.setProperty("plexus.modsDir", "mods");

        LegacyPropertyNames.copyToCurrentNames(properties);

        assertEquals("mods", properties.getProperty("plexus.modsDir"));
    }

    @Test
    void otherPropertiesAreLeftAlone() {
        Properties properties = new Properties();
        properties.setProperty("polymer.debug", "true");

        LegacyPropertyNames.copyToCurrentNames(properties);

        assertNull(properties.getProperty("plexus.mer.debug"));
        assertEquals(1, properties.size());
    }
}
