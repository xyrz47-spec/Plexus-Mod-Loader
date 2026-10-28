package dev.polymodloader.compat.forge;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * The title of the early loading window of MinecraftForge and NeoForge names Poly Mod Loader, as the game window's does
 * after it ({@code Minecraft* Poly <version>}, the game layers' {@code PolyWindowTitle}):
 *
 * <ul>
 *   <li>NeoForge's {@code earlydisplay} (FML 11 and 12) creates its window as {@code Minecraft: NeoForge Loading...};
 *       that constant becomes {@code Minecraft* Poly <version>}.
 *   <li>MinecraftForge's {@code fmlearlydisplay} names it {@code "Minecraft* "} (with the space) followed by the game's
 *       version; that constant becomes {@code "Minecraft* Poly "}.
 * </ul>
 *
 * <p>Each constant is changed only when {@code DisplayWindow} has it exactly once (checked with {@code javap} against
 * FML 11.0.16 and 12.0.12 and MinecraftForge 26.1-62.0.9, 26.2-65.1.5 and 26.3-66.1.0); otherwise the class stays as it is and the log says so. The
 * error dialogs' titles ({@code Minecraft: NeoForge}, {@code Minecraft: Forge}) stay the hosts'.
 *
 * <p>Thread: the launch thread, while the host defines its modules.
 */
final class EarlyWindowTitle {

    static final String NEOFORGE_CLASS = "net/neoforged/fml/earlydisplay/DisplayWindow.class";
    static final String NEOFORGE_TITLE = "Minecraft: NeoForge Loading...";
    static final String FORGE_CLASS = "net/minecraftforge/fml/earlydisplay/DisplayWindow.class";
    static final String FORGE_TITLE = "Minecraft* ";

    private EarlyWindowTitle() {}

    /**
     * {@code reference} with its early window titled for Poly Mod Loader when it is a host's early window module, else
     * {@code reference}.
     */
    static ModuleReference of(ModuleReference reference, String gameVersion, Consumer<String> log) {
        String classFile;
        String original;
        String replacement;
        try (ModuleReader reader = reference.open()) {
            if (reader.find(NEOFORGE_CLASS).isPresent()) {
                classFile = NEOFORGE_CLASS;
                original = NEOFORGE_TITLE;
                replacement = "Minecraft* Poly " + gameVersion;
            } else if (reader.find(FORGE_CLASS).isPresent()) {
                classFile = FORGE_CLASS;
                original = FORGE_TITLE;
                replacement = "Minecraft* Poly ";
            } else {
                return reference;
            }
        } catch (IOException e) {
            log.accept("Early loading window: " + reference.descriptor().name() + " could not be read (" + e
                    + "); its title stays the host's");
            return reference;
        }
        return PatchedClassReference.of(reference, classFile, bytes -> {
            byte[] patched = replaced(bytes, original, replacement);
            log.accept(
                    patched == bytes
                            ? "Early loading window: the title stays the host's (" + classFile + " does not have \""
                                    + original + "\" exactly once)"
                            : "Early loading window: titled \"" + replacement + "\"");
            return patched;
        });
    }

    /**
     * {@code bytes} with the one string constant {@code original} (loaded exactly once, by an {@code ldc}) reading
     * {@code replacement}; else {@code bytes}. The constant pool's UTF-8 entry of that text is rewritten in place: entries
     * are referred to by index, so no code changes and the stack maps stay valid. Both texts are ASCII.
     */
    static byte[] replaced(byte[] bytes, String original, String replacement) {
        ClassModel model = ClassFile.of().parse(bytes);
        int count = 0;
        for (MethodModel method : model.methods()) {
            if (method.code().isEmpty()) {
                continue;
            }
            for (CodeElement element : method.code().get()) {
                if (loads(element, original)) {
                    count++;
                }
            }
        }
        if (count != 1 || !ascii(original) || !ascii(replacement)) {
            return bytes;
        }
        byte[] wanted = original.getBytes(StandardCharsets.US_ASCII);
        int entries = ((bytes[8] & 0xFF) << 8) | (bytes[9] & 0xFF);
        int at = 10;
        int found = -1;
        for (int index = 1; index < entries; index++) {
            int tag = bytes[at] & 0xFF;
            int size = switch (tag) {
                case 1 -> 3 + (((bytes[at + 1] & 0xFF) << 8) | (bytes[at + 2] & 0xFF));
                case 3, 4, 9, 10, 11, 12, 17, 18 -> 5;
                case 5, 6 -> 9;
                case 7, 8, 16, 19, 20 -> 3;
                case 15 -> 4;
                default -> -1;
            };
            if (size < 0) {
                return bytes;
            }
            if (tag == 1
                    && size - 3 == wanted.length
                    && Arrays.equals(bytes, at + 3, at + size, wanted, 0, wanted.length)) {
                if (found >= 0) {
                    return bytes;
                }
                found = at;
            }
            at += size;
            if (tag == 5 || tag == 6) {
                index++;
            }
        }
        if (found < 0) {
            return bytes;
        }
        byte[] text = replacement.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[bytes.length - wanted.length + text.length];
        System.arraycopy(bytes, 0, out, 0, found + 1);
        out[found + 1] = (byte) (text.length >>> 8);
        out[found + 2] = (byte) text.length;
        System.arraycopy(text, 0, out, found + 3, text.length);
        int rest = found + 3 + wanted.length;
        System.arraycopy(bytes, rest, out, found + 3 + text.length, bytes.length - rest);
        return out;
    }

    private static boolean ascii(String text) {
        return text.chars().allMatch(c -> c >= 0x20 && c < 0x7F);
    }

    private static boolean loads(CodeElement element, String text) {
        return element instanceof ConstantInstruction.LoadConstantInstruction load
                && load.constantEntry() instanceof StringEntry string
                && string.equalsString(text);
    }
}
