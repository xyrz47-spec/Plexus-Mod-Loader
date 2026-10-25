# ADR-0027: Quilt 모드를 Poly 모드 로더에서 실행 — `quilt.mod.json`과 Quilt Loader API

- 상태: **채택** (2026-10-24). 채택 조건이던 Q2(`quilt.mod.json` 단위 테스트), Q3(`compatibilityMatrix`의 Quilt 시나리오), Q4(Quilt Loader API)가 통과했고, 그 뒤 Quilt 완전 지원 점검(Q5~Q12, `COMPATIBILITY.md`)도 세 지원 버전에서 통과했습니다. 2026-10-24 제안.
- 관련: ADR-0009(로더 브리지), ADR-0014(로더를 넘는 의존과 링크 검사), ADR-0015(중첩 jar), ADR-0016(Fabric Loader API), ADR-0018(class tweaker), ADR-0020(Fabric 모드를 Fabric Loader처럼), ADR-0025(다른 로더의 모드를 모드 목록에)
- 계획과 조사: [`docs/plans/QUILT_FULL_SUPPORT.md`](../plans/QUILT_FULL_SUPPORT.md) "Q0 조사 결과", "바꿔야 할 곳"

## 배경

Q0 조사(계획서)의 결론은 다음과 같습니다.

- 26.x에서 Quilt 전용 판은 대부분 `quilt.mod.json`만 든 jar입니다. 26.2에서 클래스가 있는 것이 32개, 리소스만 있는 것이 68개입니다(그중 66개는 QSL `quilt_resource_loader`가 필요). QSL·QFAPI는 26.x 판이 없습니다.
- 쓰는 항목은 `group`·`id`·`version`·`depends`·`metadata`·`intermediate_mappings`, `entrypoints`(`main`·`client`·`init` 등), `jars`, `breaks`·`provides`·`access_widener`, 최상위 `mixin`·`minecraft`입니다. loader plugin·`load_type`·`language_adapters`를 쓰는 모드는 없습니다.
- 모드가 참조하는 Quilt API는 `QuiltLoader`, `ModContainer`, `ModMetadata`, `Version`, `entrypoint.EntrypointContainer`, `entrypoint.PreLaunchEntrypoint`, `minecraft.MinecraftQuiltLoader`뿐입니다.

이 ADR을 위해 Quilt Loader `0.31.0-beta.4`(Apache-2.0)의 바이너리 jar와 소스 jar를 Quilt Maven에서 받아 확인했습니다(2026-10-24). 근거는 소스와 `javap -c -p`, `jdeps -verbose:class`입니다. RFC 0002(`specification/0002-quilt.mod.json.md`)도 함께 읽었습니다.

### 확인한 Quilt Loader 동작

1. **두 메타데이터를 다 든 jar.** `QuiltPluginManagerImpl`은 내장 플러그인을 `LinkedHashMap`에 Quilt(`StandardQuiltPlugin`) → Fabric(`StandardFabricPlugin`) 순서로 넣습니다. `scanZip`은 플러그인마다 jar를 읽다가 **Quilt 플러그인이 모드를 찾으면 멈춥니다**(`isQuilt = true; break`). 그래서 Quilt Loader에서는 `quilt.mod.json`이 이기고 `fabric.mod.json`은 읽지 않습니다. `quilt.mod.json`이 잘못되면 오류를 보고하고(시작이 멈춤) Fabric 쪽으로 넘어가지 않습니다. 개발 환경 밖에서 `quilt.mod.json5`가 있으면 오류입니다.
2. **정적 API → 구현.** `QuiltLoader`의 모든 정적 메서드는 private `impl()`로 `QuiltLoaderImpl.INSTANCE`(`public static final`, `null`이면 `RuntimeException("Accessed QuiltLoader too early!")`)를 읽고 그 인스턴스 메서드를 부릅니다. `getNormalizedGameVersion`·`getRawGameVersion`은 `impl().getGameProvider()`(인터페이스 `org.quiltmc.loader.impl.game.GameProvider`, `invokeinterface`)를, `getGlobalCacheDir`·`getGlobalConfigDir`·`globalDirsEnabled`는 `org.quiltmc.loader.impl.util.GlobalPaths`를 부릅니다. `MinecraftQuiltLoader.getEnvironmentType()`은 `QuiltLoaderImpl.INSTANCE.getEnvironmentType()`(반환형 `net.fabricmc.api.EnvType`)입니다. `LanguageAdapter.getDefault()`는 `org.quiltmc.loader.impl.util.DefaultLanguageAdapter.INSTANCE`입니다. API 클래스는 Java 8(major 52)로 컴파일돼 있습니다.
3. **공개 API가 가리키는 내부 클래스의 닫힘**(`jdeps`). `org.quiltmc.loader.api`(최상위)·`api.entrypoint`·`api.minecraft`가 직접 참조하는 `impl` 클래스는 15개입니다. 그 가운데
   - **바꿔야 하는 것(로더 본체로 이어짐) 3개:** `QuiltLoaderImpl`, `game.GameProvider`, `util.DefaultLanguageAdapter`. `DefaultLanguageAdapter`는 `launch.common.QuiltLauncherBase`를 통해 매핑·패치 코드(23개 이상)로, `QuiltLoaderImpl`은 로더 전체(560개 이상)로 이어집니다.
   - **그대로 담을 수 있는 순수 도우미:** 버전(`metadata.qmj.SemanticVersionImpl`·`GenericVersionImpl`·`VersionConstraintImpl`, `metadata.VersionRangeImpl`·`VersionIntervalImpl`, `lib.flexver.FlexVerComparator`), 의존 객체(`metadata.qmj.ModDependencyImpl`·`ModDependencyIdentifierImpl`·`QmjMetadataInternalFactory`), `ModContributorImpl`, `ModLicenseImpl`(리소스 `quilt_loader/licenses.json`, `JsonLoaderValue`, `lib.parsers.json.*`), `util.log.*`, `util.LoaderUtil`·`ExceptionUtil`·`QuiltLoaderInternal(Type)`, `util.GlobalPaths`, `entrypoint.EntrypointUtils`. 이 묶음(90개 클래스)이 참조하는 Quilt 클래스는 자기 자신과 위 3개뿐이고(스크립트로 확인), 3개에서 쓰는 멤버는 `QuiltLoaderImpl.INSTANCE`, `hasEntrypoints(String)`, `getEntrypointContainers(String, Class)`, 정적 `ensureDirExists(Path, String)`, `DefaultLanguageAdapter.INSTANCE`입니다. Fabric 쪽으로는 `net.fabricmc.api.EnvType`, `net.fabricmc.loader.api.ObjectShare`, `net.fabricmc.loader.api.metadata.ModEnvironment`만 참조합니다(브리지 jar에 이미 있음).
   - **담지 않을 것(로더의 큰 부분을 끌어옴):** `QuiltFileSystems`(→ `impl.filesystem` 52개), `ExtendedFiles`·`ExtendedFileSystem`·`MountOption`·`api.filesystem.*`(Quilt 가상 파일 시스템), `api.config.QuiltConfig`(→ quilt-config 라이브러리와 `impl.config`), `api.gui.*`(→ `impl.gui`), `api.plugin.*`(loader plugin), `metadata.qmj.V1ModMetadataReader`(→ 235개, 플러그인·GUI 타입).
4. **버전 지정.** `V1ModMetadataReader.readVersionSpecifier`: `*`는 전부, `=`·`>=`·`>`·`<=`·`<`, `~X`는 `[X, 다음 부 버전)`, `^X`는 `[X, 다음 주 버전)`, `A.B.x`는 `[A.B, A.(B+1))`이고 **연산자 없는 버전은 시맨틱이면 `^`와 같고, 아니면 `=`와 같습니다**(소스 주석 "Spec says that 1.0.0 is the same as ^1.0.0", RFC의 표와 같음). `versions`는 문자열, 배열(**폐기 예정, ANY**, 경고), `{"any": [...]}`/`{"all": [...]}` 객체(중첩 가능)입니다. 객체에 `any`·`all`이 없거나 배열이 아니거나 원소가 문자열·객체가 아니면 `VersionFormatException`입니다. 소스는 합친 범위가 비었거나·아무것도 안 맞거나·모두 맞으면 거부하려 하지만 `VersionRange.NONE`/`ANY`와 **동일성(`==`)으로** 비교하고, `VersionRange.ofRanges`·`combineMatchingBoth`는 늘 새 객체를 만듭니다. 그래서 실제로 거부되는 것은 빈 `all`(`null`)과 원소 하나인 `{"all": ["*"]}`뿐이고, 빈 `any`는 아무 버전도 맞지 않는 범위로, `{"any": ["*"]}`는 모든 버전으로 읽힙니다(Q2에서 확인, `QuiltVersionSpecifierTest`). Fabric의 연산자 없는 `1.0.0`은 정확히 그 버전이므로 **뜻이 다릅니다.**
5. **의존 객체.** 문자열(`"id"` 또는 `"group:id"`), 객체(`id` 필수, `versions` 기본 `*`, `reason`, `optional` 기본 `false`, `unless`), 배열. 배열은 **`depends`에서는 ANY, `breaks`에서는 ALL**입니다(`readDependencyObject(isAny, …)`, RFC "Dependency Objects"). 해석기(`QuiltRuleDepOnly`)에서
   - `optional`: 그 ID의 모드가 하나도 없으면 규칙이 없고, 맞지 않는 버전만 있으면 둘이 함께 로드될 수 없습니다. RFC의 "the mod specified by `id` is present일 때만 확인"과 같습니다. 맞는 버전이 있으면 그 모드가 로드되어야 합니다.
   - `unless`: 그 의존이 만족되면 이 의존을 따지지 않습니다.
   - 후보가 모두 다른 실행 환경(`minecraft.environment`)용이면 의존 규칙을 만들지 않습니다(`allWrongEnvironment`).
   - `"group:id"`는 그룹도 같아야 합니다. Fabric 모드의 그룹은 `loader.fabric`, 내장 모드는 `builtin`입니다.
   - `breaks`의 `unless`는 해석기 절(`!conflict ∨ !source ∨ !unless`)이 RFC와 다르게 읽힐 여지가 있습니다. 26.x 모드 중 이것을 쓰는 것은 Q0에서 찾지 못했습니다.
6. **`provides`.** 문자열(`"id"`, `"group:id"`) 또는 객체(`id`, 선택 `version`; 없으면 모드 버전).
7. **진입점.** Quilt Loader 자신이 부르는 키는 `pre_launch`(Quilt `PreLaunchEntrypoint.onPreLaunch(ModContainer)`), 그다음 `preLaunch`(Fabric `PreLaunchEntrypoint`), 그리고 `game/minecraft/Hooks`의 `main`(Fabric `ModInitializer`)·`client`(`ClientModInitializer`)·`server`(`DedicatedServerModInitializer`)뿐입니다. 진입점 저장소(`EntrypointStorage`)는 Quilt·Fabric 모드가 함께 쓰므로, `quilt.mod.json`의 `main`도 Fabric `ModInitializer`로 불리고 `FabricLoader.getEntrypoints`와 `QuiltLoader.getEntrypoints`가 같은 인스턴스를 줍니다. `init`·`client_init`·`server_init`은 QSL(`org.quiltmc.qsl.base`)이 부르는 키라서 QSL이 없으면 아무도 부르지 않습니다.
8. **그 밖의 항목.**
   - `intermediate_mappings`가 없으면 기본값 `org.quiltmc:hashed`이고, Quilt Loader는 이것을 지원하지 않아 메타데이터 오류입니다. 지원하는 값은 `net.fabricmc:intermediary`와 `org.quiltmc.experimental:mojang`인데, 뒤의 것은 매핑 해석기에 `mojang` 이름 공간이 있어야 합니다. 26.x(`EmptyMappingConfiguration`, 이름 공간 `official`)에는 없습니다.
   - `load_type`(`always`/`if_possible`/`if_required`, 기본 `if_required`)은 직접 설치한 모드에는 효과가 없고, 중첩 모드에만 해석기 가중치로 작용합니다. `if_required`는 다른 모드가 필요로 할 때만 로드합니다. Fabric 모드는 늘 `if_possible`입니다.
   - `required_features`는 `modifiable_filesystem_overlay`만 알고, 다른 값은 오류입니다.
   - loader plugin은 RFC의 `quilt_loader.plugins`가 아니라 최상위 `experimental_quilt_loader_plugin`(`class`, `packages`)을 읽습니다. `quilt_loader`의 모르는 키(`plugins`, `environment` 등)는 개발 환경에서만 오류이고 실제 실행에서는 무시합니다. `repositories`는 읽기만 합니다.
   - Mixin 설정은 `#<모드 ID>:<경로>`로 모드별 이름을 붙여 넣습니다. Quilt 모드의 Mixin 호환 수준은 늘 `FabricUtil.COMPATIBILITY_0_10_0`입니다(`QuiltMixinBootstrap`).
   - 환경 제거(`ClassStrippingData`·`PackageStrippingData`)는 Fabric `@Environment`에 더해 `@ClientOnly`·`@DedicatedServerOnly`(클래스·멤버·`package-info`, `stripLambdas`)와 `@Requires(모드 ID들)`(없으면 제거)를 따릅니다.
   - `ReflectiveFixer`는 리플렉션 이름을 매핑에 맞추는 일이라, 매핑이 없는 26.x에서는 아무것도 바꾸지 않습니다.
   - `QuiltLoader.getMappingResolver()`는 26.x에서 이름 공간 `official`의 항등 변환입니다.
   - Fabric 쪽에서 본 Quilt 모드(`GeneralExt2FabricMetadata`)는 `getType() = "quilt"`입니다. `depends`·`breaks`는 `ModDependency.Only`만 옮기고, `Owner` 역할의 기여자가 작성자, 나머지가 기여자입니다. 사용자 정의 값은 `quilt.mod.json` 최상위 전체입니다.
