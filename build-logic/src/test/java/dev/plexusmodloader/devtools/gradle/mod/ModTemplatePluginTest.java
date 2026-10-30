package dev.plexusmodloader.devtools.gradle.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModTemplatePluginTest {

    @Test
    void pluginVersionIsReplaced() {
        assertEquals(
                "    id(\"dev.polymodloader.mod\") version \"0.2.0\"",
                ModTemplatePlugin.withPluginVersion("    id(\"dev.polymodloader.mod\") version \"0.1.0-alpha.2\"", "0.2.0"));
        assertEquals("group = \"com.example\"", ModTemplatePlugin.withPluginVersion("group = \"com.example\"", "0.2.0"));
    }
}
