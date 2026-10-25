# Fabric 완전 지원 계획 (2026-10-22 작성)

ADR-0020 이후 Fabric 모드는 Fabric Loader와 같은 방식(Mixin, 인터페이스 주입, 환경 제거, 진입점 시점)으로 실행되지만, 검증은 서버에만 있습니다. 이 계획은 "Fabric Loader에서 되는 모드는 Poly 모드 로더에서도 된다"를 주장할 수 있을 때까지 남은 일을 정합니다. 원칙은 **테스트 먼저**입니다. 각 단계는 실패하는 검증을 먼저 만들고, 그것을 통과시키는 순서로 진행합니다.

## 완료 기준

"Fabric 완전 지원"이라고 쓰려면 다음이 모두 참이어야 합니다.

1. 실제 인기 Fabric 모드 묶음이 **클라이언트**에서 월드에 들어가고, 틱이 돌고, 저장하고 나올 수 있다(지원하는 모든 Minecraft 버전).
2. 같은 묶음이 전용 서버 시작과 게임 테스트 서버를 통과한다(지금 26.2만).
3. 설치 프로그램으로 만든 런처 프로필에서 Fabric 모드가 있는 채로 게임이 시작된다.
4. Fabric Loader 공개 API에서 Poly 모드 로더가 거부하는 것이 없다(`getGameInstance`, class tweaker `extend-enum`).
5. 막히는 Fabric 모드는 Fabric Loader에서도 막히는 경우뿐이다(같은 ID, 다른 쪽 전용, Fabric Loader 내부 클래스).
6. Fabric 모드를 넣은 상태의 서버 틱·시작 시간을 측정해 문서에 남긴다.

## 판정 (2026-10-22): 완료

여섯 기준을 모두 26.1과 26.2에서 확인했습니다. 근거는 [`COMPATIBILITY.md`](../COMPATIBILITY.md)의 "Fabric 완전 지원 점검"입니다.

1. `corpusFabricClient`: 실제 모드(26.2는 138개, 26.1은 126개)와 함께 저장된 월드에 들어가 200틱, "저장 후 타이틀로", 클라이언트 종료(`fabric-client-session`).
2. `corpusFabricServer`(26.2 100개, 26.1 93개), `corpusFabric`(98개, 91개).
3. `launcherProfileTest`: 설치 jar로 만든 프로필이 `fabric-mixin`을 넣은 채로 월드에 들어감.
4. F4·F5.
5. 남은 차단은 `117`(묶음 안의 여러 로더용 사본), `108`(서버의 클라이언트 전용), `114`(26.1에서 Minecraft 26.1.2나 더 새 Sodium 요구)뿐입니다. Fabric Loader 내부 클래스는 실제 모드가 쓰는 것만 제공하고, 나머지는 `111`로 막습니다(Fabric Loader 밖에서는 없는 것).
6. [`PERFORMANCE.md`](../PERFORMANCE.md) 측정 5.

"완전 지원"은 이 검증 범위의 주장입니다. 모드마다 기능을 하나하나 써 본 것은 아니고, 렌더링 결과를 사람이 보지 않았습니다. Fabric 모드와 Forge·NeoForge 모드를 함께 실행하는 것은 Forge 계획(G8)에서 다룹니다.

## 단계

