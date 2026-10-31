package dev.plexusmodloader.compat.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.compat.bridge.BridgedAccessWidening;
import dev.plexusmodloader.compat.bridge.BridgedAccessWidening.Access;
import dev.plexusmodloader.compat.bridge.BridgedAccessWidening.Kind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The class tweaker grammar of Fabric Loader 0.19.5 and its translation into access widenings. */
class ClassTweakerFileTest {

    private static ClassTweakerFile parse(String text) {
        return ClassTweakerFile.parse("test.accesswidener", text);
    }

    private static List<String> problems(String text) {
        return parse(text).problems().stream()
                .map(problem -> problem.line() + ": " + problem.message())
                .toList();
    }

    @ParameterizedTest
    @CsvSource({
        "accessWidener v1 official, accessWidener, 1",
        "accessWidener v2 official, accessWidener, 2",
        "classTweaker v1 official, classTweaker, 3",
        "classTweaker v2 official, classTweaker, 4",
    })
    void headersOfEveryFormatAreRead(String header, String format, int version) {
        ClassTweakerFile file = parse(header + "\n");

        assertEquals(new ClassTweakerFile.Header(format, version, "official"), file.header());
        assertEquals(List.of(), file.problems());
    }

    @Test
    void headerWordsMaybeSeparatedByAnyWhitespaceAsRealModsDo() {
        assertEquals(
                "official", parse("accessWidener   v1  official\n").header().namespace());
        assertEquals(
                "official", parse("classTweaker\tv1\tofficial\r\n").header().namespace());
    }

    @Test
    void invalidHeadersAreReportedOnLineOne() {
        assertEquals(
                List.of("1: the file is empty; expected the header 'classTweaker <version> <namespace>'"),
                problems(""));
        assertTrue(problems("accessWidener v1\n").getFirst().startsWith("1: Invalid access widener file header"));
        assertTrue(problems("accessWidener v1 official # c\n")
                .getFirst()
                .startsWith("1: Invalid access widener file header"));
        assertTrue(problems(" accessWidener v1 official\n")
                .getFirst()
                .startsWith("1: Invalid access widener file header"));
        assertEquals(List.of("1: Unsupported access widener format: v3"), problems("accessWidener v3 official"));
        assertEquals(List.of("1: Unsupported class tweaker format: v9"), problems("classTweaker v9 official"));
        assertNull(parse("accessTransformer v1 official").header());
    }

    @Test
    void entriesOfEveryKindAreReadWithTheirLines() {
        ClassTweakerFile file = parse("""
                accessWidener v2 official
                # a comment line

                accessible class net/minecraft/A$Inner
                extendable method net/minecraft/A run (ILjava/lang/String;)V # trailing comment
                transitive-mutable field net/minecraft/A count I
                ACCESSIBLE\tfield\tnet/minecraft/A\tnames\t[Ljava/util/List;
                """);

        assertEquals(List.of(), file.problems());
        assertEquals(
                List.of(
                        new ClassTweakerFile.Entry(
                                4, false, Access.ACCESSIBLE, Kind.CLASS, "net/minecraft/A$Inner", "", ""),
                        new ClassTweakerFile.Entry(
                                5,
                                false,
                                Access.EXTENDABLE,
                                Kind.METHOD,
                                "net/minecraft/A",
                                "run",
                                "(ILjava/lang/String;)V"),
                        new ClassTweakerFile.Entry(
                                6, true, Access.MUTABLE, Kind.FIELD, "net/minecraft/A", "count", "I"),
                        new ClassTweakerFile.Entry(
                                7,
                                false,
                                Access.ACCESSIBLE,
                                Kind.FIELD,
                                "net/minecraft/A",
                                "names",
                                "[Ljava/util/List;")),
                file.entries());
    }

    @Test
    void version1TrimsCommentedLinesAndSplitsOnAnyWhitespace() {
        ClassTweakerFile file = parse("""
                accessWidener v1 official
                accessible class net/minecraft/A   # trimmed in v1
                accessible  method\fnet/minecraft/A f ()V
                """);

        assertEquals(List.of(), file.problems());
        assertEquals(2, file.entries().size());
        // From v2 on only spaces and tabs separate words, so the form feed above would join two of them.
        assertEquals(
                List.of("2: Unsupported type: 'method\fnet/minecraft/A'"),
                problems("accessWidener v2 official\naccessible method\fnet/minecraft/A f ()V\n"));
    }

    /** Fabric Loader refuses these lines; so does Plexus Mod Loader, with the same reason and the line number. */
    @Test
    void invalidLinesAreReportedWithLineNumbers() {
        List<String> problems = problems("""
                accessWidener v2 official
                 accessible class net/minecraft/A
                accessible class net.minecraft.A
                public class net/minecraft/A
                accessible
                accessible interface net/minecraft/A
                accessible class net/minecraft/A extra
                accessible method net/minecraft/A f
                mutable class net/minecraft/A
                mutable method net/minecraft/A f ()V
                extendable field net/minecraft/A x I
                accessible method net/minecraft/A f (I
                accessible field net/minecraft/A x Q
                accessible method net/minecraft/A <clinit> ()V
                inject-interface net/minecraft/A net/example/I
                  # a comment indented by spaces
                """);

        assertEquals(
                List.of(
                        "2: Leading whitespace is not allowed",
                        "3: Class-names must be specified as a/b/C, not a.b.C, but found: net.minecraft.A",
                        "4: Unknown access type: public",
                        "5: Expected <class|field|method> following accessible",
                        "6: Unsupported type: 'interface'",
                        "7: Expected (<access> class <className>) got (accessible class net/minecraft/A extra)",
                        "8: Expected (<access> method <className> <methodName> <methodDesc>) got (accessible method"
                                + " net/minecraft/A f)",
                        "9: Classes cannot be made mutable",
                        "10: Methods cannot be made mutable",
                        "11: Fields cannot be made extendable",
                        "12: '(I' is not a valid method descriptor",
                        "13: 'Q' is not a valid field descriptor",
                        "14: a class initialiser (<clinit>) cannot be widened",
                        // inject-interface is a class tweaker (v3+) directive; in accessWidener v2 it is an access.
                        "15: Unknown access type: inject-interface",
                        "16: Leading whitespace is not allowed"),
                problems);
    }