9. **Maven.** `org.quiltmc:quilt-loader:0.31.0-beta.4`가 `https://maven.quiltmc.org/repository/release/`에 있습니다. Gradle 모듈 메타데이터(`.module`)의 `apiElements`·`runtimeElements`는 의존이 없습니다. jar SHA-256은 `53154d2efdd04f53cdc5f925a580d64add8356ac9a3c0b36b060303e01300402`입니다. jar 안에 `LICENSE_quilt-loader`가 있고, Quilt Loader 자신의 `quilt.mod.json`은 `fabricloader` `0.19.5`를 `provides`하고 MixinExtras 0.5.5를 중첩합니다. 이 두 버전은 Poly 모드 로더의 Fabric 브리지와 같습니다.

### Poly 모드 로더 쪽 사실

- `ModLoaderBridge`는 `metadataPath()`가 하나입니다. `ModFileScanner`는 `ModFileKind` 순서대로 첫 메타데이터를 `kind`로, 나머지를 `alsoFor`로 둡니다. `ForeignModResolver`는 `kind.metadataPath()`가 같은 브리지를 고르고, `NestedJarExtractor`는 중첩 jar의 메타데이터 경로가 바깥 모드의 브리지와 같을 때만 모드로 봅니다.
- Fabric 쪽의 Mixin 시작(`FabricMixinBootstrap`, JVM당 한 번), class tweaker의 인터페이스 주입(`InterfaceInjector.classTweakers`), `FabricLoaderRuntime.create`는 `LoadedMod.loader().equals("Fabric")`인 모드만 보고 `fabric.mod.json`을 다시 읽습니다(`FabricLoaderRuntime.readFabricModJson`).
- 링크 검사(`ModLinkVerifier`)는 **그 모드의 브리지**의 `runtimePackages`·`providedPackages`로 `111`을 판정합니다. `111`의 뜻은 "로더 런타임의 제공하지 않는 클래스·멤버"로, 로더를 가리지 않습니다.
- `CrossLoaderModResolver`는 `BridgedDependency.Kind`의 `REQUIRED`·`BREAKS`만 확인하고, 내장 ID는 그 모드의 브리지의 `builtInMods()`에서 찾습니다. 중첩 사본 중 최고 버전은 그 모드의 브리지의 `compareVersions`로 고릅니다.
- `POLY-COMPAT-` 번호(전체 저장소와 다른 작업 트리를 2026-10-24에 검색): `001..003`, Fabric `100..139`, FML `200..217`이 쓰였고 `3xx`는 비어 있습니다.

## 결정 (제안)

### 1. 같은 모듈, 형제 브리지: `QuiltBridge`

- `quilt.mod.json`은 **새 `ModLoaderBridge` 구현 `dev.polymodloader.compat.quilt.QuiltBridge`** 가 읽습니다(`loaderName()` = `Quilt`, `metadataPath()` = `quilt.mod.json`). 이 클래스는 **`poly-bridge-fabric` 모듈 안**에 두고, `META-INF/services/dev.polymodloader.compat.bridge.ModLoaderBridge`에 `FabricBridge` 다음 줄로 등록합니다. 새 모듈은 만들지 않습니다.
- **Fabric 쪽 실행 장치는 하나만 둡니다.** Mixin 서비스와 MixinExtras(ADR-0020), 환경 제거·인터페이스 주입·class tweaker 접근 확대, 진입점 저장소(`FabricLoaderRuntime`), `FabricLauncherBase` 대체본, 공유 객체를 Fabric 모드와 Quilt 모드가 함께 씁니다. 이 장치는 Fabric 브리지가 계속 소유합니다. `FabricBridge.prepareLaunch`·`classTransformer`·`beforeGame`이 **Fabric 계열(Fabric과 Quilt) 모드 전부**를 다루고, `QuiltBridge`의 이 세 메서드는 기본값(아무것도 하지 않음)입니다.
- 근거:
  - Mixin은 JVM에 하나뿐이고 서비스도 하나입니다(ADR-0020, ADR-0026 배경). 브리지마다 Mixin을 시작할 수 없습니다.
  - Quilt Loader에서도 Fabric과 Quilt 모드는 진입점 저장소와 Mixin을 함께 씁니다(배경 7).
  - 형제 브리지면 찾기·판정 흐름(`ModFileScanner`, `ForeignModResolver`, `metadataPath`로 브리지 고르기)을 SPI 변경 없이 그대로 씁니다.
  - 로더마다 달라야 하는 값이 모두 브리지 단위 SPI입니다. `loaderName`(보고서, `ModContext.mods()`의 로더 이름 `quilt`, 로더별 모드 수), `builtInMods`(`quilt_loader`), `compareVersions`(Quilt 버전 순서), `runtimePackages`·`providedPackages`(링크 검사)가 그렇습니다. 브리지 하나로는 모드마다 다르게 답할 수 없습니다.
  - Quilt의 뜻(버전 지정, 의존 객체, 진입점 키)을 680줄짜리 `FabricBridge`에 섞지 않고 `dev.polymodloader.compat.quilt` 패키지에 둡니다.
  - 별도 모듈(`poly-bridge-quilt`)은 Fabric 브리지 내부(`FabricLoaderRuntime`, `FabricMixinBootstrap`, `ClassTweakerFile`, `MixinConfigEntry`)를 모듈 밖으로 열어야 하고, Mixin 시작을 두 브리지 사이에서 순서로 맞춰야 합니다. 책임 경계가 실제로는 하나이므로 모듈을 나누지 않습니다. 모듈 README의 "하는 일"에 Quilt를 더합니다.
- **`quilt.mod.json`을 Fabric의 내부 모델(`FabricModMetadata`)로 옮기지 않습니다.**
  - Quilt 쪽 모델은 새 `QuiltModDescription`(읽은 그대로의 Quilt 뜻: `group`, 의존 트리, `provides` 버전, `load_type`, 진입점, Mixin, access widener, 환경, 최상위 JSON)입니다.
  - Fabric 실행 장치가 필요로 하는 것(Mixin 설정과 쪽, class tweaker 경로들, 진입점 선언, Mixin 호환 수준)은 두 로더 공통의 작은 보기 `FabricFamilyMod`로 꺼냅니다. `LoadedMod.loader()`가 `Fabric`이면 `fabric.mod.json`에서, `Quilt`이면 `quilt.mod.json`에서 읽습니다.
  - Fabric API에서 본 Quilt 모드의 메타데이터는 Quilt Loader의 `GeneralExt2FabricMetadata`와 같은 규칙으로 `QuiltModDescription`에서 만듭니다(`getType() = "quilt"`).

### 2. 두 메타데이터를 다 든 jar는 Fabric 모드로 실행

- `ModFileKind`에 `QUILT("quilt.mod.json")`를 **`FABRIC` 바로 다음**에 더합니다. 그래서 `fabric.mod.json`과 `quilt.mod.json`을 다 든 jar는 지금처럼 `kind = FABRIC`, `alsoFor`에 `QUILT`이 들어가고 Fabric 브리지가 판정합니다. 런타임은 이런 jar에 알림 `POLY-COMPAT-310`(아래)을 붙입니다.
- **실제 Quilt Loader와 다릅니다**(Quilt Loader는 `quilt.mod.json`을 씀, 배경 1). 일부러 이렇게 합니다.
  - Poly 모드 로더는 `fabric.mod.json`이 있는 jar에 대해 Fabric Loader로서 동작합니다. 이 jar는 지금도 Fabric 모드로 실행되고 실제 모드 묶음(Fabric)에서 검증되고 있습니다. 판정을 바꾸면 이미 동작하는 설치가 달라집니다.
  - 그런 jar는 26.2에서 6개뿐입니다(Q0). Fabric 쪽 메타데이터는 그 jar가 Fabric Loader에서 실행되도록 쓴 것입니다. Fabric 판정은 MixinExtras·Fabric API처럼 이미 검증된 경로를 탑니다.
  - 기존 규칙(`ModFileKind` 순서 → `kind`, 나머지는 `alsoFor`)에 그대로 들어가므로 새 분기가 없습니다.
- Q10에서 이 6개 jar를 실제 Quilt Loader와 비교합니다. Fabric 쪽 판정이 Quilt Loader보다 나쁜 jar가 나오면 이 결정을 고칩니다.
- `quilt.mod.json5`만 든 jar는 Quilt Loader가 개발 환경 밖에서 거부하므로 읽지 않습니다. 지금처럼 `POLY-DISCOVERY-002`이고, 안내 문구의 메타데이터 목록에 `quilt.mod.json`을 더합니다. `quilt.mod.json`과 `quilt.mod.json5`가 함께 있으면 Quilt Loader처럼 막습니다(`300`).

### 3. Quilt 버전 뜻은 Quilt Loader 자신의 클래스로

- 버전 값과 범위는 **Quilt Loader jar에서 담은 클래스**를 그대로 씁니다. `org.quiltmc.loader.api.Version.of`(→ `SemanticVersionImpl`·`GenericVersionImpl`, FlexVer 비교)와 `VersionRange`·`VersionInterval`(→ `VersionRangeImpl`·`VersionIntervalImpl`)입니다. Fabric이 Fabric Loader의 `VersionPredicate`를 쓰는 것(ADR-0014)과 같은 방식입니다.
- 문자열을 범위로 바꾸는 `V1ModMetadataReader.readVersionSpecifier`·`readVersionsValue`·`readVersionsObject`는 그 클래스가 로더 본체를 끌어오므로(235개) 담지 않습니다. 같은 로직을 **`dev.polymodloader.compat.quilt.QuiltVersionSpecifier`** 로 옮깁니다(Apache-2.0 출처를 파일 머리에 적음). 결과는 담은 `VersionRange`이고, 판정은 `VersionRange.isSatisfiedBy`(구간 포함)로 합니다.
- Fabric의 `FabricBridge.dependencies`·`accepts`와 코드를 나누지 않습니다. 같은 문자열이 두 로더에서 다른 범위이기 때문입니다(배경 4).
- `QuiltBridge.compareVersions`는 `Version.of(a).compareTo(Version.of(b))`입니다.

### 4. Quilt Loader API는 ADR-0016처럼 제공

