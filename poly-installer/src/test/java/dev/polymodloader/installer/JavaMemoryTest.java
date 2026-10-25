package dev.polymodloader.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class JavaMemoryTest {

    @Test
    void parsesGigabytesMegabytesAndBareNumbers() {
        assertEquals(new JavaMemory(4096, 1024), JavaMemory.parse("4G", "1g"));
        assertEquals(new JavaMemory(3072, 0), JavaMemory.parse(" 3072M ", ""));
        assertEquals(new JavaMemory(2048, 512), JavaMemory.parse("2048", "512mb"));
        assertTrue(JavaMemory.parse(null, " ").isLauncherDefault());
    }

    @Test
    void refusesNonsenseTooLittleAndAMinimumAboveTheMaximum() {
        assertThrows(IllegalArgumentException.class, () -> JavaMemory.parse("lots", ""));
        assertThrows(IllegalArgumentException.class, () -> JavaMemory.parse("512M", ""));
        IllegalArgumentException above =
                assertThrows(IllegalArgumentException.class, () -> JavaMemory.parse("2G", "4G"));
        assertTrue(above.getMessage().contains("larger than the maximum"), above.getMessage());
    }

    @Test
    void replacesOnlyTheMemoryArgumentsOfTheProfile() {
        JavaMemory memory = JavaMemory.parse("6G", "1536M");

        assertEquals(List.of("-Xms1536M", "-Xmx6G"), memory.arguments());
        assertEquals("-Xms1536M -Xmx6G", memory.mergeInto(null));
        assertEquals(
                "-XX:+UseG1GC -Dfoo=bar -Xms1536M -Xmx6G", memory.mergeInto("-Xmx2G -XX:+UseG1GC  -Xms512M -Dfoo=bar"));
        assertEquals("-Xmx4G", JavaMemory.parse("4G", "").mergeInto("-Xms1G"));
    }

    @Test
    void warnsAboutMoreMemoryThanThePcHasAndSuggestsASize() {
        assertTrue(JavaMemory.parse("16G", "").warning(OptionalLong.of(8000)).contains("more than this PC has"));
        assertNull(JavaMemory.parse("4G", "").warning(OptionalLong.of(16000)));
        assertNull(JavaMemory.parse("16G", "").warning(OptionalLong.empty()));
        assertEquals(6144, JavaMemory.recommendedMaximum(OptionalLong.of(32 * 1024)));
        assertEquals(4096, JavaMemory.recommendedMaximum(OptionalLong.of(7900)));
        assertEquals(0, JavaMemory.recommendedMaximum(OptionalLong.of(4096)));
        assertEquals("1536M", JavaMemory.format(1536));
    }
}
