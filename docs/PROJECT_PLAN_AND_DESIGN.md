# Poly Mod Loader
## 프로젝트 계획 및 통합 기술 설계서

- 문서 버전: 3.0
- 프로젝트명: **Poly 모드 로더**
- 식별자 접두사: `poly` (패키지 `dev.polymodloader`, 모듈 `poly-*`, 오류 코드 `POLY-`)
- 이름 상태: 2026-10-21에 Void Mod Loader(VML)에서 Poly 모드 로더로 바꿨다(ADR-0012).
- 프로젝트 상태: 기획·기술 검증 단계
- 이번 개정의 초점: 깊은 게임 내부 제어, 상세 API 분류, 확장 권한·안정성 규칙
- 개발 환경: **IntelliJ IDEA + Gradle 기반 멀티모듈 Java 프로젝트**
- 주 대상: Minecraft: Java Edition
- 대상 Minecraft 버전: **미확정 — Phase 0에서 한 버전을 선정해 고정한다.**
- 문서 역할: 개발자와 AI 코딩 에이전트가 프로젝트 목표, 구조, 명명 규칙, 우선순위 및 완료 기준을 동일하게 이해하기 위한 기준 문서

> **중요:** 이 문서는 계획과 설계의 기준이다. 여기 나온 모듈·API·성능 목표가 이미 구현되었다는 뜻은 아니다. 코드, 테스트, 벤치마크 결과가 존재하고 검증된 항목만 완료로 표시한다.

---

# 1. 프로젝트 한 문장 정의

**Poly 모드 로더는 Minecraft의 기본 실행 기반만 핵심으로 유지하고, 게임의 나머지 시스템은 명시적인 API·교체 가능한 시스템 구현체·클래스 변환을 통해 폭넓게 수정할 수 있도록 만드는 독자 Java 모드 로더이자 모딩 플랫폼이다. Poly 모드 로더 전용 모드에 일관된 고성능 개발 API를 제공하며, 장기 목표로 기존 Fabric·Quilt·NeoForge·Forge 모드도 동일한 게임 인스턴스에서 함께 실행할 수 있는 호환 계층을 구축한다.**

Poly 모드 로더는 단순한 모드 관리자나 런처가 아니다. 핵심 제품은 다음과 같다.

1. Minecraft 시작 과정에 연결되는 모드 로딩 런타임
2. 게임 시스템의 확장·교체·후킹을 관리하는 시스템 확장 구조
3. 클래스 변환 엔진과 변환 충돌 진단
4. Poly 모드 로더 전용 모드 API와 개발 SDK
5. Fabric 및 Forge 모드 호환 브리지
6. IntelliJ IDEA에서 개발·실행·디버깅·테스트할 수 있는 Gradle 프로젝트 구조
7. 호환성과 성능을 재현 가능하게 검증하는 자동화 테스트 및 벤치마크

런처 GUI는 나중에 추가할 수 있는 별도 구성 요소다. GUI 런처의 완성 여부가 핵심 런타임의 완성도를 대신하지 않는다.

# 2. 사용자가 원하는 최종 경험

Poly 모드 로더를 사용하는 개발자는 Minecraft를 단순히 몇 개의 레시피나 블록을 추가하는 수준에서 벗어나, 게임의 사실상 대부분의 기능과 동작을 수정할 수 있어야 한다.

예를 들어 다음과 같은 작업이 가능하도록 설계한다.

- 바닐라 블록과 아이템의 동작을 확장하거나 다른 구현으로 교체한다.
- 제작법, 인벤토리, 전리품, 상호작용, 전투, 이동과 물리 규칙을 수정한다.
- 엔티티의 상태·AI·행동·속성과 월드 내 처리 방식을 확장한다.
- 바이옴, 차원, 구조물, 청크 생성 및 월드 생성 단계를 수정한다.
- 게임 규칙, 틱 처리, 이벤트 순서, 시간 흐름을 제어한다.
- GUI, HUD, 메뉴, 입력 처리, 모델, 애니메이션, 셰이더와 렌더 패스를 교체하거나 추가한다.
- 사운드, 파티클, 리소스 관리 및 데이터 로딩 과정을 확장한다.
- 네트워크 패킷, 상태 동기화, 서버 권한 검사 및 저장 데이터 구조를 확장한다.
- 기본 API로 할 수 없는 작업은 명시적인 고급 API와 클래스 변환을 통해 게임 코드 수준에서 변경한다.
- 개발 중 가능한 변경은 핫 리로드하고, 구조 변경이나 클래스 재정의가 필요한 작업에는 재시작 필요 여부를 분명하게 표시한다.
- IntelliJ IDEA에서 코드 탐색, 자동완성, 리팩터링, 테스트 실행, 디버깅 및 Minecraft 실행을 할 수 있다.

핵심 방향은 **“바닐라 구현을 고정된 벽으로 취급하지 말고, 가능한 한 기본 구현체로 취급한다”**는 것이다. 바닐라 동작은 기본 제공 구현으로 남기되, Poly 모드 로더가 관리하는 확장 지점에서는 해당 구현을 교체하거나 다른 구현을 추가할 수 있도록 한다.

## 2.1 현실적인 범위 정의

“Minecraft의 전체적인 틀만 남기고 나머지는 모두 수정 가능하게 한다”는 목표는 모든 게임 코드가 무조건 안전하게 교체된다는 의미가 아니다. 정확한 목표는 다음과 같다.

- 게임 실행, 핵심 클래스, 기본 월드 및 게임 데이터와 연결되는 최소 기반은 유지한다.
- 게임플레이·콘텐츠·월드 생성·엔티티·렌더링·UI·오디오·네트워크 등 시스템별로 공식 확장 지점을 제공한다.
- API로 처리할 수 없는 영역에는 버전별 내부 구현에 접근하는 저수준 확장 지점을 제공한다.
- 저수준 확장 지점은 Minecraft 버전 업데이트에 영향을 받을 수 있으므로, 지원 버전과 실패 원인을 명시한다.
- 원본 Minecraft의 내부 구현을 변경하지 않아도 되는 설정/API 작업과, 클래스 변환이 필요한 작업을 구분한다.
- 각 시스템을 교체 가능한 단위로 설계하지만, 모든 시스템을 임의 조합해도 자동으로 호환된다고 약속하지 않는다.

# 3. 제품 목표와 우선순위

## 3.1 필수 목표

- Java 기반 모드 로더 런타임을 구축한다.
- Poly 모드 로더 전용 모드 형식과 API를 설계한다.
- 고수준 API와 저수준 게임 코드 확장 수단을 함께 제공한다.
- 게임의 주요 시스템을 “기본 구현 / 대체 구현 / 확장 후크” 패턴으로 접근 가능하게 한다.
- IntelliJ IDEA에서 Gradle 프로젝트를 직접 열고 빌드·테스트·실행·디버깅할 수 있게 한다.
- 패키지·클래스·메서드·오류 코드·문서의 명명 규칙을 일관되게 유지한다.
- 코드를 한눈에 훑었을 때 파일과 모듈의 책임이 드러나도록 구성한다.
- 기존 Fabric 및 Forge 모드와의 호환 브리지를 별도 모듈로 개발한다.
- 궁극적으로 Fabric + Quilt + NeoForge + Forge + Poly 모드 로더 Native 모드의 동일 인스턴스 혼합 실행을 목표로 한다. Quilt는 Fabric 모드도 로드하는 Fabric 계열이라 Fabric 브리지 위에 `quilt.mod.json`과 Quilt Standard Libraries 대응을 더하는 방식으로 다룬다(2026-10-22 추가).
- 성능 우위는 FPS, 프레임 시간, 틱 시간, 메모리 및 GC 등의 재현 가능한 측정으로만 주장한다.
- 클라이언트, 싱글플레이, 전용 서버의 코드 경계를 분명하게 유지한다.

## 3.2 장기 목표

- 모드 제작 템플릿과 CLI, Gradle 플러그인, 자동 메타데이터 검증, API 문서 생성기를 제공한다.
- IntelliJ IDEA의 코드 자동완성, 디버그 실행 구성 및 생성된 실행 프로필을 지원한다.
- 시스템별 성능 비용과 클래스 변환 출처를 볼 수 있는 진단 도구를 만든다.
- 지원 조합별 호환성 검사 데이터베이스와 자동 회귀 테스트를 구축한다.
- 리소스 및 데이터 정의의 핫 리로드를 지원한다.
- 필요할 때 버전별 어댑터와 추가 로더 브리지를 확장한다.

## 3.3 비목표 및 금지할 가정

- 처음부터 모든 Minecraft 버전과 모든 모드를 지원한다고 주장하지 않는다.
- Forge와 Fabric JAR을 같은 폴더에 복사하는 것만으로 혼합 실행이 해결된다고 보지 않는다.
- 모든 로더 모드의 100% 호환을 약속하지 않는다.
- 모든 클래스나 메서드를 런타임 중 언제나 안전하게 교체할 수 있다고 가정하지 않는다.
- 성능 테스트를 하기 전에 Poly 모드 로더가 기존 로더보다 빠르다고 주장하지 않는다.
- Java 클래스 로더 격리만으로 악성 모드가 안전하게 격리된다고 주장하지 않는다.
- 거대한 런처 UI를 핵심 엔진보다 먼저 만들지 않는다.
- `Utils`, `CommonManager`, `UniversalHelper` 같은 책임이 불분명한 만능 클래스를 늘리지 않는다.
- 실패를 숨기거나 모드가 요구하는 기능을 조용히 무시해 게임이 실행되는 것처럼 보이게 하지 않는다.

## 3.4 우선순위

1. Minecraft 버전 하나에서 실행 가능한 최소 기반을 만든다.
2. 시스템 교체·확장이 가능한 구조를 실험으로 입증한다.
3. Poly 모드 로더 전용 모드 하나를 IntelliJ에서 개발·실행·디버깅할 수 있게 한다.
4. 클래스 변환 파이프라인을 검증한다.
5. Fabric/Forge 각각에 대한 조기 호환성 실험을 수행한다. 두 브리지를 본격적으로 구현하기 전부터 기술 위험을 확인한다.
6. 고수준 API와 개발 도구를 확장한다.
7. 실험 결과에 근거해 혼합 실행과 성능 개선 범위를 확대한다.

# 4. Poly 모드 로더가 유지하는 기반과 교체 가능한 시스템

이 구분은 프로젝트 전체 아키텍처의 핵심이다.

## 4.1 Core Kernel — 유지할 최소 기반

`Core Kernel`은 모드와 시스템이 연결되는 최소 기반이다. 모든 Minecraft 코드를 여기에 몰아넣지 않는다. 적은 수의 안정적인 계약과 생명주기만 관리한다.

예상 책임:

- 게임 및 Poly 모드 로더 초기화 흐름에 연결
- 모드 검색, 메타데이터 검증, 의존성 해결
- 서비스 등록 및 서비스 수명 관리
- 이벤트와 시스템 라이프사이클의 공통 규칙
- 버전 어댑터 선택 및 연결
- 클래스 변환 계획의 실행·검증·진단
- 오류 보고와 로깅 계약
- 설정 및 기능 활성화 상태 관리

Kernel에는 특정 블록, 특정 몹, 제작법, UI 화면, 특정 렌더링 구현 등의 게임 기능을 직접 하드코딩하지 않는다. 그런 기능은 별도 시스템에 둔다.

## 4.2 Replaceable Systems — 교체·확장 대상

각 하위 시스템은 다음 중 한 가지 이상의 방법으로 확장 가능해야 한다.

- **Configure:** 기본 구현을 설정으로 변경한다.
- **Extend:** 기존 구현에 기능 또는 이벤트를 추가한다.
- **Replace:** 시스템 구현체를 다른 구현체로 교체한다.
- **Intercept:** 동작 전후나 특정 호출 지점에서 제어를 가로챈다.
- **Transform:** API로는 불가능한 내부 코드를 검증된 변환 규칙으로 수정한다.

