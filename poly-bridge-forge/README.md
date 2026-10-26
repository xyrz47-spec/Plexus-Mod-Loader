# poly-bridge-forge — MinecraftForge & NeoForge Compatibility Bridge

`poly-bridge-forge`는 **MinecraftForge**와 **NeoForge** 모드를 Poly Mod Loader의 단일 JVM 런타임 위에서 가상화하고, Fabric 및 Quilt 모드와 공존할 수 있도록 바이트코드와 FML 서비스 계층을 조율하는 핵심 호환성 브릿지 모듈입니다.

---

## 1. 주요 역할 및 설계 아키텍처

### 1) FML(Forge Mod Loader) 런타임 가상화
Forge와 NeoForge는 고유한 클래스로더 구조(ModLauncher 및 FML 모듈 레이어)를 전제하고 설계되었습니다. Poly는 이를 다음과 같은 방식으로 가상화합니다:
- **`cpw.mods.modlauncher` 계약 구현:**
  - ModLauncher(LGPL) 코드를 복제하지 않고, 공개 인터페이스 계약을 자체 구현한 경량 가상화 서비스(`src/forgeHost`, `forge-host.jar`)를 제공합니다.
  - FML의 BOOT, SERVICE, PLUGIN, GAME 레이어를 Poly의 단일 `PolyTransformingClassLoader` 위에 질서 있게 바인딩합니다.
- **NeoForge FML 11 호스팅:**
  - `neoforge-host.jar`(`src/neoForgeHost`)의 `PolyFml`을 통해 `FMLLoader`의 게임 레이어 생성과 모드 검색을 인게임 바이트코드 변환 체인에 직접 통합합니다.

### 2) Fabric / Quilt 모드와의 단일 게임 공존
- **통합 Mixin 엔진 (`FmlSharedMixin`):**
  - Forge/NeoForge가 별도의 Mixin 인스턴스를 시작하지 않도록 가로채고, FabricMC의 SpongePowered Mixin 0.8.7 엔진 하나가 모든 로더의 Mixin 구성을 통합 처리합니다.
- **레지스트리 동적 제어 (`NeoForgeRegistryOpening`):**
  - NeoForge는 부팅 초기에 레지스트리를 동결(`freeze`)합니다. Poly는 Fabric 모드 초기화 진입점이 실행되는 동안 루트 레지스트리를 일시적으로 안전하게 개방하여 `Registry is already frozen` 예외를 방지합니다.
- **호스트 패치 동적 적응 (`HostPatchAdjustments` & `NeoForgeInjectionPoints`):**
  - Forge나 NeoForge가 바닐라 클래스를 바이너리 패치하여 기존 Mixin의 인젝션 지점(Method Call, Field Access 등)이 이동한 경우, 런타임에 바이트코드 주입 대상을 올바른 대체 메서드로 리타겟합니다.
  - `fabric-item-api-v1`, `fabric-registry-sync-v0`, `fabric-content-registries-v0` 등 핵심 Fabric API 모듈들이 호스트 환경에서 무수정으로 동작합니다.

### 3) 상호 모드 인식 (Cross-Loader Mod Visibility)
- **`ForeignModList` / `ForeignModIndex`:**
  - Fabric 모드가 `FabricLoader.getInstance().isModLoaded(...)`로 Forge 모드를 조회할 수 있고,
  - 반대로 Forge/NeoForge 모드가 `ModList.get().isLoaded(...)`로 Fabric/Quilt 모드를 조회할 때 올바른 `IModInfo` 메타데이터를 응답합니다.

---

## 2. 주요 구성 요소

| 패키지 / 클래스 | 역할 |
| :--- | :--- |
| `dev.polymodloader.compat.forge.NeoForgeBridge` | NeoForge 환경 감지, 설치 파일 다운로드 및 생명주기 관리 |
| `dev.polymodloader.compat.forge.NeoForgeGameHost` | NeoForge FML 11 모듈 레이어 정의 및 변환 타겟 관리 |
| `dev.polymodloader.compat.forge.NeoForgeInjectionPoints` | NeoForge 패치로 변경된 바닐라 메서드 주입점 대체 규칙 모음 |
| `dev.polymodloader.compat.forge.MinecraftForgeBridge` | MinecraftForge 바이너리 패치 세트 적용 및 호스트 셋업 |
| `dev.polymodloader.compat.forge.ForgeInjectionPoints` | MinecraftForge 패치 대응 주입점 대체 규칙 모음 |
| `dev.polymodloader.compat.forge.ForeignModIndex` | 타 로더의 모드 메타데이터를 FML SPI 형식으로 변환 |

---

## 3. 검증 및 테스트 (Gradle Tasks)

```powershell
# 단위 테스트 및 주입점 대체 규칙 검증
.\gradlew.bat :poly-bridge-forge:check

# Forge 26.2 + Poly 네이티브 모드 통합 게임 테스트
.\gradlew.bat :poly-bootstrap:forgeGameTest

# NeoForge 26.2 + 네이티브 모드 헤드리스 게임 테스트
.\gradlew.bat :poly-bootstrap:neoForgeGameTest

# Forge + Fabric 복합 인기 모드 묶음(Corpus) 헤드리스 검증
.\gradlew.bat :poly-bootstrap:corpusFabricForge
```

---

## 4. 제약 및 주의사항

- **Forge와 NeoForge 동시 구동 불가 (`POLY-COMPAT-221`):**
  MinecraftForge와 NeoForge는 바닐라 바이트코드 패치 및 핵심 클래스 상속 구조가 서로 상충하므로, 두 로더의 모드가 동시에 투입된 경우 Forge가 우선 구동되고 NeoForge 모드는 명확한 사유와 함께 안전하게 제외됩니다.
- **원격 다운로드 무결성:**
  Forge 및 NeoForge 원본 jar와 FML 의존성 라이브러리는 저작권 보호를 위해 저장소에 포함되지 않으며, 사용자 환경에서 최초 실행 시 공식 Maven에서 받아 SHA-1 체크섬을 엄격히 검증합니다.
