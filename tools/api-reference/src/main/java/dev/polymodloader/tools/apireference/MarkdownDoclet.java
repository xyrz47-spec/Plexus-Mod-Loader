package dev.polymodloader.tools.apireference;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * A Javadoc doclet that writes the public API as Docusaurus Markdown: one page per package, a
 * section per type, and per member its signature, description, parameters, return value and
 * exceptions. The output directory comes from the {@value #OUTPUT_PROPERTY} system property, the site
 * language of the surrounding words ({@code en} or {@code ko}, default {@code en}) from
 * {@value #LOCALE_PROPERTY}.
 */
public final class MarkdownDoclet implements Doclet {

    /** System property naming the output directory. */
    public static final String OUTPUT_PROPERTY = "poly.apiReference.output";

    /** System property naming the site locale of the generated pages. */
    public static final String LOCALE_PROPERTY = "poly.apiReference.locale";

    /** System property naming {@code api-since.json} ({@link ApiSinceMain}); without it no release is shown. */
    public static final String SINCE_PROPERTY = "poly.apiReference.since";

    /** A {@code "key": "release"} or {@code "key": null} line of {@code api-since.json}. */
    private static final Pattern SINCE_LINE = Pattern.compile("^\\s*\"(.+)\": (?:\"([^\"]+)\"|null),?$");

    /** Package prefix left out of page names. */
    private static final String API_PREFIX = "dev.polymodloader.api.";

    /** A qualified type name in {@link TypeMirror#toString()} output, shortened to its simple name. */
    private static final Pattern QUALIFIER = Pattern.compile("\\b(?:[a-z_][a-z0-9_]*\\.)+(?=[A-Z])");

    private DocTrees trees;
    private javax.lang.model.util.Types types;
    private javax.lang.model.util.Elements elements;
    private Reporter reporter;
    private ReferenceLabels labels = ReferenceLabels.ENGLISH;

    /** Anchor of each documented type, for links. */
    private final Map<TypeElement, String> typeAnchors = new LinkedHashMap<>();

    /** Page file (without extension) of each documented type, for links. */
    private final Map<TypeElement, String> typePages = new LinkedHashMap<>();

    /** Anchor of each documented member, for links. */
    private final Map<Element, String> memberAnchors = new LinkedHashMap<>();

    /** The release that added each API element ({@link ApiKeys} name), {@code null} for one not released yet. */
    private final Map<String, String> since = new LinkedHashMap<>();

    /** The releases of {@code api-since.json}, oldest first. */
    private final List<String> releases = new ArrayList<>();

    /** Each documented type and member by its {@link ApiKeys} name: its link text and target, for the additions page. */
    private final Map<String, String[]> links = new LinkedHashMap<>();

    /**
     * Each documented constructor and method by package label: link text, target, return type, first sentence and
     * {@link ApiKeys} name, for the functions page.
     */
    private final Map<String, List<String[]>> functions = new TreeMap<>();

    private int typeCount;
    private int memberCount;
    private int undocumentedCount;

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "PolyMarkdown";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return Set.of();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        trees = environment.getDocTrees();
        types = environment.getTypeUtils();
        elements = environment.getElementUtils();
        String output = System.getProperty(OUTPUT_PROPERTY);
        if (output == null) {
            reporter.print(javax.tools.Diagnostic.Kind.ERROR, "System property " + OUTPUT_PROPERTY + " is not set");
            return false;
        }
        labels = ReferenceLabels.forLocale(System.getProperty(LOCALE_PROPERTY, "en"));
        readSince(System.getProperty(SINCE_PROPERTY));
        Map<PackageElement, List<TypeElement>> byPackage = new TreeMap<>(
                Comparator.comparing(element -> element.getQualifiedName().toString()));
        for (TypeElement type : ElementFilter.typesIn(environment.getIncludedElements())) {
            collect(type, byPackage);
        }
        byPackage.values().forEach(types -> types.sort(Comparator.comparing(MarkdownDoclet::displayName)));
        byPackage.forEach((pkg, types) -> {
            String page = pageName(pkg);
            for (TypeElement type : types) {
                typePages.put(type, page);
                typeAnchors.put(type, anchor(displayName(type)));
                assignMemberAnchors(type);
            }
        });
        try {
            Path directory = Path.of(output);
            Files.createDirectories(directory);
            try (Stream<Path> old = Files.list(directory)) {
                for (Path file : old.filter(file -> file.getFileName().toString().endsWith(".md"))
                        .filter(file -> !file.getFileName().toString().equals("index.md"))
                        .toList()) {
                    Files.delete(file);
                }
            }
            for (Map.Entry<PackageElement, List<TypeElement>> entry : byPackage.entrySet()) {
                Files.writeString(
                        directory.resolve(pageName(entry.getKey()) + ".md"),
                        packagePage(entry.getKey(), entry.getValue()),
                        StandardCharsets.UTF_8);
            }
            if (!releases.isEmpty()) {
                Files.writeString(directory.resolve("changes.md"), changesPage(), StandardCharsets.UTF_8);
            }
            Files.writeString(directory.resolve("functions.md"), functionsPage(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        reporter.print(
                javax.tools.Diagnostic.Kind.NOTE,
                "Wrote " + byPackage.size() + " package page(s), " + typeCount + " type(s), " + memberCount
                        + " member(s) (" + undocumentedCount + " without a description) to " + output);
        return true;
    }

    private void collect(TypeElement type, Map<PackageElement, List<TypeElement>> byPackage) {
        if (!type.getModifiers().contains(Modifier.PUBLIC)) {
            return;
        }
        PackageElement pkg = packageOf(type);
        List<TypeElement> types = byPackage.computeIfAbsent(pkg, ignored -> new ArrayList<>());
        if (types.contains(type)) {
            return;
        }
        types.add(type);
        for (TypeElement member : ElementFilter.typesIn(type.getEnclosedElements())) {
            collect(member, byPackage);
        }
    }

    private void assignMemberAnchors(TypeElement type) {
        String typeAnchor = typeAnchors.get(type);
        String page = typePages.get(type);
        links.put(ApiKeys.type(elements, type), new String[] {displayName(type), page + ".md#" + typeAnchor});
        Map<String, Integer> used = new LinkedHashMap<>();
        for (Element member : documentedMembers(type)) {
            String base = typeAnchor + "-" + anchor(member.getKind() == ElementKind.CONSTRUCTOR
                    ? "new"
                    : member.getSimpleName().toString());
            int count = used.merge(base, 1, Integer::sum);
            String memberAnchor = count == 1 ? base : base + "-" + count;
            memberAnchors.put(member, memberAnchor);
            ExecutableElement executable = (ExecutableElement) member;
            String name = executable.getKind() == ElementKind.CONSTRUCTOR
                    ? type.getSimpleName().toString()
                    : executable.getSimpleName().toString();
            String parameters = executable.getParameters().stream()
                    .map(parameter -> typeName(parameter.asType()))
                    .collect(Collectors.joining(", "));
            String key = ApiKeys.member(elements, types, type, member);
            String text = displayName(type) + "." + name + "(" + parameters + ")";
            links.put(key, new String[] {text, page + ".md#" + memberAnchor});
            functions
                    .computeIfAbsent(shortName(packageOf(type)), ignored -> new ArrayList<>())
                    .add(new String[] {
                        text,
                        page + ".md#" + memberAnchor,
                        executable.getKind() == ElementKind.CONSTRUCTOR
                                ? labels.constructorReturns()
                                : "`" + typeName(executable.getReturnType()) + "`",
                        firstSentence(member).orElse(""),
                        key
                    });
        }
    }

    // ---------------------------------------------------------------- releases

    private void readSince(String file) {
        if (file == null || !Files.isRegularFile(Path.of(file))) {
            return;
        }
        try {
            for (String line : Files.readAllLines(Path.of(file), StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.startsWith("\"releases\"")) {
                    Matcher release = Pattern.compile("\"([^\"]+)\"").matcher(trimmed.substring(trimmed.indexOf('[')));
                    while (release.find()) {
                        releases.add(release.group(1));
                    }
                    continue;
                }
                Matcher matcher = SINCE_LINE.matcher(line);
                if (matcher.matches()) {
                    since.put(matcher.group(1), matcher.group(2));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "Since" text of an element, or empty without {@code api-since.json} or for an element it does not list. */
    private Optional<String> sinceText(String key) {
        if (releases.isEmpty() || !since.containsKey(key)) {
            return Optional.empty();
        }
        String release = since.get(key);
        if (release == null) {
            return Optional.of(labels.unreleased());
        }
        return Optional.of(release.equals(releases.getFirst())
                ? labels.sinceEarliest().formatted("`" + release + "`")
                : "`" + release + "`");
    }

    /** The functions page: every documented constructor and method, by package. */
    private String functionsPage() {
        StringBuilder out = new StringBuilder();
        int count = functions.values().stream().mapToInt(List::size).sum();
        out.append("---\n")
                .append("title: ")
                .append(yaml(labels.functionsTitle()))
                .append('\n')
                .append("sidebar_label: ")
                .append(yaml(labels.functionsSidebar()))
                .append('\n')
                .append("sidebar_position: 3\n")
                .append("description: ")
                .append(yaml(labels.functionCount().formatted(count)))
                .append('\n')
                .append("toc_max_heading_level: 2\n")
                .append("custom_edit_url: null\n")
                .append("---\n\n");
        out.append(":::note[")
                .append(labels.generatedTitle())
                .append("]\n")
                .append(labels.generatedNote())
                .append("\n:::\n\n");
        out.append(labels.functionsIntro()).append("\n\n");
        functions.forEach((label, rows) -> {
            out.append("## ").append(label).append("\n\n");
            out.append(labels.functionsHeader()).append("\n|---|---|---|---|\n");
            rows.stream().sorted(Comparator.comparing((String[] row) -> row[0])).forEach(row -> out.append("| [`")
                    .append(row[0])
                    .append("`](./")
                    .append(row[1])
                    .append(") | ")
                    .append(row[2])
                    .append(" | ")
                    .append(cell(row[3]))
                    .append(" | ")
                    .append(sinceText(row[4]).orElse(""))
                    .append(" |\n"));
            out.append('\n');
        });
        return out.toString();
    }

    /** The additions page: per release after the first, newest first, the types and members it added. */
    private String changesPage() {
        StringBuilder out = new StringBuilder();
        out.append("---\n")
                .append("title: ")
                .append(yaml(labels.changesTitle()))
                .append('\n')
                .append("sidebar_label: ")
                .append(yaml(labels.changesSidebar()))
                .append('\n')
                .append("sidebar_position: 2\n")
                .append("custom_edit_url: null\n")
                .append("---\n\n");
        out.append(":::note[")
                .append(labels.generatedTitle())
                .append("]\n")
                .append(labels.generatedNote())
                .append("\n:::\n\n");
        out.append(labels.changesIntro()).append("\n\n");
        List<String> shown = new ArrayList<>();
        if (since.containsValue(null)) {
            shown.add(null);
        }
        for (int i = releases.size() - 1; i >= 1; i--) {
            shown.add(releases.get(i));
        }
        List<String> empty = new ArrayList<>();
        for (String release : shown) {
            String heading = release == null ? labels.unreleased() : release;
            List<String[]> added = since.entrySet().stream()
                    .filter(entry -> java.util.Objects.equals(entry.getValue(), release))
                    .map(entry -> links.get(entry.getKey()))
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing((String[] link) -> link[0]))
                    .toList();
            if (added.isEmpty()) {
                empty.add("`" + heading + "`");
                continue;
            }
            out.append("## ").append(heading).append("\n\n");
            for (String[] link : added) {
                out.append("- [`").append(link[0]).append("`](./").append(link[1]).append(")\n");
            }
            out.append('\n');
        }
        if (!empty.isEmpty()) {
            out.append(labels.changesNone().formatted(String.join(", ", empty))).append("\n\n");
        }
        out.append(labels.changesEarliest().formatted("`" + releases.getFirst() + "`")).append('\n');
        return out.toString();
    }

    // ---------------------------------------------------------------- pages

    private String packagePage(PackageElement pkg, List<TypeElement> types) {
        String name = pkg.getQualifiedName().toString();
        StringBuilder out = new StringBuilder();
        String summary = firstSentence(pkg).orElse(labels.typeCount().formatted(types.size()));
        out.append("---\n")
                .append("title: ")
                .append(yaml(name))
                .append('\n')
                .append("sidebar_label: ")
                .append(yaml(shortName(pkg)))
                .append('\n')
                .append("description: ")
                .append(yaml(plain(summary)))
                .append('\n')
                .append("toc_max_heading_level: 2\n")
                .append("custom_edit_url: null\n")
                .append("---\n\n");
        out.append(":::note[")
                .append(labels.generatedTitle())
                .append("]\n")
                .append(labels.generatedNote())
                .append("\n:::\n\n");
        markdown(pkg).ifPresent(text -> out.append(text).append("\n\n"));
        out.append(labels.typesHeader()).append("\n|---|---|---|\n");
        for (TypeElement type : types) {
            out.append("| [`")
                    .append(displayName(type))
                    .append("`](#")
                    .append(typeAnchors.get(type))
                    .append(") | ")
                    .append(kindName(type))
                    .append(" | ")
                    .append(cell(firstSentence(type).orElse("")))
                    .append(" |\n");
        }
        out.append('\n');
        for (TypeElement type : types) {
            typeSection(type, out);
        }
        return out.toString();
    }

    private void typeSection(TypeElement type, StringBuilder out) {
        typeCount++;
        out.append("## ")
                .append(displayName(type))
                .append(" {#")
                .append(typeAnchors.get(type))
                .append("}\n\n");
        out.append("**")
                .append(kindName(type))
                .append("**");
        stability(type).ifPresent(level -> out.append(" · ").append(labels.stability()).append(" `").append(level).append('`'));
        sinceText(ApiKeys.type(elements, type))
                .ifPresent(release -> out.append(" · ").append(labels.since()).append(' ').append(release));
        out.append("\n\n```java\n").append(declaration(type)).append("\n```\n\n");
        markdown(type).ifPresent(text -> out.append(text).append("\n\n"));

        if (type.getKind() == ElementKind.RECORD) {
            Map<String, String> params = paramDescriptions(type);
            out.append(labels.componentsHeader()).append("\n|---|---|---|\n");
            for (RecordComponentElement component : type.getRecordComponents()) {
                String componentName = component.getSimpleName().toString();
                out.append("| `")
                        .append(componentName)
                        .append("` | `")
                        .append(typeName(component.asType()))
                        .append("` | ")
                        .append(cell(params.getOrDefault(componentName, "")))
                        .append(" |\n");
            }
            out.append('\n');
        }
        if (type.getKind() == ElementKind.ENUM) {
            out.append(labels.constantsHeader()).append("\n|---|---|\n");
            for (VariableElement constant : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                if (constant.getKind() == ElementKind.ENUM_CONSTANT) {
                    out.append("| `")
                            .append(constant.getSimpleName())
                            .append("` | ")
                            .append(cell(markdown(constant).orElse("")))
                            .append(" |\n");
                }
            }
            out.append('\n');
        }
        List<VariableElement> fields = ElementFilter.fieldsIn(type.getEnclosedElements()).stream()
                .filter(field -> field.getKind() == ElementKind.FIELD)
                .filter(field -> field.getModifiers().contains(Modifier.PUBLIC) || type.getKind().isInterface())
                .toList();
        if (!fields.isEmpty()) {
            out.append(labels.fieldsHeader()).append("\n|---|---|---|\n");
            for (VariableElement field : fields) {
                memberCount++;
                out.append("| `")
                        .append(field.getSimpleName())
                        .append("` | `")
                        .append(typeName(field.asType()))
                        .append("` | ")
                        .append(cell(markdown(field).orElse("")))
                        .append(" |\n");
            }
            out.append('\n');
        }
        List<String> inherited = supertypes(type);
        if (!inherited.isEmpty()) {
            out.append(labels.supertypes()).append(": ").append(String.join(", ", inherited)).append("\n\n");
        }
        for (Element member : documentedMembers(type)) {
            memberSection(type, (ExecutableElement) member, out);
        }
    }

    private void memberSection(TypeElement owner, ExecutableElement method, StringBuilder out) {
        memberCount++;
        String name = method.getKind() == ElementKind.CONSTRUCTOR
                ? owner.getSimpleName().toString()
                : method.getSimpleName().toString();
        String parameters = method.getParameters().stream()
                .map(parameter -> typeName(parameter.asType()))
                .collect(Collectors.joining(", "));
        out.append("### `")
                .append(name)
                .append('(')
                .append(parameters)
                .append(")` {#")
                .append(memberAnchors.get(method))
                .append("}\n\n");
        out.append("```java\n").append(signature(owner, method)).append("\n```\n\n");
        if (markdown(method).isEmpty()) {
            undocumentedCount++;
        }
        stability(method).ifPresent(level -> out.append(labels.stability()).append(" `").append(level).append("`\n\n"));
        sinceText(ApiKeys.member(elements, types, owner, method))
                .ifPresent(release -> out.append(labels.since()).append(' ').append(release).append("\n\n"));
        markdown(method).ifPresent(text -> out.append(text).append("\n\n"));
        if (!method.getParameters().isEmpty()) {
            Map<String, String> params = paramDescriptions(method);
            out.append(labels.parametersHeader()).append("\n|---|---|---|\n");
            for (VariableElement parameter : method.getParameters()) {
                String parameterName = parameter.getSimpleName().toString();
                out.append("| `")
                        .append(parameterName)
                        .append("` | `")
                        .append(typeName(parameter.asType()))
                        .append("` | ")
                        .append(cell(params.getOrDefault(parameterName, "")))
                        .append(" |\n");
            }
            out.append('\n');
        }
        if (method.getKind() == ElementKind.METHOD && method.getReturnType().getKind() != TypeKind.VOID) {
            out.append("**").append(labels.returns()).append("** `").append(typeName(method.getReturnType())).append('`');
            blockText(method, DocTree.Kind.RETURN).ifPresent(text -> out.append(" — ").append(text));
            out.append("\n\n");
        }
        for (Map.Entry<String, String> thrown : throwsDescriptions(method).entrySet()) {
            out.append("**").append(labels.throwsLabel()).append("** `").append(thrown.getKey()).append('`');
            if (!thrown.getValue().isEmpty()) {
                out.append(" — ").append(thrown.getValue());
            }
            out.append("\n\n");
        }
    }

    // ---------------------------------------------------------------- members and declarations

    /** Public constructors and methods declared in {@code type}, without implicit record and enum members. */
    private List<Element> documentedMembers(TypeElement type) {
        Set<String> components = type.getRecordComponents().stream()
                .map(component -> component.getSimpleName().toString())
                .collect(Collectors.toSet());
        List<Element> members = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD && member.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            ExecutableElement executable = (ExecutableElement) member;
            boolean visible = executable.getModifiers().contains(Modifier.PUBLIC)
                    || (type.getKind().isInterface() && !executable.getModifiers().contains(Modifier.PRIVATE));
            if (!visible) {
                continue;
            }
            boolean undocumented = trees.getDocCommentTree(docSource(member)) == null;
            if (undocumented
                    && Set.of("toString", "hashCode", "equals", "compareTo")
                            .contains(executable.getSimpleName().toString())) {
                continue;
            }
            if (undocumented && type.getKind() == ElementKind.RECORD) {
                boolean accessor = executable.getParameters().isEmpty()
                        && components.contains(executable.getSimpleName().toString());
                boolean canonical = executable.getKind() == ElementKind.CONSTRUCTOR;
                boolean objectMethod = Set.of("toString", "hashCode", "equals")
                        .contains(executable.getSimpleName().toString());
                if (accessor || canonical || objectMethod) {
                    continue;
                }
            }
            if (undocumented && type.getKind() == ElementKind.ENUM) {
                String methodName = executable.getSimpleName().toString();
                if (methodName.equals("values") || methodName.equals("valueOf")
                        || executable.getKind() == ElementKind.CONSTRUCTOR) {
                    continue;
                }
            }
            if (undocumented && executable.getKind() == ElementKind.CONSTRUCTOR && executable.getParameters().isEmpty()) {
                continue;
            }
            members.add(member);
        }
        return members;
    }

    private String declaration(TypeElement type) {
        StringBuilder text = new StringBuilder();
        text.append(modifiers(type.getModifiers(), type.getKind()));
        text.append(switch (type.getKind()) {
            case INTERFACE -> "interface ";
            case ANNOTATION_TYPE -> "@interface ";
            case ENUM -> "enum ";
            case RECORD -> "record ";
            default -> "class ";
        });
        text.append(type.getSimpleName()).append(typeParameters(type.getTypeParameters()));
        if (type.getKind() == ElementKind.RECORD) {
            text.append(type.getRecordComponents().stream()
                    .map(component -> typeName(component.asType()) + " " + component.getSimpleName())
                    .collect(Collectors.joining(", ", "(", ")")));
        }
        TypeMirror superclass = type.getSuperclass();
        if (type.getKind() == ElementKind.CLASS
                && superclass.getKind() == TypeKind.DECLARED
                && !superclass.toString().equals("java.lang.Object")) {
            text.append(" extends ").append(typeName(superclass));
        }
        if (!type.getInterfaces().isEmpty()) {
            text.append(type.getKind().isInterface() ? " extends " : " implements ");
            text.append(type.getInterfaces().stream().map(this::typeName).collect(Collectors.joining(", ")));
        }
        return text.toString();
    }

    private String signature(TypeElement owner, ExecutableElement method) {
        StringBuilder text = new StringBuilder();
        Set<Modifier> modifiers = method.getModifiers();
        if (!owner.getKind().isInterface()) {
            text.append(modifiers(modifiers, method.getKind()));
        } else {
            if (modifiers.contains(Modifier.STATIC)) {
                text.append("static ");
            }
            if (modifiers.contains(Modifier.DEFAULT)) {
                text.append("default ");
            }
        }
        String typeParameters = typeParameters(method.getTypeParameters());
        if (!typeParameters.isEmpty()) {
            text.append(typeParameters).append(' ');
        }
        if (method.getKind() == ElementKind.CONSTRUCTOR) {
            text.append(owner.getSimpleName());
        } else {
            text.append(typeName(method.getReturnType())).append(' ').append(method.getSimpleName());
        }
        text.append(method.getParameters().stream()
                .map(parameter -> typeName(parameter.asType()) + " " + parameter.getSimpleName())
                .collect(Collectors.joining(", ", "(", ")")));
        if (method.isVarArgs()) {
            int bracket = text.lastIndexOf("[]");
            text.replace(bracket, bracket + 2, "...");
        }
        if (!method.getThrownTypes().isEmpty()) {
            text.append(" throws ")
                    .append(method.getThrownTypes().stream().map(this::typeName).collect(Collectors.joining(", ")));
        }
        return text.toString();
    }

    private static String modifiers(Set<Modifier> modifiers, ElementKind kind) {
        StringBuilder text = new StringBuilder();
        for (Modifier modifier : List.of(Modifier.PUBLIC, Modifier.PROTECTED, Modifier.ABSTRACT, Modifier.STATIC,
                Modifier.FINAL, Modifier.SEALED, Modifier.NON_SEALED, Modifier.DEFAULT)) {
            boolean implied = (kind == ElementKind.INTERFACE || kind == ElementKind.ANNOTATION_TYPE)
                            && modifier == Modifier.ABSTRACT
                    || (kind == ElementKind.ENUM || kind == ElementKind.RECORD) && modifier == Modifier.FINAL;
            if (modifiers.contains(modifier) && !implied) {
                text.append(modifier).append(' ');
            }
        }
        return text.toString();
    }

    private String typeParameters(List<? extends TypeParameterElement> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }
        return parameters.stream()
                .map(parameter -> {
                    List<? extends TypeMirror> bounds = parameter.getBounds();
                    boolean onlyObject = bounds.size() == 1 && bounds.get(0).toString().equals("java.lang.Object");
                    return parameter.getSimpleName()
                            + (bounds.isEmpty() || onlyObject
                                    ? ""
                                    : " extends " + bounds.stream().map(this::typeName).collect(Collectors.joining(" & ")));
                })
                .collect(Collectors.joining(", ", "<", ">"));
    }

    private List<String> supertypes(TypeElement type) {
        List<String> names = new ArrayList<>();
        List<TypeMirror> supertypes = new ArrayList<>(type.getInterfaces());
        if (type.getKind() == ElementKind.CLASS && type.getSuperclass().getKind() == TypeKind.DECLARED) {
            supertypes.add(0, type.getSuperclass());
        }
        for (TypeMirror supertype : supertypes) {
            Element element = types.asElement(supertype);
            if (element instanceof TypeElement superElement && typePages.containsKey(superElement)) {
                names.add(link(superElement, null, displayName(superElement)));
            }
        }
        return names;
    }

    private String typeName(TypeMirror type) {
        return QUALIFIER.matcher(type.toString()).replaceAll("");
    }

    // ---------------------------------------------------------------- doc comments

    /**
     * The element whose comment documents {@code element}: itself, or for an undocumented method the
     * documented method it overrides, as Javadoc does.
     */
    private Element docSource(Element element) {
        if (trees.getDocCommentTree(element) != null || !(element instanceof ExecutableElement method)) {
            return element;
        }
        TypeElement owner = enclosingType(method);
        List<TypeMirror> pending = new ArrayList<>(types.directSupertypes(owner.asType()));
        while (!pending.isEmpty()) {
            TypeMirror supertype = pending.remove(0);
            if (!(types.asElement(supertype) instanceof TypeElement superElement)) {
                continue;
            }
            for (ExecutableElement candidate : ElementFilter.methodsIn(superElement.getEnclosedElements())) {
                if (elements.overrides(method, candidate, owner) && trees.getDocCommentTree(candidate) != null) {
                    return candidate;
                }
            }
            pending.addAll(types.directSupertypes(supertype));
        }
        return element;
    }

    private Optional<String> markdown(Element element) {
        element = docSource(element);
        DocCommentTree comment = trees.getDocCommentTree(element);
        if (comment == null || comment.getFullBody().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(render(element, comment, comment.getFullBody(), false).strip());
    }

    private Optional<String> firstSentence(Element element) {
        element = docSource(element);
        DocCommentTree comment = trees.getDocCommentTree(element);
        if (comment == null || comment.getFirstSentence().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(render(element, comment, comment.getFirstSentence(), true).strip());
    }

    private Map<String, String> paramDescriptions(Element element) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        element = docSource(element);
        DocCommentTree comment = trees.getDocCommentTree(element);
        if (comment == null) {
            return descriptions;
        }
        for (DocTree tag : comment.getBlockTags()) {
            if (tag instanceof ParamTree param && !param.isTypeParameter()) {
                descriptions.put(
                        param.getName().getName().toString(),
                        render(element, comment, param.getDescription(), true).strip());
            }
        }
        return descriptions;
    }

    private Optional<String> blockText(Element element, DocTree.Kind kind) {
        element = docSource(element);
        DocCommentTree comment = trees.getDocCommentTree(element);
        if (comment == null) {
            return Optional.empty();
        }
        for (DocTree tag : comment.getBlockTags()) {
            if (tag.getKind() == kind && tag instanceof ReturnTree returnTag) {
                return Optional.of(render(element, comment, returnTag.getDescription(), true).strip());
            }
        }
        return Optional.empty();
    }

    private Map<String, String> throwsDescriptions(Element element) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        element = docSource(element);
        DocCommentTree comment = trees.getDocCommentTree(element);
        if (comment == null) {
            return descriptions;
        }
        for (DocTree tag : comment.getBlockTags()) {
            if (tag instanceof ThrowsTree thrown) {
                String exception = thrown.getExceptionName().getSignature();
                descriptions.put(
                        exception.substring(exception.lastIndexOf('.') + 1),
                        render(element, comment, thrown.getDescription(), true).strip());
            }
        }
        return descriptions;
    }

    /**
     * Renders doc comment trees as Markdown that is also valid MDX: text outside code is escaped, and
     * with {@code inline} paragraphs and lists collapse into one line for table cells.
     */
    private String render(Element owner, DocCommentTree comment, List<? extends DocTree> body, boolean inline) {
        StringBuilder out = new StringBuilder();
        StringBuilder pre = null;
        for (DocTree tree : body) {
            if (pre != null) {
                if (tree instanceof EndElementTree end && end.getName().contentEquals("pre")) {
                    out.append("\n\n```java\n").append(pre.toString().strip()).append("\n```\n\n");
                    pre = null;
                } else if (tree instanceof LiteralTree literal) {
                    pre.append(literal.getBody().getBody());
                } else if (tree instanceof TextTree text) {
                    pre.append(text.getBody());
                } else if (tree instanceof EntityTree entity) {
                    pre.append(entity(entity));
                }
                continue;
            }
            switch (tree) {
                case TextTree text -> out.append(escape(text.getBody().replaceAll("\\s+", " ")));
                case LiteralTree literal -> {
                    String literalText = literal.getBody().getBody().replaceAll("\\s+", " ");
                    out.append(tree.getKind() == DocTree.Kind.CODE ? code(literalText) : escape(literalText));
                }
                case LinkTree link -> out.append(renderLink(owner, comment, link));
                case EntityTree entity -> out.append('&').append(entity.getName()).append(';');
                case StartElementTree start -> {
                    String tag = start.getName().toString().toLowerCase(Locale.ROOT);
                    switch (tag) {
                        case "p" -> out.append(inline ? " " : "\n\n");
                        case "ul", "ol" -> out.append(inline ? " " : "\n");
                        case "li" -> out.append(inline ? " • " : "\n- ");
                        case "b", "strong" -> out.append("**");
                        case "i", "em" -> out.append('_');
                        case "pre" -> {
                            if (inline) {
                                out.append(' ');
                            } else {
                                pre = new StringBuilder();
                            }
                        }
                        case "br" -> out.append(inline ? " " : "\n\n");
                        default -> {}
                    }
                }
                case EndElementTree end -> {
                    String tag = end.getName().toString().toLowerCase(Locale.ROOT);
                    switch (tag) {
                        case "b", "strong" -> out.append("**");
                        case "i", "em" -> out.append('_');
                        case "ul", "ol" -> out.append(inline ? " " : "\n\n");
                        default -> {}
                    }
                }
                default -> {
                    if (tree.getKind() == DocTree.Kind.INHERIT_DOC) {
                        out.append(labels.inheritedDoc());
                    } else {
                        out.append(escape(tree.toString().replaceAll("\\s+", " ")));
                    }
                }
            }
        }
        String text = out.toString();
        return inline ? text.replaceAll("\\s+", " ") : text.replaceAll("\n{3,}", "\n\n");
    }

    private String renderLink(Element owner, DocCommentTree comment, LinkTree link) {
        String signature = link.getReference().getSignature();
        String label = link.getLabel().isEmpty()
                ? signature.replace('#', '.').replaceFirst("^\\.", "")
                : render(owner, comment, link.getLabel(), true).strip();
        Element target = resolve(owner, comment, link);
        boolean plain = link.getKind() == DocTree.Kind.LINK_PLAIN;
        if (target == null) {
            return plain ? escape(label) : code(QUALIFIER.matcher(label).replaceAll(""));
        }
        TypeElement type = target instanceof TypeElement typeElement ? typeElement : enclosingType(target);
        if (type == null || !typePages.containsKey(type)) {
            return plain ? escape(label) : code(QUALIFIER.matcher(label).replaceAll(""));
        }
        String shown = plain ? escape(label) : code(QUALIFIER.matcher(label).replaceAll(""));
        return link(type, target instanceof TypeElement ? null : target, shown);
    }

    private Element resolve(Element owner, DocCommentTree comment, LinkTree link) {
        TreePath path = trees.getPath(owner);
        if (path == null) {
            return null;
        }
        try {
            DocTreePath docPath = DocTreePath.getPath(path, comment, link.getReference());
            return docPath == null ? null : trees.getElement(docPath);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A Markdown link to {@code type}, or to {@code member} of it, from any package page. */
    private String link(TypeElement type, Element member, String shown) {
        String anchor = member != null && memberAnchors.containsKey(member)
                ? memberAnchors.get(member)
                : typeAnchors.get(type);
        return "[" + shown + "](./" + typePages.get(type) + ".md#" + anchor + ")";
    }

    private static TypeElement enclosingType(Element element) {
        Element current = element.getEnclosingElement();
        while (current != null && !(current instanceof TypeElement)) {
            current = current.getEnclosingElement();
        }
        return (TypeElement) current;
    }

    private static String entity(EntityTree entity) {
        return switch (entity.getName().toString()) {
            case "lt" -> "<";
            case "gt" -> ">";
            case "amp" -> "&";
            case "quot" -> "\"";
            default -> "&" + entity.getName() + ";";
        };
    }

    // ---------------------------------------------------------------- names and escaping

    private Optional<String> stability(Element element) {
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            if (annotation.getAnnotationType().asElement().getSimpleName().contentEquals("ApiStability")) {
                return annotation.getElementValues().values().stream()
                        .findFirst()
                        .map(value -> value.getValue().toString());
            }
        }
        return Optional.empty();
    }

    private String kindName(TypeElement type) {
        return switch (type.getKind()) {
            case INTERFACE ->
                type.getAnnotation(FunctionalInterface.class) != null
                        ? labels.functionalInterface()
                        : labels.interfaceKind();
            case ANNOTATION_TYPE -> labels.annotationKind();
            case ENUM -> labels.enumKind();
            case RECORD -> labels.recordKind();
            default ->
                type.getModifiers().contains(Modifier.ABSTRACT) ? labels.abstractClassKind() : labels.classKind();
        };
    }

    /** {@code Outer.Inner} for member types. */
    private static String displayName(TypeElement type) {
        String qualified = type.getQualifiedName().toString();
        String pkg = packageOf(type).getQualifiedName().toString();
        return pkg.isEmpty() ? qualified : qualified.substring(pkg.length() + 1);
    }

    private static PackageElement packageOf(Element element) {
        Element current = element;
        while (!(current instanceof PackageElement)) {
            current = current.getEnclosingElement();
        }
        return (PackageElement) current;
    }

    private static String shortName(PackageElement pkg) {
        String name = pkg.getQualifiedName().toString();
        return name.startsWith(API_PREFIX) ? name.substring(API_PREFIX.length()) : name;
    }

    private static String pageName(PackageElement pkg) {
        return shortName(pkg).replace('.', '-');
    }

    private static String anchor(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static String code(String text) {
        String fence = text.contains("`") ? "``" : "`";
        String padded = text.startsWith("`") || text.endsWith("`") ? " " + text + " " : text;
        return fence + padded + fence;
    }

    /** Escapes characters MDX would read as JSX or expressions. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

    private static String cell(String text) {
        String single = text.replace("\n", " ").replace("|", "\\|").strip();
        return single.isEmpty() ? "—" : single;
    }

    private static String plain(String markdown) {
        return markdown.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replace("`", "")
                .replace("\\{", "{")
                .replace("\\}", "}")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .strip();
    }

    private static String yaml(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