| 시스템 영역 | 기본 Poly 모드 로더 경로 | 교체·확장 예시 | 목표 수준 |
|---|---|---|---|
| 등록 및 콘텐츠 | Content Registry API | 아이템·블록·레시피 등록 규칙 변경 | 높음 |
| 블록 및 상호작용 | Block/Interaction API | 충돌, 채굴, 사용, 업데이트 동작 변경 | 높음 |
| 아이템 및 인벤토리 | Item/Inventory API | 슬롯 규칙, 컨테이너, 아이템 동작 대체 | 높음 |
| 제작·전리품 | Recipe/Loot API | 제작 시스템, 드롭 규칙, 데이터 공급자 교체 | 높음 |
| 엔티티 및 AI | Entity/Behavior API | 속성, 목표, 행동, 업데이트 절차 확장 | 높음 |
| 월드·청크 | World/Chunk API | 월드 접근, 청크 처리, 생성 콜백 및 공급자 확장 | 높음 |
| 월드 생성 | Worldgen API | 바이옴, 구조물, 지형 생성 규칙 변경 | 높음 |
| 틱 및 시간 | Tick/Task API | 작업 스케줄링, 틱 구간 후크, 규칙 변경 | 높음 |
| 이동·물리·충돌 | Physics/Movement API | 이동 규칙 및 충돌 계산 확장·대체 | 높음, 저수준 후크 일부 필요 |
| 게임 규칙·전투 | Gameplay API | 전투 계산, 게임 모드, 규칙 변경 | 높음 |
| 렌더링 | Render System API | 렌더 패스, 모델, 재질, 셰이더 경로 변경 | 높음, 버전 종속성 큼 |
| GUI·HUD·입력 | UI/Input API | 화면, 메뉴, 입력 바인딩, HUD 교체 | 높음 |
| 오디오·파티클 | Audio/Effects API | 리소스, 재생 정책, 효과 경로 확장 | 높음 |
| 리소스·데이터 | Resource/Data API | 팩 우선순위, 로딩 파이프라인, 캐시 관리 | 높음 |
| 네트워크 | Networking API | 메시지, 동기화, 검증, 프로토콜 확장 | 높음, 보안 검증 필수 |
| 저장 데이터 | Persistence API | 데이터 스키마, 저장 공급자, 마이그레이션 | 높음, 데이터 손상 주의 |
| 명령어·권한 | Command/Permission API | 명령어 트리, 권한 정책 | 높음 |
| 부팅·종료 | Lifecycle API | 로딩 단계와 종료 처리 확장 | 중간~높음, 부트스트랩 제약 있음 |
| JVM/게임 내부 | Transformation API | 메서드 후킹, 필드 접근, 호출 지점 변경 | 고급·버전 종속 |

“높음”은 해당 영역에 정식 확장 설계를 제공한다는 목표이며, 각 API의 세부 기능이 이미 구현됐다는 뜻은 아니다.

## 4.3 모든 시스템은 대체 계약을 가져야 한다

교체 가능한 시스템은 임의의 클래스를 서로 바꿔 끼우는 구조가 아니라 계약 기반 구조를 사용한다.

각 교체 가능 시스템에는 아래 항목이 필요하다.

- 시스템 ID 및 사람이 읽을 수 있는 이름
- 역할과 소유 범위
- 공개 인터페이스
- 기본 구현체
- 초기화·시작·중단·종료 단계
- 의존하는 다른 시스템
- 교체 가능 지점과 교체 금지 지점
- 스레드 및 실행 시점 규칙
- 상태/데이터 소유권
- 오류 처리와 복구 정책
- 버전 호환 정책
- 테스트용 기본 구현 또는 테스트 더블

예를 들어 `RecipeSystem`을 교체할 수 있게 하려면 제작법 API만 제공하는 데서 끝나지 않는다. 레시피 등록, 검색, 실행, 리소스 재로딩, 서버 동기화, 저장된 데이터와의 관계를 계약으로 정의해야 한다.

## 4.4 교체 권한과 충돌 규칙

- 기본 구현체는 하나여야 한다.
- 여러 모드가 같은 시스템을 완전히 교체하려 하면 우선순위 또는 명시적 의존성을 요구한다.
- 교체와 확장은 다른 개념으로 표시한다.
- 동시에 여러 대체 구현체를 적용할 수 없는 시스템에서는 무조건 하나를 선택하도록 한다.
- 다른 모드의 구현을 바꾸는 모드는 대상 시스템과 의도를 메타데이터에 표시해야 한다.
- 서로 양립할 수 없는 교체는 자동 추측하지 않고, 두 모드 ID·충돌 시스템·필요한 해결 방법을 포함한 진단을 출력한다.

## 4.5 기본 모드 API에서 다루기 어려운 내부 영역도 제어 대상으로 삼는다

Poly 모드 로더의 차별점은 블록이나 아이템을 추가하는 API만 제공하는 것이 아니다. 기존 모드 개발에서 접근이 어렵거나 특정 로더·버전·클래스 변환 기술에 의존하는 내부 지점도, 용도별로 분류한 확장 수단을 통해 접근할 수 있도록 설계한다.

여기서 “제어 가능”은 모든 변경이 안전하거나 버전 변경 후에도 자동으로 유지된다는 뜻이 아니다. Poly 모드 로더는 다음의 네 가지 제어 방식을 구분해 제공한다.

1. **공식 계약** — 버전 변화에도 가능한 한 의미를 유지하도록 관리하는 공개 API.
2. **시스템 대체 계약** — 기본 구현체를 명시적으로 바꿀 수 있는 인터페이스.
3. **버전 종속 내부 API** — Minecraft 내부 클래스·필드·메서드에 접근하되 대상 버전과 시그니처를 고정하는 API.
4. **바이트코드 변환 API** — 접근 제한, 메서드 본문, 생성자 주변 코드, 특정 메서드 호출 지점 등을 수정하는 고급 도구.

다음은 API 목표 범위다.

| 기존 로더의 일반적인 고수준 API만으로 다루기 어려운 부분 | Poly 모드 로더에서 제공할 접근 경로 | 주요 제한·검증 |
|---|---|---|
| 게임 부팅과 초기화 순서 | Bootstrap/Lifecycle/Launch API | 일부 변경은 클래스 로딩 전 단계에서만 가능 |
| 비공개 클래스·필드·메서드 | Internal Access/Accessor API | 버전별 이름·descriptor 검증 필요 |
| `final` 클래스·메서드·필드 | Access Policy/Bytecode Transform API | JVM 검증 및 호출 계약 검토 필요 |
| 생성자와 객체 생성 흐름 | Constructor Transform API | 검증되지 않은 부분 초기화 상태를 만들지 않도록 제한 |
| 정적 초기화 및 클래스 초기화 순서 | Class Initialization API | 이미 초기화된 클래스의 `<clinit>`을 일반 핫 리로드로 교체할 수 있다고 가정하지 않음 |
| 메서드의 핵심 알고리즘 전체 | Method Replacement API | 원본 사전·사후 조건, 예외 및 부작용을 명시 |
| 특정 메서드가 호출하는 다른 메서드 | Call-Site API | 대상 호출의 소유 클래스·descriptor·매핑 버전을 지정 |
| 기본 게임 루프 및 틱 순서 | Game Loop/Tick Phase API | 게임 스레드와 서버 스레드 규칙 준수 |
| 레지스트리 동결·등록 시점 | Registry Lifecycle API | 늦은 등록이 허용되는지 명시, 임의 상태 변조 금지 |
| 청크 생성·로딩·언로딩·티켓 | Chunk Pipeline API | 동시성, 저장 일관성, 클라이언트/서버 차이 검증 |
| 청크 메시·가시성·조명 갱신 | Rendering/Chunk Build API | 그래픽 백엔드 및 버전 종속성이 높음 |
| 렌더 호출 순서·렌더 상태 | Render Graph/Pass API 및 저수준 후크 | 그래픽 상태 누수와 렌더 스레드 오류 검사 |
| 충돌·이동·경로 탐색 | Physics/Collision/Navigation API | 월드 경계·예측·서버 권한·AI 영향 검사 |
| 엔티티 업데이트와 추적 | Entity Tick/Tracking API | 동기화, 제거, 청크 경계 처리 검증 |
| 클라이언트 입력과 포커스 | Input/Window API | OS 입력 및 창 라이브러리와의 경계를 구분 |
| 리소스 팩 재로딩 순서 | Resource Reload Graph API | 종속 관계·실패 원자성·캐시 무효화 검증 |
| 패킷 인코딩·디코딩 및 핸드셰이크 | Network Protocol API | 양쪽 버전 협상, 크기 제한, 권한 검증 필수 |
| 월드 저장·로드와 스키마 변경 | Persistence/Data Migration API | 백업, 마이그레이션, 이전 버전 열기 테스트 필수 |
| 내부 캐시·캐시 만료 정책 | Cache Provider API | 메모리 상한과 무효화 규칙을 계약에 명시 |
| 게임 내부 스케줄러 및 백그라운드 작업 | Task Scheduler API | 스레드 안전성, 취소, 월드 종료 시 작업 정리 검증 |
| 로더 간 초기화·이벤트 차이 | Compatibility Lifecycle Adapter | 로더별 의미를 단순히 이름만 맞춰 숨기지 않음 |

## 4.6 확장 방식의 의미를 코드와 문서에서 통일한다

Poly 모드 로더는 아래의 확장 동사를 서로 다른 기술 계약으로 사용한다.

- `Observe`: 상태를 읽거나 이벤트를 관찰한다. 기본적으로 게임 상태를 변경하지 않는다.
- `Configure`: 설정 값·정책·전략을 바꾼다.
- `Register`: 새 콘텐츠·서비스·핸들러를 등록한다.
- `Extend`: 기본 동작을 유지하면서 부가 동작을 추가한다.
- `Override`: 특정 계약 안에서 기본 동작을 대체한다.
- `Replace`: 시스템 제공자 또는 알고리즘 구현을 명시적으로 교체한다.
- `Intercept`: 지정된 호출 전·후 또는 결과 반환 시점에 개입한다.
- `Transform`: 클래스 정의 전의 바이트코드를 수정한다.
- `Migrate`: 데이터나 API 계약을 새 버전으로 이전한다.

API 문서, 메서드 이름, 진단 메시지에서 이 동사를 같은 의미로 사용한다. 예를 들어 시스템 전체를 바꾸는 행위를 모호한 `hook()` 하나로 표현하지 않고, `replaceProvider(...)` 또는 `interceptCall(...)`처럼 의도가 드러나는 API를 선택한다.

# 5. 개발자가 사용하는 API 계층

Poly 모드 로더 Native API는 초보자가 일반적인 모드를 만들 수 있을 만큼 간단하면서도, 고급 개발자는 게임 내부 구현까지 확장할 수 있도록 계층화한다.

## 5.1 API 레벨

### Level 1 — 고수준 선언형 API

일반적인 콘텐츠 추가와 간단한 동작을 적은 코드로 구현한다.

예시 개념:

- 블록·아이템·레시피 정의
- 엔티티 유형과 속성 정의
- 설정, 태그, 데이터 파일 선언
- 이벤트 구독과 명령어 등록
- 모델·텍스처·사운드 연결

### Level 2 — 시스템 확장 API

기존 기능의 동작을 확장하거나 대체한다.

예시 개념:

- `RecipeProvider` 교체
- `WorldGenerationProvider` 확장
- `EntityBehavior` 등록
- `InventoryPolicy` 변경
- `RenderPassProvider` 추가
- `ResourceReloadListener` 등록

### Level 3 — 고급 훅 API

시스템의 특정 시점에 제어를 연결한다.

예시 개념:

- 메서드 진입/종료 후크
- 검증된 메서드 래핑
- 이벤트 삽입 지점 지정
- 필드와 메서드 접근 규칙 선언
- 게임 코드의 특정 호출 지점 변경

### Level 4 — 저수준 변환 API

고급 개발자가 특정 게임 클래스의 바이트코드를 수정할 수 있게 한다.

반드시 다음을 요구한다.

- 대상 Minecraft 버전 범위
- 대상 클래스 및 메서드 시그니처
- 변환의 사전 조건과 사후 조건
- 충돌과 우선순위 처리 정책
- 검증 및 테스트
- 디버그 덤프와 롤백 가능한 원본 기록

Level 4는 강력하지만 호환성 위험도 가장 크다. 고수준 API보다 먼저 개발하지 않는다.

## 5.2 개념 예시: 전용 모드 코드

아래 코드는 API가 어떤 사용 경험을 목표로 하는지 보여주는 **설계 예시**다. 실제로 컴파일되는 확정 API라고 간주하지 않는다.

```java
public final class CopperToolsMod implements PolyMod {
    @Override
    public void initialize(ModContext mod) {
        mod.content()
            .block("copper_bricks")
            .hardness(2.0f)
            .requiresTool(true)
            .register();

        mod.content()
            .recipe("copper_bricks")
            .shaped("CCC", "CCC", "CCC")
            .ingredient('C', "minecraft:copper_ingot")
            .result("copper_bricks", 4)
            .register();
    }
}
```

API를 실제 구현할 때는 문자열 오타를 조기에 잡을 수 있는 타입 안전한 ID, 명시적 검증, IDE 자동완성을 우선한다. 선언형 DSL이 디버깅하기 어렵거나 오류가 늦게 나타난다면 단순히 코드를 짧게 만들기 위해 DSL을 강요하지 않는다.

## 5.3 상세 API 분류 카탈로그

이 카탈로그는 Poly 모드 로더가 제공할 API의 논리적 분류다. 모든 분류가 처음부터 별도 JAR이 되어야 한다는 뜻은 아니다. 하나의 모듈 안에서 먼저 구현한 뒤 공개 계약과 의존 관계가 안정되면 Gradle 모듈로 분리한다. 이름은 역할 중심으로 지정하고, 각 공개 모듈의 README에는 포함되는 API와 포함되지 않는 API를 명시한다.

