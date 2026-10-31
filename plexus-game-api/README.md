# plexus-game-api — Clean Native Game Abstraction Layer

`plexus-game-api`는 마인크래프트 원본 클래스를 직접 노출하지 않고, 블록·아이템·엔티티·명령어·네트워킹·클라이언트 렌더링 기능을 다룰 수 있도록 제공하는 **Plexus 전용 Native API** 인터페이스 모듈입니다.

---

## 1. 설계 원칙: 바닐라 제로 커플링 (Zero Coupling)

- **버전 독립성:**
  `net.minecraft.*` 바닐라 클래스를 전혀 임포트하지 않습니다.
  Native API만으로 개발된 모드는 마인크래프트 버전이 판올림되어도 별도의 재컴파일 없이 호환 레이어(`plexus-game-v26_*`)를 통해 구동될 수 있습니다.
- **불변 뷰(View) 패턴:**
  마인크래프트 원본 객체를 직접 조작하는 대신 `WorldView`, `EntityView`, `PlayerView` 등의 안전한 래퍼 뷰 인터페이스를 통해 안전한 상태 접근을 보장합니다.

---

## 2. 제공 패키지 및 주요 API

| 패키지 | 기능 설명 |
| :--- | :--- |
| `dev.plexusmodloader.api.resource` | 네임스페이스 리소스 식별자 (`ResourceId.of("mymod", "ruby")`) |
| `dev.plexusmodloader.api.content.block` | 블록 정의, 재질, 경도, 드롭 테이블 및 이벤트 콜백 |
| `dev.plexusmodloader.api.content.item` | 아이템 정의, 내구도, 창작 탭(Creative Tab) 바인딩 |
| `dev.plexusmodloader.api.world` | `WorldView`, `BlockPos`, 차원 식별자, 블록 상태 조회 |
| `dev.plexusmodloader.api.entity` | `EntityView`, `PlayerView`, 인벤토리 및 포지션 뷰 |
| `dev.plexusmodloader.api.event` | 플레이어 접속/퇴장, 블록 파괴, 서버 틱 등 핵심 라이프사이클 이벤트 |
| `dev.plexusmodloader.api.command` | 플랫폼 무관 커맨드 트리 빌더 (`CommandNode`) 및 권한 제어 |
| `dev.plexusmodloader.api.network` | 사용자 지정 페이로드 채널 등록, 바이너리 코덱 인코딩/디코딩 |
| `dev.plexusmodloader.api.client` | 키 바인딩 등록, HUD 커스텀 오버레이 렌더링 |
| `dev.plexusmodloader.api.devtools.gametest` | 게임 내 자동화 테스트 하네스 (`GameTest`) 등록 |

---

## 3. 검증 및 테스트 (Gradle Tasks)

```powershell
# 단위 테스트 실행
.\gradlew.bat :plexus-game-api:check

# 실제 게임 런타임에서 Native API 모드 통합 테스트
.\gradlew.bat :plexus-bootstrap:nativeApiGameTests
```
