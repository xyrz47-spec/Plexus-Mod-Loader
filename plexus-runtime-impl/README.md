# poly-runtime-impl — Core Loading Pipeline & Runtime Coordinator

`poly-runtime-impl`는 Poly Mod Loader의 핵심 두뇌 역할을 수행하며, 모드 탐색부터 의존성 해석, 크로스 로더 충돌 분석, 클래스로더 구성 및 게임 실행까지의 **전체 8단계 로딩 파이프라인**을 관장하는 모듈입니다.

---

## 1. 8단계 런타임 생명주기 파이프라인

```
1. [Launch Config]       -> CLI 인수, JVM 속성, 모드 폴더 경로 확정
          │
          ▼
2. [Mod Discovery]       -> mods/ 디렉터리 내 jar 및 중첩(Nested) jar 탐색
          │
          ▼
3. [Metadata Parsing]    -> poly, fabric, quilt, forge, neoforge 메타데이터 파싱
          │
          ▼
4. [Dependency Solver]   -> 버전 제약식, 필수 종속성, 순환 참조 검증
          │
          ▼
5. [Cross-Loader Compat] -> HostPatchCheck (호스트 패치 충돌 및 누락 메서드 검사)
          │
          ▼
6. [Transform Planning]  -> ASM 바이트코드 변환 계획 및 디스크 캐시 생성
          │
          ▼
7. [GameClassLoader]     -> TransformingClassLoader 및 FML/Fabric 브릿지 초기화
          │
          ▼
8. [Entrypoints & Launch]-> 로더별 진입점(Pre-launch, Main 등) 순차 실행 후 게임 진입
```

---

## 2. 주요 패키지 및 컴포넌트

| 패키지 | 핵심 클래스 | 역할 |
| :--- | :--- | :--- |
| `dev.plexusmodloader.runtime` | `PolyRuntime`, `LaunchConfiguration` | 런타임 부팅 오케스트레이션 및 상태 관리 |
| `dev.plexusmodloader.runtime` | `LoadedModCounter` | 실시간 로더별 모드 수 집계 |
| `.discovery` | `ModFileScanner`, `NestedJarExtractor`, `ForeignModResolver` | 모드 폴더의 jar 탐색, 중첩 jar 추출, 로더 판별 |
| `.dependency` | `ModDependencyResolver` | 모드 버전 제약식 해석 및 로드 우선순위 정렬 |
| `.compat` | `HostPatchCheck`, `CrossLoaderModResolver` | Forge/NeoForge 패치와 Fabric Mixin 간 충돌 사전 진단 |
| `.classloading` | `GameClassLoader` | 단일 게임 클래스로더 관리 및 미사용 로더 API 은닉(`hideFromParent`) |
| `.lifecycle` | `EntrypointInvoker`, `ShutdownCoordinator` | 엔트리포인트 호출 및 JVM 종료 시 종료 작업 실행 |

---

## 3. 핵심 기술 특성

### 1) 미사용 로더 API 격리 (`hideFromParent`)
모드팩에 Fabric 모드가 전혀 없고 Forge 모드만 존재하는 경우, 클래스패스에 잔존하는 `net.fabricmc.api.*` 클래스 때문에 멀티로더용 모드가 잘못된 로더 환경으로 오판하는 것을 방지합니다. Poly는 활성 모드가 없는 로더의 브릿지 패키지를 `GameClassLoader` 상에서 선별적으로 마스킹합니다.

### 2) 실시간 로더별 모드 수 집계 (`LoadedModCounter`)
게임 로딩 및 타이틀 화면, 그리고 커스텀 로딩 화면(`PolyLoadingOverlay`)에 정확한 모드 수를 전달하기 위해 각 로더별(`Poly`, `Fabric`, `Forge`, `NeoForge`, `Quilt`) 실질 구동 모드 수를 중복 없이 실시간 계산합니다.

### 3) 사전 링크 검증 (`ModLinkVerifier`)
로드 대상 모드들의 상수 풀(Constant Pool)을 조사하여 마인크래프트 또는 상위 라이브러리에 누락된 메서드나 필드를 참조하는 치명적 모드를 부팅 전에 찾아내어 크래시를 미연에 방지합니다.

---

## 4. 검증 및 테스트 (Gradle Tasks)

```powershell
.\gradlew.bat :poly-runtime-impl:check
```

- `ModDependencyResolverTest`: 복합 종속성 트리, 순환 의존성, 선택적 의존성 검증 12종.
- `CrossLoaderModResolverTest`: Forge + Fabric 모드 공존 시의 ID 매핑 및 브릿지 중복 해소 검증.
- `ModLinkVerifierTest`: 클래스 바이트코드 링크 무결성 검증 14종.
- `PolyRuntimeTest`: 합성 jar를 사용하여 1~8단계 전체 파이프라인의 엔드투엔드 부팅 검증.
