# Poly 모드 로더 Native API 안내 (0.1.0-alpha.10)

Minecraft 26.2용 Poly 모드가 쓰는 API입니다. 모두 `PREVIEW`이고, Minecraft 타입 없이 `poly-lifecycle-api`만으로 컴파일됩니다. 전체 예제는 [`examples/native-showcase-mod`](../examples/native-showcase-mod)에 있습니다. 구조와 근거는 [ADR-0010](adr/0010-native-api-game-layer.md)에 있습니다.

## 1. 블록과 아이템

```java
BlockHandle block = mod.content().block("copper_bricks", BlockDefinition.builder()
        .strength(2.0f, 6.0f)
        .requiresCorrectTool()
        .sound(BlockSound.COPPER)
        .item(ItemDefinition.builder().creativeTab(CreativeTab.BUILDING_BLOCKS).build())
        .build());
mod.content().item("ruby", ItemDefinition.builder().rarity(ItemRarity.RARE).build());
```

- **선언 시점:** common 엔트리포인트에서만 선언합니다. Poly 모드 로더는 게임이 레지스트리를 동결하기 직전에 블록, 블록 아이템, 아이템 순서로 등록합니다. 그 뒤에 선언하면 `POLY-CONTENT-002`입니다.
- **ID 규칙:** ID는 `<mod id>:<path>`입니다. 중복되거나 잘못된 이름이면 `POLY-CONTENT-001`입니다.
- **양쪽 일치:** 클라이언트와 서버가 같은 순서로 같은 콘텐츠를 선언해야 합니다. 게임이 블록 상태를 번호로 동기화하기 때문입니다.
- **동작 콜백:** 동작은 `BlockBehavior`(`onUse`, `onSteppedOn`, `onPlaced`)와 `ItemBehavior`(`onUse`)로 붙입니다. `PASS`를 반환하면 게임 기본 동작을 따릅니다.

## 2. 리소스와 데이터

모드 jar의 `assets/`는 클라이언트 리소스 팩으로, `data/`는 서버 데이터 팩으로 자동 추가됩니다.
- 팩 ID는 `poly/<mod id>`입니다.
- 필수 팩이며, 바닐라 위에 놓입니다.
- 형식은 실행 중인 게임 버전의 바닐라 형식입니다. 버전마다 형식이 다른 파일(예: 26.3의 `worldgen/feature`, `noise_settings`의 `material_rule`, 셰이더의 `#include`와 `layout(location = N)`)은 바닐라 팩처럼 jar 맨 위 `pack.mcmeta`의 `overlays`로 버전별 폴더에 둡니다. Poly 모드 로더는 게임이 자기 팩을 읽는 방식대로 그 버전의 팩 형식에 맞는 오버레이를 씁니다. `pack.mcmeta`가 없으면 오버레이 없이 지금처럼 동작하고, 읽지 못하면 `POLY-RESOURCE-010` 경고 뒤 오버레이 없이 씁니다. 팩은 형식 범위와 상관없이 항상 켜집니다.
- 한 `pack.mcmeta`가 리소스 팩과 데이터 팩 양쪽에 쓰이고, 오버레이 범위는 클라이언트에서는 리소스 팩 형식으로, 서버에서는 데이터 팩 형식으로 견줍니다(26.3: 리소스 97, 데이터 121). 그래서 `assets/`용과 `data/`용 오버레이 폴더를 따로 둡니다. 예: 예제 `native-showcase-mod`의 `pack.mcmeta`와 `poly_26_3/`.

| 경로 | 용도 |
|---|---|
| `assets/<mod>/blockstates/<block>.json`, `models/block/…`, `textures/block/…` | 블록 모양 |
| `assets/<mod>/items/<item>.json`, `models/item/…`, `textures/item/…` | 아이템 모양 |
| `assets/<mod>/lang/en_us.json` | `block.<mod>.<id>`, `item.<mod>.<id>`, `key.<mod>.<id>`, `key.category.<mod>.keybinds` |
| `data/<mod>/loot_table/blocks/<block>.json` | 블록 드롭(없으면 아무것도 떨어지지 않음) |
| `data/<mod>/recipe/*.json` | 레시피 |
| `data/minecraft/tags/block/mineable/pickaxe.json` (`"replace": false`) | 도구 태그 |

## 3. 월드·엔티티 뷰와 이벤트

- **뷰 타입:** `WorldView`(`blockAt`, `setBlock`, `gameTime`, `players`), `EntityView`, `PlayerView`(`sendMessage`, `give`, `countItem`, `mainHandItem`).
- **뷰 수명:** 콜백 동안만 유효합니다. 저장하지 마세요.
- **이벤트:**
  - `PlayerEvents.JOINED`, `PlayerEvents.LEAVING`
  - `BlockEvents.BROKEN`(플레이어가 부순 뒤)
  - 기존 `ServerEvents.STARTED/TICK/STOPPING`
  - `ClientEvents.TICK`

## 4. 명령어

```java
mod.commands().register(CommandNode.literal("showcase")
        .then(CommandNode.literal("add")
                .then(CommandNode.argument("a", CommandArgumentType.integer())
                        .then(CommandNode.argument("b", CommandArgumentType.integer())
                                .executes(ctx -> ctx.getInteger("a") + ctx.getInteger("b"))))));
```

- **인수 타입:** 정수, 실수, 참/거짓, 단어, 문자열, 나머지 줄, 블록 좌표, 플레이어.
- **권한:** `requiresPermission(0..4)`로 지정합니다.
- **실패 처리:** 실패는 `CommandFailure`를 던져 알립니다.
- **설치 시점:** 서버 시작과 `/reload`마다 설치됩니다.

## 5. 네트워크

```java
PayloadChannel<String> greetings = mod.network().channel("greetings", PayloadCodec.string());
greetings.onServerReceive((message, sender) -> …);   // 서버 스레드
greetings.onClientReceive(message -> …);             // 클라이언트 스레드
greetings.sendToPlayer(player, "hello");
greetings.sendToServer("thanks");                    // 클라이언트에서
```

- **크기 한도:** 서버 방향 32 KiB, 클라이언트 방향 1 MiB입니다. 넘으면 `POLY-NETWORK-003`입니다.
- **잘못된 페이로드:** 버리고 `POLY-NETWORK-004`로 기록합니다.
- **신뢰 경계:** 서버는 클라이언트가 보낸 값을 검증해야 합니다.

## 6. 클라이언트

클라이언트 엔트리포인트에서 `mod.client()`를 씁니다. 전용 서버에서는 `POLY-CLIENT-001`입니다.
- `keyBinding(path, KeyCodes.K)`: 조작 설정에 표시되고, 다시 지정할 수 있습니다.
  기본 키는 게임 버전의 키 코드가 아니라 Poly 모드 로더의 키 코드(GLFW 키 토큰 값)입니다. 게임 계층이 실행 중인 버전의 키로 바꾸므로, 26.3(SDL 스캔코드)에서도 다시 빌드하지 않고 같은 키를 받습니다(ADR-0029).
- `hudLayer(path, canvas -> …)`: 글자와 사각형을 그립니다. 예외가 나면 그 레이어만 끄고 `POLY-CLIENT-003`으로 기록합니다.

## 7. 게임 내 테스트

```java
mod.gameTests().register("glow_block_drops", test -> {
    test.setBlock(BlockPos.of(0, 1, 0), MY_BLOCK);
    test.assertBlock(BlockPos.of(0, 1, 0), MY_BLOCK);
    test.succeed();
});
```

- **실행 방법:** 모드 프로젝트에서 `gradlew gameTests -PpolyTests=<mod id>:*`로 실행합니다. 결과는 Mojang 게임 테스트 서버가 판정합니다.
- **테스트 컨텍스트:** 모의 플레이어(`createMockPlayer`), 블록 사용(`useBlock`), 명령 실행(`runCommand`, 결과값 반환), 지연 실행(`runAfter`)을 쓸 수 있습니다.
- **실행 위치:** 일반 월드에서도 `/test run <mod id>:<이름>`으로 실행할 수 있습니다. 생성 팩이 알려진 팩이기 때문입니다.

## 8. 블록 상태 속성

```java
static final BlockProperty.DirectionProperty FACING = BlockProperty.horizontalFacing("facing");
static final BlockProperty.BooleanProperty POWERED = BlockProperty.bool("powered");

BlockDefinition.builder()
        .facesPlayerWhenPlaced(FACING)   // 놓을 때 플레이어 쪽을 보게 함
        .properties(POWERED)
        .randomTicks()                   // BlockBehavior.onRandomTick
        .build();

world.stateAt(pos).get(POWERED);
world.setProperty(pos, POWERED, true);
```