- **담는 것**: `org.quiltmc:quilt-loader` jar에서 아래 패턴의 클래스만 브리지 jar에 담습니다(`poly-bridge-fabric/build.gradle.kts`의 `quiltLoaderApiClasses`, Fabric의 `fabricLoaderApiClasses`와 같은 `Sync` 방식). 모두 90개이고 닫혀 있음을 확인했습니다(배경 3).
  - `org/quiltmc/loader/api/`의 최상위 클래스 가운데 `CachedFileSystem`, `FasterFileSystem`, `FasterFiles`, `LanguageAdapter`, `LanguageAdapterException`, `LoaderValue*`, `MappingResolver`, `ModContainer*`, `ModContributor`, `ModDependency*`, `ModDependencyIdentifier`, `ModInternal`, `ModLicense`, `ModMetadata*`, `ModMetadataToBeMovedToPlugins`, `QuiltLoader`, `Requires`, `Version*`, `VersionConstraint*`, `VersionFormatException`, `VersionInterval`, `VersionRange`
  - `org/quiltmc/loader/api/entrypoint/**`, `org/quiltmc/loader/api/minecraft/**`
  - `org/quiltmc/loader/impl/metadata/VersionRangeImpl*`, `VersionIntervalImpl*`
  - `org/quiltmc/loader/impl/metadata/qmj/` 가운데 `SemanticVersionImpl*`, `GenericVersionImpl*`, `VersionConstraintImpl*`, `ModDependencyIdentifierImpl*`, `ModContributorImpl*`, `ModDependencyImpl*`, `QmjMetadataInternalFactory*`, `ModLicenseImpl*`, `JsonLoaderValue*`
  - `org/quiltmc/loader/impl/lib/flexver/**`, `org/quiltmc/loader/impl/lib/parsers/json/**`, `org/quiltmc/loader/impl/util/log/**`
  - `org/quiltmc/loader/impl/util/` 가운데 `QuiltLoaderInternal*`, `QuiltLoaderInternalType*`, `LoaderUtil*`, `ExceptionUtil*`, `GlobalPaths*`, 그리고 `org/quiltmc/loader/impl/entrypoint/EntrypointUtils*`
  - 리소스 `quilt_loader/licenses.json`, `LICENSE_quilt-loader`
  - Quilt jar 안의 `net/fabricmc/**`는 **담지 않습니다.** Fabric 클래스는 지금처럼 Fabric Loader 0.19.5 jar에서 옵니다.
- **Poly 모드 로더가 대신 두는 것**(같은 이름, Quilt가 부르는 모양 그대로, 동작은 Poly 모드 로더에 맡김). `FabricLoaderImpl`·`GameProvider` 대체본과 같은 방식입니다.
  - `org.quiltmc.loader.impl.QuiltLoaderImpl`:
    - `public static final QuiltLoaderImpl INSTANCE`
    - 인스턴스 메서드: `getEntrypoints`, `getEntrypointContainers`, `hasEntrypoints`, `getMappingResolver`, `getModContainer(String)`, `getModContainer(Class)`, `getAllMods`, `isModLoaded`, `isDevelopmentEnvironment`, `getGameInstance`, `getGameProvider`, `getGameDir`, `getCacheDir`, `getConfigDir`, `getLaunchArguments(boolean)`, `getObjectShare`, `createModTable`, `getEnvironmentType`
    - 정적 메서드: `ensureDirExists(Path, String)`
    - Poly 전용: `attachPolyModLoaderRuntime(QuiltLoaderRuntime)`, `polyModLoaderRuntime()`
    - 붙기 전에 부르면 Quilt와 같은 문구 `Accessed QuiltLoader too early!`로 `IllegalStateException`을 던집니다.
  - `org.quiltmc.loader.impl.game.GameProvider`: **인터페이스**이고 `getNormalizedGameVersion()`·`getRawGameVersion()`만 둡니다.
  - `org.quiltmc.loader.impl.util.DefaultLanguageAdapter`: `INSTANCE`, `create(ModContainer, String, Class)`. 뜻은 RFC의 JVM 표기(클래스, `::필드`, `::메서드`)로 Fabric의 `DefaultLanguageAdapter` 대체본과 같고, 같은 생성 코드를 씁니다.
- **동작**은 새 `dev.polymodloader.compat.quilt.QuiltLoaderRuntime`이 맡습니다. Fabric의 `FabricLoaderRuntime`을 감싸므로 진입점 인스턴스·공유 객체(`ObjectShare`)·게임 객체·폴더·실행 인수가 Fabric 쪽과 **같은 것**입니다(배경 7).
  - **모드 목록:** 모든 로더의 로드된 모드입니다(`BridgeLaunchContext.mods`). 각 모드는 `QuiltModContainer`로 보이고, `getSourceType()`과 `group()`은 이렇습니다.
    - Quilt 모드: `NORMAL_QUILT`, 자기 `group`
    - Fabric 모드: `NORMAL_FABRIC`, `loader.fabric`(Quilt Loader와 같음)
    - 내장 `minecraft`·`java`·`quilt_loader`·`mixinextras`: `BUILTIN`, `builtin`. `quilt_loader`는 `fabricloader`를 제공합니다.
    - Poly 모드와 호스트(Forge)가 실행하는 모드: `OTHER`, `loader.poly`·`loader.minecraftforge`
  - **`rootPath()`:** jar 루트를 zip 파일 시스템으로 엽니다(`FabricModContainer`와 같은 코드). 호스트 모드도 jar 루트를 줍니다. 26.x에는 모든 컨테이너로 리소스 팩을 만드는 Quilt 리소스 로더(QSL)가 없기 때문입니다. ADR-0025의 Fabric 쪽 규칙(루트 없음)과 다릅니다.
  - **`getSourcePaths()`:** 직접 설치는 `[[jar]]`, 중첩은 `[[바깥 jar, 안의 경로]]`입니다. `getClassLoader()`는 게임 클래스로더입니다.
  - **`ModMetadata`(`QuiltModMetadata`, `ModMetadataToBeMovedToPlugins` 구현):**
    - Quilt 모드는 `quilt.mod.json` 그대로입니다. `licenses`는 `ModLicenseImpl.fromIdentifierOrDefault`, `depends`·`breaks`는 담은 `ModDependency.Only/Any/All.of`, `values()`는 최상위 JSON의 `LoaderValue`(새 `QuiltLoaderValue`, gson 기반)입니다.
    - 다른 모드는 Quilt Loader의 `FabricModMetadataWrapper`와 같은 규칙으로 만듭니다.
  - **진입점:**
    - `getEntrypoints`·`getEntrypointContainers`는 모든 Fabric 계열 모드의 선언을 모드 순서대로 돌려줍니다. 인스턴스는 `FabricLoaderRuntime`의 것을 같이 씁니다.
    - `getProvider()`는 그 모드의 `QuiltModContainer`이고, `getDefinition()`은 선언 값입니다.
  - **그 밖:**
    - `getModContainer(Class)`는 클래스의 `CodeSource` 위치가 모드 jar인 모드를 찾습니다(게임 클래스로더가 jar마다 `CodeSource`를 둠).
    - `isDevelopmentEnvironment`는 `loader.development` 시스템 속성입니다.
    - `getCacheDir`는 `<게임 폴더>/<loader.cacheDir, 기본 .cache>`, `getConfigDir`는 `<게임 폴더>/<loader.configDir, 기본 config>`입니다.
    - 매핑 해석기는 `official` 항등 변환입니다(새 `QuiltOfficialMappingResolver`, Fabric `OfficialMappingResolver`와 같은 뜻).
    - `getEnvironmentType`는 클라이언트면 `CLIENT`, 아니면 `SERVER`입니다(Fabric 쪽과 같음).
    - `createModTable`은 ID·버전·로더 표입니다.
    - Quilt의 `Log`는 slf4j로 보냅니다(새 `QuiltLogForwarder implements org.quiltmc.loader.impl.util.log.LogHandler`, `Log.init(…, true)`).
- **연결:**
  - `FabricBridge.prepareLaunch`가 `FabricLoaderRuntime`을 만든 직후 `QuiltLoaderRuntime`을 만들어 `QuiltLoaderImpl.INSTANCE`에 붙입니다. Fabric 계열 모드가 없어도 붙여서 Quilt API는 언제나 답합니다.
  - `FabricBridge.beforeGame`은 Quilt 모드의 `pre_launch`(Quilt `PreLaunchEntrypoint.onPreLaunch(QuiltModContainer)`)를 먼저 부르고, 그다음 지금처럼 `preLaunch`를 부릅니다. Quilt Loader의 `invokePreLaunch` 순서입니다. `pre_launch`는 **Quilt 모드의 선언만** 부릅니다. Fabric 모드는 Fabric Loader처럼 실행하기 때문입니다.
- **링크 검사.**
  - `QuiltBridge.runtimePackages()` = `net/fabricmc/`, `org/quiltmc/loader/`
  - `QuiltBridge.providedPackages()` = Fabric 브리지의 목록 + `org/quiltmc/loader/api/`, `org/quiltmc/loader/impl/QuiltLoaderImpl`, `org/quiltmc/loader/impl/game/GameProvider`, `org/quiltmc/loader/impl/util/DefaultLanguageAdapter`
  - 담지 않은 API 클래스(`QuiltFileSystems`, `api.config`·`api.gui`·`api.plugin`·`api.filesystem` 등)와 Quilt Loader 내부 클래스를 쓰는 Quilt 모드는 **기존 `POLY-COMPAT-111`**(로더 런타임의 제공하지 않는 클래스·멤버, 뜻 그대로)로 막힙니다. 대체본에 없는 멤버도 막힙니다.
  - `unsupportedMembers()`는 처음에는 비어 있습니다(제공하는 API 메서드는 모두 구현).
- **클래스 하나:** `QuiltBridge.sharedLibraryPackages()` = `org.quiltmc.loader.`입니다. 모드 jar에 사본이 있어도 게임 클래스로더는 브리지 jar의 것만 씁니다(정적 상태 `INSTANCE`가 하나여야 함).
- **의존:** `gradle/libs.versions.toml`에 `quiltLoader = "0.31.0-beta.4"`, `quilt-loader = { module = "org.quiltmc:quilt-loader", version.ref = "quiltLoader" }`을 더합니다. `settings.gradle.kts`에 `exclusiveContent { forRepository { maven("https://maven.quiltmc.org/repository/release/") { name = "QuiltMC" } }; filter { includeGroup("org.quiltmc") } }`를 더합니다. `poly-bridge-fabric`은 `quiltLoaderJar` 구성으로 받은 jar에서 담은 클래스(`quiltLoaderApi`)만 `compileOnly(files(quiltLoaderApi))`로 씁니다. Quilt Loader jar 전체는 Fabric Loader 클래스의 자기 사본(`net/fabricmc/**`)을 들고 있어 컴파일 클래스 경로에서 Fabric Loader 0.19.5를 가릴 수 있고, 담지 않은 클래스를 실수로 쓰는 것도 막기 위해서입니다(Q2에서 정함). `THIRD_PARTY_NOTICES.md`에 Quilt Loader(Apache-2.0)를 더합니다.

### 5. QSL 진입점 `init`·`client_init`·`server_init`은 부르지 않음

- Quilt Loader 자신도 부르지 않습니다(배경 7). 26.x에는 QSL이 없으므로 Quilt Loader에서도 이 코드는 실행되지 않습니다. Poly 모드 로더도 부르지 않고, 모드를 막지도 않습니다. Mixin·리소스·다른 진입점은 그대로 적용됩니다.
- 이 키가 있으면 경고 `POLY-COMPAT-303`을 붙입니다. 그 모드에 Poly 모드 로더가 부르는 다른 진입점(`main`·`client`·`server`·`pre_launch`·`preLaunch`)이 없으면, 문구에 "이 모드의 초기화 코드는 실행되지 않음(Quilt Loader에서도 QSL 없이는 같음)"을 적습니다.
- QSL 진입점 인터페이스(`org.quiltmc.qsl.*`)를 클래스가 구현하면 링크 검사에서 "설치되지 않은 모드의 클래스"(`113`, 경고)입니다. 그 진입점은 부르지 않으므로 막지 않습니다.

### 6. 제공하지 않는 항목과 잘못된 메타데이터: 새 번호 블록 `POLY-COMPAT-3xx`