### A. Runtime Foundation APIs — 런타임 기반

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| Bootstrap API | 게임 프로세스 시작, Poly 모드 로더 부트스트랩, 초기 환경 검사 | `PolyBootstrap`, `RuntimeEnvironment` |
| Runtime Lifecycle API | 시작·초기화·실행·종료 단계와 단계별 제약 | `RuntimePhase`, `LifecycleParticipant` |
| Mod Discovery API | 모드 파일 찾기, 중첩 의존성 탐색, 출처 식별 | `ModDiscovery`, `ModArtifact` |
| Metadata API | 모드 ID·버전·진입점·환경·기능 요구사항 검증 | `ModDescriptor`, `MetadataValidator` |
| Dependency API | 의존성 그래프, 선택적 의존성, 충돌, 초기화 순서 | `ModDependencyResolver`, `DependencyGraph` |
| Class Loading API | 모드 코드와 게임 코드의 로딩 경계, 클래스 경로 정책 | `ModClassPath`, `ClassLoadingPolicy` |
| Mapping API | 사람이 읽는 이름과 런타임 클래스/멤버 이름 매핑 | `GameSymbol`, `MappingResolver` |
| Version Adapter API | 대상 Minecraft 버전별 내부 차이를 안정된 계약 뒤로 숨김 | `GameVersionAdapter`, `VersionCapabilitySet` |
| Service Registry API | 런타임 서비스 등록·검색·수명 관리 | `ServiceRegistry`, `ServiceKey` |
| Configuration API | 전역·모드별·월드별 설정 스키마와 검증 | `ConfigSchema`, `ConfigValue` |

### B. System Composition APIs — 시스템 구성 및 교체

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| System Definition API | 시스템 ID, 책임, 버전, 필수 기능 정의 | `SystemDescriptor` |
| System Registry API | 시스템 정의와 제공자 등록 | `SystemRegistry`, `SystemProvider` |
| System Replacement API | 기본 제공자를 하나의 대체 제공자로 교체 | `SystemReplacement`, `ReplacementPolicy` |
| System Extension API | 기본 제공자에 확장·장식·후처리 기능 결합 | `SystemExtension`, `ProviderDecorator` |
| System Ordering API | 시스템 간 선행·후행 관계 지정 | `SystemOrder`, `StartupConstraint` |
| Capability API | 현재 게임 버전·환경에서 지원되는 기능 조회 | `CapabilityQuery`, `CapabilityResult` |
| Inter-Mod Service API | 서로 다른 모드가 명시된 서비스 계약으로 연동 | `InterModService`, `ServiceContract` |

### C. Lifecycle, Event and Scheduling APIs — 생명주기·이벤트·예약

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| Lifecycle API | 모드·월드·게임·서버의 초기화/종료 콜백 | `ModLifecycle`, `WorldLifecycleEvent` |
| Event API | 관찰 이벤트, 취소 가능 이벤트, 우선순위와 구독 수명 | `EventBus`, `EventSubscription` |
| Hook API | 명시적으로 정의된 실행 지점 전후에 로직 연결 | `HookPoint`, `HookRegistration` |
| Tick API | 게임 틱 단계, 틱 우선순위, 단계별 제한 | `TickPhase`, `TickParticipant` |
| Task Scheduling API | 메인 스레드 작업, 백그라운드 작업, 지연·반복·취소 | `GameTaskScheduler`, `TaskHandle` |
| Thread Ownership API | 월드·렌더·네트워크·IO 상태의 소유 스레드 규칙 | `ThreadGuard`, `ThreadAffinity` |
| State Observation API | 안전한 상태 스냅샷과 상태 변화 알림 | `StateSnapshot`, `StateObserver` |

### D. Content, Registry and Data Definition APIs — 콘텐츠·등록·데이터 정의

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| Identifier API | 타입 안전한 namespace/path 식별자 | `ResourceId`, `ModId` |
| Registry API | 콘텐츠 및 시스템 ID 등록·조회·충돌 보고 | `RegistryKey`, `RegistryTransaction` |
| Block API | 블록 정의, 상태, 속성, 상호작용, 모양 | `BlockDefinition`, `BlockBehavior` |
| Block State API | 상태 속성, 허용 조합, 상태 직렬화 | `BlockStateSchema` |
| Item API | 아이템 정의, 스택 규칙, 사용·내구도·행동 | `ItemDefinition`, `ItemBehavior` |
| Inventory API | 슬롯, 이동, 필터, 트랜잭션과 컨테이너 정책 | `InventoryView`, `InventoryPolicy` |
| Recipe API | 제작법 스키마, 재료 조회, 결과 계산과 리로드 | `RecipeDefinition`, `RecipeProvider` |
| Loot API | 전리품 테이블, 조건, 함수, 컨텍스트 | `LootContextProvider`, `LootRule` |
| Tag API | 콘텐츠 그룹과 태그 의존성 | `TagKey`, `TagResolver` |
| Entity Type API | 엔티티 타입, 속성, 크기, 데이터 정의 | `EntityTypeDefinition` |
| Effect API | 상태 효과, 지속시간, 중첩 및 계산 규칙 | `EffectDefinition` |
| Item Component/Data Component API | 아이템별 구조화 데이터 및 직렬화 규칙 | `ComponentType`, `ComponentCodec` |
| Datapack API | JSON/데이터 리소스 등록, 병합 정책, 검증 | `DataResourceProvider` |

### E. World, Chunk and Generation APIs — 월드·청크·생성

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| World Access API | 월드 읽기·쓰기 권한, 좌표·차원 접근 | `WorldView`, `WorldEditor` |
| Dimension API | 차원 속성, 경계, 이동 및 등록 | `DimensionDefinition` |
| Biome API | 바이옴 속성, 기후, 스폰·장식 설정 | `BiomeDefinition` |
| World Generation Pipeline API | 지형 생성 단계와 단계별 공급자 교체 | `WorldgenPipeline`, `GenerationStage` |
| Noise API | 노이즈 설정, 샘플링, 노이즈 함수 제공자 | `NoiseProvider`, `NoiseSettings` |
| Structure API | 구조물 정의, 배치, 생성을 위한 데이터 및 규칙 | `StructureDefinition`, `PlacementRule` |
| Feature Placement API | 광물·식물·장식 등 월드 생성 기능 배치 | `FeatureDefinition`, `PlacementModifier` |
| Chunk Lifecycle API | 생성·로드·틱·저장·언로드 단계 | `ChunkLifecycle`, `ChunkStatusView` |
| Chunk Ticket API | 청크 유지 사유와 로드 반경 정책 | `ChunkTicketProvider` |
| Chunk Storage API | 청크 데이터의 저장·압축·복원 경로 | `ChunkStorageProvider` |
| Lighting API | 조명 갱신 계약과 조명 계산 제공자 | `LightingProvider` |
| World Simulation API | 시간, 날씨, 게임 규칙, 시뮬레이션 범위 | `WorldSimulationPolicy` |
| Spawn API | 생물 생성 규칙, 한도, 조건, 스폰 위치 | `SpawnPolicy` |
| POI API | 관심 지점 등록·검색 및 점유 상태 | `PointOfInterestProvider` |

### F. Entities, AI, Physics and Gameplay APIs — 엔티티·AI·물리·게임 규칙

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| Entity Lifecycle API | 생성·제거·월드 이동·상태 저장 단계 | `EntityLifecycleParticipant` |
| Entity Data API | 엔티티 동기화 데이터와 버전 관리 | `EntityDataSchema` |
| Entity Tick API | 엔티티 업데이트 단계와 작업 비용 관찰 | `EntityTickPolicy` |
| AI Goal API | 목표 우선순위, 목표 조건, 실행·중단 | `GoalDefinition`, `GoalScheduler` |
| Navigation API | 경로 생성·비용·경로 추종 전략 | `NavigationProvider` |
| Attributes API | 속성 정의, 수정자 적용 순서, 계산 규칙 | `AttributePolicy` |
| Damage/Combat API | 피해 원인, 피해 계산, 방어, 넉백과 전투 규칙 | `DamagePipeline`, `CombatPolicy` |
| Movement API | 이동 입력, 속도·가속, 이동 단계 확장 | `MovementController` |
| Collision API | 충돌 형상, 충돌 조회, 충돌 계산 제공자 | `CollisionProvider` |
| Physics API | 중력, 마찰, 낙하, 운동 계산 정책 | `PhysicsPolicy` |
| Interaction API | 플레이어·엔티티·블록·아이템 상호작용 결과 | `InteractionPipeline` |
| Game Rules API | 규칙 등록, 값 검증, 변경 이벤트 | `GameRuleDefinition` |
| Advancement API | 업적/발전 조건, 트리, 보상 및 진행 저장 | `AdvancementDefinition` |
| Status Effect API | 효과 계산, 틱 단계 및 상호작용 | `StatusEffectPolicy` |
| Fishing/Combat/Vehicle Specialized APIs | 낚시·발사체·탈것 등 전문 게임플레이 흐름 | 기능별 세분화 모듈 |

### G. Client, Rendering, Input and UI APIs — 클라이언트·렌더링·입력·UI

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| Client Lifecycle API | 클라이언트 초기화·화면 종료·월드 연결 | `ClientLifecycle` |
| Input API | 키 바인딩, 입력 상태, 우선순위, 충돌 정책 | `InputAction`, `KeyBindingRegistry` |
| Window API | 창 상태, 포커스, 화면 크기와 전체 화면 전환 | `WindowStateProvider` |
| Camera API | 카메라 모드, 위치·방향·시야 계산 | `CameraController` |
| HUD API | HUD 위젯, 레이어, 배치, 표시 조건 | `HudWidget`, `HudLayer` |
| Screen API | 화면, 메뉴, 포커스·입력 라우팅 | `ScreenDefinition`, `UiFocusPolicy` |
| UI Render API | GUI 렌더링, 폰트, 스케일링, 클리핑 | `UiRenderer` |
| Model API | 모델 정의, 모델 bake, 파츠와 변형 | `ModelProvider` |
| Animation API | 애니메이션 상태, 트랙, 타임라인 | `AnimationController` |
| Render Graph API | 렌더 단계·의존성·리소스 사용 선언 | `RenderGraph`, `RenderPassDefinition` |
| Render Pass API | 월드·엔티티·블록·효과의 렌더 지점 | `RenderPassProvider` |
| Material/Shader API | 재질, 셰이더 파라미터, 셰이더 공급자 | `MaterialDefinition`, `ShaderProvider` |
| Chunk Meshing API | 청크 메시 생성, 메시 캐시·재생성 정책 | `ChunkMeshBuilder` |
| Visibility/Culling API | 가시성 판정, 컬링 정책, 영역 쿼리 | `VisibilityProvider` |
| Graphics Resource API | 버퍼·텍스처·렌더 리소스 생성과 해제 | `GraphicsResourceManager` |
| Particle API | 파티클 정의·시뮬레이션·렌더 경로 | `ParticleDefinition` |
| Client Performance API | 프레임 단계별 시간·렌더 호출 비용 측정 | `FrameProfiler` |

### H. Resources, Audio and Localization APIs — 리소스·오디오·현지화

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| Resource API | 리소스 ID, 우선순위, 읽기·조회 | `ResourceProvider` |
| Resource Pack API | 팩 발견, 순서, 중복 리소스 병합 | `ResourcePackPolicy` |
| Reload Graph API | 재로딩 작업과 종속성 DAG | `ResourceReloadGraph` |
| Cache API | 캐시 정책, 한도, 무효화와 관찰 | `CachePolicy`, `CacheMetrics` |
| Audio API | 사운드 등록·재생·그룹·공간화 정책 | `AudioProvider`, `SoundDefinition` |
| Music API | 음악 트랙과 상황별 재생 정책 | `MusicPolicy` |
| Localization API | 언어 키, 폴백, 텍스트 포맷팅 | `LocalizationProvider` |
| Font API | 글꼴 로딩·선택·측정 | `FontProvider` |
| Texture API | 텍스처 수명, 캐시, 동적 텍스처 | `TextureProvider` |

### I. Networking, Multiplayer and Server APIs — 네트워크·멀티플레이·서버

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| Protocol API | 프로토콜 버전 및 기능 협상 | `ProtocolVersion`, `ProtocolNegotiator` |
| Packet Registration API | 메시지 ID, codec, 방향 및 시점 | `PacketType`, `PacketCodec` |
| Custom Payload API | 모드별 페이로드 라우팅·버전 처리 | `ModPayloadChannel` |
| State Sync API | 엔티티·블록 엔티티·월드 상태 동기화 | `SyncSchema`, `SyncPolicy` |
| Server Authority API | 클라이언트 요청 권한·검증 | `ServerActionValidator` |
| Connection Lifecycle API | 접속·인증 후 단계·종료·재접속 | `ConnectionLifecycle` |
| Network Thread API | 패킷 수신과 게임 스레드 실행 경계 | `NetworkTaskDispatcher` |
| Server Lifecycle API | 전용 서버 시작·저장·종료 | `DedicatedServerLifecycle` |
| Player Session API | 플레이어 세션 상태 및 연결 데이터 | `PlayerSession` |
| Permission API | 권한 정책과 서버 측 실행 허용 | `PermissionPolicy` |