- **속성 종류:** `bool`, `integer(min, max)`, `horizontalFacing`, `direction`.
- **기본 상태:** 속성의 기본값들이 기본 상태가 됩니다.
- **blockstate 파일:** `"facing=north,powered=false"`처럼 바닐라 형식으로 모델을 고릅니다.
- **바닐라 블록 읽기:** 같은 이름과 타입의 속성이면 게임 블록의 상태도 읽을 수 있습니다(예: 화로의 `facing`).
- **콜백:** `onRandomTick`, `onNeighborChanged`가 추가됐습니다.

## 9. 블록 엔티티와 인벤토리

```java
BlockDefinition.builder().blockEntity(BlockEntityDefinition.builder()
        .inventory(3, "container.my_mod.crate")   // 27칸, 사용하면 상자 메뉴가 열림
        .ticker(entity -> entity.data().putInt("ticks", entity.data().getInt("ticks", 0) + 1))
        .syncToClients()
        .build());

world.blockEntityAt(pos).flatMap(BlockEntityView::inventory).ifPresent(inv -> inv.add(item, 10));
```

- **데이터:** `DataTree`에 정수, 실수, 문자열, 참/거짓, 바이트 배열, 하위 트리를 저장합니다. 월드와 함께 저장됩니다. 바꾼 뒤에는 `markChanged()`를 부릅니다.
- **인벤토리:** 게임의 컨테이너로 동작합니다. 깔때기와 비교기가 작동하고, 부수면 내용물이 떨어집니다. 메뉴는 게임의 상자 메뉴(1–6줄)를 씁니다.
- **틱:** `ticker`는 서버에서 매 틱 실행됩니다.
- **클라이언트 동기화:** `syncToClients()`를 켜면 `markChanged()` 때 데이터가 클라이언트로 갑니다.

## 10. 월드 저장소, 작업 예약, 월드 이벤트

```java
StoredData stats = world.storage(ResourceId.of("my_mod", "stats"));   // 차원별, 월드와 함께 저장
stats.data().putInt("loads", stats.data().getInt("loads", 0) + 1);
stats.markChanged();

world.schedule(20, () -> world.setBlock(pos, ResourceId.minecraft("stone")));   // 20틱 뒤, 서버 스레드
mod.events().register(WorldEvents.READY, world -> …);                      // 차원이 처음 틱할 때
```

- **예약 작업:** 서버가 멈추면 사라지며, 저장되지 않습니다.
- **실패 처리:** 예약 작업에서 예외가 나면 `POLY-SCHEDULE-001`로 기록하고 다음 작업을 계속 실행합니다.

## 11. 몹, 상태 효과, 게임 규칙, 사운드, 파티클

```java
mod.content().entity("golem_friend", EntityDefinition.builder(ResourceId.of("my_mod", "textures/entity/golem_friend.png"))
        .maxHealth(20).panicWhenHurt().temptedBy(SPARK).spawnEgg(CreativeTab.SPAWN_EGGS)
        .behavior(new EntityBehavior() { … })     // onServerTick, onInteract
        .build());
mod.content().effect("glowing_heart", new EffectDefinition(EffectCategory.BENEFICIAL, 0xFFD54F, 20,
        (entity, amplifier) -> entity.setHealth(entity.health() + 1)));
GameRuleKey<Integer> multiplier = mod.content().integerRule("spark_multiplier", 1, 1, 8, GameRuleCategory.DROPS);
mod.content().sound("chime");                       // assets/<mod>/sounds.json

world.spawnEntity(GOLEM, x, y, z);   world.entitiesNear(x, y, z, 5);
world.gameRule(multiplier);          world.playSound(pos, CHIME, 1f, 1f);
world.spawnParticles(ResourceId.minecraft("heart"), x, y, z, 5, 0.3);
mod.events().register(EntityEvents.ALLOW_DAMAGE, (entity, damage) -> damage.amount() < 100);
mod.events().register(EntityEvents.DIED, (entity, cause) -> …);
```

- **몹:**
  - 게임의 길찾기 몹에 표준 AI 목표(배회, 플레이어 보기, 다치면 도망, 공격, 아이템으로 유인)를 고르는 방식입니다.
  - 모양은 플레이어 모델에 64×64 스킨 텍스처를 입혀 그립니다.
  - `/summon`, 생성 알, `spawnEntity`로 생성합니다. 자연 스폰은 아직 설정할 수 없습니다.
  - 26.2는 모드 엔티티에 대한 데이터 변환 스키마가 없어서, 시작할 때 `No data fixer registered for <id>` 오류 로그를 한 줄 남깁니다. 동작에는 영향이 없습니다.
- **생명체 뷰(`LivingEntityView`):** 체력 읽기·설정, `hurt`, 효과 추가·확인을 할 수 있습니다. `EntityView`에는 `isAlive`, `remove`, `teleport`가 추가됐습니다.
- **게임 규칙:** `/gamerule <mod>:<이름>`으로 바꿀 수 있고, 월드에 저장됩니다.
- **파티클:** 옵션이 필요 없는 단순 파티클만 쓸 수 있습니다.

## 12. 월드 생성, 차원, 데이터 레지스트리

모드 jar의 `data/`에 넣은 레지스트리 데이터는 바닐라 형식 그대로 동작합니다.
- 해당 레지스트리: `worldgen/configured_feature`, `worldgen/placed_feature`, `dimension`, `dimension_type`, `damage_type`, `enchantment` 등
- 모드 팩은 `KnownPack`(`poly:<mod id>`)으로 표시되므로, 이 항목들이 월드를 "실험적 설정"으로 만들지 않습니다.
- 같은 모드를 가진 클라이언트는 서버에서 받지 않고 자기 jar에서 읽습니다.
- 26.2는 바닐라가 아닌 차원을 모두 실험적으로 판정합니다. Poly 모드 로더는 **로드된 모드의 이름공간에 있는 차원만** 안정으로 봅니다(`poly:mod_dimension_stability`).

```java
mod.worldgen().addFeature(BiomeSelector.tag(ResourceId.minecraft("is_overworld")),
        GenerationStepName.UNDERGROUND_ORES, ResourceId.of("my_mod", "glow_ore"));   // 기존 바이옴에 광석 추가

living.hurt(ResourceId.of("my_mod", "zap"), 3);                 // 데이터로 정의한 피해 유형
world.otherDimension(ResourceId.of("my_mod", "pocket"));       // 다른 차원
entity.changeDimension(ResourceId.minecraft("the_nether"), x, y, z);
```

- **적용 시점:** 기능 추가는 서버가 월드를 만들 때마다 적용되고, 그 뒤 생성되는 청크에 반영됩니다.
- **오류 처리:** 존재하지 않는 배치 기능이면 `POLY-WORLDGEN-001`로 시작을 멈춥니다.
- **게임 테스트 서버의 제약:** 게임 테스트 서버는 평지 프리셋으로만 차원을 만들어서, 데이터 차원이 없습니다. 모드 차원은 일반 월드(싱글 플레이·서버)에서 확인하세요.
- **게임 테스트 위치:** 생성 게임 테스트도 알려진 팩에 들어가므로, 이제 일반 월드에서도 `/test`로 실행할 수 있습니다.

## 13. 화면(Screen)과 설정 디렉터리

```java
view.openScreen(new ModScreen() {          // ClientView: openScreen, closeScreen, isScreenOpen
    public String title() { return "My Menu"; }
    public void init(ScreenBuilder screen) {
        screen.label("Hello", 10, 10, 0xFFFFFFFF);
        TextInput name = screen.textInput(10, 30, 150, "Steve");
        screen.button("OK", 10, 60, 80, 20, () -> { … name.value() …; screen.close(); });
    }
});
Path config = mod.configDirectory();       // config/<mod id>, 형식은 모드가 정함
```

- **화면 구성:** 화면은 게임의 `Screen` 위에서 동작하고, 버튼과 입력칸은 게임 위젯입니다. `render(HudCanvas)`로 자유롭게 그릴 수 있습니다.
- **닫힘 콜백:** `onClosed`는 Esc, `closeScreen`, 다른 화면으로 바뀔 때 모두 호출됩니다.
- **콜백 위치:** 화면은 `ClientView`가 있는 콜백(예: `ClientEvents.TICK`)에서 엽니다.

## 14. 심화 콘텐츠: 이름 속성, 상태별 빛, 메뉴 배치, 아이템 데이터, 투사체, 자연 스폰