Fabric은 `1xx`, FML(MinecraftForge·NeoForge)은 `2xx`(지금 `217`까지, NeoForge 작업이 더 쓸 수 있음)입니다. **Quilt는 `300`부터** 씁니다. 로더를 가리지 않는 공통 판정(런타임과 공통 Fabric 실행 장치가 내는 `111..124`, class tweaker `125..130`·`137`, Mixin `131..136`)은 뜻을 바꾸지 않고 Quilt 모드에도 그대로 씁니다. 폐기한 번호는 쓰지 않습니다.

| 코드 | 막음 | 뜻 |
|---|---|---|
| `POLY-COMPAT-300` | 예 | `quilt.mod.json`이 Quilt Loader가 거부하는 메타데이터입니다. 대상: JSON 객체가 아님, `schema_version`이 없거나 숫자가 아니거나 1이 아님, `quilt.mod.json5`가 함께 있음, `quilt_loader`가 없음. `id`(`^[a-z][a-z0-9-_]{1,63}$`)·`group`(`^[a-zA-Z0-9-_.]+$`)·`version`이 없거나 틀림. `entrypoints`·`jars`·`depends`·`breaks`·`provides`·`repositories`·`metadata`·`contributors`·`contact`·`mixin`·`access_widener`의 형식이 틀림. 진입점 객체에 `adapter`·`value`가 없음. 의존 객체에 `id`가 없음, `optional`이 불리언이 아님, 읽을 수 없는 버전 지정. Quilt Loader가 거부하는 `versions` 객체(빈 `all`, `{"all": ["*"]}`, `any`·`all` 없음, 형식이 틀림; 배경 4). `load_type`·`minecraft.environment`·Mixin `environment` 값이 틀림. `intermediate_mappings`가 없거나(기본 `org.quiltmc:hashed`) `hashed`·`org.quiltmc.experimental:mojang`(26.x에 `mojang` 이름 공간 없음)·모르는 값·좌표 형식이 아님. 모르는 `required_features` |
| `POLY-COMPAT-301` | 예 | loader plugin(`experimental_quilt_loader_plugin`)을 선언합니다. Quilt Loader는 이것을 로더 안에서 실행하지만 Poly 모드 로더는 Quilt Loader 플러그인 API를 제공하지 않습니다 |
| `POLY-COMPAT-302` | 예 | 언어 어댑터를 씁니다(`language_adapters` 선언, 또는 진입점의 `adapter`가 `default`가 아님). 26.x용 Quilt 언어 어댑터(Quilt Kotlin Libraries 등)는 없습니다 |
| `POLY-COMPAT-303` | 아니오(경고) | QSL 진입점(`init`·`client_init`·`server_init`)입니다. QSL 없이는 Quilt Loader도 부르지 않으므로 실행하지 않습니다(결정 5) |
| `POLY-COMPAT-304` | 아니오 | 그 밖의 진입점 키(`modmenu`, `fabric-datagen` 등)입니다. Poly 모드 로더는 부르지 않고, 다른 모드가 `QuiltLoader.getEntrypoints`·`FabricLoader.getEntrypoints`로 받습니다(Fabric `107`에 해당) |
| `POLY-COMPAT-305` | 예 | `minecraft.environment`가 이 프로세스와 맞지 않습니다(`client` 모드를 전용 서버에서, `dedicated_server` 모드를 클라이언트에서). Fabric `108`에 해당합니다 |
| `POLY-COMPAT-306` | 예 | `required_features`에 `modifiable_filesystem_overlay`가 있습니다. Poly 모드 로더는 Quilt의 변경 가능한 모드 파일 오버레이를 제공하지 않습니다 |
| `POLY-COMPAT-307` | 예(오류 아님) | `load_type`이 `if_required`(기본값)인 중첩 Quilt 모드를, 로드되는 어떤 모드도 필요로 하지 않습니다. Quilt Loader처럼 로드하지 않습니다. 바깥 모드의 `124`에는 세지 않습니다 |
| `POLY-COMPAT-308` | 아니오 | QSL·QFAPI 모듈에 의존합니다. 대상은 QSL 소스의 모듈 ID(`quilt_base`, `quilt_resource_loader` 등), `qsl`, `quilted_fabric_api`, QFAPI 모듈(`quilted_fabric_*`)이고, 목록과 출처는 `QslModules` 한 곳에 있습니다. `quilt_loader`나 모드 자신의 `quilt_*` ID는 해당하지 않습니다. QSL은 26.x 판이 없어 Quilt Loader에서도 이 의존을 채울 수 없습니다. 막는 것은 `114`이고, 이 코드는 그 이유를 알립니다 |
| `POLY-COMPAT-309` | 아니오 | Quilt Loader가 읽지만 이 실행에서 효과가 없는 항목입니다: `repositories`, `intermediate_mappings`(26.x는 다시 매핑하지 않음), `quilt_loader`의 모르는 키(`plugins`, `environment` 등. Quilt Loader도 실제 실행에서는 무시), 최상위 `environment` |
| `POLY-COMPAT-310` | 아니오 | jar에 `fabric.mod.json`과 `quilt.mod.json`이 다 있습니다. Fabric 모드로 실행합니다(결정 2). Quilt Loader라면 `quilt.mod.json`을 읽습니다 |

브리지는 지금처럼 판정만 하고 예외를 던지지 않습니다. 값마다 형식을 확인한 뒤 읽고, 그래도 예외가 나면 런타임이 `002`로 막습니다.

### 7. 의존 관계: Quilt 뜻을 런타임 판정에 그대로 옮김

`BridgedDependency`는 지금 평평한 `REQUIRED`/`OPTIONAL`/`BREAKS`/`CONFLICTS`뿐입니다. 그래서 Quilt의 `optional`, ANY/ALL 배열, `unless`를 표현할 수 없습니다. 내부 SPI(`@ApiStability(INTERNAL)`)를 이렇게 넓힙니다. 기존 4인자 생성자는 그대로 두고 새 값은 비어 있게 해서, Fabric·FML 브리지는 바뀌지 않습니다.

- `BridgedDependency.Kind.IF_PRESENT` **추가**: 그 ID의 모드가 설치돼 있을 때만 확인합니다. 설치돼 있고 버전이 맞지 않으면 `114`로 막습니다(Quilt `optional`). 없으면 통과합니다.
- 구성 요소 `List<BridgedDependency> members` **추가**: 비어 있지 않으면 묶음입니다(`modId`는 `""`, `accepts`는 쓰지 않음).
  - `REQUIRED`/`IF_PRESENT` 묶음은 **구성원 하나라도** 만족하면 만족입니다(Quilt `depends` 배열 = ANY).
  - `BREAKS` 묶음은 **구성원이 모두** 해당할 때만 깨뜨립니다(Quilt `breaks` 배열 = ALL).
  - 구성원도 묶음일 수 있습니다(중첩 배열).
- 구성 요소 `List<BridgedDependency> unless` **추가**: 이 가운데 하나라도 (`REQUIRED`로) 만족되면 이 관계를 따지지 않습니다. 의존이든 `breaks`든 RFC 뜻을 따릅니다. `breaks`의 해석기 절 차이(배경 5)는 실제 모드가 나오면 Q10에서 비교합니다.
- 구성 요소 `boolean skipWhenOtherSideOnly` **추가**: `true`이면, 그 ID의 설치된 후보가 모두 **실행 환경 때문에만** 막힌 경우(막는 발견의 `feature`가 모두 `environment`, 곧 `108`·`305`) 이 의존을 따지지 않습니다(Quilt `allWrongEnvironment`). Quilt 브리지는 모든 `Only`에 `true`를 둡니다.
- `requirement` 문구는 사람이 읽을 원문입니다. 예: `versions >=0.30.0`, `optional`, `any of [a, b]`, `unless c`.

`QuiltBridge`가 만드는 값은 이렇습니다.

| `quilt.mod.json` | `BridgedDependency` |
|---|---|
| `depends`의 `"id"` / `{"id": …}` | `REQUIRED`, `accepts` = `VersionRange`(결정 3), `optional: true`면 `IF_PRESENT` |
| `depends`의 배열 | `REQUIRED` 묶음, 구성원 = 각 원소 |
| `breaks`의 `"id"` / 객체 | `BREAKS`(`optional`은 Quilt에서도 뜻 없음) |
| `breaks`의 배열 | `BREAKS` 묶음(ALL) |
| `unless` | 그 의존 객체를 `depends`처럼 읽은 것(배열은 ANY) |
| `"group:id"` | `modId` = `id`. 그룹은 메시지와 Quilt API(`ModDependencyIdentifier.mavenGroup()`)에만 남기고, **런타임 판정은 ID로만** 합니다 |

- 그룹을 ID로만 판정하는 것은 Quilt Loader보다 **넓게 받는** 차이입니다. Poly 모드 로더의 모드 ID는 로더를 넘어 하나의 이름 공간이고, Poly 모드·Forge 모드에는 Maven 그룹이 없습니다. 잘못 막는 경우는 생기지 않습니다. `COMPATIBILITY.md`에 적습니다.
- **`provides`:** 제공 ID는 `providedIds`(그룹을 뺀 ID)에 넣습니다. 객체의 `version`이 모드 버전과 다르면 새 구성 요소 `BridgeAssessment.providedVersions`(`Map<String, String>`, 제공 ID → 버전, 기본 비어 있음)에 넣습니다. `CrossLoaderModResolver`는 제공 ID의 버전을 이 지도에서 먼저 찾습니다.
- **내장 ID:** `QuiltBridge.builtInMods()` = `quilt_loader` → `0.31.0-beta.4`(빌드가 `fabric-bridge.properties`의 `quiltLoaderVersion`에 넣음), `fabricloader` → Fabric 브리지와 같은 `0.19.5`, `mixinextras` → `0.5.5`입니다. 실제 Quilt Loader가 `quilt_loader`로서 `fabricloader 0.19.5`를 제공하고 MixinExtras를 중첩하는 것과 같습니다. `minecraft`·`java`는 런타임이 줍니다. 버전은 Quilt 규칙으로 비교합니다(예: `26.2.x`, `>=25`).
- **로드 순서(`CrossLoaderLoadOrder`):** 로드되는 `IF_PRESENT` 대상과 묶음의 로드되는 구성원도 "먼저 실행할 모드"로 셉니다.

### 8. 중첩 jar: ADR-0015를 그대로, 바깥 로더의 규칙으로

- `quilt_loader.jars`(문자열 배열)를 `BridgeAssessment.nestedJars`로 보고합니다. 추출·판정·사본 선택은 `NestedJarExtractor`와 `CrossLoaderModResolver`가 지금처럼 합니다(내용 해시, 최대 8겹, `120..124`). 경로 조각이 `..`인 항목은 Quilt Loader(`StandardQuiltPlugin`)처럼 건너뜁니다. 이것은 Quilt의 규칙이라 `NestedJarExtractor`가 아니라 `QuiltModJsonReader`가 `nestedJars`에서 빼고 `309`로 알립니다(Q2에서 정함; Fabric 모드의 `jars`는 지금처럼 그대로). 목록에 있지만 jar에 없는 항목은 Fabric과 같이 `123`입니다.
- 어떤 중첩 jar를 모드로 볼지는 **바깥 모드의 로더 규칙**을 따릅니다. 새 SPI 기본 메서드 `ModLoaderBridge.nestedMetadataPaths()`(기본 `List.of(metadataPath())`)를 둡니다.
  - Quilt는 `quilt.mod.json`·`fabric.mod.json`을 돌려줍니다. Quilt Loader는 중첩 jar도 모든 플러그인으로 읽기 때문입니다.
  - Fabric은 기본값 그대로입니다. Fabric 모드 안의 `quilt.mod.json`만 든 jar는 Fabric Loader처럼 모드가 아니고, 지금처럼 `POLY-DISCOVERY-002`입니다.
  - 중첩 jar는 자기 메타데이터의 브리지가 판정합니다(`ForeignModResolver.assess`). Quilt 모드 안의 Fabric API 모듈은 Fabric 브리지가 판정합니다.
