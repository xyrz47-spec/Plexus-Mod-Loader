# plexus-bridge-fabric — Fabric & Quilt Compatibility Bridge

`plexus-bridge-fabric`는 **Fabric** 모드와 **Quilt** 모드를 Poly Mod Loader 위에서 실제 Fabric Loader / Quilt Loader와 100% 동일한 의미론과 런타임 신뢰성으로 구동할 수 있도록 지원하는 브릿지 모듈입니다.

---

## 1. 주요 역할 및 설계 아키텍처

### 1) 완벽한 메타데이터 분석 및 버전 해석
- **Fabric 모드 (`fabric.mod.json`):**
  - 공식 Fabric Loader의 버전 명세서(`VersionPredicate`) 엔진을 내장하여 `depends`, `recommends`, `breaks`, `conflicts`, `provides` 관계를 엄격하게 해석합니다.
  - 잘못된 형식의 메타데이터는 무단 예외 대신 `PLEXUS-COMPAT-100`으로 정밀 보고합니다.
- **Quilt 모드 (`quilt.mod.json`):**
  - Quilt Loader 0.31.0 명세를 준수하여 다단계 의존성(`optional`, `unless`, `load_type` 등)을 분석합니다 (`PLEXUS-COMPAT-300..310`).
  - `fabric.mod.json`과 `quilt.mod.json`을 동시에 포함하는 모드는 Fabric 모드로 자동 판정하여 안정적으로 로드합니다.

### 2) 공개 Loader API 에뮬레이션
- `FabricLoader.getInstance()` 및 Quilt의 `QuiltLoader`, `MinecraftQuiltLoader` 공개 API를 기본 제공합니다.
- 게임 객체 조회, 공유 객체 저장소(`ObjectShare`), 설정 디렉터리(`config/`), 진입점 컨테이너를 실제 로더와 완벽히 호환되는 상태로 매핑합니다.

### 3) 바이트코드 변환 파이프라인 (`FabricClassTransformer`)
모드 클래스가 로드될 때 Fabric Loader와 동일한 정밀 순서로 변환을 적용합니다:
1. **환경 스트리핑 (`EnvironmentStripper`):**
   - `@Environment(EnvType.CLIENT/SERVER)` 어노테이션을 검사하여 반대편 사이드(Side)의 메서드/필드를 안전하게 제거합니다.
   - Quilt 모드의 경우 `@ClientOnly`, `@DedicatedServerOnly`, `@Requires` 어노테이션까지 폭넓게 처리합니다.
2. **인터페이스 주입 (`InterfaceInjector`):**
   - `accessWidener`에 정의된 `inject-interface` 구문을 파싱하여 대상 마인크래프트 클래스에 인터페이스 구현을 주입합니다.
3. **통합 Mixin 엔진 (`PlexusMixinService`):**
   - SpongePowered Mixin 0.8.7 (FabricMC 포크) 및 MixinExtras 0.5.5를 단일 서비스로 가동합니다.
   - Quilt 모드의 mixin 구성도 함께 바인딩하여 단일 패스(Single-pass)로 클래스를 변환합니다.

### 4) 진입점 생명주기 관리
- **`pre_launch` / `preLaunch`:** Mixin 준비 직후 및 게임 초기화 직전에 호출.
- **`main` / `client` / `server`:** 마인크래프트 내부 초기화 지점에서 각 로더 인터페이스 계약에 맞추어 호출.

---

## 2. 주요 패키지 및 클래스

| 패키지 | 주요 클래스 | 역할 |
| :--- | :--- | :--- |
| `dev.plexusmodloader.compat.fabric` | `FabricBridge`, `ClassTweakerFile`, `FabricClassTransformer` | Fabric 모드 검색 및 변환 파이프라인 관리 |
| `dev.plexusmodloader.compat.fabric.mixin` | `FabricMixinBootstrap`, `PlexusMixinService`, `HostPatchAdjustments` | 통합 Mixin 구동 및 호스트 패치 자동 적응 |
| `dev.plexusmodloader.compat.fabric.loader` | `FabricLoaderRuntime`, `OfficialMappingResolver` | Fabric Loader 공개 API 런타임 구현 |
| `dev.plexusmodloader.compat.quilt` | `QuiltBridge`, `QuiltModJsonReader`, `QuiltLoaderRuntime` | Quilt 메타데이터 파서 및 Quilt Loader API 구현 |

---

## 3. 검증 및 테스트 (Gradle Tasks)

```powershell
# 단위 테스트 및 Quilt API 링크 검증
.\gradlew.bat :plexus-bridge-fabric:check

# 실제 26.2 서버 환경에서 Mixin, MixinExtras, 인터페이스 주입 검증
.\gradlew.bat :plexus-bootstrap:fabricMixin

# 대규모 실전 인기 Fabric 모드 묶음(Corpus) 헤드리스 검증
.\gradlew.bat :plexus-bootstrap:corpusFabric
```

`plexus-bootstrap`의 작업들은 `test-mods/`(와 모드 묶음 목록 `plexus-bootstrap/compat/`)가 필요해서, 이 공개 저장소에서는 실행되지 않습니다.

---

## 4. 제약 사항
- Quilt Standard Libraries (QSL)는 마인크래프트 26.x 대응 릴리즈가 존재하지 않으므로, QSL에 직접 의존하는 모드는 시작 전 명확한 사유(`PLEXUS-COMPAT-303`)와 함께 제외됩니다.
- 모드가 Fabric Loader나 Quilt Loader의 비공개 내부 패키지(`net.fabricmc.loader.impl.*` 등)에 직접 접근하는 경우 링크 검증 단계(`PLEXUS-COMPAT-111`)에서 거부될 수 있습니다.