```java
BlockProperty.ChoiceProperty COLOR = BlockProperty.choice("color", "red", "green", "blue"); // 첫 값이 기본값
BlockDefinition.builder()
        .properties(COLOR, LIT)
        .lightLevel(state -> state.get(LIT) ? 15 : 0)        // 상태마다 빛 세기
        .blockEntity(BlockEntityDefinition.builder()
                .menu(MenuLayout.builder(176, 166)
                        .slot(56, 35).outputSlot(116, 35)      // 넣는 칸, 꺼내기만 하는 칸
                        .progressBar(79, 34, MenuLayout.ProgressStyle.ARROW, "progress", "total")
                        .build(), "container.my_mod.grinder")
                .ticker(entity -> { … })
                .build());

ItemComponentKey<Integer> CHARGE = mod.content().itemComponent("charge", ComponentType.INTEGER);
use.stack().set(CHARGE, use.stack().get(CHARGE).orElse(0) + 1);   // ItemUse.stack(), PlayerView.mainHandStack()

mod.content().projectile("bolt", ProjectileDefinition.builder(SPARK).damage(2).behavior(…).build());
player.launchProjectile(ResourceId.of("my_mod", "bolt"), 1.5f, 1.0f);

EntityDefinition.builder(texture).spawnsNaturally(BiomeSelector.ids(ResourceId.minecraft("plains")), 5, 1, 2);
```

- **이름 속성:** `choice` 속성은 블록 상태 파일에서 `color=blue`처럼 씁니다.
  - 같은 이름의 게임 속성(계단의 `half`, 통나무의 `axis` 등)도 문자열로 읽고 바꿀 수 있습니다.
  - 허용되지 않은 값은 `IllegalArgumentException`입니다.
- **상태별 빛:** `lightLevel(state -> …)`는 블록이 만들어질 때 상태마다 한 번 계산합니다. `WorldView.emittedLight(pos)`로 확인할 수 있습니다.
- **메뉴 배치:**
  - 인벤토리 크기는 레이아웃 칸 수와 같습니다. 블록 ID와 같은 이름의 메뉴 타입이 등록됩니다.
  - 배경 텍스처를 주지 않으면 Poly 모드 로더가 게임과 비슷한 회색 패널과 칸 테두리를 그립니다.
  - 진행 막대는 블록 엔티티 데이터의 두 정수 키(값, 최대값)로 채워집니다. 메뉴가 열려 있는 동안 클라이언트로 전달되며 `-32768..32767` 범위입니다.
  - 상자 행(`inventory(rows, …)`)과 함께 쓸 수 없습니다.
  - `PlayerView.openMenu(pos)`는 모드 메뉴와 게임 메뉴(화로 등)를 모두 엽니다.
- **아이템 데이터 컴포넌트:**
  - 값 타입은 `INTEGER`, `BOOLEAN`, `STRING`, `TREE`(`DataTree`)입니다.
  - 값은 스택과 함께 저장·복사·동기화되고, `/give <id>[<mod>:<name>=<값>]` 문법으로도 쓸 수 있습니다. 값이 다른 스택은 합쳐지지 않습니다.
  - `ItemStackRef`는 실제 스택을 바로 바꿉니다. 이름(`setCustomName`)과 내구도(`setDamage`)도 다룹니다. 서버에서 바꾸세요.
- **투사체:** 눈송이처럼 날아가다 처음 맞은 곳에서 피해를 주고 행동 콜백을 부른 뒤 사라집니다.
  - 클라이언트에서는 정의의 아이템 모양으로 그려집니다.
  - `launchProjectile`은 `minecraft:snowball` 같은 게임 투사체도 쏩니다.
- **자연 스폰:** 서버가 월드를 만들 때 선택한 바이옴의 스폰 목록에 추가됩니다(`[Poly 모드 로더] Added N natural spawn(s)`).
  - 스폰 위치 규칙은 같은 분류의 게임 몹을 따릅니다. 생물은 밝은 `#minecraft:animals_spawnable_on` 위, 몬스터는 어두운 곳, 수중 생물은 물속입니다.
  - `WorldView.naturalSpawns(pos, category)`로 확인합니다.
- **블록 상태 번호(버그 수정):**
  - 게임은 블록 상태 번호와 캐시를 `Blocks` 정적 초기화에서 한 번만 만듭니다.
  - Poly 모드 로더가 모드 블록에도 같은 작업을 하지 않아, 모드 블록 변경을 클라이언트로 보내면 연결이 끊기던 문제를 고쳤습니다.

## 15. 게임플레이: AI 목표, 이동·탑승, 피해량 수정, 발전 과제

```java
EntityDefinition.builder(texture)
        .goal(4, SeekGlowBlockGoal::new)       // 몹마다 새 목표 객체
        .rideable(true)                        // 빈손으로 사용하면 탑승, 이동 키로 조종
        .noGravity().notPushable();

final class SeekGlowBlockGoal implements AiGoal {   // canStart, shouldContinue, start, tick, stop
    public boolean canStart(MobView mob) { … }
    public void start(MobView mob) { mob.navigateTo(x, y, z, 1.0); }
}

mod.events().register(EntityEvents.MODIFY_DAMAGE, (entity, damage) -> damage.amount() / 2);

AdvancementTrigger usedWand = mod.content().advancementTrigger("used_wand");
usedWand.trigger(player);                    // data/<mod>/advancement/*.json 의 "trigger": "<mod>:used_wand"
player.hasAdvancement(id); player.grantAdvancement(id); player.revokeAdvancement(id);
```

- **AI 목표:**
  - 게임의 목표 스케줄러가 서버 스레드에서 돌립니다.
  - 숫자가 낮을수록 우선이고, 같은 제어(`MOVE`, `LOOK`, `JUMP`, `TARGET`)를 쓰는 목표는 동시에 돌지 않습니다.
  - 기본 목표의 우선순위는 0(뜨기), 1(도망), 2(공격), 3(유혹), 5(배회), 6–7(둘러보기)입니다.
  - `targetGoal`은 공격 대상을 고르는 목표입니다.
- **`MobView`:** 몹의 뷰는 `MobView`이며 길찾기(`navigateTo`), 시선(`lookAt`), 공격 대상(`target`/`setTarget`)을 다룹니다.
- **이동과 탑승:**
  - `EntityView`에 속도(`velocity`/`setVelocity`, 플레이어는 클라이언트로 전달), 방향, 중력, 땅 위 여부, 탈것·승객(`startRiding`/`stopRiding`)이 추가되었습니다.
  - `rideable(true)`인 몹은 말처럼 조종됩니다. 조종 이동은 말과 같이 탄 플레이어의 클라이언트가 계산합니다.
- **피해량 수정:** `ALLOW_DAMAGE`로 취소되지 않은 피해의 양을 리스너가 등록 순서대로 바꿉니다. 0 이하가 되면 취소입니다.
  - 바뀐 양으로 게임의 피해 처리를 다시 부르므로, 갑옷·저항 효과는 바뀐 양에 적용됩니다.
- **발전 과제:**
  - 발전 과제 파일은 데이터 팩(`data/<mod>/advancement/`)에 둡니다.
  - 모드는 트리거를 선언하고 `trigger(player)`로 조건을 충족시킵니다.
  - `grantAdvancement`/`revokeAdvancement`는 모든 조건을 한 번에 바꿉니다. 서버 플레이어만 가능합니다.
- **게임 테스트:** `GameTestContext.createMockServerPlayer()`는 발전 과제 같은 서버 플레이어 기능을 시험할 수 있는, 연결되지 않은 플레이어를 만듭니다.

## 16. 코드로 만드는 월드 생성, 청크 강제 로드와 청크 이벤트

```java
mod.worldgen().biome("glow_meadow", BiomeDefinition.builder()
        .climate(0.7f, 0.6f, true).skyColor(0x9ad0ff).grassColor(0x7fd16b)
        .feature(GenerationStepName.VEGETAL_DECORATION, ResourceId.minecraft("flower_plains"))
        .spawn(MobCategory.CREATURE, ResourceId.minecraft("sheep"), 8, 2, 4)
        .build());
mod.worldgen().chunkGenerator("terraces", (x, z, column) -> {   // 열 하나씩 채움
    column.fill(column.minY(), 63, ResourceId.minecraft("stone"));
    column.set(64, ResourceId.minecraft("grass_block"));
});
// data/<mod>/dimension/terraces.json:
// {"type": "minecraft:overworld",
//  "generator": {"type": "<mod>:terraces", "biome_source": {"type": "minecraft:fixed", "biome": "<mod>:glow_meadow"}}}

world.setChunkForced(chunkX, chunkZ, true);    // /forceload와 같음, 월드와 함께 저장
world.isChunkLoaded(chunkX, chunkZ); world.biomeAt(pos);
mod.events().register(WorldEvents.CHUNK_LOADED, (world, chunkX, chunkZ) -> { … });   // CHUNK_UNLOADED도 있음
```

