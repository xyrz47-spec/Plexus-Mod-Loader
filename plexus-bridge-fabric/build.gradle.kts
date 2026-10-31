plugins {
    id("plexus.java-conventions")
}

description = "Fabric bridge: fabric.mod.json analysis, Fabric Loader API for bridged mods, entrypoint loading."

// Fabric Loader (Apache-2.0) itself never starts. Its public API (net.fabricmc.api, net.fabricmc.loader.api) and
// the version parser that API uses are packed into this jar with the licence; the bridge provides its own
// FabricLoaderImpl and DefaultLanguageAdapter, the only internal classes that API refers to (ADR-0016).
val fabricLoaderJar = configurations.create("fabricLoaderJar") {
    isCanBeConsumed = false
    isTransitive = false
}
// Quilt Loader (Apache-2.0) never starts either. Its public API for mods (org.quiltmc.loader.api, its entrypoint and
// minecraft packages) and the pure helper classes that API refers to (versions, dependency objects, licences, JSON
// values, logging) are packed the same way (ADR-0027). The bridge provides QuiltLoaderImpl, GameProvider and
// DefaultLanguageAdapter itself: they lead into the loader. When Quilt Loader is upgraded, check again that the packed
// classes refer only to each other, those three, and the packed Fabric API (jdeps -verbose:class on the patterns
// below), and compare dev.plexusmodloader.compat.quilt.QuiltVersionSpecifier with V1ModMetadataReader.
val quiltLoaderJar = configurations.create("quiltLoaderJar") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    implementation(project(":plexus-bridge-api"))
    implementation(libs.gson)
    implementation(libs.slf4j.api)
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.guava)
    testImplementation(libs.guava)
    fabricLoaderJar(libs.fabric.loader)
    quiltLoaderJar(libs.quilt.loader)
    // Mixin (FabricMC fork, MIT) and MixinExtras (MIT) at the versions Fabric Loader 0.19.5 ships, on Plexus Mod Loader's
    // class path for every Fabric mod (ADR-0020). Mixin asks for ASM 9.8; Plexus Mod Loader's ASM is newer and API
    // compatible, and every ASM module is held at that one version.
    implementation(libs.sponge.mixin) { isTransitive = false }
    implementation(libs.mixinextras.common) { isTransitive = false }
    implementation(libs.asm)
    implementation(libs.asm.tree)
    implementation(libs.asm.commons)
    implementation(libs.asm.analysis)
    implementation(libs.asm.util)
}
// The closure of the public API's references (checked with javap), minus the two classes the bridge provides
// itself.
val fabricLoaderApiClasses = listOf(
    "net/fabricmc/api/**",
    "net/fabricmc/loader/api/**",
    "net/fabricmc/loader/util/**",
    // The pre-1.0 adapter interface, still implemented by language mods such as fabric-language-kotlin.
    "net/fabricmc/loader/language/LanguageAdapter*",
    "net/fabricmc/loader/impl/util/version/**",
    "net/fabricmc/loader/impl/util/ExceptionUtil*",
    "net/fabricmc/loader/impl/util/UrlUtil*",
    "net/fabricmc/loader/impl/util/UrlConversionException*",
    "net/fabricmc/loader/impl/util/SystemProperties*",
    "LICENSE_fabric-loader",
)
val fabricLoaderApi = tasks.register<Sync>("fabricLoaderApi") {
    // Declared as an input: Gradle does not rerun the copy when only the patterns change.
    inputs.property("classes", fabricLoaderApiClasses)
    from(provider { zipTree(fabricLoaderJar.singleFile) })
    include(fabricLoaderApiClasses)
    into(layout.buildDirectory.dir("fabric-loader-api"))
}
tasks.jar {
    from(fabricLoaderApi)
}
val quiltLoaderApiClasses = listOf(
    "org/quiltmc/loader/api/CachedFileSystem*",
    "org/quiltmc/loader/api/FasterFileSystem*",
    "org/quiltmc/loader/api/FasterFiles*",
    "org/quiltmc/loader/api/LanguageAdapter*",
    "org/quiltmc/loader/api/LoaderValue*",
    "org/quiltmc/loader/api/MappingResolver*",
    "org/quiltmc/loader/api/ModContainer*",
    "org/quiltmc/loader/api/ModContributor*",
    "org/quiltmc/loader/api/ModDependency*",
    "org/quiltmc/loader/api/ModInternal*",
    "org/quiltmc/loader/api/ModLicense*",
    "org/quiltmc/loader/api/ModMetadata*",
    "org/quiltmc/loader/api/QuiltLoader.class",
    "org/quiltmc/loader/api/QuiltLoader$*",
    "org/quiltmc/loader/api/Requires*",
    "org/quiltmc/loader/api/Version*",
    "org/quiltmc/loader/api/entrypoint/**",
    "org/quiltmc/loader/api/minecraft/**",
    "org/quiltmc/loader/impl/metadata/VersionRangeImpl*",
    "org/quiltmc/loader/impl/metadata/VersionIntervalImpl*",
    "org/quiltmc/loader/impl/metadata/qmj/SemanticVersionImpl*",
    "org/quiltmc/loader/impl/metadata/qmj/GenericVersionImpl*",
    "org/quiltmc/loader/impl/metadata/qmj/VersionConstraintImpl*",
    "org/quiltmc/loader/impl/metadata/qmj/ModDependencyIdentifierImpl*",
    "org/quiltmc/loader/impl/metadata/qmj/ModContributorImpl*",
    "org/quiltmc/loader/impl/metadata/qmj/ModDependencyImpl*",
    "org/quiltmc/loader/impl/metadata/qmj/QmjMetadataInternalFactory*",
    "org/quiltmc/loader/impl/metadata/qmj/ModLicenseImpl*",
    "org/quiltmc/loader/impl/metadata/qmj/JsonLoaderValue*",
    "org/quiltmc/loader/impl/lib/flexver/**",
    "org/quiltmc/loader/impl/lib/parsers/json/**",
    "org/quiltmc/loader/impl/util/log/**",
    "org/quiltmc/loader/impl/util/QuiltLoaderInternal*",
    "org/quiltmc/loader/impl/util/LoaderUtil*",
    "org/quiltmc/loader/impl/util/ExceptionUtil*",
    "org/quiltmc/loader/impl/util/GlobalPaths*",
    "org/quiltmc/loader/impl/entrypoint/EntrypointUtils*",
    "quilt_loader/licenses.json",
    "LICENSE_quilt-loader",
)
val quiltLoaderApi = tasks.register<Sync>("quiltLoaderApi") {
    inputs.property("classes", quiltLoaderApiClasses)
    from(provider { zipTree(quiltLoaderJar.singleFile) })
    include(quiltLoaderApiClasses)
    into(layout.buildDirectory.dir("quilt-loader-api"))
}
// Plexus Mod Loader's builds of Fabric API modules on NeoForge's hooks and their index (ADR-0031, HostModules): in the jar
// only, so that the bridge's unit tests do not install NeoForge.
val hostModules = configurations.dependencyScope("hostModules")
val hostModuleResources = configurations.resolvable("hostModuleResources") { extendsFrom(hostModules.get()) }
dependencies {
    hostModules(project(path = ":plexus-fabric-api-neoforge-v26_2", configuration = "hostModuleElements"))
}
tasks.jar {
    from(quiltLoaderApi)
    from(hostModuleResources)
}
dependencies {
    // The bridge compiles against the packed Quilt classes only, not the whole Quilt Loader jar: that jar also carries
    // its own copies of Fabric Loader's classes, which must not shadow Fabric Loader 0.19.5 on the compile class path.
    compileOnly(files(quiltLoaderApi))
    // Tests see exactly the Fabric and Quilt classes the bridge jar carries.
    testImplementation(files(fabricLoaderApi))
    testImplementation(files(quiltLoaderApi))
}

// The Fabric Loader API version on the class path; mods that require "fabricloader" are checked against it.
tasks.processResources {
    val fabricLoaderVersion = libs.versions.fabricLoader.get()
    val mixinExtrasVersion = libs.versions.mixinExtras.get()
    inputs.property("fabricLoaderVersion", fabricLoaderVersion)
    val quiltLoaderVersion = libs.versions.quiltLoader.get()
    inputs.property("mixinExtrasVersion", mixinExtrasVersion)
    inputs.property("quiltLoaderVersion", quiltLoaderVersion)
    filesMatching("dev/plexusmodloader/compat/fabric/fabric-bridge.properties") {
        expand(
            "fabricLoaderVersion" to fabricLoaderVersion,
            "mixinExtrasVersion" to mixinExtrasVersion,
            "quiltLoaderVersion" to quiltLoaderVersion,
        )
    }
}
