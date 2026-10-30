package dev.plexusmodloader.game.layer.v26_1.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.plexusmodloader.api.devtools.gametest.GameTestContext;
import dev.plexusmodloader.api.entity.EntityView;
import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.world.BlockPos;
import dev.plexusmodloader.api.world.Direction;
import dev.plexusmodloader.api.world.InteractionResult;
import dev.plexusmodloader.api.world.WorldView;
import dev.plexusmodloader.game.layer.v26_1.Views;
import dev.plexusmodloader.game.layer.v26_1.content.BlockStates;
import dev.plexusmodloader.game.layer.v26_1.content.Interactions;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link GameTestContext} over the game's {@link GameTestHelper}. */
final class GameTestContextView implements GameTestContext {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final Field TEST_INFO;

    static {
        try {
            TEST_INFO = GameTestHelper.class.getDeclaredField("testInfo");
            TEST_INFO.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final GameTestHelper helper;
    /** Players this test connected; removed from the server when the test ends. */
    private final List<UUID> connectedPlayers = new ArrayList<>();

    GameTestContextView(GameTestHelper helper) {
        this.helper = helper;
    }

    @Override
    public WorldView world() {
        return Views.world(helper.getLevel());
    }

    @Override
    public BlockPos absolute(BlockPos relative) {
        return Views.pos(helper.absolutePos(Views.pos(relative)));
    }

    @Override
    public void setBlock(BlockPos relative, ResourceId block) {
        helper.setBlock(Views.pos(relative), Views.block(block));
    }

    @Override
    public void assertBlock(BlockPos relative, ResourceId expected) {
        helper.assertBlockPresent(Views.block(expected), Views.pos(relative));
    }

    @Override
    public void assertTrue(boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    @Override
    public PlayerView createMockPlayer() {
        return Views.player(helper.makeMockPlayer(GameType.SURVIVAL));
    }

    @Override
    public PlayerView createMockServerPlayer() {
        // 26.1's GameTestHelper has no makeMockServerPlayer(GameType); this is what 26.2's does (javap): a
        // server player that is not added to the level, in survival.
        ServerLevel level = helper.getLevel();
        ServerPlayer player =
                new ServerPlayer(
                        level.getServer(),
                        level,
                        new GameProfile(UUID.randomUUID(), "test-mock-player"),
                        ClientInformation.createDefault()) {
                    @Override
                    public GameType gameMode() {
                        return GameType.SURVIVAL;
                    }
                };
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        return Views.player(player);
    }

    @Override
    public PlayerView createConnectedMockPlayer() {
        MinecraftServer server = helper.getLevel().getServer();
        // As GameTestHelper.makeMockServerPlayerInLevel, but a plain survival player: that helper's
        // player always reports creative mode.
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "poly-test-player"), false);
        ServerPlayer player =
                new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        if (connectedPlayers.isEmpty()) {
            removeConnectedPlayersWhenDone();
        }
        connectedPlayers.add(player.getUUID());
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        reportLoaded(player);
        return Views.player(player);
    }

    @Override
    public PlayerView respawn(PlayerView player, boolean returnedFromEnd) {
        ServerPlayer connected = connectedPlayer(player);
        if (returnedFromEnd) {
            // What the game sets when the End credits start.
            connected.wonGame = true;
        } else if (connected.getHealth() > 0) {
            throw new IllegalStateException("Only a dead player respawns after death: " + player.name());
        }
        // The packet a client sends from the death or credits screen; the game then respawns the player,
        // and restarts the client-load wait as for a real client.
        connected.connection.handleClientCommand(
                new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        ServerPlayer respawned = connected.connection.getPlayer();
        reportLoaded(respawned);
        return Views.player(respawned);
    }

    @Override
    public void disconnect(PlayerView player) {
        ServerPlayer connected = connectedPlayer(player);
        PlayerList players = helper.getLevel().getServer().getPlayerList();
        ServerPlayer current = players.getPlayer(connected.getUUID());
        if (current != null) {
            players.remove(current);
        }
    }

    /**
     * Sends what a client sends once it shows the world: until then the game keeps a joined or
     * respawned player invulnerable.
     */
    private static void reportLoaded(ServerPlayer player) {
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
    }

    private ServerPlayer connectedPlayer(PlayerView player) {
        ServerPlayer serverPlayer = serverPlayer(player);
        if (!connectedPlayers.contains(serverPlayer.getUUID()) || serverPlayer.connection == null) {
            throw new IllegalArgumentException(
                    "Not a player from this test's createConnectedMockPlayer(): " + player.name());
        }
        return serverPlayer;
    }

    /** Removes this test's connected players from the server when the test passes or fails. */
    private void removeConnectedPlayersWhenDone() {
        GameTestInfo info;
        try {
            info = (GameTestInfo) TEST_INFO.get(helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read GameTestHelper.testInfo", e);
        }
        info.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo test) {}

            @Override
            public void testPassed(GameTestInfo test, GameTestRunner runner) {
                removeConnectedPlayers();
            }

            @Override
            public void testFailed(GameTestInfo test, GameTestRunner runner) {
                removeConnectedPlayers();
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
        });
    }

    private void removeConnectedPlayers() {
        PlayerList players = helper.getLevel().getServer().getPlayerList();
        for (UUID id : connectedPlayers) {
            ServerPlayer player = players.getPlayer(id);
            if (player != null) {
                players.remove(player);
            }
        }
        connectedPlayers.clear();
    }

    @Override
    public void useBlock(BlockPos relative, PlayerView player) {
        helper.useBlock(Views.pos(relative), Views.gamePlayer(player));
    }

    @Override
    public InteractionResult interactBlock(PlayerView player, BlockPos relative, Direction face) {
        ServerPlayer serverPlayer = serverPlayer(player);
        net.minecraft.core.BlockPos pos = helper.absolutePos(Views.pos(relative));
        net.minecraft.core.Direction direction = BlockStates.toGame(face);
        Vec3 hit = Vec3.atCenterOf(pos).add(direction.getUnitVec3().scale(0.5));
        return Interactions.fromGame(serverPlayer.gameMode.useItemOn(
                serverPlayer,
                helper.getLevel(),
                serverPlayer.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, direction, pos, false)));
    }

