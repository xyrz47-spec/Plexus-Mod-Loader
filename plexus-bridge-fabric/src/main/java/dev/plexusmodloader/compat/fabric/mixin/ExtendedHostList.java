package dev.plexusmodloader.compat.fabric.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Called by the bridge of a {@code @Redirect} whose static {@code List} field read the host's patch replaced with a call
 * that returns the field's elements followed by the host's own ({@link HostListExtensions}): the result is the
 * handler's list followed by the host's own elements, so that both the mod's and the host's additions are there.
 *
 * <p>Thread: whichever thread makes the patched call; no state.
 */
public final class ExtendedHostList {

    private ExtendedHostList() {}

    /**
     * The handler's list followed by the host's own elements, in the shape the host's call returns.
     *
     * @param hostResult what the host's call returned: a {@code List} or a {@code Stream}
     * @param field the static field the host's call starts with, as it is now
     * @param handlerList what the mod's handler returns in place of the field
     * @return a {@code Stream} when {@code hostResult} is one, else an unmodifiable {@code List}
     * @throws IllegalStateException {@code PLEXUS-COMPAT-225} when the host's result does not start with {@code field}'s
     *     elements, which the check before the launch could not see
     */
    public static Object extend(Object hostResult, List<?> field, List<?> handlerList) {
        boolean stream = hostResult instanceof Stream<?>;
        List<?> host = stream ? ((Stream<?>) hostResult).toList() : (List<?>) hostResult;
        if (host.size() < field.size() || !host.subList(0, field.size()).equals(field)) {
            throw new IllegalStateException("PLEXUS-COMPAT-225 the host loader's list does not start with the game's"
                    + " list that a Fabric or Quilt mod's mixin replaces (" + field.size() + " game entries, "
                    + host.size() + " in the host's), so Poly Mod Loader cannot add the mod's entries to the host's."
                    + " This is a limitation of Poly Mod Loader, not a bug in the mod: report it to Poly Mod Loader");
        }
        List<Object> extended = new ArrayList<>(handlerList);
        for (Object own : host.subList(field.size(), host.size())) {
            if (!extended.contains(own)) {
                extended.add(own);
            }
        }
        List<Object> result = Collections.unmodifiableList(extended);
        return stream ? result.stream() : result;
    }
}
