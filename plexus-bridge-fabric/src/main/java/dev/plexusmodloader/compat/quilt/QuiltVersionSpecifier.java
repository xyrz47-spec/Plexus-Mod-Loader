/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from Quilt Loader 0.31.0-beta.4, org.quiltmc.loader.impl.metadata.qmj.V1ModMetadataReader:
 * readVersionSpecifier, readVersionsValue, readVersionsObject and VersionJoiner. Changes: reads Gson values instead of
 * Quilt's JsonLoaderValue; a malformed specifier that made Quilt's code throw an unchecked exception (an empty text, a
 * lone operator) is a VersionFormatException; the warnings about deprecated arrays and unnecessary ranges are not
 * logged. The resulting ranges are the same.
 */
package dev.plexusmodloader.compat.quilt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionFormatException;
import org.quiltmc.loader.api.VersionRange;

/**
 * Reads the version requirements of {@code quilt.mod.json} dependency objects ({@code versions}) as Quilt Loader does
 * (RFC 0002, ADR-0027 decision 3), into Quilt Loader's own {@link VersionRange}. Unlike Fabric, a version without an
 * operator means {@code ^} when it is semantic ({@code 1.0.0} accepts {@code 1.2.0}) and {@code =} otherwise.
 *
 * <p>Used while assessing a mod, on the launch thread; stateless.
 */
public final class QuiltVersionSpecifier {

    private QuiltVersionSpecifier() {}

    /**
     * The range of one specifier: {@code *}, {@code =X}, {@code >=X}, {@code >X}, {@code <=X}, {@code <X}, {@code ~X}
     * ({@code [X, next minor)}), {@code ^X} ({@code [X, next major)}), {@code A.B.x} ({@code [A.B, A.(B+1))}), or a bare
     * version ({@code ^} if semantic, {@code =} otherwise).
     *
     * @throws VersionFormatException if the specifier cannot be read
     */
    public static VersionRange read(String specifier) throws VersionFormatException {
        try {
            return readUnchecked(specifier);
        } catch (RuntimeException e) {
            // Quilt's own code runs off the end of the text here (an empty text, a lone '>'); Quilt Loader refuses the
            // mod either way.
            throw new VersionFormatException("Invalid version specifier \"" + specifier + "\"", e);
        }
    }

    private static VersionRange readUnchecked(String string) throws VersionFormatException {
        if (string == null) {
            return VersionRange.ANY;
        }
        if (string.equals("*")) {
            return VersionRange.ANY;
        }
        String withoutPrefix = string.substring(1);
        switch (string.charAt(0)) {
            case '=':
                return VersionRange.ofExact(Version.of(withoutPrefix));
            case '>':
                if (string.charAt(1) == '=') {
                    return VersionRange.ofInterval(Version.of(string.substring(2)), true, null, false);
                } else {
                    return VersionRange.ofInterval(Version.of(withoutPrefix), false, null, false);
                }
            case '<':
                if (string.charAt(1) == '=') {
                    return VersionRange.ofInterval(null, false, Version.of(string.substring(2)), true);
                } else {
                    return VersionRange.ofInterval(null, false, Version.of(withoutPrefix), false);
                }
            // Semantic versions only
            case '~': {
                Version.Semantic min = Version.Semantic.of(withoutPrefix);
                int[] components = min.semantic().versionComponents();
                if (components.length < 2) {
                    components = new int[] {components[0], 1};
                } else if (components.length == 2) {
                    components[1]++;
                } else {
                    components = new int[] {components[0], components[1] + 1};
                }
                Version max = Version.Semantic.of(components, null, null);
                return VersionRange.ofInterval(min, true, max, false);
            }
            case '^': {
                Version.Semantic min = Version.Semantic.of(withoutPrefix);
                return nextMajor(min);
            }
            default: {
                // Get just the version part of the string
                String stripped = string;
                int preIndex = string.indexOf('-');
                if (preIndex != -1) {
                    stripped = string.substring(0, preIndex);
                }
                int metadataIndex = string.indexOf('+');
                if (metadataIndex != -1) {
                    stripped = string.substring(0, metadataIndex);
                }
                if (stripped.endsWith(".x")) {
                    if (string.indexOf(".x") != string.length() - 2) {
                        throw new VersionFormatException(String.format("Invalid version specifier \"%s\"", string));
                    }
                    Version.Semantic min = Version.Semantic.of(string.substring(0, string.length() - 2));
                    int[] components = min.versionComponents(); // Copies
                    components[components.length - 1]++;
                    Version.Semantic max = Version.Semantic.of(components, null, null);
                    return VersionRange.ofInterval(min, true, max, false);
                }
                Version v = Version.of(string);
                if (v.isSemantic()) {
                    // Spec says that 1.0.0 is the same as ^1.0.0
                    return nextMajor(v.semantic());
                }
                // same as =
                return VersionRange.ofExact(v);
            }
        }
    }