- **`load_type`:** 중첩 Quilt 모드의 `load_type`이 `if_required`(기본값)이면 새 구성 요소 `BridgeAssessment.loadOnlyIfRequired = true`입니다. `CrossLoaderModResolver`는 이런 모드를 반복 판정 안에서 이렇게 다룹니다.
  - 이 표시가 없는 활성 모드에서 `REQUIRED`·`IF_PRESENT`(묶음 구성원 포함) 관계로 닿을 때만 활성입니다.
  - 닿지 않으면 `307`로 로드하지 않습니다. Quilt 해석기에서는 맞는 버전이 있는 `optional` 의존도 그 모드를 끌어옵니다(배경 5).
  - `always`·`if_possible`과 중첩 Fabric 모드는 지금 규칙(바깥 모드가 로드되면 후보)입니다. 직접 설치한 모드는 `load_type`과 상관없이 늘 후보입니다(RFC).
- **사본 선택:** 같은 ID의 사본이 서로 다른 브리지에서 왔으면(Quilt 모드에 든 Fabric 사본과 Quilt 사본), 모든 사본의 메타데이터 경로를 `nestedMetadataPaths()`로 받는 브리지(Quilt)의 `compareVersions`로 비교합니다. Quilt Loader가 Fabric 버전도 Quilt `Version`으로 비교하는 것과 같습니다.

### 9. Fabric 실행 장치를 Quilt 모드에 그대로 적용

- **Mixin:** Quilt `mixin`(문자열·`{config, environment}`·배열, 환경 `client`/`dedicated_server`/`*`)을 `MixinConfigEntry`로 읽습니다(새 `MixinConfigEntry.parseQuilt`). 같은 `PolyMixinService`가 적용하고, Quilt 모드의 호환 수준은 `FabricUtil.COMPATIBILITY_0_10_0`입니다(Quilt Loader와 같음). Mixin 설정 판정은 `131`·`132`·`136`을 그대로 씁니다.
  - 차이: Quilt Loader는 설정 이름에 `#<모드 ID>:`를 붙여 두 모드가 같은 설정 이름을 써도 됩니다. Poly 모드 로더는 Fabric처럼 이름 하나당 모드 하나입니다(`134`). 실제 모드에서 나오면 다시 정합니다.
- **access widener:** `access_widener`(문자열 또는 배열)의 파일마다 `ClassTweakerFile`로 읽습니다(`125..130`, `137`). Fabric 쪽 코드는 모드당 파일 하나를 가정하므로 목록을 받게 고칩니다.
- **환경 제거:** `EnvironmentStripper`가 **Quilt 모드 jar의 클래스에만** Quilt의 `@ClientOnly`·`@DedicatedServerOnly`(클래스·멤버·`package-info`, `stripLambdas`)와 `@Requires`(로드된 모드·제공 ID·내장 ID에 없으면 제거)를 Quilt Loader의 `ClassStrippingData`·`PackageStrippingData`·`LambdaStripCalculator`와 같은 뜻으로 적용합니다(옮긴 부분은 출처와 Apache-2.0을 머리에 적음). Fabric 모드는 Fabric Loader처럼 `@Environment`만 봅니다.
- **진입점 실행:** Quilt 모드의 `main`→`common`, `client`→`client`(클라이언트), `server`→`server`(전용 서버)는 Fabric 인터페이스로 부릅니다. `pre_launch`와 `preLaunch`는 `BridgedEntrypoint`(단계 `preLaunch`)로도 보고해서 링크 검사(`119`)를 받습니다. 실행은 결정 4의 `beforeGame`이 합니다. `QuiltBridge.invokeEntrypoint`와 `FabricBridge.invokeEntrypoint`는 같은 코드(`FabricLoaderRuntime.invokeModEntrypoint`)를 씁니다.
- **다른 곳:**
  - `ModContext.mods()`의 로더 이름은 `quilt`입니다(`RuntimeLoadedMods`가 이미 소문자로 바꿈).
  - Forge 쪽 `ForeignModIndex`의 표시 이름은 `quilt_loader.metadata.name`입니다.
  - 제목 화면 모드 수(`LoadedModCounts`)에 `quiltMods`를 더합니다.
  - Forge·NeoForge 호스트의 "Fabric 모드가 함께 있음" 판단(`PolyRuntime.onlyFabric`, `GameHost.Request`)에 Quilt 모드도 셉니다. Quilt 모드도 같은 Mixin을 쓰기 때문입니다.

## 고르지 않은 것

- **`FabricBridge` 하나가 두 메타데이터를 읽기**(`metadataPaths()` 복수): 로더 이름·내장 ID·버전 순서·링크 검사 패키지가 모드마다 달라져야 하는데 이 SPI는 브리지 단위입니다. SPI를 모드 단위로 바꾸는 것이 형제 브리지보다 큽니다.
- **별도 모듈 `poly-bridge-quilt`**: 결정 1의 근거. Mixin과 진입점 저장소는 하나여야 합니다.
- **`quilt.mod.json`을 `fabric.mod.json` 모양으로 바꿔 Fabric 브리지에 넘기기**: 버전 지정 뜻(`1.0.0` = `^1.0.0`), `optional`·`unless`·ANY/ALL, `group`, `load_type`을 잃습니다. Q0에서 확인한 대로 잘못 막거나 잘못 받습니다.
- **Quilt Loader 자체를 실행**(Fabric도 하지 않음, ADR-0016): Quilt Loader는 자기 Knot·해석기·Mixin 서비스를 가져옵니다. Poly 모드 로더의 게임 클래스로더(ADR-0003)·Mixin 서비스와 둘 다 있을 수 없습니다.
- **Quilt API를 Poly 모드 로더가 다시 작성**: API 클래스와 버전 클래스를 그대로 담는 편이 뜻이 정확하고 Quilt Loader를 올릴 때 다시 확인하기 쉽습니다(ADR-0016과 같은 이유).
- **`V1ModMetadataReader`를 담아 그대로 쓰기**: 플러그인·GUI 타입 때문에 235개 클래스를 끌어옵니다. 해석 로직만 옮기고 출처를 적습니다.
- **두 파일을 다 든 jar를 Quilt로 판정**: 결정 2.

## 결과와 위험

- `quilt.mod.json`만 든 Quilt 모드가 Fabric 모드와 같은 실행 장치(Mixin, class tweaker, 진입점 시점 ADR-0020)로 실행됩니다. Quilt API를 쓰는 모드는 Quilt Loader와 같은 답을 받습니다. Fabric 모드와 Quilt 모드가 서로를 봅니다.
- QSL이 필요한 모드(26.2 리소스 모드 66개, `anti-crash-fix`)는 `114`+`308`로 이유와 함께 빠집니다. Quilt Loader에서도 빠지는 경우입니다(완료 기준 5).
- **내부 SPI가 늘어납니다:** `BridgedDependency`(`IF_PRESENT`, `members`, `unless`, `skipWhenOtherSideOnly`), `BridgeAssessment`(`providedVersions`, `loadOnlyIfRequired`), `ModLoaderBridge.nestedMetadataPaths()`. 모두 기본값이 지금 동작이고 `INTERNAL` 등급입니다.
- **Quilt Loader를 올리면** 담은 API의 닫힘(90개), 대체본 멤버, `readVersionSpecifier` 옮긴 코드, 환경 제거 옮긴 코드를 다시 확인해야 합니다(`jdeps`·`javap` 절차는 `poly-bridge-fabric/build.gradle.kts` 주석에 적음). 0.31.0은 베타입니다(계획서 위험).
- **실제 Quilt Loader와 다른 점(알고 있는 것):**
  - 두 메타데이터를 다 든 jar(결정 2)
  - 의존의 Maven 그룹(넓게 받음)
  - Mixin 설정 이름 접두사(`134`)
  - `breaks`의 `unless`(RFC 뜻)
  - 호스트 모드의 `rootPath`

  `COMPATIBILITY.md`에 적고, 실제 모드에서 차이가 나오면 Q10에서 고칩니다.
- 구현 상태(아래 구현 계획): Q2(메타데이터·판정·런타임 SPI)와 Q4(Quilt Loader API·실행 장치)의 코드와 단위 테스트, Q3의 테스트 모드와 `compatibilityMatrix` 시나리오(`quiltOnly`, `quiltFabricPoly`, `quiltBlocked`)가 있습니다.

## 구현 계획

순서는 Q2 → Q3 → Q4입니다. 각 단계는 실패하는 테스트를 먼저 만듭니다(계획서 원칙). 모든 단계 끝에 `.\gradlew.bat check`를 실행하고, `build-logic`을 고쳤으면 `.\gradlew.bat -p build-logic check`도 실행합니다.

### Q2 — 메타데이터, 판정, 런타임 SPI

| 모듈 | 클래스(새/바꿈) | 하는 일 |
|---|---|---|
| (루트) | `gradle/libs.versions.toml`, `settings.gradle.kts` (바꿈) | `quiltLoader`, `quilt-loader` 별칭, Quilt Maven `exclusiveContent`(`org.quiltmc`) |
| `poly-bridge-api` | `BridgedDependency` (바꿈) | `Kind.IF_PRESENT`, `members`, `unless`, `skipWhenOtherSideOnly`, 기존 4인자 생성자 유지(결정 7) |
| `poly-bridge-api` | `BridgeAssessment` (바꿈) | `providedVersions`, `loadOnlyIfRequired`, `withProvidedVersions`, `withLoadOnlyIfRequired`. 기존 `of`는 빈 값 |
| `poly-bridge-api` | `ModLoaderBridge` (바꿈) | `default List<String> nestedMetadataPaths()` |
| `poly-runtime-impl` | `discovery/ModFileKind` (바꿈) | `QUILT("quilt.mod.json")`를 `FABRIC` 다음에 |
| `poly-runtime-impl` | `discovery/NestedJarExtractor` (바꿈) | 바깥 브리지의 `nestedMetadataPaths()`로 중첩 모드 판단. 자기 메타데이터의 브리지로 판정 |
| `poly-runtime-impl` | `PolyRuntime`, `discovery/ForeignModResolver` (바꿈) | `POLY-DISCOVERY-002` 문구에 `quilt.mod.json`. 두 파일 jar에 `310`(`ForeignModResolver.assess`가 붙여서 중첩 jar에도 붙음). `onlyFabric` → Fabric 계열(Fabric, Quilt) |
| `poly-runtime-impl` | `compat/CrossLoaderModResolver` (바꿈) | `IF_PRESENT`, 묶음(ANY/ALL), `unless`, `skipWhenOtherSideOnly`, `providedVersions`, `loadOnlyIfRequired`(`307`, `124`에서 뺌), 브리지가 다른 사본의 버전 비교 |
| `poly-runtime-impl` | `compat/CrossLoaderLoadOrder` (바꿈) | `IF_PRESENT`·묶음 구성원 순서 |
| `poly-bridge-fabric` | `dev.polymodloader.compat.quilt.QuiltBridge` (새) | `ModLoaderBridge`: `assess`(아래 읽기 클래스 사용), `builtInMods`, `runtimePackages`, `providedPackages`, `sharedLibraryPackages`, `nestedMetadataPaths`, `compareVersions`, `invokeEntrypoint` |
| `poly-bridge-fabric` | `quilt.QuiltModJsonReader` (새) | `quilt.mod.json`(+ `quilt.mod.json5` 유무)을 gson으로 읽어 `QuiltModDescription`과 발견(`300..309`)을 만듭니다. Quilt Loader의 `ModMetadataReader`·`V1ModMetadataReader` 검사와 같은 순서·같은 조건입니다 |
| `poly-bridge-fabric` | `quilt.QuiltModDescription` (새, record) | `id`, `group`, `version`, `name`, `description`, `contributors`, `contact`, `licenses`, `icon`, `provides`(ID·그룹·버전), `entrypoints`(키 → 선언 목록), `jars`, `depends`, `breaks`, `loadType`, `mixins`(쪽별), `accessWideners`, `environment`, `root` |
| `poly-bridge-fabric` | `quilt.QuiltVersionSpecifier` (새) | `readVersionSpecifier`·`readVersionsValue`·`readVersionsObject`를 옮김(출처·Apache-2.0 머리). `VersionRange`를 돌려주거나 `VersionFormatException` |
| `poly-bridge-fabric` | `quilt.QuiltDependencyReader` (새) | 의존 객체 → (가) 담은 `ModDependency`(Quilt API용), (나) `BridgedDependency`(결정 7 표), `308` |
| `poly-bridge-fabric` | `build.gradle.kts` (바꿈) | `quiltLoaderJar` 구성, `quiltLoaderApiClasses` 패턴(결정 4), `quiltLoaderApi` `Sync`, `testImplementation(files(quiltLoaderApi))`, `fabric-bridge.properties`에 `quiltLoaderVersion` |
| `poly-bridge-fabric` | `META-INF/services/…ModLoaderBridge` (바꿈) | `QuiltBridge` 한 줄 |
| (루트) | `THIRD_PARTY_NOTICES.md` (더함) | Quilt Loader 0.31.0-beta.4, Apache-2.0, 담은 클래스와 옮긴 코드 |