    @Override
    public InteractionResult interactItem(PlayerView player) {
        ServerPlayer serverPlayer = serverPlayer(player);
        return Interactions.fromGame(serverPlayer.gameMode.useItem(
                serverPlayer, helper.getLevel(), serverPlayer.getMainHandItem(), InteractionHand.MAIN_HAND));
    }

    @Override
    public InteractionResult interactEntity(PlayerView player, EntityView target) {
        ServerPlayer serverPlayer = serverPlayer(player);
        Entity entity = entity(target);
        return Interactions.fromGame(serverPlayer.interactOn(entity, InteractionHand.MAIN_HAND, entity.position()));
    }

    @Override
    public void attackEntity(PlayerView player, EntityView target) {
        serverPlayer(player).attack(entity(target));
    }

    @Override
    public boolean breakBlock(PlayerView player, BlockPos relative) {
        return serverPlayer(player).gameMode.destroyBlock(helper.absolutePos(Views.pos(relative)));
    }

    private static ServerPlayer serverPlayer(PlayerView player) {
        if (Views.gamePlayer(player) instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        throw new IllegalArgumentException(
                "Interactions need a server player from createMockServerPlayer(), got " + player.name());
    }

    private Entity entity(EntityView target) {
        Entity entity = helper.getLevel().getEntity(target.uuid());
        if (entity == null) {
            throw new IllegalArgumentException("No entity " + target.uuid() + " in the test world");
        }
        return entity;
    }

    @Override
    public int runCommand(String command) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)));
        try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            LOGGER.info("[Poly Mod Loader] Game test command '{}' failed: {}", command, e.getMessage());
            return 0;
        }
    }

    @Override
    public void runAfter(int ticks, Runnable action) {
        helper.runAfterDelay(ticks, action);
    }

    @Override
    public void succeed() {
        helper.succeed();
    }

    @Override
    public void fail(String message) {
        helper.fail(message);
    }
}