| # | 일 | 검증(먼저 만듦) | 상태 |
|---|---|---|---|
| F1 | 클라이언트에서 실제 Fabric 모드 실행 | `corpusFabricClient`(창이 열림): 저장된 월드에 들어가 200틱, 저장 후 종료 | **완료** (2026-10-22, 26.2 140개·26.1 128개, 테스트 모드 2개 포함) |
| F2 | 26.1 계열 실제 모드 묶음 | `compat/mod-corpus-26.1.json`을 Modrinth에서 만들고(`-PmodCorpusRefresh -PpolyMinecraft=26.1`) `compatibilityCorpus` | **완료** (서버 시작 88개, 게임 테스트 서버 86개). 클라이언트는 `corpusFabricClient -PpolyMinecraft=26.1` |
| F3 | 런처 프로필 + Fabric 모드 | `launcherProfileTest`에 Fabric 모드 하나(fabric-mixin)를 넣고 실행 | **완료** (2026-10-22) |
| F4 | `FabricLoader.getGameInstance()` | 클라이언트는 `Minecraft`, 서버는 `runServer`가 시작된 서버. `FabricLoaderRuntimeTest`, `fabricMixin`·`corpusFabricClient` 기대 줄 | **완료** (2026-10-22) |
| F5 | class tweaker `extend-enum` | Fabric이 쓰는 class-tweaker 0.3.0 `EnumExtensionClassVisitor`를 옮김(빈 상수 + `StubEnumConstant`, 값은 모드의 Mixin이 넣음). `EnumExtenderTest`, `fabricMixin` 기대 줄. `127` 폐기 | **완료** (2026-10-22) |
| F6 | Poly 모드 ↔ Fabric 모드 초기화 순서 | Poly 모드가 "외부 모드 초기화 뒤" 시점을 받을 공개 이벤트 설계(ADR) | **완료** (2026-10-22, [ADR-0022](../adr/0022-mods-initialized-event.md) `LifecycleEvents.MODS_INITIALIZED`. `dependencyCrossLoader`에서 `native_addon`이 이 이벤트로 초기화된 Fabric 라이브러리를 봄) |
| F7 | 성능 | `tickBenchmark`를 Fabric 묶음으로, 시작 시간 비교. `docs/PERFORMANCE.md` | **완료** (2026-10-22, [측정 5](../PERFORMANCE.md): `startupBenchmarkFabric`·`tickBenchmarkFabric`. 시작 +9.0초, GC 직후 힙 +41 MiB. 틱은 묶음의 Lithium이 적용돼 바닐라보다 짧음. Fabric Loader와의 비교는 아직 없음) |
| F8 | 문서·웹사이트 | 호환성 상태, 오류 코드, 첫 화면 카드 | 단계마다 |

## 완료 기준 5 점검 (2026-10-22)

26.2 묶음에서 직접 설치한 Fabric 모드 중 막히던 것을 하나씩 확인했습니다.

- `customskinloader-bootstrap`: 막힌 이유가 둘이었고, 둘 다 Poly 모드 로더가 Fabric Loader보다 엄격했던 경우입니다.
  - Fabric Loader 내부 `FabricLauncherBase.getLauncher().addToClassPath`(`111`): 이제 제공합니다.
  - 예전 LaunchWrapper 참조(`112`): 1.6–1.12용 코드 경로이고 게임 클래스가 아니라 `113`으로 바꿨습니다.
  - 이제 클라이언트 판정에서 로드됩니다. 실제 클라이언트 실행 확인은 `corpusFabricClient`에서 합니다.
- 26.1 묶음의 `veinminer`: `((FabricLoaderImpl) FabricLoader.getInstance()).getGameProvider().getRawGameVersion()`(`111`). `GameProvider`의 게임 설명 메서드와 `getGameProvider`를 제공해 이제 로드됩니다. 일부만 제공하는 내부 클래스는 링크 검사가 멤버까지 확인합니다.
- 26.1 묶음의 나머지: `reeses-sodium-options`(sodium 0.9.1 이상 필요, 0.8.9 설치), `jei`(안의 `mezz_config`가 Minecraft 26.1.2 요구). 둘 다 Fabric Loader도 막습니다.
- 같은 ID(`117`): 같은 모드의 여러 로더용 jar가 묶음에 함께 들어 있어서입니다. Fabric Loader도 같은 ID를 둘 다 로드하지 않습니다.
- 서버의 클라이언트 전용 모드(`108`): Fabric Loader도 로드하지 않습니다.

## F1에서 지금까지 찾은 것

- **문맥 클래스로더가 늦게 설정됨(고침).**
  - 증상: Sodium의 `preLaunch`가 ImmediatelyFast의 Mixin 설정 플러그인을 부르고, 플러그인이 `ServiceLoader.load(Class)`로 서비스를 찾다가 "No implementation found"로 시작이 멈췄습니다.
  - 원인: Poly 모드 로더는 문맥 클래스로더를 `preLaunch` 뒤에 게임 클래스로더로 바꿨습니다. Fabric의 Knot은 Mixin 시작 전에 바꿉니다.
  - 고침: 게임 클래스로더를 만든 직후 설정합니다(`PolyRuntime`).
- **테스트 모드가 실제 모드와 충돌(테스트를 고침).** Lithium의 `math.sine_lut` Mixin은 `Mth.SIN` 표를 없앱니다. Fabric Loader에서도 같습니다. `fabric-mixin` 테스트 모드가 이 경우를 받아들이게 했습니다.

## 순서와 시간

창이 열리는 F1·F3은 사람이 PC를 쓰지 않을 때 하나씩 실행합니다(AGENTS.md: GPU 드라이버 오류로 재부팅된 적이 있음). 헤드리스 검증(F2, F4, F5)은 그 사이에 진행합니다.