- **코드 바이옴:**
  - Poly 모드 로더가 정의를 게임의 바이옴 파일 형식으로 생성 팩(`.poly/generated-pack`)에 씁니다.
  - 데이터 팩 바이옴과 똑같이 차원, 바이옴 태그, `/locate biome`, `/fillbiome`에 쓸 수 있습니다.
  - 생성 팩은 알려진 팩이며, 그 버전은 생성된 파일 내용으로 정해집니다. 그래서 같은 모드를 가진 클라이언트는 서버에서 바이옴을 받지 않습니다.
- **코드 지형:**
  - `TerrainGenerator`는 새 청크의 블록 열을 하나씩 채웁니다.
  - 생성기 종류 `<mod>:<path>`로 등록되며, 데이터 팩 차원에서 `biome_source`와 함께 씁니다.
  - 바이옴 기능(광석·나무), 구조물, 처음 스폰되는 몹은 게임이 다른 생성기와 똑같이 붙입니다.
  - 월드 생성 스레드에서 여러 열이 동시에 호출되므로, 스레드에 안전하고 결정적이어야 합니다.
- **청크 이벤트:** 서버에서 청크가 다 로드된 뒤(`CHUNK_LOADED`)와 내리기 직전(`CHUNK_UNLOADED`)에 불립니다. 자주 불리니 가볍게 처리하세요.
- **빛:** 블록 빛은 블록 정의의 `lightLevel`(상태별 가능, 14절)로 정합니다. 별도의 빛 공급자 API는 두지 않았습니다.

## 17. 클라이언트 그리기: 몹 모델, 블록 엔티티 그리기, 파티클, 카메라, 후처리 셰이더, 동적 텍스처

```java
EntityDefinition.builder(ResourceId.of("my_mod", "textures/entity/wisp.png"))
        .model(EntityModelDefinition.builder(32, 32)            // 게임 모델 좌표: 픽셀, y는 아래로, 땅은 y=24
                .part("body", part -> part.pivot(0, 16, 0).box(0, 0, -3, -3, -3, 6, 6, 6).animation(PartAnimation.BOB))
                .part("ring", part -> part.parent("body").box(0, 12, -4, 0, -4, 8, 1, 8).animation(PartAnimation.SPIN))
                .build());

mod.content().particle("sparkle", ParticleDefinition.of(16).asGlowing().withTint(0xFFD54F));
// assets/my_mod/particles/sparkle.json: {"textures": ["my_mod:sparkle_0", "my_mod:sparkle_1"]}

client.blockEntityRenderer(GRINDER, (entity, canvas) -> {          // 블록 좌표 (0,0,0)~(1,1,1)
    canvas.item(ResourceId.minecraft("cobblestone"), 0.5, 1.3, 0.5, 0.5f, canvas.animationTime() * 4);
    canvas.text("Grinding 40%", 0.5, 1.75, 0.5, 0.02f, 0xFFFFFFFF);   // 카메라를 향함
});
DynamicTexture map = client.dynamicTexture("map", 64, 64);       // my_mod:dynamic/map
map.setPixel(x, y, argb); map.upload(); canvas.drawTexture(map.id(), 4, 4, 64, 64);

mod.events().register(ClientEvents.FIELD_OF_VIEW, fov -> zooming ? fov * 0.3f : fov);
view.setCameraPerspective(CameraPerspective.THIRD_PERSON_BACK);
view.setPostEffect(ResourceId.of("my_mod", "sepia"));            // assets/my_mod/post_effect/sepia.json + shaders/post/*.fsh
```

- **몹 모델:**
  - 상자 모델을 코드로 정의합니다. 좌표는 게임 모델 공간(1/16블록, y가 아래)이고 Blockbench "Modded Entity" 형식과 같습니다.
  - 부품 애니메이션은 `HEAD`(시선), `WALK_SWING`·`WALK_SWING_OPPOSITE`(걷기), `BOB`(오르내림), `SPIN`(회전)입니다.
  - 모델이 없으면 지금처럼 플레이어 모양 모델을 씁니다.
- **블록 엔티티 그리기:**
  - 블록 엔티티가 있는 자기 모드 블록에 하나씩 등록합니다.
  - 매 프레임 렌더 스레드에서 클라이언트 쪽 블록 엔티티로 불립니다. 데이터가 필요하면 `syncToClients`를 켜세요.
  - 아이템(아이템 액자처럼)과 카메라를 향한 글자를 그릴 수 있습니다. 예외를 던지면 그 그리기는 꺼지고 `POLY-CLIENT-003`이 기록됩니다.
- **파티클:**
  - 파티클 종류는 양쪽에서 선언합니다(`content().particle`).
  - 스프라이트는 `particles/<path>.json`과 `textures/particle/`에서 읽습니다. 여러 장이면 수명에 따라 바뀝니다.
  - `spawnParticles`와 `/particle`로 생성합니다.
- **카메라:** `FIELD_OF_VIEW`는 게임의 시야각 계산(달리기·물속) 뒤에 매 프레임 불립니다. 시점은 F5와 같이 바꿉니다.
- **후처리 셰이더:**
  - 모드 리소스의 `post_effect/<path>.json`과 셰이더를 게임의 후처리 체인으로 적용합니다.
  - 불러오지 못하면 `false`를 돌려주고 게임이 오류를 기록합니다.
  - 카메라 대상이나 시점이 바뀌면 게임이 효과를 바꿉니다(크리퍼 관전 등).
- **동적 텍스처:** 픽셀은 Poly 모드 로더가 보관하고 `upload()` 때 클라이언트 스레드에서 게임 텍스처로 보냅니다. 처음 올릴 때 텍스처가 만들어집니다.

## 18. 리로드 리스너, 엔티티 데이터, 저장소 버전·동기화, 권한, 연결 이벤트, 월드 백업

```java
mod.resources().onDataReload("my_mod/spells", files -> { … });    // data/<ns>/my_mod/spells/**.json
mod.resources().onAssetReload("my_mod/tips", files -> { … });     // assets/… (클라이언트)

EntityDataKey MANA = mod.content().entityData("mana", true);      // true: 플레이어 자신의 클라이언트로 동기화
StoredData mana = player.data(MANA); mana.data().putInt("value", 10); mana.markChanged();

StorageOptions options = StorageOptions.DEFAULT
        .withVersion(2, (data, fromVersion) -> { … })            // 옛 버전 데이터를 불러올 때 한 번
        .syncedToClients();
world.storage(ResourceId.of("my_mod", "info"), options);          // 클라이언트에서는 읽기 전용 사본
BlockEntityDefinition.builder().dataVersion(2, (data, from) -> { … });

PermissionNode TELEPORT = mod.permissions().node("teleport", 2);  // my_mod.teleport, 기본 운영자 레벨 2
mod.permissions().provider((player, node) -> PermissionResult.UNDECIDED);
CommandNode.literal("tp").requiresPermission(TELEPORT);  player.hasPermission(TELEPORT);

mod.events().register(ClientEvents.JOINED_WORLD, view -> { … });  // LEFT_WORLD도 있음
view.leaveWorld();                                                // "저장하고 타이틀로"와 같음
mod.gameTests().register("big", 100, TestArea.cube(9), test -> { … });
```

- **리로드 리스너:**
  - 서버 데이터는 시작할 때와 `/reload` 때 게임 데이터 다음에 읽습니다. 시작 때는 메인 스레드, `/reload` 때는 서버 스레드에서 불립니다.
  - 클라이언트 에셋은 시작할 때와 F3+T 때 클라이언트 스레드에서 읽습니다.
  - 파일 ID는 `<namespace>:<디렉터리 아래 경로>`이고, 우선순위가 높은 팩이 이깁니다.
- **엔티티 데이터:**
  - 모든 엔티티(플레이어 포함)에 붙고, 엔티티 저장 데이터의 `poly_entity_data`에 저장됩니다.
  - 플레이어는 리스폰해도 유지됩니다.
  - 동기화 키는 바뀔 때와 접속할 때 그 플레이어의 클라이언트로 갑니다. 다른 플레이어나 몹의 데이터는 클라이언트로 가지 않습니다.
