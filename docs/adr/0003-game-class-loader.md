# ADR-0003: 클래스 변환 개입점 — 자체 게임 클래스로더 (Java agent 아님)

- 상태: 채택 (2026-10-19)
- 관련: 설계서 §11, §12.2, §16 Phase 0 "클래스 변환 개입점 확인"

## 문제

Poly 모드 로더는 Minecraft 클래스가 정의되기 **전에** 바이트코드를 검증·변환해야 합니다. 동시에 Poly 모드 로더 자신, 모드, 게임 라이브러리가 어떤 클래스로더에 속할지 정해야 합니다.

## 고려한 선택지

1. **Java agent + `ClassFileTransformer`**
   - 장점: JVM 표준 API이고, 모든 로더의 클래스를 볼 수 있습니다.
   - 단점: `-javaagent` 실행 옵션이 필요합니다. JDK·라이브러리 클래스까지 변환 경로에 들어와 범위를 통제하기 어렵습니다. 이미 로드된 클래스의 구조 변경은 retransform 제약을 받습니다.
2. **자체 `URLClassLoader` 하위 클래스(채택)**
   - 장점: 게임 jar와 모드 jar만 이 로더가 정의하므로 변환 범위가 명확합니다. 부트스트랩 시점을 완전히 통제합니다.
   - 단점: 게임이 자기 클래스로더를 전제하는 코드(예: `ProtectionDomain`)와 호환되는지 확인해야 합니다.
3. 게임 jar를 미리 변환해 디스크에 쓰기 — 캐시 무효화가 복잡하고, 모드 조합마다 jar를 다시 만들어야 해서 제외했습니다.

## 결정

`GameClassLoader`(`poly-runtime-impl`)를 사용합니다.

- **JVM 클래스 경로:** Poly 모드 로더 모듈과 Minecraft 라이브러리(LWJGL, log4j, gson, netty …)
- **`GameClassLoader`:** 게임 jar + 해석된 순서의 모드 jar. 자기 jar에 있는 클래스는 child-first로 직접 정의하며, 그 전에 `ClassTransformExecutor`를 거칩니다.
- **부모 전용 패키지:** `java.`, `javax.`, `jdk.`, `sun.`, `com.sun.`, `dev.polymodloader.{api,bootstrap,game,internal,runtime}.`. 모드와 Poly 모드 로더가 같은 Poly 모드 로더 API 클래스를 공유하도록 하기 위해서입니다.
- **jar 소유:** 로더가 `JarFile`을 직접 열고 `close()`에서 닫습니다. `URL.openStream()`은 JDK 전역 jar 캐시에 파일을 남겨 Windows에서 jar 잠금을 일으켰습니다(테스트로 재현 후 수정).
- **변환 계획:** 로더 생성 전에 `ClassTransformPlanner`가 게임 jar의 바이트를 직접 읽어 확정합니다(§12.4 "클래스 정의 전에 계획").

## 검증

- 26.2 클라이언트: 타이틀 화면까지 실행했습니다. 스택 트레이스에서 게임 클래스가 `poly-game//` 로더 소속으로 표시됩니다.
- 26.2 전용 서버: `Bootstrap.bootStrap()`을 지나 EULA 확인까지 실행했습니다.
- `PolyRuntimeTest`: 합성 게임 jar로 로더 이름, 변환 반영, 엔트리포인트 호출을 단위 테스트합니다.

## 알려진 제한과 위험

- Fabric(Knot)과 NeoForge(FML `TransformingClassLoader` + JPMS 레이어)도 각자 `net.minecraft`를 정의하려 합니다. 한 JVM에서 두 로더가 게임 클래스를 동시에 소유할 수 없으므로, 브리지는 Poly 모드 로더 로더 위에서 그들의 변환 의미를 재현해야 합니다([COMPATIBILITY.md](../COMPATIBILITY.md)).
- 리소스 조회(`getResource`)는 아직 부모 우선입니다. 모드가 바닐라 리소스를 같은 경로로 덮어쓰는 경우는 검증하지 않았습니다.
- 서명된 jar의 `CodeSigner`는 보존하지 않습니다.
