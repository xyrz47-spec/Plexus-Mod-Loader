package dev.polymodloader.runtime.nativeapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.command.CommandNode;
import dev.polymodloader.api.content.ContentHandle;
import dev.polymodloader.api.content.CreativeTabDefinition;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.BlockHandle;
import dev.polymodloader.api.content.item.ItemDefinition;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.network.PayloadChannel;
import dev.polymodloader.api.network.PayloadCodec;
import dev.polymodloader.api.network.PayloadReader;
import dev.polymodloader.api.network.PayloadWriter;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.api.tag.TagKey;
import dev.polymodloader.api.tag.TagRegistry;
import dev.polymodloader.game.version.layer.ChannelEndpoint;
import dev.polymodloader.game.version.layer.PayloadTransport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeRegistrationsTest {

    @Test
    void blocksDeclareTheirItemAndHandlesTrackRegistration() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        BlockHandle handle = registrations
                .forMod("demo")
                .block("copper_bricks", BlockDefinition.builder().build());
        registrations
                .forMod("demo")
                .block("hidden", BlockDefinition.builder().noItem().build());

        assertEquals(ResourceId.of("demo", "copper_bricks"), handle.id());
        assertEquals(2, registrations.blocks().size());
        assertEquals(1, registrations.items().size(), "noItem() declares no block item");
        assertTrue(registrations.items().getFirst().blockItem());
        assertFalse(handle.isRegistered());

        registrations.blocks().getFirst().registered().run();
        assertTrue(handle.isRegistered());
    }

    @Test
    void duplicateAndInvalidIdsAreRejectedWithTheirCode() {
        NativeRegistrations.ModRegistrars demo = new NativeRegistrations(GameSide.CLIENT).forMod("demo");
        demo.item("gem", ItemDefinition.builder().build());

        IllegalArgumentException duplicate = assertThrows(
                IllegalArgumentException.class,
                () -> demo.block("gem", BlockDefinition.builder().build()));
        IllegalArgumentException invalid = assertThrows(
                IllegalArgumentException.class,
                () -> demo.item("Bad Name", ItemDefinition.builder().build()));

        assertTrue(duplicate.getMessage().startsWith("PLEXUS-CONTENT-001"), duplicate.getMessage());
        assertTrue(invalid.getMessage().startsWith("PLEXUS-CONTENT-001"), invalid.getMessage());
    }

    @Test
    void registrationClosesWhenTheGameFreezesItsRegistries() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        NativeRegistrations.ModRegistrars demo = registrations.forMod("demo");
        registrations.close();

        IllegalStateException content = assertThrows(
                IllegalStateException.class,
                () -> demo.item("late", ItemDefinition.builder().build()));
        IllegalStateException command =
                assertThrows(IllegalStateException.class, () -> demo.register(CommandNode.literal("late")));

        assertTrue(content.getMessage().startsWith("PLEXUS-CONTENT-002"), content.getMessage());
        assertTrue(command.getMessage().startsWith("PLEXUS-COMMAND-002"), command.getMessage());
    }

    @Test
    void tagEntriesAddUpAcrossCallsAndCloseWithRegistration() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        NativeRegistrations.ModRegistrars demo = registrations.forMod("demo");
        TagKey pickaxe = TagKey.block(ResourceId.minecraft("mineable/pickaxe"));
        demo.addToTag(pickaxe, ResourceId.of("demo", "ore"), ResourceId.of("demo", "bricks"));
        registrations.forMod("other").addToTag(pickaxe, ResourceId.of("other", "stone"));

        assertEquals(2, registrations.tagEntries().size());
        assertEquals("demo", registrations.tagEntries().getFirst().ownerModId());
        assertEquals(
                List.of(ResourceId.of("demo", "ore"), ResourceId.of("demo", "bricks")),
                registrations.tagEntries().getFirst().entries());
        assertEquals(
                TagRegistry.BLOCK, registrations.tagEntries().getFirst().tag().registry());
        assertEquals("#minecraft:mineable/pickaxe", pickaxe.toString());

        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class, () -> demo.addToTag(pickaxe));
        assertTrue(empty.getMessage().startsWith("PLEXUS-CONTENT-001"), empty.getMessage());
        assertThrows(NullPointerException.class, () -> demo.addToTag(pickaxe, (ResourceId) null));

        registrations.close();
        IllegalStateException late =
                assertThrows(IllegalStateException.class, () -> demo.addToTag(pickaxe, ResourceId.of("demo", "late")));
        assertTrue(late.getMessage().startsWith("PLEXUS-CONTENT-002"), late.getMessage());
    }

    @Test
    void creativeTabsNeedEntriesAndUniqueIds() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        NativeRegistrations.ModRegistrars demo = registrations.forMod("demo");
        ResourceId gem = ResourceId.of("demo", "gem");
        CreativeTabDefinition tab = CreativeTabDefinition.builder()
                .entries(gem, ResourceId.minecraft("diamond"))
                .build();

        ContentHandle handle = demo.creativeTab("gems", tab);

        assertEquals(ResourceId.of("demo", "gems"), handle.id());
        assertEquals(gem, tab.iconOrFirstEntry(), "without an icon the first entry is shown");
        assertEquals(
                ResourceId.minecraft("emerald"),
                CreativeTabDefinition.builder()
                        .icon(ResourceId.minecraft("emerald"))
                        .entry(gem)
                        .build()
                        .iconOrFirstEntry());
        assertEquals(1, registrations.creativeTabs().size());
        assertFalse(handle.isRegistered());
        registrations.creativeTabs().getFirst().registered().run();
        assertTrue(handle.isRegistered());

        IllegalArgumentException duplicate =
                assertThrows(IllegalArgumentException.class, () -> demo.creativeTab("gems", tab));
        assertTrue(duplicate.getMessage().startsWith("PLEXUS-CONTENT-001"), duplicate.getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> CreativeTabDefinition.builder().build());
    }

    @Test
    void clientRegistrationsAreRefusedOnADedicatedServer() {
        NativeRegistrations.ModRegistrars demo = new NativeRegistrations(GameSide.DEDICATED_SERVER).forMod("demo");

        IllegalStateException failure = assertThrows(IllegalStateException.class, demo::client);

        assertTrue(failure.getMessage().startsWith("PLEXUS-CLIENT-001"), failure.getMessage());
    }

    @Test
    void itemDefinitionsValidateEagerly() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemDefinition.builder().maxStackSize(100).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemDefinition.builder().durability(10).maxStackSize(16).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> BlockDefinition.builder().lightLevel(16).build());
        assertEquals(1, ItemDefinition.builder().durability(10).build().maxStackSize());
    }

    @Test
    void payloadsRoundTripThroughTheTransportAndReachTheHandler() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        PayloadChannel<Point> channel = registrations.forMod("demo").channel("points", new PointCodec());
        List<byte[]> sent = new ArrayList<>();
        registrations.bindTransport(new PayloadTransport() {
            @Override
            public void sendToPlayer(PlayerView player, ResourceId id, byte[] data) {
                sent.add(data);
            }

            @Override
            public void sendToServer(ResourceId id, byte[] data) {
                sent.add(data);
            }
        });
        List<Point> received = new ArrayList<>();
        channel.onClientReceive(received::add);

        channel.sendToServer(new Point(3, -7, "home"));
        ChannelEndpoint endpoint = registrations.channels().getFirst();
        endpoint.receiveOnClient(sent.getFirst());

        assertEquals(ResourceId.of("demo", "points"), endpoint.id());
        assertEquals(List.of(new Point(3, -7, "home")), received);
    }

    @Test
    void malformedAndOversizedPayloadsAreRejected() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        PayloadChannel<String> channel = registrations.forMod("demo").channel("text", PayloadCodec.string());
        List<String> received = new ArrayList<>();
        channel.onClientReceive(received::add);
        registrations.bindTransport(new PayloadTransport() {
            @Override
            public void sendToPlayer(PlayerView player, ResourceId id, byte[] data) {}

            @Override
            public void sendToServer(ResourceId id, byte[] data) {}
        });

        registrations.channels().getFirst().receiveOnClient(new byte[] {0, 0, 0, 9, 1});
        IllegalArgumentException tooLarge =
                assertThrows(IllegalArgumentException.class, () -> channel.sendToServer("x".repeat(32_764)));

        assertEquals(List.of(), received, "a truncated payload is dropped, not delivered");
        assertTrue(tooLarge.getMessage().startsWith("PLEXUS-NETWORK-003"), tooLarge.getMessage());
    }

    @Test
    void sendingBeforeTheGameConnectsFailsClearly() {
        PayloadChannel<String> channel =
                new NativeRegistrations(GameSide.CLIENT).forMod("demo").channel("early", PayloadCodec.string());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> channel.sendToServer("hi"));

        assertTrue(failure.getMessage().startsWith("PLEXUS-NETWORK-002"), failure.getMessage());
    }

    private record Point(int x, int z, String label) {}

    private static final class PointCodec implements PayloadCodec<Point> {
        @Override
        public void encode(Point value, PayloadWriter out) {
            out.writeInt(value.x()).writeInt(value.z()).writeString(value.label());
        }

        @Override
        public Point decode(PayloadReader in) {
            return new Point(in.readInt(), in.readInt(), in.readString());
        }
    }
}
