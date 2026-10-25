# poly-bridge-fabric

## 이 모듈이 하는 일
Fabric 모드를 Fabric Loader에서와 같은 방식으로 실행합니다([ADR-0020](../docs/adr/0020-fabric-mixin-and-runtime-fidelity.md)). Quilt 모드(`quilt.mod.json`)도 같은 실행 장치로 Quilt Loader에서와 같은 뜻으로 실행합니다([ADR-0027](../docs/adr/0027-quilt-mods-on-poly-mod-loader.md)).

- `fabric.mod.json`을 분석합니다. `depends`·`recommends`·`suggests`·`breaks`·`conflicts`·`provides`는 Fabric Loader의 버전 해석기(`VersionPredicate`)로 해석해 보고하고, 형식이 틀린 값은 예외 대신 `POLY-COMPAT-100`으로 보고합니다.
- `quilt.mod.json`은 형제 브리지 `QuiltBridge`가 Quilt Loader 0.31.0-beta.4의 검사 순서와 뜻(버전 지정, 의존 객체의 `optional`·`unless`·ANY/ALL, `provides` 버전, `load_type`)으로 분석합니다. 판정 번호는 `POLY-COMPAT-300..310`입니다.
- Fabric Loader의 공개 API(`FabricLoader.getInstance()`, ADR-0016)와 Quilt Loader의 공개 API(`QuiltLoader`, `MinecraftQuiltLoader`, ADR-0027 결정 4)를 제공합니다. 두 API는 진입점 인스턴스·공유 객체(`ObjectShare`)·게임 객체·폴더·실행 인수를 함께 씁니다(Quilt Loader에서와 같음).
- `accessWidener`(Fabric, 하나)와 `access_widener`(Quilt, 여럿)가 가리키는 class tweaker를 Fabric Loader 0.19.5와 같은 문법으로 읽습니다(`ClassTweakerFile`). 접근 줄은 접근 확대로 바꾸고(ADR-0018), `inject-interface` 줄은 인터페이스 주입으로 적용합니다.
- 게임 클래스로더에 Fabric 변환 단계(`FabricClassTransformer`)를 설치합니다. 순서는 Fabric Loader와 같습니다: 환경 제거(`EnvironmentStripper`) → 인터페이스 주입(`InterfaceInjector`) → Mixin. Fabric 모드나 Quilt 모드가 하나라도 로드되면 설치합니다.
- 환경 제거: Fabric 모드의 클래스는 Fabric Loader처럼 `@Environment`만 봅니다. Quilt 모드 jar의 클래스에는 Quilt Loader처럼 `@ClientOnly`·`@DedicatedServerOnly`(멤버, `implements`의 인터페이스, 클래스, `package-info`의 패키지)와 `@Requires`(로드된 모드·제공 ID·내장 ID에 없으면 제거)를 더 적용하고, `stripLambdas`면 제거한 메서드만 쓰는 람다 본문도 지웁니다.
- Mixin 0.8.7(FabricMC 0.17.4)과 MixinExtras 0.5.5를 시작합니다(`FabricMixinBootstrap`, `PolyMixinService`). Quilt 모드의 최상위 `mixin` 설정도 같은 Mixin에 넣고, 호환 수준은 Quilt Loader처럼 늘 0.10.0입니다.
- 진입점을 Fabric Loader·Quilt Loader와 같은 때에 호출합니다. Mixin 준비 직후 Quilt 모드의 `pre_launch`(Quilt `PreLaunchEntrypoint`)를 먼저, 그다음 모든 Fabric·Quilt 모드의 `preLaunch`를 호출합니다(Quilt Loader의 `invokePreLaunch` 순서). `main`·`client`·`server`는 Minecraft 안의 초기화 지점에서 Fabric 인터페이스로 호출합니다. QSL 진입점(`init`·`client_init`·`server_init`)은 Quilt Loader처럼 부르지 않습니다(`303`).

