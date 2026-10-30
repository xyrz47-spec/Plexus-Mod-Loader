# Third-party notices

Poly Mod Loader is licensed under the Mozilla Public License 2.0 (see `LICENSE`). Its source code is at
https://github.com/xyrz47-spec/Plexus-Mod-Loader. The components below are not Poly Mod Loader's own work. They keep
their own licenses, which Poly Mod Loader's license does not change.

Releases up to 1.1.0 were distributed under the PolyLoader Non-Commercial License 1.0; those copies keep that
license, in addition to any more permissive terms granted later.

## Distributed with PolyLoader

These are inside PolyLoader's jars, the installer, or the libraries the installer copies.

| Component | Version | License | Where |
|---|---|---|---|
| [Fabric Loader](https://github.com/FabricMC/fabric-loader) public API classes (`net.fabricmc.api`, `net.fabricmc.loader.api`, and the classes they need) | 0.19.5 | Apache License 2.0 | `poly-bridge-fabric` jar, with its `LICENSE_fabric-loader` |
| [Quilt Loader](https://github.com/QuiltMC/quilt-loader) public API classes (`org.quiltmc.loader.api`, its `entrypoint` and `minecraft` packages) and the helper classes they need (versions, dependency objects, licences, JSON values, logging; 90 classes, ADR-0027) | 0.31.0-beta.4 | Apache License 2.0 | `poly-bridge-fabric` jar, with its `LICENSE_quilt-loader` and `quilt_loader/licenses.json` |
| [ASM](https://asm.ow2.io/) (`asm`, `asm-tree`, `asm-analysis`, `asm-util`) | 9.11.1 | BSD 3-Clause | runtime library |
| [tomlj](https://github.com/tomlj/tomlj) | 1.1.1 | Apache License 2.0 | runtime library |
| [XZ for Java](https://tukaani.org/xz/java.html) (`org.tukaani:xz`) | 1.9 | Public domain | runtime library (reads Forge's binary patches, ADR-0021) |
| [ANTLR 4 runtime](https://www.antlr.org/) | 4.11.1 | BSD 3-Clause | runtime library (needed by tomlj) |
| [Checker Framework qualifiers](https://checkerframework.org/) | 3.21.2 | MIT | runtime library (needed by tomlj) |
| [Gson](https://github.com/google/gson) | 2.14.0 | Apache License 2.0 | inside the installer jar. At run time PolyLoader uses the copy Minecraft ships |
| [Mixin](https://github.com/FabricMC/Mixin) (FabricMC fork, `net.fabricmc:sponge-mixin`) | 0.17.4+mixin.0.8.7 | MIT | runtime library (Fabric bridge, ADR-0020) |
| [MixinExtras](https://github.com/LlamaLad7/MixinExtras) (`mixinextras-common`) | 0.5.5 | MIT | runtime library (Fabric bridge, ADR-0020) |
| [ASM](https://asm.ow2.io/) `asm-commons` | 9.11.1 | BSD 3-Clause | runtime library (needed by Mixin) |
| Code adapted from [Fabric Loader](https://github.com/FabricMC/fabric-loader): its Mixin service (`MixinServiceKnot`), Mixin compatibility table (`FabricMixinVersions`), environment stripping (`EnvironmentStrippingData`, `ClassStripper`, `FabricTransformer`) | 0.19.5 | Apache License 2.0 | `poly-bridge-fabric` jar (`dev.polyloader.compat.fabric.mixin.PlexusMixinService`, `MixinCompatibility`, `dev.polyloader.compat.fabric.EnvironmentStripper`); each file names its source and changes, and the jar carries `LICENSE_fabric-loader` |
| Code adapted from [Quilt Loader](https://github.com/QuiltMC/quilt-loader): version specifiers and `versions` values of `quilt.mod.json` (`V1ModMetadataReader.readVersionSpecifier`, `readVersionsValue`, `readVersionsObject`), environment stripping of Quilt mods' classes (`AbstractStripData`, `ClassStrippingData`, `PackageStrippingData`, `LambdaStripCalculator`, `QuiltTransformer`) | 0.31.0-beta.4 | Apache License 2.0 | `poly-bridge-fabric` jar (`dev.plexusmodloader.compat.quilt.QuiltVersionSpecifier`, `dev.plexusmodloader.compat.fabric.EnvironmentStripper`); each file names its source and changes, and the jar carries `LICENSE_quilt-loader` |
| Code adapted from [class-tweaker](https://github.com/FabricMC/class-tweaker), the class tweaker library Fabric Loader ships: enum extension (`EnumExtensionClassVisitor`) | 0.3.0 | Apache License 2.0 | `poly-bridge-fabric` jar (`dev.polyloader.compat.fabric.EnumExtender`) |
| Code adapted from [Forgified Fabric API](https://github.com/Sinytra/ForgifiedFabricAPI) (branch `26.2.x`, commit `40f8ce0`) and [Fabric API](https://github.com/FabricMC/fabric): `fabric-block-api-v1` with NeoForge-specific changes (mixins on NeoForge's `IBlockExtension` and `IBlockStateExtension` instead of `Block`/`BlockState`, `EnchantmentPowerBridge` between Fabric's and NeoForge's enchanting power, `LiquidBlockMixin` on NeoForge's `FluidInteractionRegistry.canInteract`, `EnchantmentMenuMixin` on NeoForge's `EventHooks.onEnchantmentLevelSet`) | 3.1.0 | Apache License 2.0 | `poly-bridge-fabric` jar (`META-INF/polymodloader/host-modules/neoforge-26.2.0.93/fabric-block-api-v1-3.1.0+poly.neoforge.26.2.jar`); each file names its source and commit, and the jar carries `LICENSE_fabric-api` |
| Code adapted from [Forgified Fabric API](https://github.com/Sinytra/ForgifiedFabricAPI) (branch `26.2.x`, commit `40f8ce0`) and [Fabric API](https://github.com/FabricMC/fabric): `fabric-item-api-v1` with NeoForge-specific changes (mixins on NeoForge's `IItemExtension` and `IItemStackExtension` (`IItemExtensionMixin`, client `IItemExtensionClientMixin`), tooltips through NeoForge's `ItemTooltipHandler` with `ItemTooltipHandlerMixin`, anvil and enchant checks on NeoForge's hooks (`AnvilMenuMixin`, `EnchantCommandMixin`, `EnchantRandomlyFunctionMixin`), client entrypoint `ClientItemEventHooks`, `DataComponentMapBuilderMixin` declaring `getOrDefault`, `IItemExtensionMixin.isPrimaryItemFor` evaluating `EnchantmentEvents.ALLOW_ENCHANTING` first) | 14.5.0 | Apache License 2.0 | `poly-bridge-fabric` jar (`META-INF/polymodloader/host-modules/neoforge-26.2.0.93/fabric-item-api-v1-14.5.0+poly.neoforge.26.2.jar`); each file names its source and commit, and the jar carries `LICENSE_fabric-api` |

## Used but not distributed by PolyLoader

- **Minecraft** (Mojang / Microsoft). Not included. The build tools and the installer use the copy that Mojang's
  servers or the Minecraft Launcher provide. Use is subject to the Minecraft EULA.
- **SLF4J** (MIT), **Gson** (Apache-2.0) and other libraries that Minecraft ships. PolyLoader uses Minecraft's
  copies at run time.
- Build and test tools (Gradle, JUnit, Spotless, palantir-java-format). They are used to build PolyLoader and
  are not part of its distribution.

## Documentation website

The built website (`site/`) contains code and fonts from these projects:

| Component | License |
|---|---|
| [Docusaurus](https://docusaurus.io/) and its dependencies, React, prism-react-renderer, clsx | MIT |
| [@easyops-cn/docusaurus-search-local](https://github.com/easyops-cn/docusaurus-search-local) | MIT |
| IBM Plex Sans KR, IBM Plex Mono (via @fontsource) | SIL Open Font License 1.1 |

## License texts

- Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- BSD 3-Clause: https://opensource.org/license/bsd-3-clause
- MIT: https://opensource.org/license/mit
- SIL Open Font License 1.1: https://openfontlicense.org

Each library's jar also has its own license file where the project provides one.
