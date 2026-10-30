package dev.plexusmodloader.devtools.gradle;

import org.gradle.api.provider.Property;

/**
 * {@code polySdkPublication} of every Poly Mod Loader module: how the module goes into the public SDK repository
 * ({@code publishSdk}, ADR-0008). The root plugin {@code poly.sdk-repository} sets it for the modules it
 * publishes; build scripts do not need to.
 */
public abstract class SdkPublicationExtension {

    /**
     * Whether the SDK publication carries the JavaDoc jar: only the public API modules that mod projects
     * compile against. The default is {@code false}; sources are never part of the SDK publication.
     */
    public abstract Property<Boolean> getJavadoc();
}
