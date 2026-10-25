# ADR-0009: 로더 브리지 — 진단 우선, Fabric 엔트리포인트 경로부터

- 상태: 채택 (2026-10-19)
- 관련: 설계서 §11 단계 5, §13, Phase 5

## 결정
1. **브리지는 SPI입니다.**
   - `poly-bridge-api`의 `ModLoaderBridge`를 `ServiceLoader`로 찾습니다.
   - 브리지는 `assess`(메타데이터 → `BridgeAssessment`)와 `invokeEntrypoint`를 제공합니다.
   - 런타임은 브리지가 없는 로더의 jar를 `POLY-COMPAT-001`로 차단합니다.
2. **진단 우선입니다.** 브리지는 Poly 모드 로더가 제공하지 못하는 기능을 기능 하나당 `CompatibilityFinding` 하나로 보고합니다. 차단(blocking) 발견이 하나라도 있으면 상태는 `BLOCKED`이고, 모드를 로드하지 않습니다. 그 외에는 `EXPERIMENTAL`입니다.
   - Fabric `POLY-COMPAT-100..108`: 메타데이터, Mixin, class tweaker, 중첩 jar, Fabric API 및 다른 모드 의존, `preLaunch`, 기본이 아닌 언어 어댑터, 기타 엔트리포인트(경고), 실행 환경
   - FML(NeoForge, MinecraftForge) `POLY-COMPAT-200..204`: 메타데이터, FML 언어 공급자, Mixin, Access Transformer, 의존성(경고)
3. **Fabric 1단계: 엔트리포인트만 쓰는 모드를 실행합니다.**
   - `net.fabricmc:fabric-loader`(Apache-2.0)의 `net.fabricmc.api` 인터페이스만 클래스 경로에 둡니다. Fabric Loader 자체는 시작하지 않습니다.
   - 모드 클래스는 Poly 모드 로더 게임 클래스로더가 정의합니다.
   - 호출 순서는 Fabric과 같습니다. 모든 `main`을 먼저 호출하고, 그다음 `client` 또는 `server`를 호출합니다.
4. **FML 계열은 진단만 합니다.** `@Mod` 생성자에 넘길 이벤트 버스, 레지스트리 생명주기, 클래스 처리기 그래프가 없기 때문입니다.

## 근거(실험, 2026-10-19)

| jar | 결과 |
|---|---|
| `test-mods/fabric-entrypoint-only`(`fabric_hello`) | `EXPERIMENTAL`. 26.2 클라이언트·게임 테스트 서버에서 `main`·`client` 엔트리포인트 실행, 클래스로더 `poly-game` |
| Fabric API 0.161.0+26.2 | `BLOCKED`: `POLY-COMPAT-103` 중첩 jar 44개(Mixin은 중첩 jar 안에 있음) |
| NeoForge 26.2.0.93 | `BLOCKED`: `201` FML, `202` Mixin 1개, `203` AT 2개 |
| MinecraftForge 65.1.5 | `BLOCKED`: `201` FML. 메타데이터 버전이 `${global.forgeVersion}` 플레이스홀더(FML이 실행 중 채움) |

## 다음 단계 (우선순위)
1. Fabric 중첩 jar 해제(재귀 분석). Fabric API의 각 하위 모듈이 무엇을 요구하는지 정확히 보고하기 위해서입니다.
2. sponge-mixin(`net.fabricmc:sponge-mixin`)을 Poly 모드 로더 클래스로더의 `IMixinService`로 연결해 소형 Mixin 모드 하나를 적용하는 실험
3. `FabricLoader.getInstance()`의 최소 구현(모드 목록, 환경, 설정 디렉터리)
