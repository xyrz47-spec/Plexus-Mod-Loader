# Third-party notices

PolyLoader is licensed under the PolyLoader Non-Commercial License (see `LICENSE`). The components below are
not PolyLoader's own work. They keep their own licenses, which the PolyLoader license does not change.

## Distributed with PolyLoader

These are inside PolyLoader's jars, the installer, or the libraries the installer copies.

| Component | Version | License | Where |
|---|---|---|---|
| [Fabric Loader](https://github.com/FabricMC/fabric-loader) public API classes (`net.fabricmc.api`, `net.fabricmc.loader.api`, and the classes they need) | 0.19.5 | Apache License 2.0 | `poly-bridge-fabric` jar, with its `LICENSE_fabric-loader` |
| [ASM](https://asm.ow2.io/) (`asm`, `asm-tree`, `asm-analysis`, `asm-util`) | 9.11.1 | BSD 3-Clause | runtime library |
| [tomlj](https://github.com/tomlj/tomlj) | 1.1.1 | Apache License 2.0 | runtime library |
| [ANTLR 4 runtime](https://www.antlr.org/) | 4.11.1 | BSD 3-Clause | runtime library (needed by tomlj) |
| [Checker Framework qualifiers](https://checkerframework.org/) | 3.21.2 | MIT | runtime library (needed by tomlj) |
| [Gson](https://github.com/google/gson) | 2.14.0 | Apache License 2.0 | inside the installer jar. At run time PolyLoader uses the copy Minecraft ships |

## Used but not distributed by PolyLoader

- **Minecraft** (Mojang / Microsoft). Not included. The build tools and the installer use the copy that Mojang's
  servers or the Minecraft Launcher provide. Use is subject to the Minecraft EULA.
- **SLF4J** (MIT), **Gson** (Apache-2.0) and other libraries that Minecraft ships. PolyLoader uses Minecraft's
  copies at run time.
- Build and test tools (Gradle, JUnit, Spotless, palantir-java-format, Rust). They are used to build PolyLoader and
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