## 무엇을 소유하는가
- `dev.polymodloader.compat.fabric`: `FabricBridge`, `ClassTweakerFile`, `InterfaceInjector`, `EnvironmentStripper`, `FabricClassTransformer`
- `dev.polymodloader.compat.fabric.mixin`: `FabricMixinBootstrap`, `PolyMixinService`, `MixinConfigEntry`, `MixinSupertypes`, `MixinCompatibility`, 호스트 로더(Forge, NeoForge)가 패치한 게임에 맞추는 `HostPatchAdjustments`(`HostAddedMembers`, `HostRenamedLocals`, `CoupledInjections`, `HostArguments`; 빠진 mixin이 더할 인터페이스·접근자를 확인 없이 쓰는 주입과, 호스트가 다른 생성자를 부르는 `NEW` 지점을 감싸는 주입은 실행하지 않음, ADR-0026 39; 호스트가 인자를 뒤에 더 넘기는 호출을 감싸는 주입은 `AppendedArguments` 다리로 실행, ADR-0026 40; 호스트가 하위 클래스의 재정의에서 `super`로 부르게 만든 메서드로의 `@Inject`는 가드 메서드가 바닐라의 수신 객체에서만 실행, `222`, ADR-0026 41; 호스트가 있을 때 Fabric 단계 뒤 호스트의 default와 모드의 default가 겹친 게임 인터페이스에 호스트 쪽을 부르는 default를 더하는 `HostInterfaceDefaults`, `223`, ADR-0026 42), 호스트의 Mixin 설정을 같은 Mixin에 더하는 `FabricMixinBootstrap.addShared`(NeoForge는 FML이 시작한 뒤 설정을 알려 주므로 그 묶음을 따로 선택함, `HostedMixinErrors`의 `220`)
- `dev.polymodloader.compat.fabric.loader`: `FabricLoaderRuntime`, `FabricFamilyMod`(Fabric·Quilt 모드의 Mixin 설정·class tweaker·진입점 선언을 한 모양으로), `JvmEntrypointValue`(두 기본 언어 어댑터가 함께 쓰는 생성 코드), `OfficialMappingResolver`
- `dev.polymodloader.compat.quilt`: `QuiltBridge`, `QuiltModJsonReader`, `QuiltModDescription`, `QuiltVersionSpecifier`, `QuiltDependencyReader`, Quilt Loader API 구현 `QuiltLoaderRuntime`·`QuiltModContainer`·`QuiltModMetadata`·`QuiltLoaderValue`·`QuiltOfficialMappingResolver`·`QuiltLogForwarder`, Fabric API에서 본 Quilt 의존 `QuiltFabricModDependency`
- Fabric Loader 내부 클래스 대체본 `net.fabricmc.loader.impl.FabricLoaderImpl`, `net.fabricmc.loader.impl.util.DefaultLanguageAdapter`
- Quilt Loader 내부 클래스 대체본 `org.quiltmc.loader.impl.QuiltLoaderImpl`(`INSTANCE`와 Quilt API가 부르는 멤버, 붙기 전에는 `Accessed QuiltLoader too early!`), `org.quiltmc.loader.impl.game.GameProvider`(게임 버전 두 메서드만 있는 인터페이스), `org.quiltmc.loader.impl.util.DefaultLanguageAdapter`

## 무엇을 하지 않는가
- Fabric Loader 자체(Knot)와 Quilt Loader 자체는 시작하지 않습니다. 게임 클래스는 Poly 모드 로더 게임 클래스로더가 정의합니다.
- class tweaker의 `official`이 아닌 이름 공간은 막습니다(`126`). `extend-enum`은 Fabric Loader처럼 빈 enum 상수를 더합니다(`EnumExtender`).
- Fabric Loader 내부 클래스(`net.fabricmc.loader.impl.*` 중 대체본이 없는 것)와 Quilt Loader 내부 클래스(`org.quiltmc.loader.impl.*` 중 대체본이 아닌 것. 담은 도우미 클래스도 모드에는 내주지 않음), 담지 않은 Quilt API(`QuiltFileSystems`, `api.filesystem`·`api.config`·`api.gui`·`api.plugin`)는 제공하지 않습니다. 이것을 쓰는 모드와 대체본에 없는 멤버를 쓰는 모드는 링크 검사가 막습니다(`111`). `FabricLoader.getGameInstance()`·`QuiltLoader.getGameInstance()`는 버전 어댑터가 알려 주는 게임 객체를 돌려줍니다(클라이언트는 `Minecraft`, 서버는 `runServer`가 시작된 서버).
- Quilt loader plugin, Quilt 언어 어댑터, 변경 가능한 파일 시스템 오버레이는 제공하지 않습니다(`301`, `302`, `306`).
- 의존성의 최종 판정, 링크 검사, 중첩 jar 풀기, 진입점 호출 시점의 결정은 런타임(`poly-runtime-impl`)과 버전 어댑터가 합니다.

