package dev.polymodloader.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EarlyWindowProtocolTest {

    @Test
    void everyCommandReadsBackAsWritten() {
        assertEquals(new EarlyWindowProtocol.Show(), parse(EarlyWindowProtocol.show()));
        assertEquals(new EarlyWindowProtocol.Close(), parse(EarlyWindowProtocol.close()));
        assertEquals(
                new EarlyWindowProtocol.Status("Loading 3 mods"), parse(EarlyWindowProtocol.status("Loading 3 mods")));
        assertEquals(new EarlyWindowProtocol.Progress(0.5f), parse(EarlyWindowProtocol.progress(0.5f)));
        assertEquals(
                new EarlyWindowProtocol.UnderBar(List.of("Mods: Fabric 2", "1 not loaded")),
                parse(EarlyWindowProtocol.underBar(List.of("Mods: Fabric 2", "1 not loaded"))));
        assertEquals(new EarlyWindowProtocol.UnderBar(List.of()), parse(EarlyWindowProtocol.underBar(List.of())));
        assertEquals(
                new EarlyWindowProtocol.Snapshot("C:\\a b\\s.png"),
                parse(EarlyWindowProtocol.snapshot("C:\\a b\\s.png")));
    }

    @Test
    void textStaysOnOneLine() {
        assertEquals(
                new EarlyWindowProtocol.Status("a b c"),
                parse(EarlyWindowProtocol.status("a\nb\r\nc").replace("  ", " ")));
        assertEquals(
                new EarlyWindowProtocol.UnderBar(List.of("a b", "c")),
                parse(EarlyWindowProtocol.underBar(List.of("a\tb", "c"))));
    }

    @Test
    void progressIsClampedAndUnknownLinesAreIgnored() {
        assertEquals(new EarlyWindowProtocol.Progress(1.0f), parse(EarlyWindowProtocol.progress(3.0f)));
        assertTrue(EarlyWindowProtocol.parse("progress x").isEmpty());
        assertTrue(EarlyWindowProtocol.parse("snapshot").isEmpty());
        assertTrue(EarlyWindowProtocol.parse("hello").isEmpty());
        assertTrue(EarlyWindowProtocol.parse("").isEmpty());
    }

    @Test
    void progressNeverGoesBack() {
        LoadingScreenState state =
                LoadingScreenState.initial().withProgress(0.6f).withProgress(0.2f);
        assertEquals(0.6f, state.progress());
    }

    private static EarlyWindowProtocol.Command parse(String line) {
        return EarlyWindowProtocol.parse(line).orElseThrow();
    }
}