- **저장소 버전:**
  - 저장 데이터에 `poly_version`(예약 키)으로 버전을 기록합니다. 더 옛 버전이면 마이그레이션을 실행하고, 더 새 버전이면 그대로 두고 `POLY-STORAGE-001`을 기록합니다.
  - 같은 저장소 ID를 다른 옵션으로 열면 `IllegalArgumentException`입니다.
  - 블록 엔티티 데이터도 `dataVersion`으로 같게 동작합니다.
- **권한:**
  - 노드는 `<mod id>.<path>`입니다.
  - 공급자(권한 모드)가 등록 순서대로 묻고, 결정이 없으면 기본 운영자 레벨을 씁니다.
  - 명령어 노드의 레벨과 권한 노드는 함께 검사됩니다. 콘솔·명령 블록은 레벨로 판단합니다.
- **연결 이벤트:** 클라이언트가 월드에 들어간 뒤(`JOINED_WORLD`)와 나가기 직전(`LEFT_WORLD`). 서버 쪽은 `PlayerEvents.JOINED`/`LEAVING`입니다.
- **월드 백업:**
  - 월드를 불러오기 전, 지난번과 비교해 Poly 모드 로더 모드가 빠졌거나 버전이 바뀌었으면 게임의 월드 백업(`backups/` zip)을 먼저 만듭니다. 새로 추가된 모드는 바뀐 것으로 보지 않습니다.
  - 기록은 `<월드>/poly/mods.properties`, 정책은 `config/poly/backups.properties`(`backup-on-mod-change=true`)입니다.
  - 오래된 백업은 지우지 않습니다.
- **게임 테스트 공간:** 게임은 테스트 구역 사이를 몇 칸만 띄웁니다. 1칸보다 넓게 쓰는 테스트는 `TestArea`로 공간을 잡아야 이웃 테스트와 겹치지 않습니다. Poly 모드 로더가 그 크기의 빈 구조물을 생성 팩에 씁니다.

## 19. 엔티티 데이터 옵션, 파일 리로드, 로그인 검사, 백업 정리

```java
EntityDataKey MOOD = mod.content().entityData("golem_mood", EntityDataOptions.DEFAULT
        .syncedToTrackers()                                   // 그 엔티티를 보는 모든 클라이언트로
        .withVersion(2, (data, fromVersion) -> { … }));       // 옛 버전 저장 데이터는 불러올 때 변환
mod.resources().onDataFiles("my_mod/banners", ".txt", files -> { … });   // 바이트로 받음, onAssetFiles도 있음
mod.events().register(PlayerEvents.LOGIN_CHECK, (id, name) -> banned(id) ? Optional.of("이유") : Optional.empty());
```

- **추적자 동기화:** 클라이언트가 엔티티를 보기 시작할 때(게임의 엔티티 추적) 한 번, 바뀔 때마다 보내집니다. 클라이언트에서 `entity.data(key)`로 읽습니다.
- **엔티티 데이터 버전:** 키마다 저장 트리에 예약 키 `poly_version`으로 기록합니다. `/data merge`로 넣은 데이터도 같은 변환을 거칩니다.
- **로그인 검사:** 게임의 차단·화이트리스트·인원 검사를 통과한 뒤, 플레이어가 만들어지기 전에 불립니다. 첫 거절 사유가 연결 끊김 화면에 나옵니다.
- **백업 정리:** `config/poly/backups.properties`의 `max-backups-per-world`(기본 10, 0이면 모두 보관)보다 오래된 그 월드의 백업 zip을 지웁니다.

## 20. 블록 모양, 비행·수영 몹, 키프레임 애니메이션

```java
mod.content().block("stone_step", BlockDefinition.builder()
        .shape(BlockShape.box(0, 0, 0, 16, 8, 16))                       // 픽셀, 0..16
        .build());
BlockDefinition.builder().properties(FACING)
        .shape(state -> BASE.or(HANDLE.facing(state.get(FACING))))       // 상태별, 방향으로 돌리기
        .collisionShape(BASE);                                           // 충돌만 다를 때

EntityDefinition.builder(texture).flying(0.4);                           // 공중 길찾기, 떠 있기, 낙하 피해 없음
EntityDefinition.builder(texture).category(MobCategory.WATER_CREATURE).swimming();   // 물속 길찾기, 물에서 숨쉼

EntityModelDefinition.builder(32, 32)
        .part("ring", …)
        .animation("wave", KeyframeAnimation.builder(1)                  // 기본 트리거 ON_DEMAND
                .rotation("ring", 0, 0, 0, 0).rotation("ring", 0.5f, 45, 0, 0).rotation("ring", 1, 0, 0, 0)
                .build());
mobView.playAnimation("wave");                                           // 서버에서; 보는 모든 클라이언트가 재생
```

- **블록 모양:** 윤곽선과 충돌에 쓰이고, 게임은 모양이 덮는 이웃 면만 숨깁니다. 상태별 모양은 시작할 때 상태마다 한 번 계산합니다(`POLY-CONTENT-006`). 블록 모델은 직접 맞게 그립니다.
- **이동 방식:** `Movement.WALK`(기본), `FLY`, `SWIM`. 길찾기, 이동 제어, 기본 배회 목표가 함께 바뀝니다. 공중 속도는 `flyingSpeed`(게임의 `FLYING_SPEED` 속성)입니다. 헤엄치는 몹은 물을 따라 밀리지 않습니다.
- **키프레임 애니메이션:** 게임의 키프레임 애니메이션 엔진으로 그립니다. 회전은 도, 위치는 픽셀(y 위로, Blockbench와 같음), 크기는 배율입니다. 트리거는 `ALWAYS`, `WHILE_MOVING`, `ON_DEMAND`입니다. `ON_DEMAND`는 엔티티 이벤트로 클라이언트에 전해지고, 모델마다 64개까지입니다. `looping()`이 없는 `ON_DEMAND` 애니메이션은 끝나면 멈춥니다.

## 21. 코드 배치 기능, 노이즈 지형의 코드 단계, 코드 구조물

```java
ResourceId boulder = mod.worldgen().feature("boulder", FeaturePlacement.onSurface(1).rarity(4), context -> { … return true; });
mod.worldgen().densityFunction("waves", -1, 1, (x, y, z) -> …);         // 노이즈 설정 JSON에서 {"type": "my_mod:waves"}
mod.worldgen().structure("shrine", StructureDefinition.builder(7, 4, 7, shrine -> { … }).spacing(40, 12).build());
```

- **배치 기능:** 게임의 `Feature` 형식으로 등록하고, 생성 팩에 같은 ID의 configured·placed feature를 씁니다. 배치는 개수, 희귀도, 지표면 또는 높이 범위, 바이옴 필터로 이루어집니다. `addFeature`, `/place feature`, 데이터 팩에서 씁니다.
- **density function:** 게임의 `DensityFunction` 형식으로 등록합니다. 노이즈 생성기의 `final_density` 등에 끼워 게임의 동굴·대수층·광맥·지표 규칙과 함께 지형 모양을 바꿉니다. 게임이 칸 안에서 값을 섞으므로 표면은 한 블록 어긋날 수 있습니다.
- **구조물:** 구조물 형식(구조물마다 하나)과 Poly 모드 로더 공용 조각 형식 `poly:code_structure_piece`를 등록합니다. 생성 팩에 structure와 structure_set(`random_spread`)을 씁니다. 청크 가운데 지표면에 상자를 두고, 상자가 걸친 청크마다 같은 시드로 코드를 다시 불러 그 청크 안만 씁니다. 조각은 구조물 ID와 시드를 저장합니다.
- **고친 결함:** `addFeature`로 바꾼 바이옴을 쓰는 차원에서 기능을 놓을 때 게임이 멈췄습니다. 클라이언트의 월드 만들기·불러오기 화면이 서버 시작 전에 생성기의 기능 색인을 미리 만들기 때문입니다. 평지 세계에서는 드러나지 않았습니다. 이제 Poly 모드 로더가 바이옴을 바꾼 뒤 모든 차원 생성기의 색인을 다시 만듭니다.

## 22. 월드에 그리기, 블록 엔티티의 블록 모델, 사용자 셰이더 파이프라인

```java
ShaderPipeline glow = client.shaderPipeline("glow", ShaderPipelineDefinition.builder(ResourceId.of("my_mod", "core/glow"))
        .blend(ShaderPipelineDefinition.Blend.ADDITIVE).build());   // assets/my_mod/shaders/core/glow.vsh, .fsh
client.worldPainter("compass", canvas -> {
    canvas.line(x1, y1, z1, x2, y2, z2, 0xFFFF5555, 3);
    canvas.box(minX, minY, minZ, maxX, maxY, maxZ, 0xFFFFFFFF, 2, 0x40FFFFFF);
    canvas.text("N", x, y, z, 0xFFFF5555, 1);
    canvas.quads(glow, quads -> quads.vertex(…).vertex(…).vertex(…).vertex(…));
});
client.blockEntityRenderer(GRINDER, (entity, canvas) -> canvas.block(GLOW_BLOCK, 0.5, 2.2, 0.5, 0.35f, yaw));
```

