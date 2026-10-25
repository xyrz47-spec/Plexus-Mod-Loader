package dev.polymodloader.tools.apireference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.DocumentationTool;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Writes the API reference: {@code ApiReferenceMain <locale>=<output directory>... <source root>...} runs
 * {@link MarkdownDoclet} over every {@code dev.polymodloader.api} package in the source roots, once per
 * {@code locale=directory} pair (for example {@code en=site/docs/api-reference}).
 */
public final class ApiReferenceMain {

    private ApiReferenceMain() {}

    public static void main(String[] args) throws IOException {
        Map<String, String> outputs = new LinkedHashMap<>();
        List<Path> sourceRoots = new ArrayList<>();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (equals > 0 && !arg.substring(0, equals).contains(java.io.File.separator)) {
                outputs.put(arg.substring(0, equals), arg.substring(equals + 1));
            } else {
                sourceRoots.add(Path.of(arg));
            }
        }
        if (outputs.isEmpty() || sourceRoots.isEmpty()) {
            throw new IllegalArgumentException(
                    "Usage: ApiReferenceMain <locale>=<output directory>... <source root>...");
        }
        for (Map.Entry<String, String> output : outputs.entrySet()) {
            write(output.getKey(), output.getValue(), sourceRoots);
        }
    }

    private static void write(String locale, String output, List<Path> sourceRoots) throws IOException {
        System.setProperty(MarkdownDoclet.OUTPUT_PROPERTY, output);
        System.setProperty(MarkdownDoclet.LOCALE_PROPERTY, locale);
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        try (StandardJavaFileManager files = tool.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            files.setLocationFromPaths(StandardLocation.SOURCE_PATH, sourceRoots);
            List<String> options = new ArrayList<>(
                    List.of("-encoding", "UTF-8", "-quiet", "-subpackages", "dev.polymodloader.api"));
            boolean ok = tool.getTask(null, files, null, MarkdownDoclet.class, options, null)
                    .call();
            if (!ok) {
                throw new IllegalStateException("The API reference doclet failed for " + locale + "; see the messages above");
            }
        }
    }
}