### J. Persistence, Commands and Administration APIs — 저장·명령·운영

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| Persistence API | 데이터 저장·로드 공급자 선택 | `DataStoreProvider` |
| World Save API | 월드 저장 단계와 저장 원자성 | `WorldSaveCoordinator` |
| Data Schema API | 데이터 스키마 버전과 검증 | `DataSchema` |
| Migration API | 월드·모드 데이터 버전 마이그레이션 | `DataMigration` |
| Backup API | 저장 전 백업 및 복구 메타데이터 | `BackupPolicy` |
| Command API | 명령어 트리·인수 타입·완성 제안 | `CommandDefinition` |
| Permission API | 명령어 및 상호작용별 권한 검사 | `PermissionRule` |
| Admin API | 진단 명령, 안전한 런타임 조회 | `RuntimeDiagnosticsCommand` |

### K. Deep Internal and Transformation APIs — 내부 접근·변환

이 그룹은 기존 모드 API만으로 접근하기 어려운 게임 내부를 위한 명시적 고급 API다. 단순히 전부 `Reflection`으로 열어주는 대신, 작업 성격에 맞는 하위 API를 둔다.

| API 분류 | 제공 기능 | 요구 사항 |
|---|---|---|
| Internal Symbol API | 클래스·필드·메서드의 버전별 심볼 해석 | 대상 버전·매핑 namespace·descriptor 고정 |
| Internal Access API | 비공개 멤버 접근 핸들 또는 접근자 생성 | 가능한 경우 타입 안전 핸들 우선, 직접 reflection은 별도 표시 |
| Access Policy API | 필요 멤버를 접근 가능·상속 가능·수정 가능하게 변환 | 적용 시점, 대상 클래스 및 부작용 보고 |
| Method Hook API | 메서드 진입/종료/예외/결과 지점 연결 | 후크 위치와 제어 흐름을 명시 |
| Call-Site API | 메서드가 호출하는 특정 호출 지점 교체 | 호출 지점과 호출 대상 descriptor 검증 |
| Field Access/Write API | 필드 읽기·쓰기 또는 필드 접근 경로 변경 | `final`·정적 필드·메모리 가시성 주의 |
| Constructor API | 생성자 실행 흐름 관찰·검증된 삽입 | 객체 초기화 불변조건 유지, 제한된 작업만 허용 |
| Class Definition API | 클래스 로딩 전 바이트코드 변경 | 변환 단계 이전에 실행 계획 확정 |
| Class Structure Transform API | 클래스/메서드 구조 변환이 가능한 시작 단계 도구 | 런타임 핫 리로드 가능 여부를 별도 표기 |
| Mixin Compatibility API | Fabric Mixin과 유사 규칙을 호환 계층에서 다룸 | Mixin 의미·우선순위·환경별 적용을 검증 |
| Access Transformer Compatibility API | Forge 계열 접근 변환 규칙 연결 | 대상 Forge 세대별 의미 차이 처리 |
| Transformation Ordering API | 다수 변환의 순서·선행 관계·우선순위 | 충돌 시 자동 추측하지 않고 진단 |
| Transformation Validation API | 사전/사후 바이트코드·시그니처·불변조건 검사 | 변환 실패 위치와 모드 ID 노출 |
| Transformation Debug API | 변환 전후 클래스 덤프와 diff 제공 | 민감 정보가 될 수 있는 파일 경로 관리 |
| Runtime Inspection API | 현재 시스템·후크·구독·변환 출처 조회 | 조회는 기본적으로 비파괴적이어야 함 |

### L. Compatibility, Diagnostics and Developer Experience APIs — 호환·진단·개발 경험

| API 분류 | 담당 기능 | 대표 타입 예시 |
|---|---|---|
| Fabric Compatibility API | Fabric 메타데이터·수명주기·서비스 어댑터 | `FabricModAdapter` |
| Forge Compatibility API | Forge 메타데이터·이벤트·등록 어댑터 | `ForgeModAdapter` |
| Compatibility Report API | 모드/기능/버전별 호환 상태 보고 | `CompatibilityReport` |
| Inter-Mod Compatibility API | 모드 간 기대 기능 및 서비스 교환 | `ModIntegrationDescriptor` |
| Diagnostic API | 오류 ID, 조치 단계, 영향을 받은 시스템 보고 | `DiagnosticReport` |
| Logging API | 모드·시스템·실행 단계별 구조화 로그 | `PolyLogger` |
| Metrics API | 프레임·틱·이벤트·메모리 등 비용 수집 | `MetricRegistry` |
| Profiler API | 호출 경로와 시스템별 성능 분석 | `PolyProfiler` |
| Testkit API | 가짜 월드, 테스트 이벤트, 모드 격리 테스트 도구 | `PolyTestContext` |
| Devtools API | IntelliJ 실행 구성, 리소스 검증, 코드 생성 연결 | `PolyDevModel` |
| Code Generation API | 메타데이터·등록 코드·스키마 생성 | `SourceGenerator` |
| Documentation API | 공개 타입·시스템 계약 문서 생성 | `ApiDocModel` |

## 5.4 API별 지원 동작과 안정성 등급을 명시한다

API를 이름만 분류하는 것으로는 충분하지 않다. 각각의 공개 타입·메서드 또는 변환 규칙은 다음을 선언해야 한다.

### 지원 동작 표기

- `READ`: 상태 읽기
- `WRITE`: 상태 변경
- `REGISTER`: 새 항목 또는 핸들러 등록
- `EXTEND`: 기본 동작을 유지하며 확장
- `REPLACE`: 기본 구현/제공자를 대체
- `INTERCEPT`: 호출 전후 또는 지정 시점 개입
- `TRANSFORM`: 바이트코드 수정
- `MIGRATE`: 데이터/계약 이전
- `INSPECT`: 런타임 구성·출처 진단

### API 안정성 등급

- `STABLE`: 공개 호환 정책이 있고, 비호환 변경은 API 버전 변경과 마이그레이션 설명이 필요하다.
- `PREVIEW`: 사용 가능하지만 계약이 바뀔 수 있다. 변경 이력을 기록한다.
- `VERSION_BOUND`: 특정 Minecraft 버전 또는 버전 범위에 묶여 있으며 버전 어댑터로 접근한다.
- `INTERNAL`: Poly 모드 로더 핵심 모듈만 사용한다. 외부 모드는 직접 의존하지 않는다.
- `UNSAFE`: 충돌·크래시·월드 데이터 손상 가능성을 포함하는 저수준 동작이며 개발자 명시 동의와 진단을 요구한다.

안정성 등급을 JavaDoc, 개발 문서와 IntelliJ 자동완성 설명에 모두 노출한다. 안정성 등급을 숨겨서는 안 된다.


## 5.4.1 기능 권한 선언과 위험도

Poly 모드 로더는 모든 모드가 모든 내부 기능을 아무 설명 없이 사용하는 방식을 기본값으로 삼지 않는다. 전용 모드의 메타데이터는 요구하는 기능을 사람이 읽을 수 있게 선언하고, 로더는 시작 보고서와 진단 화면에 해당 권한의 이유를 보여준다. 이것은 모드 코드가 악성 동작을 할 수 없게 만드는 보안 샌드박스가 아니라 **투명성·검토·정책 적용을 위한 기능 선언**이다.

예시 기능 ID:

- `content.registry.register`: 새 콘텐츠를 등록한다.
- `gameplay.rules.extend`: 게임플레이 규칙을 확장한다.
- `worldgen.provider.replace`: 월드 생성 제공자를 교체한다.
- `render.pipeline.extend`: 렌더링 파이프라인을 확장한다.
- `system.provider.replace`: Poly 모드 로더 시스템 제공자를 교체한다.
- `internal.member.access`: Minecraft 내부 클래스 멤버에 접근한다.
- `internal.callsite.intercept`: 특정 내부 호출 지점에 개입한다.
- `internal.bytecode.transform`: 게임 또는 다른 모드의 바이트코드 변환을 선언한다.
- `runtime.bootstrap.modify`: 부트스트랩 또는 초기화 흐름에 개입한다.
- `network.protocol.extend`: 네트워크 프로토콜을 확장한다.
- `world.data.migrate`: 저장 데이터 스키마 마이그레이션을 등록한다.

모드의 요구 기능이 현재 환경에서 지원되지 않으면, Poly 모드 로더는 기능을 조용히 무시하지 않는다. 해당 모드를 차단하거나 명확한 제한 상태로 표시한다. 권한이 넓은 기능은 개발자 보고서에 대상 시스템, 대상 버전, 변경 방법 및 테스트 결과를 연결해 표시한다.

## 5.5 내부 API와 버전 어댑터의 설계 규칙

“상세하게 건드릴 수 있다”는 목표를 달성하려면 내부 클래스 이름을 모드 전체에 직접 노출하는 방식을 피해야 한다. Minecraft 버전이 바뀔 때 수백 개 모드가 직접 수정되는 문제를 줄이기 위해 아래 원칙을 적용한다.

1. 일반 공개 API에는 Minecraft 내부 클래스·메서드 타입을 가능한 한 노출하지 않는다.
2. 버전별 내부 API는 `GameVersionAdapter`와 `InternalSymbolResolver`를 통해 해석한다.
3. 심볼 키는 class name만 사용하지 않는다. owner, member name, descriptor, expected access, target version, mapping namespace를 포함한다.
4. 클래스 이름이나 메서드 이름이 바뀌거나 오버로드가 추가되면 조용히 유사 대상을 선택하지 않는다. 정확한 심볼 해결에 실패하면 실행을 중단하거나 해당 기능을 비활성화하고 진단한다.
5. `MethodHandle`, `VarHandle`, 명시적 accessor 등 가능한 더 명확한 경로를 우선한다. 직접 reflection은 `INTERNAL` 또는 `UNSAFE` 등급으로 표시한다.
6. 저수준 접근을 기능별 facade 뒤에 감춘다. 예를 들어 모드가 `MinecraftClient`의 필드를 직접 반사적으로 접근하기보다 `ClientInputAccess`를 사용한다.
7. 버전 어댑터가 기능을 제공하지 않을 때는 `CapabilityResult.unsupported(reason)` 같은 분명한 응답을 반환한다.
8. 내부 API는 업데이트 시 자동 호환이 보장되지 않는다. 지원 범위와 회귀 테스트 결과를 버전별로 기록한다.

## 5.6 시스템 대체 API의 계약

각 `SystemProvider`는 최소한 다음 계약을 구현한다.

```java
public interface SystemProvider<T extends PolySystem> {
    SystemDescriptor descriptor();
    SystemCapabilities capabilities();
    T create(SystemContext context);
}
```

위 코드는 개념 예시이며 확정된 구현 API가 아니다. 실제 인터페이스는 생명주기, 비동기 초기화, 오류 복구와 자원 소유권을 검토한 뒤 확정한다. 각 시스템 descriptor에는 다음이 포함돼야 한다.

- 고유 시스템 ID와 사람이 읽는 표시명
- 시스템이 책임지는 동작과 소유하는 데이터
- 기본 제공자인지 대체 제공자인지
- 의존하는 시스템 및 기능 요구사항
- 클라이언트/서버/공통 실행 환경
- 초기화 단계와 종료 순서
- 스레드 제약과 작업 비용 분류
- API 안정성 등급
- 다른 제공자와의 우선순위·호환 관계
- 재시작 필요 여부와 안전한 교체 범위

## 5.7 저수준 변환을 안전하게 다루는 규칙

저수준 API를 제공하되 아무 검증 없이 임의 변환을 실행하지 않는다.

- 변환 선언은 별도의 선언 파일 또는 명확한 Java 타입으로 표현한다.
- 변환마다 고유 ID, 소유 모드, 대상 클래스, 멤버 descriptor, 게임 버전 범위, 변환 종류를 선언한다.
- 가능하면 게임 클래스 로드 전에 모든 변환을 모아 계획을 확정한다.
- 대상 바이트코드가 예상과 다르면 변환을 시도하지 말고 구체적인 오류를 출력한다.
- 변환 후 클래스 검증, 통합 테스트, 클라이언트/전용 서버 환경 테스트를 요구한다.
- 변환 전후 바이트코드와 규칙 목록을 개발 모드에서 확인할 수 있게 한다.
- 여러 변환이 같은 메서드를 수정할 때 순서·협력 계약·충돌 정책을 적용한다.
- 핵심 게임 시스템 변환 실패가 월드 데이터 손상이나 무결성 문제를 유발할 수 있으면 시작을 중단한다.
- JVM이 이미 정의한 클래스의 임의 구조 변경이 언제나 가능하다고 가정하지 않는다. 클래스 재정의가 허용되는 범위와 제한을 실험으로 확인하고, 구조적 수정은 초기 클래스 변환 단계에서 처리하는 방식을 우선한다.

## 5.8 코드만 보아도 역할을 알 수 있게 하는 추가 규칙

