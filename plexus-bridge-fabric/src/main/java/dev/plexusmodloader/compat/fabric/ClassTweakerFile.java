package dev.plexusmodloader.compat.fabric;

import dev.plexusmodloader.compat.bridge.BridgedAccessWidening;
import dev.plexusmodloader.compat.bridge.BridgedAccessWidening.Access;
import dev.plexusmodloader.compat.bridge.BridgedAccessWidening.Kind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A Fabric class tweaker file ({@code accessWidener} in {@code fabric.mod.json}), read with the grammar of
 * Fabric Loader 0.19.5's reader ({@code ClassTweakerReaderImpl}, checked with {@code javap}), and its
 * translation into Poly Mod Loader access widenings (ADR-0018).
 *
 * <p>Grammar, as Fabric Loader reads it:
 *
 * <ul>
 *   <li>Header, the first line split on whitespace into exactly three words: {@code accessWidener v1}, {@code
 *       accessWidener v2}, {@code classTweaker v1} or {@code classTweaker v2}, then the namespace. The header
 *       may not carry a comment.
 *   <li>{@code #} starts a comment. Blank lines are skipped; a line that starts with whitespace is an error.
 *       Words are split on any whitespace in {@code accessWidener v1}, on spaces and tabs otherwise.
 *   <li>{@code <access> class <class>}, {@code <access> method <class> <name> <descriptor>}, {@code <access>
 *       field <class> <name> <descriptor>}, where {@code <access>} is {@code accessible}, {@code extendable}
 *       or {@code mutable} (any case). From {@code accessWidener v2} on, each may be prefixed {@code
 *       transitive-}.
 *   <li>From {@code classTweaker v1} on, {@code [transitive-]inject-interface <class> <interface>}; from
 *       {@code classTweaker v2} on, {@code [transitive-]extend-enum <class> <constant>}.
 *   <li>Classes cannot be made mutable, methods cannot be made mutable, fields cannot be made extendable.
 * </ul>
 *
 * <p>Fabric Loader stops at the first error; this reader reports every line it cannot accept. It also rejects
 * member names and descriptors that are not valid JVM names, which Fabric Loader would keep but never match.
 *
 * <p>Thread safety: instances are immutable.
 */
final class ClassTweakerFile {

    /** The only namespace a class tweaker may use on Minecraft 26.2: Mojang's names, which the game runs with. */
    static final String RUNTIME_NAMESPACE = "official";

    private static final Pattern V1_DELIMITER = Pattern.compile("\\s+");
    private static final Pattern V2_DELIMITER = Pattern.compile("[ \\t]+");
    private static final Pattern FIELD_DESCRIPTOR = Pattern.compile("\\[*([BCDFIJSZ]|L[^;\\[.]+;)");
    private static final Pattern METHOD_DESCRIPTOR =
            Pattern.compile("\\((\\[*([BCDFIJSZ]|L[^;\\[.]+;))*\\)(V|\\[*([BCDFIJSZ]|L[^;\\[.]+;))");
    private static final String TRANSITIVE = "transitive-";

    /**
     * The first line.
     *
     * @param format {@code accessWidener} or {@code classTweaker}
     * @param version Fabric Loader's internal version: 1 and 2 for {@code accessWidener v1}/{@code v2}, 3 and
     *     4 for {@code classTweaker v1}/{@code v2}
     * @param namespace the mapping namespace the names are in
     */
    record Header(String format, int version, String namespace) {}

    /**
     * One access line.
     *
     * @param line 1-based line number
     * @param transitive whether it was written {@code transitive-...}; Fabric Loader applies both alike at run
     *     time, the difference only matters to Loom when compiling dependent mods
     */
    record Entry(
            int line, boolean transitive, Access access, Kind kind, String owner, String name, String descriptor) {}

    /**
     * A two-word directive line as read, before it becomes an {@link InjectedInterface} or {@link EnumExtension}.
     *
     * @param directive the directive as written, such as {@code transitive-inject-interface}
     * @param target the class it changes
     * @param argument the interface or enum constant it adds
     */
    record Directive(int line, String directive, String target, String argument) {}

    /**
     * An {@code extend-enum} line: the enum gets a stub constant as Fabric Loader adds it (ADR-0020, {@link
     * EnumExtender}); a mod's mixin initialises it.
     *
     * @param target the enum, as a JVM internal name
     * @param constant the constant's name
     */
    record EnumExtension(int line, String target, String constant) {}

    /**
     * An {@code inject-interface} line: the class gets the interface as Fabric Loader adds it (ADR-0020).
     *
     * @param target the class, as a JVM internal name
     * @param argument the interface as written: an internal name, possibly with generic arguments ({@code
     *     a/Api<Lb/Thing;>})
     */
    record InjectedInterface(int line, String target, String argument) {

        /** The interface's internal name without generic arguments; a nested type keeps its {@code $} name. */
        String interfaceName() {
            if (!argument.contains("<")) {
                return argument;
            }
            StringBuilder raw = new StringBuilder();
            int depth = 0;
            for (int i = 0; i < argument.length(); i++) {
                char ch = argument.charAt(i);
                if (ch == '<') {
                    depth++;
                } else if (ch == '>') {
                    depth--;
                } else if (depth == 0) {
                    raw.append(ch == '.' ? '$' : ch);
                }
            }
            return raw.toString();
        }

        /** Its entry in a class signature, as Fabric Loader writes it: {@code L<argument>;}. */
        String signature() {
            return "L" + argument + ";";
        }

        /** Whether it has generic arguments, which Fabric Loader then adds to the class signature. */
        boolean hasGenerics() {
            return argument.contains("<");
        }
    }

    /** A line Poly Mod Loader cannot accept, with Fabric Loader's wording where it has one. */
    record Problem(int line, String message) {}

    private final String fileName;
    private final Header header;
    private final List<Entry> entries;
    private final List<InjectedInterface> injectedInterfaces;
    private final List<EnumExtension> enumExtensions;
    private final List<Problem> problems;

    private ClassTweakerFile(
            String fileName,
            Header header,
            List<Entry> entries,
            List<InjectedInterface> injectedInterfaces,
            List<EnumExtension> enumExtensions,
            List<Problem> problems) {
        this.fileName = fileName;
        this.header = header;
        this.entries = List.copyOf(entries);
        this.injectedInterfaces = List.copyOf(injectedInterfaces);
        this.enumExtensions = List.copyOf(enumExtensions);
        this.problems = List.copyOf(problems);
    }

    /**
     * Reads a class tweaker. Never throws for malformed text; every problem is in {@link #problems()}.
     *
     * @param fileName the path inside the jar, used in diagnostics
     * @param text the file decoded as UTF-8, as Fabric Loader reads it
     */
    static ClassTweakerFile parse(String fileName, String text) {
        List<String> lines = text.lines().toList();
        List<Entry> entries = new ArrayList<>();
        List<InjectedInterface> injected = new ArrayList<>();
        List<EnumExtension> extensions = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        if (lines.isEmpty()) {
            problems.add(new Problem(1, "the file is empty; expected the header 'classTweaker <version> <namespace>'"));
            return new ClassTweakerFile(fileName, null, entries, injected, extensions, problems);
        }
        Header header = header(lines.getFirst(), problems);
        if (header == null) {
            return new ClassTweakerFile(fileName, null, entries, injected, extensions, problems);
        }
        Pattern delimiter = header.version() < 2 ? V1_DELIMITER : V2_DELIMITER;
        for (int index = 1; index < lines.size(); index++) {
            int lineNumber = index + 1;
            String line = withoutComment(header.version(), lines.get(index));
            if (line.isEmpty()) {
                continue;
            }
            if (Character.isWhitespace(line.codePointAt(0))) {
                problems.add(new Problem(lineNumber, "Leading whitespace is not allowed"));
                continue;
            }
            List<String> words = List.of(delimiter.split(line));
            String directive = words.getFirst();
            if (header.version() >= 3
                    && (directive.equals("inject-interface") || directive.equals(TRANSITIVE + "inject-interface"))) {
                List<Directive> read = new ArrayList<>();
                directive(lineNumber, line, words, "inject-interface <className> <interfaceName>", read, problems);
                read.forEach(
                        found -> injected.add(new InjectedInterface(found.line(), found.target(), found.argument())));
                continue;
            }
            if (header.version() >= 4
                    && (directive.equals("extend-enum") || directive.equals(TRANSITIVE + "extend-enum"))) {
                List<Directive> read = new ArrayList<>();
                directive(lineNumber, line, words, "extend-enum <className> <constantName>", read, problems);
                read.forEach(
                        found -> extensions.add(new EnumExtension(found.line(), found.target(), found.argument())));
                continue;
            }
            entry(lineNumber, line, words, header.version(), problems).ifPresent(entries::add);
        }
        return new ClassTweakerFile(fileName, header, entries, injected, extensions, problems);
    }

    /** The header, or {@code null} when it is not one (the problem is added). */
    private static Header header(String line, List<Problem> problems) {
        String[] words = line.split("\\s+");
        if (words.length != 3 || !(words[0].equals("accessWidener") || words[0].equals("classTweaker"))) {
            problems.add(new Problem(
                    1,
                    "Invalid access widener file header. Expected: 'classTweaker <version> <namespace>', found '" + line
                            + "'"));
            return null;
        }
        boolean accessWidener = words[0].equals("accessWidener");
        int version;
        switch (words[1]) {
            case "v1" -> version = accessWidener ? 1 : 3;
            case "v2" -> version = accessWidener ? 2 : 4;
            default -> {
                problems.add(new Problem(
                        1,
                        (accessWidener ? "Unsupported access widener format: " : "Unsupported class tweaker format: ")
                                + words[1]));
                return null;
            }
        }
        return new Header(words[0], version, words[2]);
    }

    /** Fabric Loader cuts at {@code #}, and trims what is left only in {@code accessWidener v1}. */
    private static String withoutComment(int version, String line) {
        int comment = line.indexOf('#');
        if (comment < 0) {
            return line;
        }
        String code = line.substring(0, comment);
        return version <= 1 ? code.trim() : code;
    }

    private static void directive(
            int lineNumber,
            String line,
            List<String> words,
            String expected,
            List<Directive> read,
            List<Problem> problems) {
        if (words.size() != 3) {
            problems.add(new Problem(lineNumber, "Expected (" + expected + ") got (" + line + ")"));
            return;
        }
        if (!validClassName(words.get(1), lineNumber, problems)) {
            return;
        }
        read.add(new Directive(lineNumber, words.get(0), words.get(1), words.get(2)));
    }

    private static Optional<Entry> entry(
            int lineNumber, String line, List<String> words, int version, List<Problem> problems) {
        String accessWord = words.getFirst();
        boolean transitive = false;
        if (version >= 2 && accessWord.startsWith(TRANSITIVE)) {
            accessWord = accessWord.substring(TRANSITIVE.length());
            transitive = true;
        }
        Access access = switch (accessWord.toLowerCase(Locale.ROOT)) {
            case "accessible" -> Access.ACCESSIBLE;
            case "extendable" -> Access.EXTENDABLE;
            case "mutable" -> Access.MUTABLE;
            default -> null;
        };
        if (access == null) {
            problems.add(new Problem(lineNumber, "Unknown access type: " + accessWord));
            return Optional.empty();
        }
        if (words.size() < 2) {
            problems.add(new Problem(lineNumber, "Expected <class|field|method> following " + words.getFirst()));
            return Optional.empty();
        }
        Kind kind = switch (words.get(1)) {
            case "class" -> Kind.CLASS;
            case "method" -> Kind.METHOD;
            case "field" -> Kind.FIELD;
            default -> null;
        };
        if (kind == null) {
            problems.add(new Problem(lineNumber, "Unsupported type: '" + words.get(1) + "'"));
            return Optional.empty();
        }
        int expectedWords = kind == Kind.CLASS ? 3 : 5;
        if (words.size() != expectedWords) {
            String shape = switch (kind) {
                case CLASS -> "<access> class <className>";
                case METHOD -> "<access> method <className> <methodName> <methodDesc>";
                case FIELD -> "<access> field <className> <fieldName> <fieldDesc>";
            };
            problems.add(new Problem(lineNumber, "Expected (" + shape + ") got (" + line + ")"));
            return Optional.empty();
        }
        String owner = words.get(2);
        if (!validClassName(owner, lineNumber, problems)) {
            return Optional.empty();
        }
        String name = kind == Kind.CLASS ? "" : words.get(3);
        String descriptor = kind == Kind.CLASS ? "" : words.get(4);
        String invalid = switch (kind) {
            case CLASS -> access == Access.MUTABLE ? "Classes cannot be made mutable" : null;
            case METHOD -> method(name, descriptor, access);
            case FIELD -> field(name, descriptor, access);
        };
        if (invalid != null) {
            problems.add(new Problem(lineNumber, invalid));
            return Optional.empty();
        }
        return Optional.of(new Entry(lineNumber, transitive, access, kind, owner, name, descriptor));
    }

    private static String method(String name, String descriptor, Access access) {
        if (access == Access.MUTABLE) {
            return "Methods cannot be made mutable";
        }
        if (name.equals("<clinit>")) {
            return "a class initialiser (<clinit>) cannot be widened";
        }
        if (!name.equals("<init>") && !validMemberName(name)) {
            return "'" + name + "' is not a valid method name";
        }
        if (!METHOD_DESCRIPTOR.matcher(descriptor).matches()) {
            return "'" + descriptor + "' is not a valid method descriptor";
        }
        return null;
    }

    private static String field(String name, String descriptor, Access access) {
        if (access == Access.EXTENDABLE) {
            return "Fields cannot be made extendable";
        }
        if (!validMemberName(name)) {
            return "'" + name + "' is not a valid field name";
        }
        if (!FIELD_DESCRIPTOR.matcher(descriptor).matches()) {
            return "'" + descriptor + "' is not a valid field descriptor";
        }
        return null;
    }

    private static boolean validClassName(String name, int lineNumber, List<Problem> problems) {
        if (name.contains(".")) {
            problems.add(
                    new Problem(lineNumber, "Class-names must be specified as a/b/C, not a.b.C, but found: " + name));
            return false;
        }
        if (name.isEmpty()
                || name.startsWith("/")
                || name.endsWith("/")
                || name.contains("//")
                || name.contains(";")
                || name.contains("[")) {
            problems.add(new Problem(lineNumber, "'" + name + "' is not a valid class name"));
            return false;
        }
        return true;
    }

    private static boolean validMemberName(String name) {
        return !name.isEmpty()
                && !name.contains(".")
                && !name.contains(";")
                && !name.contains("[")
                && !name.contains("/")
                && !name.contains("<")
                && !name.contains(">");
    }

    /** The path inside the jar. */
    String fileName() {
        return fileName;
    }

    /** The header, or {@code null} when the first line is not a valid one. */
    Header header() {
        return header;
    }

    /** The access lines Poly Mod Loader applies, in file order. */
    List<Entry> entries() {
        return entries;
    }

    /** The {@code inject-interface} lines, in file order. */
    List<InjectedInterface> injectedInterfaces() {
        return injectedInterfaces;
    }

    /** The {@code extend-enum} lines, in file order. */
    List<EnumExtension> enumExtensions() {
        return enumExtensions;
    }

    /** Lines that are not valid, in file order. Fabric Loader would refuse the file for any of them. */
    List<Problem> problems() {
        return problems;
    }

    /** {@code file:line} for diagnostics. */
    String where(int line) {
        return fileName + ":" + line;
    }

    /**
     * The access widenings these entries mean, with Fabric Loader's implied class access: a member made
     * accessible also makes its class accessible, and a method made extendable also makes its class
     * extendable. Widenings of the same target are joined; each keeps the line that first asked for it.
     */
    List<BridgedAccessWidening> accessWidenings() {
        Map<String, Target> targets = new LinkedHashMap<>();
        for (Entry entry : entries) {
            add(targets, entry.line(), entry.kind(), entry.owner(), entry.name(), entry.descriptor(), entry.access());
            if (entry.kind() == Kind.METHOD || entry.kind() == Kind.FIELD) {
                if (entry.access() == Access.ACCESSIBLE) {
                    add(targets, entry.line(), Kind.CLASS, entry.owner(), "", "", Access.ACCESSIBLE);
                } else if (entry.access() == Access.EXTENDABLE) {
                    add(targets, entry.line(), Kind.CLASS, entry.owner(), "", "", Access.EXTENDABLE);
                }
            }
        }
        return targets.values().stream()
                .map(target -> new BridgedAccessWidening(
                        where(target.line), target.owner, target.kind, target.name, target.descriptor, target.access))
                .toList();
    }

    private static void add(
            Map<String, Target> targets,
            int line,
            Kind kind,
            String owner,
            String name,
            String descriptor,
            Access access) {
        targets.computeIfAbsent(
                        kind + " " + owner + " " + name + " " + descriptor,
                        key -> new Target(line, kind, owner, name, descriptor, EnumSet.noneOf(Access.class)))
                .access
                .add(access);
    }

    private record Target(int line, Kind kind, String owner, String name, String descriptor, Set<Access> access) {}
}