- **월드 그리기:** `LevelRenderer.submitFeatures` 앞에서 매 프레임 불립니다(변환 규칙 `poly:client_world_painters`). 선·상자·점·글자는 게임의 기즈모로 그 프레임에 그려지고, `onTop()`이면 블록 앞에 그려집니다. 셰이더 사각형은 카메라 기준 좌표로 `submitCustomGeometry`에 넘깁니다.
- **블록 모델:** 게임의 `BlockModelResolver`로 블록 상태의 모델을 블록 엔티티 그리기 안에 그립니다. `block(BlockStateView, …)`은 그 상태를, `block(ResourceId, …)`는 기본 상태를 그립니다. 상태는 `defaultState(block)`나 `WorldView.stateAt`으로 얻고 `BlockStateView.with(속성, 값)`으로 바꾼 사본을 만듭니다(월드는 바뀌지 않음).
- **셰이더 파이프라인:** 게임의 `RenderPipeline`과 `RenderType`을 처음 쓸 때 만듭니다. 정점 형식은 위치·색(텍스처가 있으면 위치·텍스처 좌표·색)이고, 사각형을 그립니다. 바인드 그룹은 `GLOBALS`와 `MATRICES_PROJECTION`, 텍스처가 있으면 `Sampler0`…, 모드 유니폼이 있으면 `ModUniforms`입니다. 셰이더가 컴파일되지 않으면 `POLY-CLIENT-006`입니다.
- **텍스처:** `ShaderPipelineDefinition.Builder.texture(id)`를 순서대로 `Sampler0`~`Sampler3`에 연결합니다. `RenderSetup.withTexture`로 게임의 텍스처 관리자에서 가져오므로 리소스 텍스처와 동적 텍스처를 모두 씁니다.
- **모드 유니폼:** `uniform(이름, 형식, 값)`이 std140 블록 `ModUniforms`의 필드가 됩니다. 파이프라인마다 유니폼 버퍼 하나를 두고, `LevelRenderer.submitFeatures` 앞에서 매 프레임 값을 올립니다(렌더 패스 밖이어야 버퍼에 쓸 수 있음). 그리기 때는 `RenderPass.setPipeline` 반환 직전에 그 파이프라인이면 버퍼를 `ModUniforms`로 연결합니다(변환 규칙 `poly:client_shader_uniforms`). 값 공급자가 예외를 던지거나 개수가 틀리면 `POLY-CLIENT-007`을 한 번 기록하고 마지막으로 올바른 값을 씁니다.

## 23. 접속 준비(configuration) 단계의 교환

```java
ConfigurationExchange<String> handshake = mod.network().configurationExchange("handshake", PayloadCodec.string())
        .serverRequest((id, name) -> Optional.of("version?"))
        .serverCheck((id, name, reply) -> reply.isPresent() ? Optional.empty() : Optional.of("Install My Mod to join."));
handshake.clientAnswer(request -> "my_mod 1.2.0");                    // client 엔트리포인트
```

- **진행:** 게임의 `ConfigurationTask`로 실행됩니다(변환 규칙 `poly:configuration_tasks`, `poly:configuration_payloads`, `poly:client_configuration_payloads`). 레지스트리 동기화 뒤, 플레이어를 월드에 넣기 전입니다. 과제가 요청을 보내고, 답이나 대기 시간이 지나기를 서버 틱마다 확인한 뒤, 검사 결과에 따라 다음 과제로 넘어가거나 연결을 끊습니다.
- **메시지:** 페이로드 채널과 같은 코덱·크기 한도(클라이언트 방향 1 MiB, 서버 방향 32 KiB)를 씁니다.
- **기본 동작:** `serverCheck`가 없으면 답이 없는 클라이언트를 거절합니다. 모드가 없는 클라이언트는 답하지 않으므로 대기 시간 뒤 거절됩니다.
- **검증 범위:** 싱글 플레이 통합 서버의 교환(요청, 답, 들여보냄)은 실제 클라이언트로 확인했습니다. 거절과 대기 시간 초과는 단위 테스트로 확인했습니다.

## 24. 이벤트 우선순위와 결과, 상호작용 이벤트

```java
mod.events().register(BlockEvents.BREAK, EventPriority.HIGH, (world, pos, block, player) ->
        claimed(pos) ? EventResult.DENY : EventResult.PASS);      // 먼저 결정
mod.events().register(BlockEvents.BROKEN, (world, pos, block, player) -> …);   // 우선순위 없으면 NORMAL
```

- **우선순위:** `EventPriority`의 `HIGHEST`, `HIGH`, `NORMAL`, `LOW`, `LOWEST` 순서로 실행합니다. 같은 우선순위에서는 등록 순서(엔트리포인트에서 등록하면 모드 로드 순서)입니다. 우선순위 없이 등록하면 `NORMAL`입니다. NeoForge의 `EventPriority`와 이름·순서가 같고, Fabric의 이벤트 단계(phase)는 브리지가 이 우선순위로 옮깁니다(기본 단계 = `NORMAL`).
- **결정 이벤트:** 리스너가 `EventResult`(`PASS`/`ALLOW`/`DENY`)나 `InteractionResult`를 돌려주는 이벤트입니다. 처음으로 `PASS`가 아닌 값을 돌려준 리스너가 결과를 정하고, 그 뒤 리스너는 불리지 않습니다. 모두 `PASS`면 게임이 원래대로 진행합니다. `ALLOW`는 남은 리스너를 건너뛰고 진행하라는 뜻이며, 게임 자체의 검사(도구, 권한 등)는 그대로 적용됩니다.
- **실패:** 결정 리스너가 예외를 던지면 `POLY-EVENT-001`, `null`을 돌려주면 `POLY-EVENT-003`으로 기록하고 그 리스너를 비활성화합니다. 둘 다 `PASS`로 칩니다.
- **상호작용 이벤트(서버):** 서버가 플레이어의 클릭을 처리하기 전에 불립니다(연결된 플레이어와 모의 플레이어 모두).

```java
mod.events().register(InteractionEvents.USE_BLOCK, (player, hand, stack, pos, face) -> InteractionResult.PASS);
mod.events().register(InteractionEvents.USE_ITEM, (player, hand, stack) -> InteractionResult.PASS);
mod.events().register(InteractionEvents.USE_ENTITY, (player, hand, stack, target) -> InteractionResult.PASS);
mod.events().register(InteractionEvents.ATTACK_ENTITY, (player, target) -> EventResult.PASS);
mod.events().register(BlockEvents.PLACE, (world, pos, block, player) -> EventResult.PASS);    // 놓기 전
mod.events().register(BlockEvents.PLACED, (world, pos, block, player) -> { … });              // 놓은 뒤
```

  - `USE_BLOCK`/`USE_ITEM`/`USE_ENTITY`: `PASS`면 다음 리스너와 게임이 처리합니다. `SUCCESS`/`CONSUME`은 처리된 것으로 끝내고(게임 동작 — 상자 열기, 블록 놓기, 먹기 — 없음), `FAIL`은 거절로 끝냅니다. `USE_BLOCK`은 게임이 시도하는 손마다 한 번(주 손, 주 손이 넘기면 보조 손) 불립니다. 결과가 정해지면 클라이언트 예측을 바로잡도록 인벤토리를 다시 보냅니다.
  - `ATTACK_ENTITY`: `DENY`면 피해·넉백·내구도 소모가 없습니다.
  - `BlockEvents.BREAK`: 블록을 없애기 전, 게임의 도구·관리자 블록·모험 모드 검사보다 먼저 불립니다. `DENY`면 블록이 남고, 클라이언트에 블록(과 블록 엔티티)을 다시 보냅니다. 부순 뒤는 기존 `BROKEN`입니다.
  - `BlockEvents.PLACE`/`PLACED`: 블록 아이템(침대·문 포함)으로 놓을 때. `PLACE`는 게임이 자리를 확인한 뒤, 블록을 두기 전입니다. `DENY`면 아이템이 그대로 남고 클릭 결과는 `FAIL`입니다. `PLACED`는 블록 엔티티 데이터 적용과 아이템 소모까지 끝난 뒤입니다. 디스펜서처럼 플레이어가 없는 배치에는 불리지 않습니다.
  - 변환 규칙: `poly:use_block`(`ServerPlayerGameMode.useItemOn` 진입), `poly:use_item`(`useItem` 진입), `poly:use_entity`(`Player.interactOn` 진입), `poly:attack_entity`(`Player.attack` 진입), `poly:block_break`(`ServerPlayerGameMode.destroyBlock` 진입, `poly:block_broken`보다 먼저), `poly:block_place`(`BlockItem.place` 안의 `placeBlock` 호출 바꾸기), `poly:block_placed`(`BlockItem.place` 반환 직전).
  - 클라이언트 쪽 예측(클라이언트가 먼저 그리는 결과)은 아직 이벤트가 아닙니다. Fabric API의 `UseBlockCallback` 등은 양쪽에서 불리므로, 브리지는 클라이언트 쪽을 따로 다뤄야 합니다.
  - 게임 테스트: `GameTestContext.interactBlock`, `interactItem`, `interactEntity`, `attackEntity`, `breakBlock`은 `createMockServerPlayer()` 플레이어가 클라이언트 입력처럼 행동하게 합니다(이벤트 포함, 주 손).