    @Test
    void transitiveIsAnErrorInVersion1() {
        assertEquals(
                List.of("2: Unknown access type: transitive-accessible"),
                problems("accessWidener v1 official\ntransitive-accessible class net/minecraft/A\n"));
    }

    @Test
    void interfaceInjectionAndEnumExtensionAreRead() {
        ClassTweakerFile file = parse("""
                classTweaker v2 official
                transitive-inject-interface net/minecraft/A net/example/Extra
                inject-interface net/minecraft/B net/example/Extra
                extend-enum net/minecraft/C NEW_CONSTANT
                accessible class net/minecraft/A
                """);

        assertEquals(List.of(), file.problems());
        assertEquals(
                List.of(
                        new ClassTweakerFile.InjectedInterface(2, "net/minecraft/A", "net/example/Extra"),
                        new ClassTweakerFile.InjectedInterface(3, "net/minecraft/B", "net/example/Extra")),
                file.injectedInterfaces());
        assertEquals(
                List.of(new ClassTweakerFile.EnumExtension(4, "net/minecraft/C", "NEW_CONSTANT")),
                file.enumExtensions());
        assertEquals(1, file.entries().size());
        assertEquals(
                List.of("2: Expected (inject-interface <className> <interfaceName>) got (inject-interface"
                        + " net/minecraft/A)"),
                problems("classTweaker v1 official\ninject-interface net/minecraft/A\n"));
        // Enum extension only exists from classTweaker v2 on.
        assertEquals(
                List.of("2: Unknown access type: extend-enum"),
                problems("classTweaker v1 official\nextend-enum net/minecraft/C X\n"));
    }

    @Test
    void membersImplyTheAccessFabricLoaderGivesTheirClass() {
        List<BridgedAccessWidening> widenings = parse("""
                classTweaker v1 official
                accessible field net/minecraft/A a I
                mutable field net/minecraft/A a I
                extendable method net/minecraft/B <init> ()V
                accessible method net/minecraft/B b ()V
                mutable field net/minecraft/C c J
                transitive-extendable class net/minecraft/A
                """).accessWidenings();

        assertEquals(
                List.of(
                        new BridgedAccessWidening(
                                "test.accesswidener:2",
                                "net/minecraft/A",
                                Kind.FIELD,
                                "a",
                                "I",
                                Set.of(Access.ACCESSIBLE, Access.MUTABLE)),
                        new BridgedAccessWidening(
                                "test.accesswidener:2",
                                "net/minecraft/A",
                                Kind.CLASS,
                                "",
                                "",
                                Set.of(Access.ACCESSIBLE, Access.EXTENDABLE)),
                        new BridgedAccessWidening(
                                "test.accesswidener:4",
                                "net/minecraft/B",
                                Kind.METHOD,
                                "<init>",
                                "()V",
                                Set.of(Access.EXTENDABLE)),
                        new BridgedAccessWidening(
                                "test.accesswidener:4",
                                "net/minecraft/B",
                                Kind.CLASS,
                                "",
                                "",
                                Set.of(Access.ACCESSIBLE, Access.EXTENDABLE)),
                        new BridgedAccessWidening(
                                "test.accesswidener:5",
                                "net/minecraft/B",
                                Kind.METHOD,
                                "b",
                                "()V",
                                Set.of(Access.ACCESSIBLE)),
                        // mutable alone leaves the class as it is.
                        new BridgedAccessWidening(
                                "test.accesswidener:6",
                                "net/minecraft/C",
                                Kind.FIELD,
                                "c",
                                "J",
                                Set.of(Access.MUTABLE))),
                widenings);
    }

    /** Fabric Loader's InjectedInterfaceImpl: raw name without generics, signature {@code L<argument>;}. */
    @Test
    void injectedInterfacesKeepTheirGenericSignature() {
        ClassTweakerFile.InjectedInterface plain = new ClassTweakerFile.InjectedInterface(2, "a/B", "c/Api");
        ClassTweakerFile.InjectedInterface generic =
                new ClassTweakerFile.InjectedInterface(3, "a/B", "c/Holder<Lc/Thing;Ljava/util/List<Lc/Item;>;>");

        assertEquals("c/Api", plain.interfaceName());
        assertEquals("Lc/Api;", plain.signature());
        assertFalse(plain.hasGenerics());
        assertEquals("c/Holder", generic.interfaceName());
        assertEquals("Lc/Holder<Lc/Thing;Ljava/util/List<Lc/Item;>;>;", generic.signature());
        assertTrue(generic.hasGenerics());
    }
}
