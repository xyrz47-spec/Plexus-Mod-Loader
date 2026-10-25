# ADR-0029: Poly 모드 로더의 키 코드는 GLFW 값으로 고정하고 게임 계층이 바꿉니다

- 상태: 채택 (2026-10-25)
- 관련: ADR-0010(Native API와 게임 계층), ADR-0011(여러 Minecraft 버전)
- 계획: `docs/plans/MINECRAFT_26_3.md` "키 코드"

## 배경

공개 API `dev.polymodloader.api.client.KeyCodes`(`PREVIEW`)는 `ClientRegistrar.keyBinding(path, defaultKey)`의 기본 키를 담습니다. 값은 26.1·26.2의 게임이 쓰는 GLFW 키 코드이고, 게임 계층은 이 값을 `KeyMapping` 생성자에 그대로 넘겼습니다. 내부 SPI `NativeDeclarations.KeyBindingDeclaration.defaultKey`도 "GLFW 키 코드, 없으면 -1"이라고 적었습니다.

Minecraft 26.3은 창과 입력을 GLFW에서 SDL로 바꿨습니다(`lwjgl-glfw` 대신 `lwjgl-sdl`). 26.3 jar에서 `javap -constants`로 확인한 값:

| 키 | 26.2 `InputConstants` (GLFW) | 26.3 `InputConstants` (SDL 스캔코드) |
|---|---|---|
| `KEY_A` | 65 | 4 |
| `KEY_G` | 71 | 10 |
| `KEY_K` | 75 | 14 |
| `KEY_Z` | 90 | 29 |
| `KEY_F6` | 295 | 63 |
| `KEY_F8` | 297 | 65 |
| `KEY_ESCAPE` | 256 | 41 |
| 키 없음(`UNKNOWN`) | -1 | 0 |

두 체계는 숫자가 겹칩니다. 26.3에 GLFW `A`(65)를 넘기면 오류 없이 `F8`이 됩니다. 그리고 `KeyCodes`의 필드는 `static final int`이므로 모드를 컴파일할 때 값이 모드 안에 박힙니다. API의 값을 바꿔도 이미 빌드된 모드는 옛 값을 넘깁니다.

## 결정

1. **Poly 모드 로더의 키 코드는 GLFW 키 토큰의 값입니다.** 게임 버전이 쓰는 키 코드와 상관없이 고정합니다. `KeyCodes`의 값은 바꾸지 않습니다. GLFW 토큰은 미국 배열 기준 물리 키 이름이므로, SDL 스캔코드(물리 키 위치)와 같은 키를 가리킵니다.
2. **게임 계층이 그 버전의 게임 키 코드로 바꿉니다.** 바꾸는 곳은 기본 키를 게임에 넘기는 한 곳(`KeyMapping` 생성)입니다.
   - 26.1·26.2: 그대로 넘깁니다.
   - 26.3: GLFW 키 토큰 → SDL 스캔코드 표를 게임 계층에 둡니다. 표는 `KeyCodes`의 상수만이 아니라 GLFW의 키보드 토큰 전체(글자, 숫자, F1~F25, 방향키, 키패드, 수정 키 등)를 담습니다. 모드가 LWJGL의 `GLFW.GLFW_KEY_*` 값을 직접 넘길 수도 있기 때문입니다.
   - `KeyCodes.UNBOUND`(-1)는 그 버전의 "키 없음"(26.3은 0)으로 바꿉니다.
   - 표에 없는 값은 "키 없음"으로 등록하고, 모드 ID·키 바인딩 이름·받은 값을 경고로 남깁니다. 게임을 멈추지 않습니다. 플레이어가 조작 설정에서 키를 정할 수 있기 때문입니다.
3. `KeyCodes`, `ClientRegistrar.keyBinding`, `KeyBindingDeclaration.defaultKey`의 JavaDoc에 "게임 버전의 키 코드가 아니라 Poly 모드 로더의 키 코드(GLFW 키 토큰 값)이고, 게임 계층이 실행 중인 버전의 키로 바꾼다"고 적습니다. 앞으로 키 코드를 받거나 돌려주는 API(키 입력 이벤트 등)도 같은 규칙을 씁니다.

## 고르지 않은 것

- **그대로 넘기기:** 26.3에서 이미 빌드된 Poly 모드가 엉뚱한 키를 받습니다. 숫자가 겹쳐 오류도 나지 않습니다.
- **새 API(키 이름 `"key.keyboard.a"`나 열거형)로 바꾸고 상수를 폐기:** 이미 빌드된 모드를 위해 1번의 변환이 어차피 필요합니다. 새 API는 필요가 생기면 이 결정 위에 더할 수 있습니다.
- **API의 값을 SDL 스캔코드로 바꾸기:** 26.1·26.2용으로 빌드한 모드와 새로 빌드한 모드가 서로 다른 값을 넘기게 되고, 26.1·26.2 게임 계층이 반대 방향 표를 가져야 합니다.

## 결과

- Native API만 쓰는 Poly 모드는 다시 빌드하지 않고 26.3에서도 같은 기본 키를 받습니다(ADR-0011의 약속).
- 26.3 게임 계층에 키 표와 그 단위 테스트가 생깁니다. 다음 버전이 키 체계를 또 바꾸면 그 버전의 게임 계층에 표를 둡니다.
- 플레이어가 저장한 키(`options.txt`의 `key.keyboard.*` 이름)는 게임이 읽으므로 이 결정과 상관없습니다.
- 마우스 버튼은 `KeyCodes`에 없어 이 결정의 범위 밖입니다.