- **기존 이벤트:** `EntityEvents.ALLOW_DAMAGE`(모든 리스너 실행, 하나라도 `false`면 취소), `MODIFY_DAMAGE`(이전 결과를 이어받음), `PlayerEvents.LOGIN_CHECK`(처음 거절 사유)는 원래 규칙을 유지합니다. 우선순위는 이 이벤트에도 적용됩니다.

## 25. 서버·월드·리스폰·엔티티 생명주기 이벤트

```java
mod.events().register(ServerEvents.STARTING, server -> …);          // 월드를 만들기 전
mod.events().register(ServerEvents.STOPPED, server -> …);           // 월드를 닫은 뒤
mod.events().register(WorldEvents.LOADED, world -> …);              // SAVING, UNLOADED도 있음
mod.events().register(PlayerEvents.COPY, (oldPlayer, newPlayer, returnedFromEnd) -> …);
mod.events().register(PlayerEvents.RESPAWNED, (player, returnedFromEnd) -> …);
mod.events().register(EntityEvents.JOINED_WORLD, (entity, world) -> …);
```

- **서버 순서:** `STARTING` → `STARTED` → `TICK`(반복) → `STOPPING` → `STOPPED`. `STARTING`은 레지스트리와 데이터 팩을 불러온 뒤, 월드를 만들기 전입니다(변환 규칙 `poly:worldgen_features`의 `MinecraftServer.createLevels` 진입 훅). `STOPPED`는 플레이어와 월드를 저장하고 월드를 닫은 뒤입니다(`poly:server_stopped`, `stopServer` 반환 직전).
- **월드 순서(차원마다):** `LOADED`(모든 차원을 만든 뒤, 첫 틱 전, `poly:world_loaded`) → `READY`(첫 틱) → `SAVING`(자동 저장·`/save-all`·정지 때 저장 직전, `poly:world_saving`) → `UNLOADED`(정지 중 저장한 뒤 닫기 직전, `poly:world_unloaded`가 `stopServer` 안의 `ServerLevel.close()` 호출을 바꿈).
- **리스폰:** 게임은 리스폰할 때 새 플레이어 객체를 만듭니다. `COPY`는 그 객체를 만드는 중, 게임이 남기는 것(`keepInventory`, End에서 나올 때는 전부)과 Poly 모드 로더의 `EntityDataKey` 데이터를 복사한 뒤 불립니다. 그 밖에 모드가 플레이어 객체마다 들고 있는 것을 여기서 옮기세요. `RESPAWNED`는 새 객체가 월드에 들어간 뒤입니다(`poly:player_respawned`, `PlayerList.respawn` 반환 직전). `returnedFromEnd`는 End 출구로 살아서 나왔을 때 `true`, 죽은 뒤면 `false`입니다.
- **엔티티:** `JOINED_WORLD`는 플레이어·몹·아이템·투사체 등 모든 엔티티가 서버 월드에 추적되기 시작할 때(생성, 청크와 함께 로드, 차원 이동) 불립니다(`poly:entity_joined_world`, `ServerLevel.EntityCallbacks.onTrackingStart`의 첫 호출 바꾸기). 리스너 안에서 엔티티를 없애거나 옮기지 말고 `WorldView.schedule`로 미루세요. 취소할 수 없습니다.
- **고친 것:** 리스폰한 플레이어의 `EntityDataKey` 데이터가 사라지던 문제(게임이 새 객체에 옛 엔티티 번호를 주는데, 데이터가 번호로 비교되는 키에 묶여 있었음), 서버 플레이어가 죽어도 `EntityEvents.DIED`가 불리지 않던 문제(`ServerPlayer.die`는 `LivingEntity.die`를 부르지 않음, 새 규칙 `poly:player_death`)를 고쳤습니다.
- **게임 테스트:** `GameTestContext.createConnectedMockPlayer()`는 메모리 연결로 접속한 생존 모드 플레이어를 만들고(`JOINED` 발생), `respawn(player, returnedFromEnd)`는 클라이언트가 사망·엔딩 화면에서 보내는 요청으로 리스폰시키며, `disconnect(player)`는 내보냅니다(`LEAVING` 발생). 접속한 플레이어는 진짜 플레이어처럼 월드 전체에 영향을 주므로(멀리 있는 몹이 사라지고 근처에 몹이 생김) 테스트 함수가 끝나기 전에 `disconnect`하세요. 남은 플레이어는 테스트가 끝날 때 내보냅니다.

## 26. 태그

```java
static final TagKey WARDED = TagKey.block(ResourceId.of("my_mod", "warded"));
mod.content().addToTag(WARDED, ResourceId.of("my_mod", "warded_block"));                 // 코드로 항목 추가
mod.content().addToTag(TagKey.block(ResourceId.minecraft("mineable/pickaxe")), ResourceId.of("my_mod", "warded_block"));

world.isBlockIn(pos, WARDED);                                   // 블록 태그
player.mainHandStack().isIn(TagKey.item(ResourceId.minecraft("logs")));   // 아이템 태그
entity.isIn(TagKey.entityType(ResourceId.minecraft("skeletons")));         // 엔티티 타입 태그
world.tags().contains(tag, id); world.tags().entries(tag);      // 아무 항목, 전체 목록
```

- **키:** `TagKey`는 이름일 뿐입니다(`#namespace:path`). 항목은 데이터 팩과 모드 jar의 `data/<ns>/tags/<block|item|entity_type>/<path>.json`, 그리고 `addToTag`에서 오고, `/reload`로 바뀝니다. 블록·아이템·엔티티 타입 태그를 지원합니다.
- **조회:** 서버에서는 불러온 데이터, 클라이언트에서는 서버가 보낸 사본을 읽습니다. 중첩 태그도 풀어서 봅니다. 모르는 태그·항목은 "없음"입니다. 블록 위치에 아이템 태그를 묻는 것처럼 종류가 다르면 `IllegalArgumentException`입니다.
- **코드로 추가:** `addToTag`는 Poly 모드 로더 생성 데이터 팩(`.poly/generated-pack`)에 `"replace": false` 태그 파일을 씁니다. 같은 태그에 여러 번, 여러 모드가 더하면 합쳐집니다. 항목은 선택 항목(`"required": false`)으로 쓰고, 레지스트리가 얼 때 등록되지 않은 항목은 `POLY-CONTENT-007`로 알립니다. 그 항목만 빠지고 태그의 나머지는 그대로 불러옵니다.

## 27. 모드 창작 탭

```java
ContentHandle tab = mod.content().creativeTab("showcase", CreativeTabDefinition.builder()
        .icon(ResourceId.of("my_mod", "glow_block"))                        // 없으면 첫 항목
        .entries(ResourceId.of("my_mod", "glow_block"), ResourceId.of("my_mod", "spark"))
        .entry(ResourceId.minecraft("amethyst_shard"))                     // 게임 아이템도 됨
        .build());
// 언어 파일: "itemGroup.my_mod.showcase": "My Mod"
```

