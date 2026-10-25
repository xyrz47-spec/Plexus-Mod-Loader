package dev.polymodloader.tools.apireference;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * Writes the public API surface of the sources it runs over: one {@link ApiKeys} name per line for every public type
 * of a {@code dev.polymodloader.api} (or, before the rename, {@code dev.polyloader.api}) package and every public
 * constructor, method, field and enum constant it declares. Members the compiler adds (a record's accessors and
 * canonical constructor, an enum's {@code values}) are left out. The output file is the system property {@value
 * #OUTPUT_PROPERTY}.
 */
public final class ApiSurfaceDoclet implements Doclet {

    /** System property naming the output file. */
    public static final String OUTPUT_PROPERTY = "poly.apiSurface.output";

    private Reporter reporter;

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "PolyApiSurface";
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
        Set<String> keys = new TreeSet<>();
        for (TypeElement type : ElementFilter.typesIn(environment.getIncludedElements())) {
            collect(type, environment, keys);
        }
        String output = System.getProperty(OUTPUT_PROPERTY);
        if (output == null) {
            reporter.print(javax.tools.Diagnostic.Kind.ERROR, "System property " + OUTPUT_PROPERTY + " is not set");
            return false;
        }
        try {
            Files.write(Path.of(output), keys, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return true;
    }

    private static void collect(TypeElement type, DocletEnvironment environment, Set<String> keys) {
        Elements elements = environment.getElementUtils();
        String packageName = elements.getPackageOf(type).getQualifiedName().toString();
        boolean api = ApiKeys.normalize(packageName + ".").startsWith("dev.polymodloader.api.");
        if (!api || !type.getModifiers().contains(Modifier.PUBLIC)) {
            return;
        }
        keys.add(ApiKeys.type(elements, type));
        boolean isInterface = type.getKind().isInterface();
        for (Element member : type.getEnclosedElements()) {
            if (member instanceof TypeElement nested) {
                collect(nested, environment, keys);
                continue;
            }
            boolean apiMember = switch (member.getKind()) {
                case CONSTRUCTOR, METHOD, FIELD, ENUM_CONSTANT -> true;
                default -> false;
            };
            if (!apiMember) {
                continue;
            }
            boolean visible = member.getModifiers().contains(Modifier.PUBLIC)
                    || (isInterface && !member.getModifiers().contains(Modifier.PRIVATE));
            boolean written = elements.getOrigin(member) == Elements.Origin.EXPLICIT;
            if (visible && written) {
                keys.add(ApiKeys.member(elements, environment.getTypeUtils(), type, member));
            }
        }
    }
}
