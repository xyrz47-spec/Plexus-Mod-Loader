# poly-game-v26_2 — Minecraft 26.2 Game Implementation Layer

`poly-game-v26_2`는 Minecraft 26.2 버전에 직접 바인딩되어, `poly-game-api`의 Native API 선언을 실제 마인크래프트 게임 엔진 객체로 인스턴스화하고 바이트코드 훅(Hook)과 커스텀 렌더링 UI를 실행하는 게임 레이어 모듈입니다.

---

## 1. 주요 역할 및 구성 요소

### 1) Native API 객체 인스턴스화
- `ContentInstaller`: 모드가 선언한 `PolyBlock`, `PolyItem`을 마인크래프트 원본 레지스트리(`BuiltInRegistries`)에 직접 등록.
- `Views`: 마인크래프트 바닐라 `ServerLevel`, `Entity`, `ServerPlayer` 객체를 안전한 `WorldView`, `PlayerView` 불변 인터페이스로 래핑.
- `commands.CommandInstaller`: 바닐라 Brigadier 커맨드 디스패처에 플랫폼 중립적 커맨드 트리 등록.

### 2) 인게임 훅 (Runtime Hooks)
- `LifecycleHooks`: 서버 틱, 레벨 로드/언로드, 엔티티 스폰 및 플레이어 리스폰 이벤트 가로채기.
- `InteractionHooks`: 블록 파괴, 아이템 사용, 엔티티 우클릭 상호작용 콜백 처리.
- `HostPayloadHooks`: Forge/NeoForge와 Fabric의 커스텀 네트워크 페이로드가 단일 네트워크 채널에서 충돌하지 않도록 디코딩 및 라우팅 조율.

### 3) 로딩 화면과 창 제목 (`PolyLoadingOverlay`, `PolyWindowTitle`)
- 게임의 로딩 화면(`LoadingOverlay`)은 그대로 둡니다(Mojang 빨간 배경 또는 단색 로고 설정의 검은 배경, Mojang Studios 로고, 진행 막대, 페이드). `poly:client_loading_overlay`가 `extractRenderState` 끝에서 Poly 표시를 같은 페이드로 덧그립니다: 오른쪽 아래의 회전하는 금색 큐브(`poly_cube.png`, 32×32 프레임 24개)와 `POLY MOD LOADER`, 진행 막대 아래의 로더별 모드 수(`MODS: FABRIC 34 · NEOFORGE 53`).
- 글자는 게임 글꼴이 아니라 5×7 픽셀 글자로 그립니다. MinecraftForge의 첫 로딩 화면은 게임이 글꼴을 읽기 전입니다.
- MinecraftForge의 `ForgeLoadingOverlay`도 같은 화면을 그리므로 같은 표시가 붙습니다. Forge·NeoForge 초기 로딩 창의 큐브와 제목은 `poly-bridge-forge`의 `EarlyScreenArt`, `EarlyWindowTitle`이 맡습니다.
- `poly:client_window_title`: 창 제목을 로더와 상관없이 `Minecraft* Poly 26.2`(뒤의 ` - Singleplayer` 등은 게임 그대로)로 씁니다.

---

## 2. 클래스로더 분리 원칙

- 이 모듈의 클래스들은 컴파일 시 Minecraft 26.2 바닐라 코드에 의존하지만, **게임 클래스로더(`GameClassLoader`)**에 의해서만 로드됩니다.
- 부트스트랩 클래스로더나 런타임 코어 모듈이 이 모듈의 클래스를 직접 정적으로 참조할 수 없으며, 버전 어댑터 계약을 통해서만 호출됩니다.
- 클라이언트 하위 패키지(`client.*`)는 서버 환경에서 참조되지 않도록 분리되어 있습니다.

---

## 3. 검증 및 테스트 (Gradle Tasks)

```powershell
# 변환 규칙 무결성 검증 (BuiltInRulesTest)
.\gradlew.bat :poly-game-v26_2:check

# 26.2 게임 런타임 네이티브 API 통합 테스트
.\gradlew.bat :poly-bootstrap:nativeApiGameTests
```