- 공개 API에서 `Manager`, `Helper`, `Utils`, `Common`, `Data` 같은 포괄적 이름만으로 핵심 책임을 표현하지 않는다. 부득이하게 사용하면 모듈 내부의 제한된 범위에 두고 이름에 구체적 책임을 포함한다.
- 데이터 타입은 `...Definition`, 런타임 객체는 `...Instance` 또는 `...Runtime`, 정책은 `...Policy`, 실행 제공자는 `...Provider`, 인터페이스 계약은 필요할 때 `...Service` 또는 역할 이름을 쓴다.
- `Registry`는 등록·조회·중복 정책을, `Resolver`는 이름/의존성 해석을, `Planner`는 실행 계획을, `Executor`는 준비된 계획의 실행을 담당한다.
- `Controller`는 상태 전이·사용자/게임 입력을 받아 동작을 조정하고, `Coordinator`는 여러 독립 구성요소의 수명과 순서를 조율한다.
- `Access` 타입은 접근 경계와 권한을, `View` 타입은 제한된 읽기 관점을, `Editor` 타입은 제한된 변경 관점을 나타낸다.
- API는 한 클래스에 직렬화, 등록, 렌더링, 네트워크, 저장을 동시에 넣지 않는다.
- 복잡한 시스템에는 `README.md`와 `ARCHITECTURE.md`를 두어 “무엇을 소유하는가 / 무엇을 소유하지 않는가 / 어디서 시작하는가 / 어떤 테스트가 증명하는가”를 설명한다.

## 5.9 API 개발 완료 기준

API 영역은 아래 항목을 갖추기 전까지 완성으로 표시하지 않는다.

- 공개 계약 및 안정성 등급
- 타입과 메서드에 대한 JavaDoc
- 허용 환경(공통/클라이언트/서버)과 스레드 규칙
- 정상 경로·오류 경로의 단위 테스트
- 최소 Native 샘플 모드
- IntelliJ 자동완성 및 소스 탐색 확인
- Minecraft 버전별 지원 매트릭스
- 저수준 API라면 대상 시그니처·변환 충돌 테스트·진단 예시
- API 변경에 대한 마이그레이션 기록

## 5.10 개발자가 내부 동작을 조사할 수 있는 도구

Poly 모드 로더는 고급 API만 제공해서는 안 된다. 개발자가 어느 코드가 실제로 실행되는지 확인할 수 있어야 한다.

- **System Explorer:** 등록된 시스템, 기본/대체 제공자, 시스템 의존성 표시
- **Hook Explorer:** 활성 후크, 대상 메서드, 적용 모드, 우선순위 표시
- **Transformation Report:** 각 클래스에 적용된 변환 순서와 성공/실패 사유 표시
- **Event Explorer:** 이벤트 이름, 호출 빈도, 구독자, 취소 여부 및 측정 비용 표시
- **Tick Profiler:** 틱 단계·월드·엔티티/모드 비용의 p50/p95/p99 표시
- **Render Profiler:** 프레임 단계·렌더 패스·청크 메시 생성 비용 표시
- **Resource Reload Report:** 리로드 DAG, 느린 단계, 실패 단계 표시
- **Network Inspector:** 개발 환경에서 메시지 유형·크기·처리 비용을 표시하되 비밀 값은 기본적으로 가림
- **Compatibility Matrix:** Minecraft/Java/Poly 모드 로더/Bridge/모드 버전의 검증 결과 표시
- **API Capability Inspector:** 현재 버전에서 지원/미지원인 API 기능과 사유 표시

도구는 기본적으로 관찰용으로 시작한다. 런타임 동작을 변경하는 작업은 별도 명시적 액션으로 분리하고, 안전하지 않은 조작은 개발 모드에서만 제공한다.

# 6. IntelliJ IDEA 개발 환경 요구사항

**IntelliJ IDEA를 Poly 모드 로더의 공식 개발 워크플로로 지원한다.** 개발자는 IntelliJ에서 저장소를 열고 Gradle 동기화를 완료한 뒤, 별도의 수동 IDE 프로젝트 생성 없이 컴파일·테스트·게임 실행·디버깅할 수 있어야 한다.

## 6.1 기본 기술 선택

- 언어: Java
- 빌드: Gradle Wrapper
- 빌드 스크립트: Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`)
- 프로젝트 구조: Gradle 멀티모듈
- 테스트: JUnit 5 기반을 우선 검토
- IDE: IntelliJ IDEA의 Gradle 지원을 이용
- 형식 검사: 저장소 공통 포매터와 정적 검사 도구를 자동 실행
- Java 버전: 선택한 Minecraft 대상 버전 및 의존 도구가 요구하는 버전으로 결정. 버전을 선택하기 전에 임의로 확정하지 않는다.

Gradle 프로젝트로 관리하는 이유는 IDE에 종속되지 않는 재현 가능한 빌드와 테스트를 유지하면서 IntelliJ에서 자동완성, 디버거, 리팩터링 및 실행 구성을 사용할 수 있기 때문이다. IntelliJ의 Gradle 통합은 프로젝트 가져오기와 동기화, Gradle 작업 실행, 여러 모듈 관리를 지원한다. 공식 문서는 참고 자료에 연결한다.

## 6.2 개발자가 사용할 기본 절차

1. 저장소를 Git으로 복제한다.
2. IntelliJ IDEA에서 저장소의 루트 폴더를 연다.
3. Gradle Wrapper가 지정한 Gradle 버전으로 프로젝트 동기화를 완료한다.
4. 프로젝트가 요구하는 JDK가 설정돼 있는지 확인한다.
5. Gradle 창에서 `test`, `check`, `build` 작업을 실행한다.
6. 제공되는 `runClient` 또는 버전별로 정한 동등 실행 작업으로 테스트 게임을 실행한다.
7. 같은 실행 작업을 디버그 구성으로 시작하여 Poly 모드 로더 Runtime·모드 코드·변환 엔진에 브레이크포인트를 설정한다.
8. 오류가 생기면 콘솔 로그와 `run/` 디렉터리의 진단 파일을 확인한다.

`runClient`, `runServer`는 구현 도구와 대상 게임 버전에서 지원될 경우 사용하는 이름 예시다. 실제 Gradle 작업 이름은 Phase 0에서 사용하는 Minecraft 개발 도구를 검증한 뒤 문서화한다.

## 6.3 IntelliJ 지원 완료 기준

- 저장소 루트를 열면 Gradle 프로젝트로 인식된다.
- 모든 소스 모듈이 올바른 모듈 및 의존성으로 표시된다.
- 공개 API는 코드 자동완성과 JavaDoc을 제공한다.
- 테스트는 IntelliJ와 Gradle CLI 양쪽에서 같은 결과로 실행된다.
- 샘플 모드를 실행하는 구성이 재현 가능하다.
- 런타임과 샘플 모드에 브레이크포인트를 설정할 수 있다.
- 개발자별 IDE 설정 파일이 빌드의 필수 조건이 되지 않는다.
- 팀에서 사용하는 검사·포매터 설정이 저장소에 버전 관리된다.

## 6.4 저장소에 둘 IDE·빌드 설정

```text
polymodloader/
  .editorconfig
  .gitattributes
  .gitignore
  gradlew
  gradlew.bat
  gradle/wrapper/
  gradle/libs.versions.toml
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
```

- Gradle Wrapper 파일은 저장소에 포함한다.
- 라이브러리 버전은 가능한 한 `gradle/libs.versions.toml` 등 한 곳에서 관리한다.
- `build.gradle.kts`에 무관한 설정을 반복하지 않는다.
- 개인 IDE 캐시, 빌드 결과, 실행 월드와 API 키는 저장소에 넣지 않는다.
- IntelliJ 전용 실행 구성이 필요한 경우 저장소에 공유 가능한 `.run/` 구성을 둘 수 있지만, CLI에서도 실행할 수 있어야 한다.
- 대상 Minecraft의 Java 버전과 Gradle 실행에 필요한 Java 버전이 다를 수 있으므로, toolchain 및 Gradle JVM 설정을 구분해 검증한다.

# 7. 코드 일관성과 한눈에 알아보기 쉬운 구조

코드의 일관성은 단순히 들여쓰기만 같은 것이 아니다. 개발자가 새 파일을 열었을 때 어떤 역할인지, 이 코드가 어느 시스템의 소유인지, 어느 계층에 의존할 수 있는지 쉽게 이해할 수 있어야 한다.

## 7.1 모듈·패키지·클래스 책임 규칙

- 모듈 하나는 하나의 명확한 책임을 가진다.
- 패키지 이름은 기능/도메인 기준으로 정한다. 임의의 작성자 이름이나 너무 추상적인 이름을 사용하지 않는다.
- 클래스는 하나의 주된 역할과 명확한 변경 이유를 가진다.
- API 인터페이스와 기본 구현체를 분리한다.
- 버전별 게임 내부 코드는 `version` 또는 `adapter` 계층 밖으로 새어 나오지 않게 한다.
- 로더별 호환 로직을 Native API에 직접 섞지 않는다.
- 클래스 변환 규칙은 모드 로더 본체의 임의 메서드 안에 숨기지 말고 독립적인 규칙 객체/설정으로 관리한다.
- `Utils`, `Helper`, `Common`, `Manager`, `Handler`만으로 역할을 설명하는 모호한 이름은 피한다. 필요하면 `RecipeIndex`, `ModDependencyResolver`, `ClassTransformPlanner`, `WorldTickScheduler`처럼 책임을 드러내는 이름을 사용한다.
- 단, `Manager`나 `Handler`라는 단어 자체를 절대 금지하지는 않는다. 클래스 이름만 봐도 실제 책임이 특정될 때에만 사용한다.

## 7.2 이름 규칙

- 패키지: 소문자 점 표기, 예: `dev.polymodloader.runtime.lifecycle`
- 타입: `UpperCamelCase`, 예: `ModDependencyResolver`
- 메서드/필드: `lowerCamelCase`, 예: `resolveDependencies`
- 상수: `UPPER_SNAKE_CASE`를 프로젝트 전역 규칙으로 사용할지 Phase 1에서 확정하고 통일한다.
- 모드 ID, 시스템 ID, 리소스 ID는 임의 문자열 비교 대신 전용 값 객체를 우선한다.
- 오류는 사람이 읽을 메시지 외에 안정적인 오류 코드도 제공한다. 예: `POLY-TRANSFORM-003`.
- 파일과 클래스는 가능하면 1:1로 대응한다.
- 약어는 JVM, API, ID, JSON처럼 널리 알려진 범위 외에는 피한다.

## 7.3 파일을 한눈에 이해하게 하는 규칙

각 모듈에는 `README.md`를 둔다. 각 README에는 다음 내용이 있어야 한다.

1. **이 모듈이 하는 일** — 한두 문장
2. **무엇을 소유하는가** — 책임지는 서비스와 데이터
3. **무엇을 하지 않는가** — 경계 및 다른 모듈과의 역할 분담
4. **어떤 모듈에 의존하는가** — 의존성 방향
5. **주요 진입점** — 핵심 클래스와 API
6. **빌드·테스트 명령** — 재현 가능한 명령
7. **대표 흐름** — 간단한 다이어그램 또는 단계
8. **알려진 제한** — 미구현 기능, 버전별 제약

큰 하위 시스템은 `docs/architecture/`에 구조도와 실행 흐름을 둔다. 복잡한 클래스 변환 과정은 단순한 긴 설명만 쓰지 말고 단계별 다이어그램도 제공한다.

## 7.4 코드 형식 및 정적 검사

- `.editorconfig`로 들여쓰기, 줄 끝, 문자 인코딩, 파일 끝 규칙을 고정한다.
- 저장소가 정한 Java 포매터를 CI에서 검사한다.
- 코드 스타일 검사와 테스트는 `check` 작업에 연결한다.
- 새 모듈을 추가할 때 기존 규칙을 복사하는 템플릿을 제공한다.
- 검사 규칙을 임의로 우회하지 않는다. 예외가 필요하면 이유와 범위를 코드 리뷰에서 기록한다.
- 자동 포매팅은 의미를 바꾸지 않아야 하며, 포매터 변경은 별도 변경으로 분리한다.

## 7.5 공개 API 문서 규칙

- 모든 공개 타입과 복잡한 공개 메서드에 JavaDoc을 작성한다.
- 문서에는 목적, 입력 조건, 결과, 실패 조건, 스레드 규칙, API 안정성 및 버전 호환성을 표시한다.
- “무엇을 하는지”는 이름으로 드러내고, 주석은 특히 “왜 이렇게 해야 하는지”, “어떤 제약이 있는지”를 설명한다.
- 일반 API와 실험적 API를 명시적으로 구분한다.
- 내부 구현 세부 사항에 대한 의존을 공개 API에 노출하지 않는다.
- 예제 코드는 CI에서 컴파일되는 샘플 프로젝트로 관리하거나, 컴파일 불가 개념 예제임을 눈에 띄게 표시한다.

## 7.6 아키텍처 변경 기록

주요 설계 결정을 `docs/adr/` 아래 Architecture Decision Record(ADR)로 기록한다.

ADR에는 아래를 포함한다.

- 문제와 결정해야 하는 이유
- 고려한 선택지
- 선택한 안과 이유
- 장단점 및 알려진 위험
- 어떤 모듈과 공개 API에 영향이 있는지
- 되돌리거나 변경할 조건

중대한 클래스 로딩, 버전 매핑, 변환 엔진, 브리지 구조의 결정은 ADR 없이 암묵적으로 바꾸지 않는다.


## 7.7 논리적 API 분류와 Java 패키지 구조를 연결한다

다음은 API 카탈로그를 실제 코드로 옮길 때 사용할 기본 패키지 지도다. 이것은 패키지 네이밍과 책임 경계의 기준이며, 처음부터 모든 하위 패키지를 빈 폴더로 만들라는 뜻은 아니다.

```text
dev.polymodloader
├── bootstrap                 # JVM/게임 시작 및 부트스트랩 진입점
├── runtime
│   ├── lifecycle              # 런타임 생명주기
│   ├── discovery              # 모드 검색
│   ├── metadata               # 메타데이터 모델·검증
│   ├── dependency             # 모드 의존성 분석
│   ├── classloading           # 클래스 로딩 정책
│   ├── service                # 런타임 서비스 등록
│   └── diagnostics            # 로딩/실행 진단
├── api
│   ├── system                 # 시스템 계약·교체·기능 조회
│   ├── event                  # 이벤트와 구독
│   ├── schedule               # 틱·작업·스레드 실행 정책
│   ├── content
│   │   ├── registry
│   │   ├── block
│   │   ├── item
│   │   ├── recipe
│   │   ├── loot
│   │   └── component
│   ├── world
│   │   ├── access
│   │   ├── chunk
│   │   ├── dimension
│   │   ├── biome
│   │   └── generation
│   ├── entity
│   │   ├── lifecycle
│   │   ├── data
│   │   ├── ai
│   │   └── tracking
│   ├── gameplay
│   │   ├── interaction
│   │   ├── combat
│   │   ├── movement
│   │   ├── physics
│   │   └── rules
│   ├── client
│   │   ├── input
│   │   ├── camera
│   │   ├── ui
│   │   ├── render
│   │   └── audio
│   ├── resource               # 리소스·팩·리로드·캐시
│   ├── network                # 프로토콜·패킷·동기화
│   ├── persistence            # 저장·스키마·마이그레이션
│   ├── command                # 명령어·권한
│   ├── diagnostics            # 로그·메트릭·프로파일러 계약
│   └── devtools               # 개발 도구가 사용하는 공개 계약
├── internal
│   ├── symbol                 # 버전별 클래스·멤버 심볼 해석
│   ├── access                 # 비공개 멤버 접근 facade
│   ├── hook                   # 고급 후크 계약
│   ├── transform              # 바이트코드 변환 계약·실행
│   └── inspect                # 런타임 내부 상태 조사
├── game
│   └── version
│       └── v1_x_y              # Minecraft 버전별 실제 연결 코드
├── compat
│   ├── fabric                 # Fabric 호환 구현
│   └── forge                  # Forge 호환 구현
└── devtools
    ├── gradle                 # Gradle 플러그인
    ├── cli                    # 명령줄 도구
    └── testkit                # 모드 테스트 도구