단위 테스트(새 `QuiltBridgeTest`, `QuiltVersionSpecifierTest`, `QuiltDependencyReaderTest`. 각 항목의 맞는 값과 틀린 값을 모두 넣음):

- 루트: JSON이 아님, 객체가 아님, `schema_version` 없음·문자열·0·-1·2 → `300`, 1은 통과. `quilt.mod.json5`가 함께 있음 → `300`.
- `quilt_loader`: 없음·객체 아님 → `300`.
- `id`: 없음, 대문자, 한 글자, 65자, 숫자로 시작 → `300`(정규식 `^[a-z][a-z0-9-_]{1,63}$`이라 64자까지 받음).
- `group`: 없음, 허용하지 않는 문자 → `300`.
- `version`: 없음, 문자열 아님 → `300`.
- `entrypoints`:
  - 객체 아님, 원소가 문자열·객체 아님, 객체에 `adapter`·`value` 없음 → `300`
  - `adapter: "kotlin"` → `302`
  - `main`·`client`·`server`·`pre_launch`·`preLaunch`의 쪽별 단계
  - `init`·`client_init`·`server_init` → `303`(다른 초기화가 없을 때의 문구 포함)
  - `modmenu` → `304`
- `jars`: 배열 아님, 문자열 아닌 원소 → `300`. `..` 항목은 빠짐.
- `language_adapters`: 객체 아님 → `300`, 있음 → `302`.
- `depends`/`breaks`:
  - 배열 아님 → `300`
  - 문자열, `group:id`, 객체, 중첩 배열(depends = ANY, breaks = ALL)
  - 객체에 `id` 없음, `optional`이 불리언 아님 → `300`
  - `reason`, `unless`(문자열·객체·배열)
  - `versions`: 문자열·배열(폐기, ANY)·`any`·`all`(중첩), 빈 `all`·`{"all": ["*"]}` → `300`, 빈 `any`(아무것도 안 맞음)와 `{"any": ["*"]}`(모두 맞음)는 Quilt Loader처럼 읽음(배경 4), 읽을 수 없는 지정(`~abc`, 가운데 `.x`) → `300`
  - QSL ID → `308`
- 버전 지정 표(`QuiltVersionSpecifierTest`):
  - `*`, `1.0.0`(1.5.0 받음, 2.0.0 거절), `=`, `>=`, `>`, `<=`, `<`
  - `1.0.x`, `~1.0.0`, `^1.0.0`, `26.2.x`(26.2·26.2.1 받음, 26.3 거절)
  - 시맨틱이 아닌 버전의 연산자 없는 지정(정확히 같아야 함), 사전 배포·빌드 메타데이터
  - 같은 문자열이 Fabric 브리지에서는 다른 결과임을 보이는 대조 테스트
- `provides`: 문자열, `group:id`, 객체(`version` 있음·없음), 다른 형식 → `300`. `providedIds`·`providedVersions`.
- `load_type`: `always`·`if_possible`·`if_required`·기본값, 모르는 값·문자열 아님 → `300`. `loadOnlyIfRequired`.
- `repositories`: 배열 아님 → `300`, 있음 → `309`.
- `intermediate_mappings`:
  - 없음, `org.quiltmc:hashed`, `org.quiltmc.experimental:mojang`, 모르는 좌표, 좌표 형식 아님, 문자열 아님 → `300`
  - `net.fabricmc:intermediary` → `309`
- `required_features`: `modifiable_filesystem_overlay`·`quilt_loader:modifiable_filesystem_overlay` → `306`. 모르는 값, 배열이 아님 → `300`.
- `metadata`: 객체 아님, `contributors`·`contact` 객체 아님 → `300`. `license` 문자열·객체·배열, `icon` 문자열·객체.
- `mixin`:
  - 문자열·객체·배열
  - 객체에 `config` 없음, `environment` 틀림 → `300`
  - 없는 설정 → `136`, 더 새 Mixin이 필요한 `required` 설정 → `132`, 쪽별 적용 → `131`
- `access_widener`: 문자열·배열, 다른 형식 → `300`. 없는 파일 → `125`, `official`이 아닌 이름 공간 → `126`, 여러 파일 합치기.
- `minecraft.environment`: `*`·`""`·`client`·`dedicated_server`·틀린 값(`300`). 쪽이 맞지 않으면 → `305`.
- 최상위 `experimental_quilt_loader_plugin` → `301`.
- `quilt_loader`의 모르는 키·최상위 `environment` → `309`.
- `builtInMods`에 `quilt_loader`, `fabricloader`, `mixinextras`. 브리지가 어떤 입력에도 예외를 던지지 않음(깨진 값 조합 퍼즈 몇 개).

런타임 테스트(바꿈): `ModFileScannerTest`(`QUILT`, 두 파일 jar = `FABRIC` + `alsoFor QUILT`), `NestedJarExtractorTest`(Quilt 모드 안의 Fabric·Quilt jar는 모드, Fabric 모드 안의 Quilt 전용 jar는 `002`), `CrossLoaderModResolverTest`(`IF_PRESENT` 있음·없음·틀린 버전, ANY·ALL 묶음, `unless`, `skipWhenOtherSideOnly`, `providedVersions`, `loadOnlyIfRequired` 닿음·안 닿음·서로만 필요로 함 → `307`, 다른 브리지 사본 비교), `CrossLoaderLoadOrderTest`.

### Q3 — 테스트 모드와 `compatibilityMatrix`

새 테스트 모드(`settings.gradle.kts` include, `test-mods/README.md` 표에 추가, `compileOnly(libs.quilt.loader)`·`compileOnly(libs.fabric.loader)`):

- **`test-mods/quilt-hello`**(`quilt_hello`): `quilt.mod.json`만 듭니다.
  - 기본 항목: `schema_version` 1, `group` `dev.polymodloader.test`, `version` `1.0.0`, `intermediate_mappings` `net.fabricmc:intermediary`, `metadata`(이름, `Owner` 기여자, 연락처, 라이선스 `MIT`, 아이콘)
  - `provides`: `"quilt_hello_api"`, `{"id": "quilt_hello_versioned", "version": "3.0.0"}`
  - `entrypoints`:
    - `main`(Fabric `ModInitializer`), `client`
    - `pre_launch`(Quilt `PreLaunchEntrypoint`), `preLaunch`(Fabric)
    - `init`(QSL 키 → `303`)
    - `quilt_hello:custom`(사용자 키 → `304`, 자기가 `QuiltLoader.getEntrypointContainers`로 받음)
  - `depends`:
    - `{"id": "quilt_loader", "versions": ">=0.30.0"}`
    - `{"id": "minecraft", "versions": {"any": ["26.1.x", "26.2.x"]}}`
    - `{"id": "java", "versions": ">=25"}`
    - `{"id": "fabric_lib", "versions": "1.0.0", "optional": true}`: Quilt 뜻 `^1.0.0`이라 1.2.0을 받음. Fabric 뜻이면 거절
    - `{"id": "absent_optional", "optional": true}`
    - `{"id": "absent_required", "unless": "quilt_loader"}`
    - `[{"id": "absent_a"}, {"id": "quilt_loader"}]`(ANY)
  - `breaks`: `[{"id": "fabric_lib"}, {"id": "absent_b"}]`(ALL이라 깨지지 않음)
  - `jars`: `META-INF/jars/fabric-nested-lib.jar`(기존 Fabric 테스트 모드), `META-INF/jars/quilt-nested-lib.jar`, `META-INF/jars/quilt-nested-unused.jar`
  - 최상위 `mixin`(`{"config": "quilt_hello.mixins.json", "environment": "*"}`, `MinecraftServer`의 한 메서드에 `@Inject`), `access_widener`(배열, 접근 줄 하나), `minecraft.environment` `*`
  - 코드가 로그에 찍는 것:
    - `MinecraftQuiltLoader.getEnvironmentType()`, `QuiltLoader.getRawGameVersion()`
    - `isModLoaded("fabric_lib")`, `getModContainer("fabric_lib")`의 `getSourceType()`(`NORMAL_FABRIC`)·`group()`(`loader.fabric`), `getModContainer("native_lib")`의 `OTHER`
    - 자기 `metadata().version()`, `values()`의 사용자 값
    - 사용자 진입점 인스턴스와 `FabricLoader.getEntrypoints`가 같은 인스턴스인지
    - `FabricLoader.getInstance().getModContainer("quilt_hello")`의 `getType()`(`quilt`)
    - `getModContainer(QuiltHello.class)`
    - 전용 서버·게임 테스트 서버에서 `@ClientOnly` 메서드와 `@Requires("absent_mod")` 필드가 제거됐는지(리플렉션)
    - `pre_launch`가 `preLaunch`보다 먼저 불렸는지
- **`test-mods/quilt-nested-lib`**(`quilt_nested_lib`): Quilt 중첩 라이브러리, `load_type` 기본(`if_required`). `quilt_hello`가 `depends`로 필요로 하므로 로드됩니다.
- **`test-mods/quilt-nested-unused`**(`quilt_nested_unused`): 아무도 필요로 하지 않음 → `307`.
- **`test-mods/fabric-quilt-dual`**: `fabric.mod.json`과 `quilt.mod.json`을 다 듭니다. 같은 ID에 진입점 클래스가 다릅니다. Fabric 쪽 진입점만 찍혀야 하고 `310`이 붙습니다.
- **`test-mods/quilt-qsl-resource`**: 클래스 없는 리소스 모드이고 `depends`에 `quilt_resource_loader`가 있습니다 → `114` + `308`, 게임은 나머지로 시작합니다.
- **`test-mods/quilt-blocked`**(여러 jar를 만드는 한 프로젝트, 또는 아래 셋을 각각):
  - `quilt_plugin`(`experimental_quilt_loader_plugin`) → `301`
  - `quilt_malformed`(`intermediate_mappings` 없음) → `300`
  - `quilt_client_only`(`minecraft.environment: client`, 서버에서) → `305`
  - `quilt_init_only`(`init`만 있음) → 로드되고 `303`

`poly-bootstrap/build.gradle.kts`의 `matrix` 목록에 더할 게임 테스트 서버 시나리오:

- **`quiltOnly`**(`native`, `quilt-hello`): `quilt_hello` 로드, 중첩 `fabric_nested_lib`·`quilt_nested_lib` 로드, `quilt_nested_unused` `307`, `303`·`304` 알림, `pre_launch` → `preLaunch` → `main` 순서, Mixin 적용, 환경 제거, Quilt API 줄, `fabric_lib` 선택 의존 건너뜀.
- **`quiltFabricPoly`**(`native`, `nativeLib`, `fabricLib`, `:test-mods:fabric-api-user`, `quilt-hello`, `fabric-quilt-dual`):
  - Quilt → Fabric·Poly 보기, Fabric → Quilt 보기(`getType() = quilt`)
  - `fabric_lib 1.2.0`이 Quilt 뜻의 `1.0.0`을 만족
  - 같은 진입점 인스턴스
  - `ModContext.mods()`의 `quilt_hello: quilt 1.0.0`
  - `Loaded mods per loader` 줄의 Quilt 수
  - 두 파일 jar는 Fabric으로 실행되고 `310`
- **`quiltBlocked`**(`native`, `quilt-qsl-resource`, `quilt-blocked`의 jar들, `quilt-hello`): 각 이유 줄(`114`+`308`, `301`, `300`, `305`, `303`)과 `Foreign mods: N loaded, M not loaded`, 게임은 정상 시작.
- 기존 시나리오의 `Loaded mods per loader:` 기대 문구를 새 형식(Quilt 수 포함)에 맞춥니다. NeoForge 작업이 같은 줄을 바꾸면 합칩니다.