- **게임 탭:** 지금처럼 `ItemDefinition.Builder.creativeTab(CreativeTab.…)`으로 게임 탭 끝에 넣습니다. 그대로 동작합니다.
- **페이지:** 게임의 창작 화면에는 탭 자리가 14개뿐이고 모두 게임이 씁니다. 그래서 모드 탭은 선언 순서대로 추가 페이지 1, 2, …에 놓입니다(페이지마다 위·아래 줄의 왼쪽 다섯 칸, 10개). 오른쪽 탭(저장한 핫바, 검색, 관리자 아이템, 인벤토리)은 모든 페이지에 보입니다. 모드 탭이 있으면 화면 위 탭 줄 위에 `<`/`>` 버튼이 생기고, 넘기면 그 페이지의 첫 탭을 고릅니다(`client.CreativeTabPaging`).
- **변환 규칙:** `poly:creative_tab_pages`(`CreativeModeTabs.validate` 진입: 게임의 위치 중복 검사는 페이지를 모르므로, 모드 탭이 있으면 Poly 모드 로더가 페이지마다 검사하고 게임 검사를 건너뜀), 클라이언트 `poly:client_creative_tabs_clicked`/`_released`/`_hovered`/`_drawn`(창작 화면의 네 탭 반복문에서 `CreativeModeTabs.tabs()` 호출을 현재 페이지 탭으로 바꿈), `poly:client_creative_page_buttons`(`init` 반환 직전).
- **내용:** 게임의 아이템 생성기 형식이 보호된 타입이라, 탭은 비어 있게 만들고 게임이 탭을 채울 때마다(`CreativeModeTab.buildContents` 반환 직전) 항목을 덧붙입니다. 검색 탭에도 나옵니다. 레지스트리가 얼 때 등록되지 않은 항목·아이콘은 `POLY-CONTENT-008`로 알리고 뺍니다(아이콘은 첫 항목으로 대신). 항목이 하나도 남지 않으면 탭을 등록하지 않습니다.
- **검증 범위:** 등록과 페이지 검사는 게임 테스트 서버에서 확인했습니다(`Registered 1 creative tab(s) from mods on 1 extra page(s)`). 클라이언트 쪽 규칙과 훅은 단위 테스트 `BuiltInRulesTest`가 실제 26.2 클래스에 적용해 대상·훅 시그니처·접근자를 확인합니다. 화면에 보이는 모습(버튼 위치, 탭 그림)은 창을 여는 테스트를 돌리지 않아 아직 사람이 확인하지 않았습니다.

## 28. 아이템 툴팁

```java
mod.events().register(ClientEvents.ITEM_TOOLTIP, (stack, lines, advanced) -> {   // client 엔트리포인트
    if (stack.isIn(SPARKLY)) {
        lines.add(1, "§bSparkly");                                                  // 이름 바로 아래
    }
});
```

- **시점:** 클라이언트 스레드에서, 게임이 아이템 툴팁 줄을 만들 때마다(툴팁이 보이는 프레임마다, 창작 검색 때도) 게임의 줄 뒤에 불립니다(변환 규칙 `poly:client_item_tooltip`, `ItemStack.getTooltipLines` 반환 직전). 가볍게 처리하세요. 게임이 툴팁을 숨기는 아이템(`tooltip_display`의 `hide_tooltip`, 창작 모드 밖)에는 불리지 않습니다.
- **줄:** `TooltipLines`는 첫 줄이 아이템 이름이고, 일반 텍스트로 읽고 씁니다. `§7` 같은 서식 코드는 색으로 그려집니다. 게임이나 다른 모드가 넣은 줄은 지우지 않는 한 서식을 유지합니다. `stack`은 클라이언트의 사본이니 읽기만 하세요.
- **검증 범위:** 규칙과 훅은 `BuiltInRulesTest`, 줄 편집은 `ItemTooltipsTest`로 확인했습니다. 실제 툴팁 화면은 창을 여는 테스트를 돌리지 않아 확인하지 않았습니다.

## 오류 코드

| 코드 | 의미 |
|---|---|
| `POLY-EVENT-001` / `003` | 리스너가 예외를 던짐 / 결정 이벤트 리스너가 `null`을 돌려줌 (둘 다 그 리스너를 비활성화) |
| `POLY-CONTENT-001` / `002` | 잘못되었거나 중복된 콘텐츠 ID / 등록이 닫힌 뒤 선언 |
| `POLY-CONTENT-003` / `004` | 창작 탭에 추가할 수 없음 / 팩 종류를 알 수 없음 |
| `POLY-CONTENT-005` | 이 게임 버전에서 메뉴 타입이나 스폰 위치 규칙을 만들 수 없음 |
| `POLY-CONTENT-006` | 블록 모양 함수가 예외를 던지거나 `null`을 돌려줌 |
| `POLY-CONTENT-007` | `addToTag`로 태그에 넣은 항목이 레지스트리가 얼 때 등록되어 있지 않음(그 항목만 빠짐) |
| `POLY-CONTENT-008` | 모드 창작 탭의 항목이나 아이콘이 레지스트리가 얼 때 등록된 아이템이 아님(그 항목만 빠짐, 항목이 없으면 탭도 빠짐) |
| `POLY-COMMAND-001` / `002` | 두 모드가 같은 최상위 명령어를 등록(경고, 트리 병합) / 등록이 닫힌 뒤 |
| `POLY-NETWORK-001` ~ `004` | 채널 ID 오류, 연결 전 송신 또는 닫힌 뒤 선언, 크기 초과, 해독 실패 |
| `POLY-NETWORK-005` | 접속 준비 교환의 모드 코드가 예외를 던짐(요청 실패는 건너뜀, 답 실패는 무응답, 검사 실패는 거절), 또는 잘못된 대기 시간 |
| `POLY-GAMETEST-001` / `002` | 잘못된 테스트 이름·시간 / 등록이 닫힌 뒤 |
| `POLY-CLIENT-001` ~ `004` | 서버에서 클라이언트 API 사용, 클라이언트 ID 오류·등록 종료, HUD 레이어 실패, 키 바인딩 추가 실패 |
| `POLY-CLIENT-005` | 동적 텍스처를 클라이언트 스레드가 아닌 곳에서 올림, 또는 파티클·후처리 연결 실패 |
| `POLY-CLIENT-006` | 모드 셰이더 파이프라인을 만들거나 컴파일하지 못함(한 번 기록, 그 사각형은 건너뜀) |
| `POLY-CLIENT-007` | 모드 셰이더 유니폼 값이 예외를 던지거나 개수가 틀림, 또는 업로드 실패(한 번 기록, 마지막 올바른 값을 씀) |
| `POLY-CLIENT-008` | 키 바인딩의 기본 키 코드에 해당하는 키가 실행 중인 게임 버전에 없음(경고, 그 키 바인딩은 키 없이 시작, ADR-0029) |
| `POLY-BOOTSTRAP-004` / `005` | 게임 계층 jar를 찾을 수 없음 / 게임 계층 시작 실패 |
| `POLY-SCHEDULE-001` | 예약 작업이 예외를 던짐(기록 후 계속) |
| `POLY-WORLDGEN-001` / `002` / `003` | 모르는 배치 기능 / 등록이 닫힌 뒤 선언 / 바이옴 기능·스폰 목록에 접근할 수 없음 |
| `POLY-WORLDGEN-004` | 잘못되었거나 중복된 생성기·바이옴·배치 기능·density function·구조물 ID, 또는 density function 범위가 거꾸로 |
| `POLY-WORLDGEN-005` | 코드 배치 기능·density function·구조물이 예외를 던짐(한 번 기록, 그 자리는 건너뜀) |
| `POLY-RESOURCE-001` / `002` / `003` | 잘못된 리로드 디렉터리 / 등록이 닫힌 뒤 선언 / 파일을 읽지 못함·리스너 실패(기록 후 계속) |
| `POLY-RESOURCE-010` | 모드 jar의 `pack.mcmeta`를 읽지 못함(경고, 오버레이 없이 팩을 씀) |
| `POLY-PERMISSION-001` / `002` / `003` | 잘못되었거나 중복된 권한 노드 / 등록이 닫힌 뒤 선언 / 공급자 예외(그 공급자 건너뜀) |
| `POLY-STORAGE-001` | 저장소가 더 새 버전으로 저장됨(그대로 둠) |
| `POLY-BACKUP-001` | 월드 백업 확인 실패(불러오기는 계속) |

## 아직 없는 것 (설계서 §5.3 대비)

3단계까지 D~J 각 영역에 기본 경로와 심화 경로가 생겼습니다. 아래는 아직 없는 항목입니다.

- **엔티티·게임플레이(F):** 탈것 조종은 키 입력을 자동화할 수 없어 사람이 확인해야 합니다.
- **클라이언트(G):** 그린 결과가 보기에 맞는지는 사람이 확인해야 합니다(실행 경로만 자동 확인).
- **멀티플레이 검증:** 전용 서버와 별도 클라이언트 간 접속은 EULA 동의가 필요해 자동 검증하지 않았습니다. 싱글 플레이 통합 서버의 네트워크 왕복만 검증했습니다.