```

### 패키지 경계 규칙

- `api.*`는 모드 개발자에게 공개할 계약을 담는다. 가능한 한 특정 로더나 특정 버전의 Minecraft 클래스를 직접 노출하지 않는다.
- `internal.*`은 고급 확장 계약과 저수준 실행부를 담당한다. 공개 여부와 안정성 등급을 명시하고, `internal`이라는 패키지명만으로 안정 API라고 오해하게 하지 않는다.
- `game.version.*`은 선택한 Minecraft 버전에 강하게 결합된 연결 코드다. 버전별 패키지 밖으로 게임 내부 타입이 새어 나가면 아키텍처 검사를 실패시킨다.
- `compat.fabric`과 `compat.forge`는 Native API의 구현 세부에 의존하지 않고, 공통 런타임 계약을 통해 연결한다.
- 패키지 지도는 모듈 구조와 항상 일치해야 한다. 기술적 이유 없이 같은 개념의 API가 여러 패키지에 중복되면 통합한다.
- API 그룹을 나누기 위한 패키지 깊이는 탐색성을 높이는 범위에서만 사용한다. 하나의 작은 타입을 위해 불필요한 다단계 패키지를 만들지 않는다.

# 8. 권장 저장소 및 Gradle 모듈 구조

아래 구조는 목표 구조다. Phase 0부터 모든 모듈을 빈 껍데기로 만들지 않는다. 실제 구현 경계가 생길 때 해당 모듈을 추가한다.

```text
polymodloader/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── .editorconfig
├── .gitignore
├── README.md
├── AGENTS.md
├── docs/
│   ├── PROJECT_PLAN_AND_DESIGN.md
│   ├── ARCHITECTURE.md
│   ├── API_DESIGN.md
│   ├── API_CATALOG.md
│   ├── INTERNAL_API_POLICY.md
│   ├── SYSTEM_REPLACEMENT.md
│   ├── COMPATIBILITY.md
│   ├── PERFORMANCE.md
│   ├── SECURITY.md
│   ├── DEVELOPMENT_IN_INTELLIJ.md
│   ├── architecture/
│   └── adr/
├── poly-bootstrap/
├── poly-runtime-api/
├── poly-runtime-impl/
├── poly-mod-metadata/
├── poly-dependency-resolver/
├── poly-system-api/
├── poly-system-registry/
├── poly-lifecycle-api/
├── poly-event-api/
├── poly-content-api/
├── poly-world-api/
├── poly-entity-api/
├── poly-gameplay-api/
├── poly-render-api/
├── poly-ui-api/
├── poly-resource-api/
├── poly-network-api/
├── poly-persistence-api/
├── poly-command-api/
├── poly-transform-api/
├── poly-transform-engine/
├── poly-version-adapter-api/
├── poly-version-adapter-template/
├── poly-bridge-fabric/
├── poly-bridge-forge/
├── poly-devtools-gradle/
├── poly-cli/
├── poly-testkit/
├── poly-benchmarks/
├── examples/
│   ├── minimal-native-mod/
│   ├── custom-system-mod/
│   └── transform-demo-mod/
├── test-mods/
│   ├── missing-dependency/
│   ├── transform-conflict/
│   ├── client-only-on-server/
│   └── system-replacement/
└── poly-launcher/  # optional; separate from runtime
```

## 8.1 Gradle 의존성 방향

의존 방향은 가능한 한 아래 방향을 따른다.

```text
Public API modules
       ↑
Runtime implementation
       ↑
Bootstrap / game integration