    private static VersionRange nextMajor(Version.Semantic min) throws VersionFormatException {
        int newMajor = min.versionComponent(0) + 1;
        Version max = Version.Semantic.of(new int[] {newMajor}, null, null);
        return VersionRange.ofInterval(min, true, max, false);
    }

    /**
     * The range of a dependency object's {@code versions} value: absent ({@code null}) is any version, a text is one
     * specifier, an array (deprecated by Quilt, RFC 56) is any of its specifiers, an object is {@code {"any": [...]}}
     * or {@code {"all": [...]}} of specifiers and nested objects.
     *
     * @throws VersionFormatException if the value cannot be read, or an object is empty, matches no version, or
     *     matches every version
     */
    public static VersionRange readVersions(JsonElement versions) throws VersionFormatException {
        if (versions == null) {
            return VersionRange.ANY;
        }
        if (versions instanceof JsonObject object) {
            return readVersionsObject(object);
        }
        if (versions instanceof JsonArray array) {
            List<VersionRange> ranges = new ArrayList<>();
            for (JsonElement element : array) {
                ranges.add(read(text(element)));
            }
            return VersionRange.ofRanges(ranges);
        }
        if (isString(versions)) {
            return read(versions.getAsString());
        }
        throw new VersionFormatException("Expected to find a string, object, or array but found " + versions);
    }

    private static VersionRange readVersionsObject(JsonObject object) throws VersionFormatException {
        JsonElement any = object.get("any");
        JsonElement all = object.get("all");
        if (any == null && all == null) {
            throw new VersionFormatException("Expected to find either 'any' or 'all', but found '" + object + "'");
        }
        JsonElement value = any != null ? any : all;
        if (!(value instanceof JsonArray array)) {
            String name = any != null ? "any" : "all";
            throw new VersionFormatException("Expected the value of " + name + " to be an array, but was " + value);
        }
        List<VersionRange> ranges = new ArrayList<>();
        for (JsonElement element : array) {
            if (isString(element)) {
                ranges.add(read(element.getAsString()));
            } else if (element instanceof JsonObject nested) {
                ranges.add(readVersionsObject(nested));
            } else {
                throw new VersionFormatException("Expected to find either a string or an object, but found " + element);
            }
        }
        VersionRange range = any != null ? VersionRange.ofRanges(ranges) : matchingAll(ranges);
        if (range == null) {
            throw new VersionFormatException("Expected the 'versions' array to be non-empty");
        }
        if (range == VersionRange.NONE) {
            throw new VersionFormatException(
                    "Expected the 'versions' array to match at least one version, but it doesn't!");
        }
        if (range == VersionRange.ANY) {
            throw new VersionFormatException(
                    "Expected the 'versions' array to restrict some versions, but it doesn't!");
        }
        return range;
    }

    /** Quilt's {@code VersionJoiner.ALL}: {@code null} for no ranges. */
    private static VersionRange matchingAll(List<VersionRange> ranges) {
        VersionRange range = null;
        for (VersionRange sub : ranges) {
            range = range == null ? sub : sub.combineMatchingBoth(range);
        }
        return range;
    }

    /**
     * Whether a version text is in {@code range}, as Quilt Loader decides it ({@link Version#of}: semantic versions by
     * their parts and FlexVer otherwise).
     */
    public static Predicate<String> accepts(VersionRange range) {
        return text -> {
            try {
                return range.isSatisfiedBy(Version.of(text));
            } catch (RuntimeException e) {
                return range == VersionRange.ANY;
            }
        };
    }

    private static String text(JsonElement element) throws VersionFormatException {
        if (!isString(element)) {
            throw new VersionFormatException("Expected a version specifier text, but found " + element);
        }
        return element.getAsString();
    }

    private static boolean isString(JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString();
    }
}