#### Q3 구현에서 정한 것 (2026-10-24)

- **`quilt-blocked`는 jar 넷을 만드는 한 프로젝트입니다.** 프로젝트 자신의 jar가 `quilt-init-only`이고, `quilt-plugin`·`quilt-malformed`·`quilt-client-only`는 `src/jars/<이름>/quilt.mod.json`만 든 jar로 같은 `runtimeElements` 변형의 아티팩트입니다. 그래서 `modJars(":test-mods:quilt-blocked")` 하나가 넷을 다 넣습니다.
- **`quilt_hello`의 추가 항목.** 계획의 `depends`에 `{"id": "quilt_nested_lib", "versions": ">=1.0.0"}`을 더했습니다(중첩 `if_required` 모드를 끌어오는 관계). `access_widener` 배열은 파일 둘(줄 하나씩: `SharedConstants.prefixDebugFlagName`, `Mth.SIN`)이고, 모드는 리플렉션으로 둘 다 `public`인지 찍습니다. 사용자 값은 최상위 `quilt_hello:settings`입니다. 아이콘은 경로만 적고 파일은 넣지 않았습니다. Mixin은 `@Mixin(targets = "net.minecraft.server.MinecraftServer")`로 스텁 없이 `tickServer` 머리에 `@Inject`합니다.
- **`native_lib`** 의 `ModContext.mods()` 줄에 `quilt_hello`를 더했습니다(`[native_lib] sees quilt_hello: quilt 1.0.0`).
- **"Fabric → Quilt 보기"** 는 `fabric-quilt-dual`의 Fabric 진입점이 `FabricLoader.getModContainer("quilt_hello")`로 확인합니다(형 `quilt`, 이름, `Owner` 기여자가 작성자). `quilt_hello` 자신도 같은 것을 찍습니다.
- **나오면 안 되는 줄.** `VerifiedGameRunTask`에 `unexpectedOutput`(있으면 실패)을 더했습니다. Quilt 시나리오는 QSL `init`·서버의 `client`·두 파일 jar의 Quilt 쪽 진입점·`307`로 빠진 모드의 진입점이 실행된 줄, `124`(`307`은 세지 않음), `002`, `Exception in thread`, `Caused by: `, 크래시 리포트를 금지합니다.
- **세 시나리오 모두** 게임 테스트 서버에서 `minecraft:always_pass`를 통과하고 `universe/gametestworld/level.dat`을 저장해야 합니다.
- **`308`의 QSL 판정(해결).** 처음 규칙(`quilt_loader`가 아닌 `quilt_*` ID)은 `quilt_hello`가 필요로 하는 중첩 모드 `quilt_nested_lib`(설치됨, 로드됨)에도 "Quilt Loader도 이 의존을 채울 수 없음"을 붙였습니다. QSL 소스(QuiltMC/quilt-standard-libraries의 `library/*/*/build.gradle` `qslModule { id = … }`, 기본 브랜치 `1.21.5` 커밋 `fef35afe5fb0`과 `1.21`·`1.20.6`·`1.20`·`1.19.4`의 합집합, `provides`의 `quilt_registry_entry_attachments`)와 Quilted Fabric API(QuiltMC/quilted-fabric-api `1.20.1` 커밋 `db482fe3067e`: 묶음 `quilted_fabric_api`, 모듈 `quilted_` + Fabric API 모듈 이름)의 ID 목록으로 좁혔습니다(`dev.polymodloader.compat.quilt.QslModules`, `QuiltDependencyReaderTest`). `quiltOnly`·`quiltFabricPoly`는 `308`을, 세 시나리오 모두 `the QSL module 'quilt_nested_lib'` 줄을 금지합니다.

### Q4 — Quilt Loader API와 실행 장치

| 모듈 | 클래스(새/바꿈) | 하는 일 |
|---|---|---|
| `poly-bridge-fabric` | `org.quiltmc.loader.impl.QuiltLoaderImpl` (새, 대체본) | 결정 4의 멤버. 동작은 `QuiltLoaderRuntime` |
| `poly-bridge-fabric` | `org.quiltmc.loader.impl.game.GameProvider` (새, 대체본 인터페이스) | `getNormalizedGameVersion`, `getRawGameVersion` |
| `poly-bridge-fabric` | `org.quiltmc.loader.impl.util.DefaultLanguageAdapter` (새, 대체본) | `INSTANCE`, `create`. Fabric 대체본의 생성 코드를 씀 |
| `poly-bridge-fabric` | `quilt.QuiltLoaderRuntime` (새) | 모드 목록·조회·클래스로 찾기, 진입점(인스턴스 공유), 폴더, 환경, 인수, 공유 객체, 모드 표, `invokePreLaunch` |
| `poly-bridge-fabric` | `quilt.QuiltModContainer`, `quilt.QuiltModMetadata`, `quilt.QuiltLoaderValue`, `quilt.QuiltOfficialMappingResolver`, `quilt.QuiltLogForwarder` (새) | Quilt API 구현(결정 4) |
| `poly-bridge-fabric` | `compat.fabric.loader.FabricFamilyMod` (새) | Fabric·Quilt 모드에서 Mixin 설정·class tweaker 경로들·진입점 선언·Mixin 호환 수준을 꺼냄 |
| `poly-bridge-fabric` | `FabricLoaderRuntime` (바꿈) | Quilt 모드를 `FabricModMetadata.quilt(…)`로 넣고 선언을 진입점 저장소에 넣음. `invokeModEntrypoint`로 두 브리지의 진입점 호출을 합침 |
| `poly-bridge-fabric` | `FabricModMetadata` (바꿈), `quilt.QuiltFabricModDependency` (새) | Fabric API에서 본 Quilt 모드(`GeneralExt2FabricMetadata`와 같은 규칙) |
| `poly-bridge-fabric` | `FabricBridge` (바꿈) | `classTransformer` 조건에 Quilt 모드. `prepareLaunch`에서 `QuiltLoaderImpl` 연결. `beforeGame`에서 `pre_launch` → `preLaunch` |
| `poly-bridge-fabric` | `mixin/FabricMixinBootstrap`, `mixin/MixinConfigEntry` (바꿈) | `FabricFamilyMod`로 Quilt 모드의 설정 추가, `parseQuilt`, 호환 수준 0.10.0 |
| `poly-bridge-fabric` | `InterfaceInjector`, `ClassTweakerFile` 사용처 (바꿈) | 모드당 여러 access widener |
| `poly-bridge-fabric` | `EnvironmentStripper` (바꿈) | Quilt 모드 클래스에 `@ClientOnly`·`@DedicatedServerOnly`·`@Requires`, `package-info`, 람다 제거 |
| `poly-bridge-forge` | `ForeignModIndex` (바꿈) | Quilt 모드 표시 이름 |
| `poly-version-adapter-api` | `LoadedModCounts`, `ModCountLines` (바꿈) | `quiltMods` |
| `poly-runtime-impl` | `LoadedModCounter` (바꿈) | Quilt 수, `NOT_MODS`에 `quilt_loader` |
| `poly-game-v26_1`, `poly-game-v26_2` | `client/TitleScreenModCounts` (바꿈) | Quilt 줄(두 버전 함께) |
| `poly-bridge-fabric` | `README.md` (바꿈) | 하는 일·담은 Quilt 클래스·대체본·제한 |

단위 테스트:

- `QuiltLoaderRuntimeTest`:
  - 모드 조회(`provides` 포함), 소스 종류·그룹
  - `getModContainer(Class)`
  - 진입점 생성·캐시·형 검사, Fabric 쪽과 같은 인스턴스
  - `pre_launch` 순서, 공유 객체가 같은 인스턴스
  - 폴더·`loader.cacheDir`·인수 `sanitize`, 매핑 해석기, 붙기 전 호출의 예외
- `QuiltModMetadataTest`: 라이선스, 기여자, `values()`, 의존 트리, Fabric 보기의 `getType`·의존·작성자
- `EnvironmentStripperTest`: Quilt 어노테이션은 Quilt 모드 클래스에만, `package-info`, 람다, `@Requires`
- `FabricBridgeTest`: Quilt 모드의 Mixin 설정·호환 수준
- `QuiltLoaderApiLinkTest`(`poly-bootstrap`): `QuiltFileSystems`·`api.plugin`·`QuiltLoaderImpl`에 없는 멤버 → `111`
- 같은 테스트의 담은 클래스 확인: 브리지 jar에 `org.quiltmc.loader.api.QuiltLoader`가 있고 `org.quiltmc.loader.impl.filesystem`·`net.fabricmc`(Quilt jar의 것)은 없음

검증: `quiltOnly`·`quiltFabricPoly`의 Quilt API 줄이 통과해야 Q4 완료입니다. 다음 단계는 계획서의 Q5(`corpusQuilt`, `ModCorpusTask`에 Quilt 전용 판 고르기)입니다.

#### Q4 구현에서 정한 것 (2026-10-24)

- **링크 검사 테스트의 위치.** 실제 Quilt 브리지와 실제 브리지 jar(담은 클래스 포함)로 확인해야 하므로 `poly-runtime-impl`의 `ModLinkVerifierTest`가 아니라 브리지 jar가 클래스 경로에 있는 `poly-bootstrap`의 `QuiltLoaderApiLinkTest`에 둡니다. `poly-runtime-impl`은 테스트에서도 브리지에 의존하지 않습니다.
- **담은 내부 도우미는 모드에 내주지 않습니다.** `providedPackages`는 결정 4 그대로(`api/`와 대체본 셋)이므로, 모드가 담은 도우미(`impl.metadata.qmj.SemanticVersionImpl` 등)를 직접 쓰면 `111`입니다. Fabric 브리지가 담은 `impl.util.version`을 내주지 않는 것과 같습니다.
- **로그 줄.** `Loaded mods per loader: N Poly, N Fabric, N Quilt, N Forge`입니다. 호스트(FML)가 로드하는 모드는 지금처럼 `Forge` 칸입니다(NeoForge 작업이 바꿀 수 있음). `LoadedModCounts`는 `quiltMods`를 마지막 구성 요소로 더하고 세 수 생성자를 남깁니다. 제목 화면 줄은 Poly·Fabric·Quilt·Forge 순서이고 키는 `poly.title.mod_count.quilt`입니다.
- **Mixin 로그.** Quilt 모드 설정이 있으면 `Mixin: N configuration(s) from F Fabric mod(s) and Q Quilt mod(s) for SIDE`, 없으면 지금 문구 그대로입니다.
- **jar가 없는 내장 모드의 `rootPath()`**(`java`, `quilt_loader`, `mixinextras`): 캐시 폴더 아래 빈 폴더(`<cacheDir>/poly-mod-loader/empty-mod-root`)입니다. `minecraft`는 게임 jar 루트입니다.
- **`@Requires`의 `stripLambdas`** 는 어노테이션 전체를 읽은 뒤 판단합니다(Quilt Loader는 `value` 배열을 다 읽은 때 판단해서, `stripLambdas`가 `value` 뒤에 적히면 무시함). 클래스에 붙은 Fabric `@Environment`가 다른 쪽이면 Quilt 모드 클래스도 로드되지 않습니다(Quilt Loader와 같음).
- **진입점 예외.** `QuiltLoader.getEntrypoints`의 생성 실패와 형 불일치는 Quilt의 `EntrypointException`(키 포함)이고, `pre_launch` 실패는 Quilt Loader의 문구(`Could not execute entrypoint stage 'pre_launch' due to errors, provided by '<mod>' at '<value>'!`)로 모아 던집니다.

#### Q5–Q11 구현에서 정한 것 (2026-10-24)