Compatibility bridges ──> Runtime API + Transform API + Version Adapter API
Version adapters ────────> Version Adapter API + selected game integration
Dev tools ────────────────> Metadata/API schemas and build integration
Examples/tests ──────────> Public APIs + testkit
```

- API 모듈이 구체적 런타임 구현체를 의존하지 않는다.
- Fabric/Forge 브리지의 타입이 Poly 모드 로더 Native 공개 API의 필수 타입이 되지 않게 한다.
- 버전별 Minecraft 참조가 일반적인 API 모듈에 들어오지 않게 한다.
- 런처는 가능한 한 런타임의 구현 세부를 직접 소유하지 않는다.
- 순환 의존성을 만들지 않는다. 순환이 생기면 소유권과 계약 경계를 다시 검토한다.

# 9. 이름 공간과 공개 API 명명

프로젝트 식별자는 임시 이름 변경을 감안해 코드 곳곳에 브랜드 문자열을 하드코딩하지 않는다.

- Java package 예시: `dev.polymodloader.*`
- Gradle group 예시: `dev.polymodloader`
- 런타임 식별자: `poly`
- 전용 모드 메타데이터 파일명 제안: `poly.mod.json` — 확정 전 스키마 검토 필요
- 메타데이터 schema version과 API version은 별도로 관리한다.
- 사용자 표시 이름과 기술적인 모드 ID를 구분한다.

공개 API 이름 예시는 다음과 같다.

- `ModDependencyResolver`: 모드 의존성 그래프를 검증하고 로딩 순서를 계산한다.
- `ClassTransformPlanner`: 변환 규칙을 검사하고 실행 계획을 만든다.
- `SystemRegistry`: 시스템 정의와 구현체를 등록한다.
- `WorldAccess`: 지원되는 방식으로 월드 데이터를 조회하거나 변경한다.
- `WorldGenerationProvider`: 월드 생성 확장 지점을 제공한다.
- `RecipeProvider`: 레시피 등록 및 조회 동작을 제공한다.
- `ModDiagnosticReport`: 모드별 검증 결과와 오류를 담는다.

이 이름들은 책임을 명확히 표현하기 위한 예시다. 최종 공개 API는 중복 개념을 줄이고 실제 계약이 확정된 뒤 결정한다.

# 10. 모드 메타데이터 설계

Poly 모드 로더 Native 모드 메타데이터 파일은 임시로 `poly.mod.json`을 사용한다. 명세·검증기·예제가 완성되기 전에는 고정된 표준으로 간주하지 않는다.

예시:

```json
{
  "schemaVersion": 1,
  "id": "example_copper_tools",
  "version": "1.0.0",
  "name": "Example Copper Tools",
  "environment": "both",
  "minecraft": ["TARGET_MINECRAFT_VERSION"],
  "entrypoints": {
    "common": ["com.example.copper.ExampleMod"],
    "client": ["com.example.copper.client.ExampleClientMod"]
  },
  "depends": {
    "poly": ">=0.1.0 <0.2.0"
  },
  "conflicts": [],
  "provides": [],
  "replacesSystems": [],
  "transforms": []
}
```

메타데이터 검증기는 다음을 확인한다.

- 스키마 버전, 필수 필드 및 타입
- 모드 ID 형식 및 중복
- 게임·Java·Poly 모드 로더 API 버전 범위
- 공통/클라이언트/서버 진입점의 유효성
- 의존성 누락과 순환 의존성
- 시스템 교체 선언의 대상과 권한
- 변환 규칙에 선언된 대상 클래스/메서드 정보
- 지원하지 않는 필드와 향후 버전에서 사용할 필드

기존 Fabric/Forge 메타데이터는 각각의 브리지에서 읽고, 가능하면 원본 파일을 직접 덮어쓰지 않는다.

# 11. 모드 로딩 및 시스템 교체 파이프라인

예상 로딩 단계:

1. **Bootstrap:** 대상 게임 버전, Java 환경, 클래스패스 및 런타임을 확인한다.
2. **Discovery:** 모드 디렉터리와 의존 라이브러리를 검색한다.
3. **Metadata Validation:** 모드 정의, 버전 범위, ID, 환경 및 선언된 교체 시스템을 검사한다.
4. **Dependency Resolution:** 의존성 그래프와 초기화 순서를 계산한다.
5. **Compatibility Classification:** 각 모드를 Native/Fabric/Forge 유형으로 분류하고 필요한 브리지를 선택한다.
6. **Transformation Planning:** 모든 모드의 변환 규칙을 수집하고 대상/우선순위/충돌을 분석한다.
7. **Transformation Execution:** 클래스 정의 전의 적절한 시점에 검증된 변환을 수행한다.
8. **System Registration:** 기본 시스템과 대체 시스템의 제공자를 등록한다.
9. **Conflict Resolution:** 같은 시스템을 교체하는 구현이 충돌하면 정책에 따라 해결하거나 시작을 중단한다.
10. **Lifecycle Initialization:** 시스템 및 모드 초기화 단계를 실행한다.
11. **Game Validation:** 최소 필수 시스템이 모두 준비됐는지 확인한다.
12. **Runtime Monitoring:** 모드별 오류·성능 비용·호환 상태를 기록한다.
13. **Shutdown:** 이벤트 구독, 작업, 리소스 및 시스템을 정해진 순서로 정리한다.

핵심 원칙은 시스템 교체와 클래스 변환을 게임 초기화 마지막 순간에 즉흥적으로 처리하지 않는다는 것이다. 가능한 한 시작 전에 계획을 확정하고, 교체 충돌이 발견되면 사람이 이해할 수 있는 이유를 제공한다.

# 12. 클래스 변환 엔진

## 12.1 목적

클래스 변환 엔진은 고수준 API에서 다루기 어려운 변경을 처리하기 위한 저수준 기반이다. 시스템 교체 인터페이스와 고수준 API가 가능한 작업을 굳이 바이트코드 변환으로 구현하지 않는다.

## 12.2 구현 후보

- Java Instrumentation API의 `ClassFileTransformer`
- ASM 같은 Java 바이트코드 분석·변환 라이브러리
- 기존 로더의 변환 규칙을 처리할 필요가 있을 때는 해당 방식의 정확한 의미를 재현하는 브리지 또는 별도 호환 엔진

정확한 선택은 Phase 0의 게임 버전 및 부트스트랩 실험으로 결정한다. 단순히 변환 라이브러리를 도입했다고 기존 Mixin 또는 Forge 변환 규칙과 호환되는 것은 아니다.

## 12.3 변환 규칙 메타데이터

각 변환 규칙은 최소한 다음 정보를 담아야 한다.

- 제공 모드 ID 및 변환 ID
- 목표 Minecraft 버전 범위
- 대상 클래스, 메서드 이름, descriptor, 필요 시 대상 필드
- 적용 위치와 변환 종류
- 사전 조건 및 사후 조건
- 변환 규칙 간 선행 의존성
- 변환 충돌 처리 정책
- 중복 적용 방지 규칙
- 실패 시 오류 코드 및 진단 정보
- 변환 전후 바이트코드 해시 또는 덤프 참조

## 12.4 필수 안전 규칙

- 변환은 클래스 정의 전에 계획하는 방식을 우선한다.
- 이미 정의된 모든 클래스를 언제든 임의로 바꿀 수 있다고 가정하지 않는다.
- 클래스 변환 중 대상 클래스를 재귀적으로 로드하지 않도록 한다.
- 클래스 이름만 확인하지 말고 메서드 descriptor와 사전 조건을 검사한다.
- 실패한 변환을 자동으로 무시하고 정상 실행처럼 보고하지 않는다.
- 변환 후 바이트코드 검증과 게임 통합 테스트를 수행한다.
- 캐시에는 Minecraft·Java·Poly 모드 로더·매핑·모드·변환 규칙의 영향을 반영한다.
- 원본 입력 바이트를 임의 변경하지 않고 새 결과를 반환하는 방식과 라이브러리 계약을 따른다.
- 디버그 모드에서 클래스 덤프, 적용 규칙 목록, 실패 원인을 확인할 수 있어야 한다.

# 13. Fabric 및 Forge 호환 전략

Poly 모드 로더는 호환성을 한 번에 해결한다고 가정하지 않는다. 먼저 최소 실행 실험을 하고 기능별 단계로 확장한다.

## 13.1 Fabric Bridge

조사 및 대응 범위:

- `fabric.mod.json`과 의존성
- 초기화 진입점 및 라이프사이클
- Fabric API 모듈과 이벤트 의미
- Mixin 기반 클래스 변환
- access widener 및 매핑 관련 규칙
- 리소스와 네트워크 서비스
- 중첩/내장 의존성 및 모드별 부트스트랩

## 13.2 Forge Bridge

조사 및 대응 범위:

- Forge 모드 메타데이터와 로딩 단계
- 이벤트 버스 및 이벤트 우선순위
- 레지스트리와 등록 생명주기
- 설정, 네트워크, capability 등 해당 버전의 주요 API
- 접근 변환 및 클래스 변환
- Forge 서비스와 다른 Forge 모드에 대한 의존성

## 13.3 혼합 실행의 핵심 기술 위험

- 양쪽 로더가 같은 게임 클래스를 다른 단계에서 변환할 수 있다.
- 두 로더가 서로 다른 게임 버전이나 다른 매핑 체계를 요구할 수 있다.
- 이벤트 순서, 등록 생명주기와 기본 객체 수명이 다를 수 있다.
- 같은 대상 클래스에 대한 변환 순서가 결과를 바꿀 수 있다.
- 어떤 모드는 특정 로더의 내부 구현이 존재한다는 가정에 의존할 수 있다.
- 클라이언트 전용 API가 전용 서버에 잘못 로딩될 수 있다.

따라서 호환성은 다음 등급으로 기록한다.

- `Verified`: 정해진 테스트 계획 통과
- `Partial`: 동작하지만 알려진 기능 제한 있음
- `Experimental`: 일부 실험만 통과
- `Blocked`: 알려진 문제로 실행 불가
- `Untested`: 검증 안 됨

모드 JAR이 로드되었다는 사실만으로 `Verified`로 표시하지 않는다. 실제 기능, 월드 저장/재불러오기, 렌더링, 서버 접속 등 계획된 테스트를 수행해야 한다.

## 13.4 기존 모드와 Poly 모드 로더 Native 모드의 실행 경로 분리

- 기존 Fabric/Forge 모드는 원래 API 의미와 생명주기를 보존하는 호환 경로를 사용한다.
- Poly 모드 로더 Native 모드는 시스템 계약 및 최적화된 API 경로를 우선한다.
- 공통 게임 상태와 시스템 등록은 명시된 경계를 통해 공유한다.
- 호환성 비용이 성능을 악화시킨다면 해당 경로를 측정하여 보고한다.
- 기존 모드를 자동으로 Native 코드로 변환했다고 주장하지 않는다.

# 14. 성능 설계와 계측

## 14.1 측정 항목

- FPS 및 프레임 시간 p50/p95/p99
- 서버 틱 시간 p50/p95/p99 및 50ms 기준 초과 빈도
- TPS, 청크 생성/로드 시간
- 힙 사용량, 피크 메모리, 할당률, GC 정지 시간
- 게임 초기화 시간, 모드 검색 시간, 변환 시간 및 캐시 적중률
- 이벤트, 틱 작업, 렌더 패스 및 모드별 비용

## 14.2 최적화 원칙

- 이벤트 리스너 목록을 가능한 한 초기화 단계에서 구성하고 반복 조회·임시 할당을 줄인다.
- 월드 변경은 올바른 스레드에서 수행한다.
- 비동기 작업은 안전한 계산을 분리할 수 있을 때만 사용하고 결과 적용 시점과 월드 상태 동기화를 보장한다.
- 렌더링은 리소스 재사용, 배치, 재생성 비용과 드로우 호출 수를 측정한다.
- 캐시에는 명확한 키, 무효화 조건 및 크기 제한이 있어야 한다.
- 시스템 교체 구조가 불필요한 가상 호출이나 객체 생성 비용을 만들지 않는지 프로파일링한다.
- 기존 호환 계층과 Native 경로를 각각 측정한다.

## 14.3 제안 초기 성능 목표

아래는 목표치이며 측정 결과가 아니다. 기준선 및 동등한 기능 구성으로 비교한 뒤 조정한다.

- Poly 모드 로더 Native 테스트에서 기준 환경 대비 프레임 시간 p95 5% 이상 개선 목표
- 서버 틱 시간 p95 10% 이상 개선 목표
- 피크 힙 사용량 10% 이상 절감 목표
- 기능 정확성·월드 데이터·네트워크 상태 회귀 0건

수치가 변동성 또는 측정 오차 안에 있으면 개선되었다고 발표하지 않는다.

## 14.4 벤치마크 절차

1. 동일 하드웨어, OS, Java, Minecraft 버전, 그래픽 설정, 월드, 모드 조합을 기록한다.
2. 초기화·워밍업 구간과 정상 실행 구간을 분리한다.
3. 시나리오별로 충분한 반복 실행을 한다.
4. 평균뿐 아니라 p50/p95/p99와 분산을 저장한다.
5. JVM Flight Recorder 등으로 CPU, 스레드, 할당, GC 병목을 조사한다.
6. JMH는 독립 코드의 마이크로벤치마크에 사용하고 전체 게임 벤치마크를 대체하지 않는다.
7. 원시 결과, 커밋, 실행 인수와 설정을 기록해 회귀를 추적한다.

# 15. 테스트 전략

## 15.1 단위 테스트

- 메타데이터 파싱과 스키마
- 의존성 그래프와 순환 의존성
- 중복 ID와 버전 범위
- 시스템 등록·교체·충돌 규칙
- 라이프사이클 및 이벤트 구독·해제
- 변환 계획과 충돌 탐지
- 캐시 무효화
- 오류 코드 및 진단 보고서

## 15.2 바이트코드 테스트

- 대상 클래스/메서드 누락
- 메서드 descriptor 변경
- 변환 순서 충돌
- 사전 조건 불일치
- 잘못된 바이트코드 결과
- 캐시 손상과 이전 결과 재사용
- 실패 시 원인 모드와 대상 위치를 추적할 수 있는지

## 15.3 Minecraft 통합 테스트

- 게임 시작과 월드 진입
- 블록/아이템/레시피 등록 및 사용
- 시스템 기본 구현을 대체 모드가 교체하는 시나리오
- 시스템 두 개가 같은 교체 지점을 요구하는 시나리오
- 월드 저장·종료·재실행·불러오기
- 클라이언트와 전용 서버 실행
- 클라이언트 전용 모드의 서버 로딩 차단
- 잘못된 메타데이터 및 누락된 의존성의 오류 출력
- 리소스 재로딩 및 네트워크 상태 동기화

## 15.4 혼합 모드 테스트 행렬

- Native 단독
- Fabric 단독
- Forge 단독
- Native + Fabric
- Native + Forge
- Fabric + Forge
- Native + Fabric + Forge

각 조합에 대해 버전, 모드 버전, 의존성, 실행 환경, 기능 테스트 결과와 알려진 문제를 남긴다.

# 16. 개발 단계 및 완료 기준

## Phase 0 — 대상 버전 및 타당성 검증

작업:
- Minecraft 버전 하나를 첫 목표로 선정한다.
- 해당 버전의 요구 Java, 매핑, 클래스/리소스 구조를 확인한다.
- IntelliJ IDEA와 Gradle Wrapper에서 최소 프로젝트가 빌드되는지 검증한다.
- 최소 부트스트랩으로 게임을 시작하는 실험을 수행한다.
- 클래스 변환 개입점을 확인한다.
- Fabric/Forge의 초기화 및 변환 구조를 조사한다.
- Native 모드 시스템 교체와 혼합 로더의 최소 PoC를 각각 시도한다.

완료 기준:
- 대상 버전과 Java/Gradle 조합이 문서화됐다.
- IntelliJ에서 빌드·테스트·실행이 재현된다.
- 첫 실행의 로그와 진단 흐름이 확인됐다.
- 시스템 교체 개념을 검증할 최소 시나리오가 있다.
- 혼합 실행의 가장 큰 기술 위험이 실험 결과로 기록됐다.

## Phase 1 — Bootstrap 및 Runtime Core

작업:
- 런타임 진입점
- 로깅과 진단
- 모드 검색 및 메타데이터 검증
- 의존성 해결
- 초기화/종료 생명주기
- 최소 Poly 모드 로더 Native 샘플 모드 실행

완료 기준:
- Poly 모드 로더 샘플 모드가 선택한 게임 버전에서 동작한다.
- 누락된 의존성, 중복 ID, 잘못된 메타데이터가 구체적인 오류로 보고된다.
- 게임 월드를 저장하고 재불러오는 기본 통합 테스트가 통과한다.

## Phase 2 — 시스템 계약 및 첫 교체 지점

작업:
- `SystemRegistry` 및 시스템 수명 관리
- 기본 구현체와 대체 구현체 계약
- 교체 우선순위·충돌·의존성 규칙
- 최소한 하나의 대표 시스템(예: 레시피 처리 또는 아이템 등록/조회)의 대체 구현 PoC
- 진단 도구와 테스트 샘플

완료 기준:
- 기본 구현과 대체 구현의 차이가 테스트로 확인된다.
- 두 모드가 같은 교체 지점을 요구할 때 충돌이 자동 진단된다.
- 일반 모드 코드에서 게임 내부 클래스 직접 참조 없이 핵심 교체 시나리오를 작성할 수 있다.

## Phase 3 — Transformation Engine PoC

작업:
- 변환 계획, 클래스/메서드 검증 및 변환 실행
- 변환 결과 검사 및 디버그 덤프
- 실패 원인과 적용 규칙 보고
- 캐시의 정확성과 무효화 검증

완료 기준:
- 의도한 단일 클래스 변환의 전후 차이를 검증한다.
- 잘못된 대상은 명확히 실패한다.
- 변환 실패가 조용히 성공으로 처리되지 않는다.

## Phase 4 — IntelliJ SDK 경험과 Native API 확대

작업:
- Gradle 템플릿과 예제 모드
- 콘텐츠·월드·엔티티·게임플레이 API
- 렌더링·UI·네트워크·저장 API의 경계 설계
- 자동 문서 생성
- 정적 검사, 테스트 및 실행 구성을 정리

완료 기준:
- 개발자가 IntelliJ에서 새 모드 프로젝트를 만들고 빌드·실행·디버깅할 수 있다.
- API 문서와 예제의 컴파일 가능성이 검증된다.
- 모듈별 책임이 README 및 아키텍처 맵으로 설명된다.

## Phase 5 — Fabric 및 Forge Bridge 기술 확장

작업:
- Native 구현과 병행해 각 브리지의 지원 범위를 기능별로 확장한다.
- 메타데이터, 초기화, 주요 API, 변환 방식을 대응시킨다.
- 각 모드 유형의 단독 실행 테스트를 구축한다.
- 호환성 결과를 버전별로 기록한다.

완료 기준:
- 명시적으로 선정한 샘플 모드가 정의된 범위에서 실행된다.
- 미지원 기능과 제약이 자동 진단으로 나타난다.
- 각 브리지의 독립 테스트가 CI에서 실행된다.

## Phase 6 — 혼합 실행 및 회귀 안정화

작업:
- Fabric + Forge + Native의 동시 실행 실험과 확장
- 변환 충돌, 이벤트 순서, 등록 및 의존성 문제 해결
- 멀티플레이, 저장, 리로드와 서버 테스트

완료 기준:
- 지정된 조합에 대한 통합 테스트가 통과한다.
- 실패하는 조합은 원인·범위·지원 상태가 기록된다.
- 기능 범위가 확인되지 않은 모드는 완전 호환으로 표기되지 않는다.

## Phase 7 — 성능 최적화와 공개 베타

작업:
- 기준 성능 측정 및 프로파일링
- 확인된 병목 최적화
- 성능 회귀 CI와 벤치마크 리포트
- 설치/업데이트/오류 복구 문서
- 필요 시 별도 런처 GUI 개발

완료 기준:
- 동등한 기능과 동일 조건의 재현 가능한 벤치마크가 있다.
- 지원 Minecraft/Java/모드 조합과 알려진 문제가 공개돼 있다.
- 월드 데이터 및 서버 정확성 테스트가 통과한다.

# 17. 오류·로그·진단 정책

모든 오류는 개발자가 원인을 좁힐 수 있어야 한다.

로그/리포트에 포함할 항목:

- 오류 코드
- 영향받은 모드 및 버전
- Minecraft 및 Java 버전
- 관련 브리지/시스템/클래스 변환 규칙 ID
- 대상 클래스·메서드·시스템
- 실패한 단계와 원인
- 자동 해결을 시도했는지 여부
- 가능한 경우 해결 방법 또는 추가로 필요한 정보

비밀 값, 인증 정보, 임의의 개인 파일 내용을 기본 로그에 출력하지 않는다. 로그 레벨은 `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`처럼 일관되게 사용하고, 개발자용 변환 덤프는 별도 디렉터리로 분리한다.

# 18. 데이터·보안·신뢰성

- 클래스 로더 격리는 완전한 보안 샌드박스가 아니다.
- 네트워크 데이터는 크기·형식·권한·서버 측 검증을 적용한다.
- 월드 스키마 변경에는 버전과 명시적 마이그레이션 경로가 필요하다.
- 모드 및 캐시 파일은 필요 시 해시로 무결성을 확인한다.
- 치명적인 변환 오류나 시스템 교체 충돌은 게임 데이터 손상 가능성을 고려하여 적절히 실행을 중단한다.
- 개발 중 핫 리로드는 안전한 범위부터 지원한다. 클래스 구조 변경·상태 마이그레이션이 필요하면 재시작을 요구할 수 있다.
- 자동 수정 및 복구가 월드 파일을 덮어쓰기 전에 백업과 명시적 사용자 동의를 고려한다.

# 19. AI 코딩 에이전트와 협업하는 규칙

프로젝트에 `AGENTS.md`를 두고 이 문서의 목표와 규칙을 요약한다. Claude Code 등 다른 코딩 에이전트도 동일한 기준 문서를 따르게 한다.

AI 코딩 에이전트는 다음 원칙을 따른다.

1. 변경 전에 관련 모듈과 문서를 읽고 기존 책임 경계를 파악한다.
2. 큰 기능을 구현하기 전에 가장 작은 재현 가능한 PoC를 만든다.
3. 기존에 같은 기능이 있는지 검색한 후 중복 시스템/API를 추가하지 않는다.
4. TODO와 구현 완료를 구분하고, 완료 주장은 테스트 결과를 근거로 한다.
5. 새 공개 API는 JavaDoc, 예제, 테스트와 함께 작성한다.
6. 새 모듈에는 README와 의존성 방향 설명을 추가한다.
7. 클래스 변환·버전별 코드·호환 브리지에 관한 변경은 영향 범위와 회귀 테스트를 포함한다.
8. 오류를 잡기 위해 광범위하게 예외를 삼키지 않는다.
9. 이름과 역할이 불분명한 만능 클래스를 새로 만들지 않는다.
10. 불확실한 Minecraft 내부 동작은 추측으로 구현하지 말고 대상 버전의 디컴파일/매핑/실험/공식 자료를 확인한다.
11. 성능 개선은 프로파일링 전후 결과가 있을 때만 주장한다.
12. 설계 변경은 ADR과 본 문서의 변경 이력에 반영한다.
13. 현재 사용자 요청을 구현하되, Poly 모드 로더의 최종 목표를 단순 모드 관리자나 일반 단일 로더로 축소하지 않는다.
14. 빌드/테스트를 실행하지 못했다면 성공했다고 보고하지 않고, 실행하지 못한 이유와 검증하지 못한 범위를 적는다.

# 20. 문서와 산출물 구조

프로젝트는 코드 외에도 다음 문서를 유지한다.

- `README.md`: 프로젝트 소개 및 현재 상태
- `docs/PROJECT_PLAN_AND_DESIGN.md`: 이 기준 문서
- `docs/ARCHITECTURE.md`: 모듈·시스템 계층 다이어그램
- `docs/API_DESIGN.md`: 공개 API 안정성 및 설계 규칙
- `docs/COMPATIBILITY.md`: 버전별 브리지 지원 범위
- `docs/PERFORMANCE.md`: 벤치마크 방법과 결과
- `docs/SECURITY.md`: 신뢰 경계와 위협 모델
- `docs/DEVELOPMENT_IN_INTELLIJ.md`: IntelliJ 개발 환경 설정
- `docs/adr/`: 주요 아키텍처 의사결정
- 각 모듈의 `README.md`: 모듈 책임, 공개 진입점, 의존성, 테스트 명령
- `AGENTS.md`: AI 코딩 에이전트가 지켜야 할 저장소 규칙

아키텍처 맵은 모듈이 추가·삭제될 때 업데이트한다. 실제 코드와 문서가 어긋나면 문서 또는 코드를 함께 수정하고, 어느 쪽이 현재 사실인지 불명확한 상태를 방치하지 않는다.

# 21. 첫 구현 스프린트

처음부터 전체 목표를 한 번에 구현하지 않는다. 첫 번째 스프린트에서 다음 항목을 완료한다.

1. Git 저장소와 Gradle Wrapper 기반 멀티모듈 최소 프로젝트를 만든다.
2. IntelliJ IDEA에서 프로젝트를 열어 Gradle 동기화·빌드·테스트가 되는지 확인한다.
3. 지원 대상 Minecraft 버전을 선정하고 Java/Gradle 조합을 기록한다.
4. 최소 게임 실행 프로필과 로그 위치를 만든다.
5. Poly 모드 로더 Runtime의 진입점과 버전 어댑터 경계를 만든다.
6. 최소 Native 모드 메타데이터를 읽고 검증한다.
7. 로그에 모드 ID, 버전, 로딩 단계가 표시되도록 한다.
8. 가장 간단한 샘플 모드가 실행되게 한다.
9. 시스템 교체를 확인할 최소 데모를 설계한다.
13. 내부 접근 API의 최소 PoC를 만든다. 대상 메서드의 이름·descriptor 검증, 비공개 멤버 접근 또는 하나의 단순한 메서드 변환을 실행하고, 대상 시그니처가 달라졌을 때 안전하게 실패하는지 확인한다.
14. API 분류 카탈로그를 생성하고 각 기능을 `STABLE`, `PREVIEW`, `VERSION_BOUND`, `INTERNAL`, `UNSAFE` 중 하나로 표시한다.
10. Fabric/Forge 각각에 대해 변환과 초기화의 최소 실험을 수행해, 혼합 실행의 고위험 장애물을 문서화한다.
11. `check` 작업이 자동 테스트와 코드 형식 검사를 수행하도록 한다.
12. 첫 스프린트의 실행 결과, 실패, 다음 단계와 알려진 제한을 문서에 기록한다.

첫 스프린트의 성공은 “모든 모드 호환”이나 “최고 성능”이 아니다. 성공 조건은 IntelliJ에서 재현 가능한 실행 기반, 샘플 Native 모드, 가시적인 진단, 시스템 교체 PoC 계획 및 Fabric/Forge 혼합의 기술 위험에 대한 실험 근거다.

# 22. 릴리스 전 필수 질문

Poly 모드 로더의 기능을 완료 또는 공개 베타로 표시하기 전에 다음 질문에 답해야 한다.

- 어떤 Minecraft 버전과 Java 버전을 지원하는가?
- Minecraft의 최소 기반과 교체 가능한 시스템의 경계가 문서화되어 있는가?
- 각 핵심 시스템에 기본 구현, 확장 계약, 교체 정책 및 충돌 규칙이 있는가?
- IntelliJ에서 새 모드 개발·실행·디버깅이 재현되는가?
- 개발자가 파일과 클래스의 역할을 이름만으로 상당 부분 파악할 수 있는가?
- 공개 API에 JavaDoc, 테스트와 예제가 있는가?
- 클래스 변환이 실패할 때 모드와 대상 메서드가 식별되는가?
- Fabric/Forge/Poly 모드 로더 조합별 실제 검증이 기록되어 있는가?
- 월드 저장·불러오기와 클라이언트/서버 통합 테스트가 통과했는가?
- 성능 개선 주장을 뒷받침하는 재현 가능한 측정이 있는가?
- 미지원 기능과 알려진 위험이 사용자에게 공개되어 있는가?

# 23. 참고 문서

아래의 공식 자료는 개발 환경과 변환/호환성 구조를 검토하기 위한 출발점이다. 구체적인 구현은 반드시 선택한 Minecraft 버전의 요구사항과 문서로 다시 확인한다.

- IntelliJ IDEA Gradle 통합: https://www.jetbrains.com/help/idea/gradle.html
- IntelliJ IDEA Gradle 프로젝트 시작: https://www.jetbrains.com/help/idea/getting-started-with-gradle.html
- Fabric Loom: https://github.com/FabricMC/fabric-loom
- Fabric 개발 환경 설정: https://wiki.fabricmc.net/tutorial:setup
- Fabric Loader 및 Mixin 관련 개발 문서: https://docs.fabricmc.net/develop/loader/
- Fabric Class Tweakers: https://docs.fabricmc.net/develop/class-tweakers/index
- Fabric Access Widening: https://docs.fabricmc.net/develop/class-tweakers/access-widening
- NeoForge 이벤트 개념: https://github.com/neoforged/Documentation/blob/main/docs/concepts/events.md
- Forge 개발 환경: https://docs.minecraftforge.net/en/latest/gettingstarted/
- Sinytra Connector 소개: https://github.com/Sinytra/Connector/blob/26.1.x/docs/docs/introduction.mdx
- Sinytra Connector 호환성 FAQ: https://connector.sinytra.org/faq
- Java `ClassFileTransformer`: https://docs.oracle.com/en/java/javase/26/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html
- Java Instrumentation API 개요: https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/package-summary.html
- ASM 바이트코드 프레임워크: https://asm.ow2.io/
- JMH 벤치마크 프로젝트: https://github.com/openjdk/jmh

참고 프로젝트는 설계 비교 및 기술 위험 분석에 활용한다. Poly 모드 로더는 기존 로더를 단순 복제하는 프로젝트가 아니며, 자체 런타임·시스템 교체 구조·전용 API·일관된 개발 경험을 가진 독자 플랫폼을 목표로 한다.

---

# 최종 프로젝트 정의

**Poly 모드 로더는 Minecraft의 실행 기반은 유지하면서, 그 위에 있는 게임 시스템을 폭넓게 확장·교체·재구성할 수 있도록 만드는 독자 Java 모드 로더 및 모딩 플랫폼이다.**

Poly 모드 로더는 시스템 계약과 기본 구현체를 중심으로 콘텐츠, 월드 생성, 블록·아이템, 엔티티·AI, 물리, 틱, 렌더링, 청크 메시·컬링·조명, UI·입력, 리소스 리로드, 오디오, 패킷·동기화, 저장·마이그레이션, 클래스 로딩·접근 제한·메서드 호출 지점 등 기존의 일반적인 고수준 모딩 API만으로는 세밀하게 다루기 어려운 게임 내부 영역의 수정 경로까지 제공한다. 일반적인 작업은 상세하게 분류된 고수준·시스템 확장 API로 처리하고, 필요할 때는 버전별 검증과 명확한 위험 등급을 요구하는 저수준 내부 접근 및 클래스 변환 API로 게임 내부 동작까지 확장한다.

프로젝트는 **IntelliJ IDEA와 Gradle에서 직접 개발·실행·디버깅할 수 있어야 하며**, 모듈·패키지·클래스·메서드·오류·문서의 이름만 보아도 역할을 이해하기 쉽도록 일관된 코드 규칙을 유지한다. 장기 목표는 기존 Fabric 모드와 Forge 모드 및 Poly 모드 로더 전용 모드의 동일 인스턴스 혼합 실행이며, 호환성과 성능은 실제 테스트·로그·재현 가능한 벤치마크로 검증한다.