## 의존성
- `poly-bridge-api`, Gson, SLF4J
- Mixin(`net.fabricmc:sponge-mixin`, MIT), MixinExtras(`mixinextras-common`, MIT), ASM
- `net.fabricmc:fabric-loader`(Apache-2.0)는 컴파일에만 씁니다. 공개 API 클래스와 `LICENSE_fabric-loader`만 이 jar에 담습니다.
- `org.quiltmc:quilt-loader` 0.31.0-beta.4(Apache-2.0)는 jar 전체를 쓰지 않습니다. 공개 API(`org.quiltmc.loader.api`의 최상위 일부, `entrypoint`, `minecraft`)와 그 API가 닿는 순수 도우미(버전, 의존 객체, 라이선스, JSON 값, 로그) 90개 클래스, `quilt_loader/licenses.json`, `LICENSE_quilt-loader`만 `quiltLoaderApi` 작업으로 꺼내 컴파일과 이 jar에 씁니다. Quilt jar 안의 `net/fabricmc/**` 사본은 담지 않습니다.
- Fabric Loader·Quilt Loader 소스를 옮긴 파일은 머리에 출처를 적었습니다(`THIRD_PARTY_NOTICES.md`).

## 빌드·테스트
```powershell
.\gradlew.bat :poly-bridge-fabric:check
.\gradlew.bat :poly-bootstrap:test --tests '*QuiltLoaderApiLinkTest'   # 실제 브리지 jar로 Quilt API 링크 검사·담은 클래스
.\gradlew.bat :poly-bootstrap:fabricMixin           # Mixin·MixinExtras·인터페이스 주입을 실제 26.2 서버에서
.\gradlew.bat :poly-bootstrap:compatibilityCorpus   # 실제 인기 모드 (네트워크 필요)
```

## 대표 흐름
1. 검색 단계에서 `assess`가 메타데이터, Mixin 설정, class tweaker를 판정합니다(`fabric.mod.json`은 `FabricBridge`, `quilt.mod.json`은 `QuiltBridge`).
2. 런타임이 로드할 모드를 정하고 게임 클래스로더를 만듭니다.
3. `FabricBridge.prepareLaunch`가 Fabric Loader API를 붙이고, 그 위에 Quilt Loader API(`QuiltLoaderImpl.INSTANCE`)를 붙입니다. Quilt 모드가 없어도 붙입니다.
4. `FabricBridge.classTransformer`가 환경 제거·인터페이스 주입·Mixin 단계를 설치합니다.
5. `FabricBridge.beforeGame`이 Quilt 모드의 `pre_launch`, 그다음 `preLaunch`를 호출합니다. `QuiltBridge`의 이 세 메서드는 기본값입니다.
6. 게임이 초기화 지점에 이르면 런타임이 `invokeEntrypoint`로 `main`과 `client`/`server`를 호출합니다. 두 브리지 모두 `FabricLoaderRuntime.invokeModEntrypoint`를 씁니다.

## 알려진 제한
[ADR-0020](../docs/adr/0020-fabric-mixin-and-runtime-fidelity.md)과 [ADR-0027](../docs/adr/0027-quilt-mods-on-poly-mod-loader.md)의 "결과와 위험", [호환성 문서](../docs/COMPATIBILITY.md)를 보세요. Quilt에서는 특히:

- `fabric.mod.json`과 `quilt.mod.json`을 다 든 jar는 Fabric 모드로 실행합니다(`310`). Quilt Loader는 `quilt.mod.json`을 씁니다.
- Mixin 설정 이름은 Fabric처럼 이름 하나당 모드 하나입니다(`134`). Quilt Loader는 모드 ID를 접두사로 붙입니다.
- 호스트(Forge)가 실행하는 모드도 `QuiltModContainer.rootPath()`로 jar 루트를 줍니다. jar가 없는 내장 모드(`java`, `quilt_loader`)의 루트는 캐시 폴더 아래의 빈 폴더입니다.
- `QuiltLoader.getMappingResolver()`는 `official` 항등 변환이고, `ReflectiveFixer`는 26.x에서 할 일이 없어 두지 않습니다.