- **Quilt 묶음의 구성.** `ModCorpusTask`의 로더 `quilt`는 인기 순 상위가 아니라 **그 게임 버전에서 Quilt용이고 Fabric용이 아닌 판이 있는 모든 프로젝트**(가장 새 그런 판)와 그 판이 Modrinth에서 필수로 적은 모드입니다(`modCorpusQuilt`, `compat/mod-corpus-quilt-<버전>.json`). Quilt를 표시한 26.x 판은 거의 모두 Fabric jar라서 Fabric 묶음이 이미 실행하기 때문입니다(계획서 Q0). 필수 모드는 Quilt 전용 판이 있으면 그것, 없으면 Quilt를 표시한 가장 새 판(대개 Fabric jar, Quilt 사용자도 그것을 설치함)입니다. 26.2는 114개 + 필수 3개(Fabric API, Cloth Config, Fusion)로 Q0의 수와 같고, 26.1은 97개 + 1개입니다. 같은 출력 폴더의 `quilt/`만 이 작업이 정리합니다.
- **실제 Quilt Loader와 비교하는 방법(`quiltLoaderReference`).** Quilt Loader 0.31.0-beta.4의 서버 런처 프로필 라이브러리(meta)를 Gradle로 받고, 바닐라 전용 서버 jar를 `loader.gameJarPath`로 넘겨 Quilt Loader 자신의 `KnotServer`를 실행합니다(오류 창 끔, EULA는 동의하지 않음). 먼저 필수 모드만(기준), 그다음 Quilt 전용 판마다 기준에 하나씩 더해 실행하고, EULA 확인까지 가는지와 Quilt Loader가 센 모드 수(`Loading N mods`), 멈추면 Quilt Loader의 오류 문구를 보고서에 적습니다. 모드 수가 기준보다 많으면 그 모드를 로드한 것입니다. 클라이언트(창)에서는 실행하지 않습니다. 클라이언트에서만 빠지는 것은 메타데이터 판정(`300`, `114`)이 쪽과 상관없고, 나머지는 모드 자신의 `minecraft.environment`라서 서버 실행과 메타데이터로 판단합니다.
- **결정 2(두 메타데이터 jar는 Fabric으로)는 유지합니다.** 26.2 묶음의 그런 jar 6개를 실제 Quilt Loader와 비교했습니다. 서버에서 다른 것은 Armor Visibility 하나입니다. `fabric.mod.json`은 `environment: client`라 Poly 모드 로더는 서버에서 빼고(`108`), Quilt Loader는 `quilt.mod.json`에 환경이 없어 로드합니다. 그러나 그 모드는 `client` 진입점과 `client` Mixin만 있어 Quilt Loader에서도 서버에서는 아무 코드도 실행되지 않습니다(완료 기준 5의 "실행되지 않는 경우"). Real-Football은 두 파일 모두 서버 전용이라 클라이언트에서 둘 다 빼고, 서버에서는 Quilt Loader가 QSL 진입점 `init`만 있어 초기화하지 않는 데 비해 Poly 모드 로더는 `fabric.mod.json`의 `main`을 실행합니다.
- **`POLY-COMPAT-124`는 다른 쪽 전용 중첩 모드를 세지 않습니다.** 막는 발견이 모두 환경(`108`·`305`)인 중첩 모드는 "일부만 동작" 목록에서 뺍니다. 실제 Quilt Loader는 26.2 전용 서버에서 Simple Voice Chat과 Fabric API의 클라이언트 모듈을 하나도 로드하지 않고 아무것도 알리지 않습니다(`quiltLoaderReference` 기준·각 실행의 모드 표). Fabric Loader도 같습니다. 그 중첩 모드 자신의 판정 줄(`108`/`305`)은 그대로 남습니다. 124의 뜻은 바꾸지 않았습니다.
- **Fabric 브리지의 내장 모드에 `quilt_loader`.** Quilt용 판인데 `fabric.mod.json`만 들었거나 Fabric으로 판정되는 jar 4개(Better Shulker Boxes, F34, InvSearch Storage Indexer, MTA NYC Subway Signs)가 `depends`에 `quilt_loader`를 적습니다. 실제 Quilt Loader는 이것들을 로드하고, Poly 모드 로더는 모든 모드에 Quilt Loader API를 주므로 `FabricBridge.builtInMods()`에 `quilt_loader`(0.31.0-beta.4)를 더했습니다. Fabric 모드는 Fabric Loader처럼 실행하므로 `breaks`·`conflicts`의 `quilt_loader`는 읽지 않고, `FabricLoader.isModLoaded("quilt_loader")`도 지금처럼 `false`입니다(Fabric 모드가 Quilt 전용 경로를 타지 않도록). Quilt Loader에서는 `true`인 차이입니다.
- **QSL 리소스 로더 의존의 `unless`.** 26.2 묶음의 리소스 모드 65개는 `{"id": "quilt_resource_loader", "unless": "fabric-resource-loader-v0"}`입니다. Fabric API가 있으면 실제 Quilt Loader도 이 의존을 따지지 않고 로드합니다(`quiltLoaderReference`, 기준에 Fabric API). Poly 모드 로더도 같고, Fabric API의 리소스 로더가 Quilt 모드를 포함한 모든 모드로 데이터 팩을 만듭니다. `corpusQuilt`가 저장된 월드의 `level.dat`에서 그 데이터 팩들이 켜져 있는지 확인합니다. Fabric API가 없으면 `114`+`308`로 빠지며 Quilt Loader도 같습니다.
- **Quilt 모드와 Forge 모드를 한 게임에서(`corpusQuiltForge`).** Fabric 모드와 같은 규칙입니다(ADR-0021 G6·G8): Mixin 하나를 Quilt·Fabric·Forge 모드가 함께 쓰고, Quilt와 Forge 메타데이터를 다 든 jar는 Forge가 실행하며, Forge가 자기 빌드를 로드하는 모드의 Quilt 빌드는 `139`로 빠집니다(Simple Voice Chat). 새 코드는 없습니다.
- **개발용 실행의 클래스 경로 jar.** Gradle은 Windows에서 클래스 경로가 너무 길면 `Class-Path`만 든 jar 하나로 JVM을 시작합니다. `GameLibraries`가 이 jar를 라이브러리로 게임 클래스로더에 넣어, 그 `Class-Path`를 따라 바닐라 게임 jar까지 Forge의 패치된 게임보다 앞에 들어갔고, FML이 바닐라 게임을 찾아 Forge용 Mixin이 실패했습니다(Quilt 묶음 + Forge 묶음에서 발견). 클래스 없이 `Class-Path`만 있는 jar는 JVM처럼 그 항목들로 풀어 판단합니다(`GameLibrariesTest`). 런처와 설치한 서버의 실행 방식은 바뀌지 않습니다.
- **전용 서버 실행에서 뺀 모드.** InvSearch Storage Indexer는 초기화할 때 JDK `HttpServer`(데몬이 아닌 스레드)를 시작해, EULA 확인에서 멈춘 전용 서버의 JVM이 끝나지 않습니다(Quilt Loader에서도 같음). `corpusQuiltServer`에서만 빼고 게임 테스트 서버에서는 실행합니다.

#### Q6 클라이언트 첫 실행에서 정한 것 (2026-10-24)

- **Mixin이 거부하는 주입 지점은 시작 전에 판정(`POLY-COMPAT-140`).** 26.1·26.1.2 클라이언트에서 Even Better Moon and Sun(1.1.0, 1.0.0; `fabric.mod.json`만 있는 Quilt 표시 jar)의 `SkyRendererMixin`이 `@At("INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose:()V")`를 씁니다. Mixin 0.8.7은 주입기를 만들 때 이 문자열을 `TargetSelector.parseAndValidate`로 읽다가 `InvalidMemberDescriptorException: Invalid name: pushPose:`로 거부하고, 필수 설정이라 `SkyRenderer`가 로드되지 않습니다. 클라이언트는 리소스를 다시 읽을 때마다 같은 오류를 내며 월드에 들어가지 못했습니다. Fabric Loader와 Quilt Loader 0.31.0-beta.4는 같은 Mixin(`net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7`, Quilt Loader의 런처 프로필 라이브러리)을 쓰므로 그 로더들에서도 같습니다. 실제 로더로 클라이언트를 실행해 보지는 않았고, 같은 Mixin jar로 그 문자열이 거부됨을 테스트로 확인했습니다(`MixinTargetCheckTest`). 그래서 브리지의 Mixin 설정 판정(`MixinConfigCheck`)이 이 쪽에 적용되는 Mixin 클래스의 `@At`(`INVOKE`·`INVOKE_ASSIGN`·`INVOKE_STRING`·`FIELD`)의 `target`을 Mixin 자신의 `TargetSelector.parseAndValidate`로 미리 읽습니다. 필수 설정이면 막고, 선택 설정이면 Mixin이 그 Mixin만 건너뛰므로 경고입니다. Mixin 문맥이 있어야 판단할 수 있는 선택자(다시 매핑, 동적 선택자)는 Mixin에 맡깁니다. 번호는 Fabric 계열 Mixin 판정이라 `1xx`의 다음 번호 `140`입니다. 세 버전의 모든 묶음(Fabric, Forge, NeoForge, Quilt)에서 이 판정에 걸리는 것은 이 모드뿐입니다(`corpusAssessment`, `corpusAssessmentQuilt`).
- **변환 실패가 왜 멈춤이 되었는가.** `SkyRenderer`는 `LevelRenderer.onResourceManagerReload`에서 처음 로드되고, 그 실패는 리소스 다시 읽기의 `CompletableFuture`에 담깁니다. 게임은 다시 읽기가 실패하면 기본 팩으로 다시 읽기를 시도하고 매번 같은 클래스 로드가 실패합니다(로그의 반복). Poly 모드 로더는 `133`을 그 클래스 로드에서 던지고(Fabric의 Knot도 변환 오류를 클래스 로드에서 던지므로 같을 것으로 보지만 실행해 보지는 않음), 멈춤은 게임의 다시 읽기 처리 때문입니다. 게임 안의 변환 실패 일반을 크래시로 바꾸지는 않았습니다. 이제 이 모드는 시작 전에 `140`으로 빠집니다.
- **Mixin 적용 실패를 게임 없이 찾기(`mixinAuditQuiltClient`·`mixinAuditQuiltServer`).** 26.2 클라이언트 두 번째 실행에서 NeoFullbright의 static이 아닌 `@ModifyArg` 핸들러가 static 대상에 붙어 Mixin이 `ClientLanguage`를 거부했습니다(`ModifyArgInjector.checkTarget`). 이것을 시작 전 판정으로 만들려면 게임 클래스를 읽고 Mixin의 대상 메서드 선택을 다시 구현해야 해서, 결과가 Mixin과 어긋날 수 있으므로 판정(`140`)을 넓히지 않았습니다. 대신 진단용 테스트 모드 `fabric-mixin-audit`가 Fabric `preLaunch`에서 Mixin이 준비한 모든 설정(`MixinProcessor.configs`, 반사로 읽음)의 대상 클래스를 초기화 없이 로드해 Mixin 자신이 적용하게 하고, 실패한 클래스를 알린 뒤 게임의 main이 돌기 전에 JVM을 멈춥니다. 클라이언트 작업은 `--accessToken` 없이 시작해, 만에 하나 멈추지 않아도 게임의 옵션 해석에서 창보다 먼저 끝납니다. 세 버전의 Quilt 묶음에서 실패는 NeoFullbright 하나이고, 같은 Mixin을 쓰는 Fabric Loader·Quilt Loader에서도 같으므로 클라이언트 실행에서 뺍니다(`brokenOnQuiltClient`, `brokenUnderForge`와 같은 방식). 두 작업은 `compatibilityCorpus`에 들어갑니다.
- **클라이언트 실행의 명령줄.** 26.2 Quilt 묶음의 클라이언트는 명령줄이 Windows 한도를 넘어(`CreateProcess error=206`) 시작하지 못했습니다. `ClientWorldReloadTask`가 `java @인수파일`로 시작합니다(`JavaArgumentFileTest`). `corpusQuiltClient`는 버전마다 다른 폴더에 실행하고, 실패하면 클라이언트 출력의 마지막 줄들을 빌드 출력에 남깁니다.

### 문서(각 단계에서 함께)

- `site/docs/reference/error-codes.md`와 한국어판: `300..310`을 더하고, `111`·`114`의 설명에 Quilt 예를 더합니다.
- `docs/COMPATIBILITY.md`: Quilt 상태(Q3 통과 뒤 `Experimental`), 알려진 차이 목록(결과와 위험).
- `CHANGELOG.md`, 계획서 단계 표.
