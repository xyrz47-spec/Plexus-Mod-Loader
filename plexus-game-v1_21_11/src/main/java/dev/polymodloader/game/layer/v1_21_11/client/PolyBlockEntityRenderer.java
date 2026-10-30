package dev.polymodloader.game.layer.v1_21_11.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.polymodloader.api.client.BlockEntityCanvas;
import dev.polymodloader.api.client.ShaderPipeline;
import dev.polymodloader.api.client.VertexSink;
import dev.polymodloader.api.content.block.state.BlockStateView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.game.layer.v1_21_11.Views;
import dev.polymodloader.game.layer.v1_21_11.content.BlockStates;
import dev.polymodloader.game.layer.v1_21_11.content.ModBlockEntity;
import dev.polymodloader.game.version.layer.NativeDeclarations.BlockEntityRendererDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a mod's block entity painter while the game extracts render state (on the render thread),
 * records what it draws, and replays that when the game submits the frame.
 */
final class PolyBlockEntityRenderer implements BlockEntityRenderer<BlockEntity, PolyBlockEntityRenderer.State> {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    private final BlockEntityRendererDeclaration declaration;
    private final ItemModelResolver items;
    private final Font font;
    private final AtomicBoolean failed = new AtomicBoolean();
    private final AtomicBoolean reported = new AtomicBoolean();

    PolyBlockEntityRenderer(BlockEntityRendererProvider.Context context, BlockEntityRendererDeclaration declaration) {
        this.declaration = declaration;
        this.items = context.itemModelResolver();
        this.font = context.font();
    }

    /** What one block entity draws this frame. */
    static final class State extends BlockEntityRenderState {
        final List<Draw> draws = new ArrayList<>();
    }

    private sealed interface Draw {}

    private record ItemDraw(ItemStackRenderState item, double x, double y, double z, float scale, float yaw)
            implements Draw {}

    private record TextDraw(FormattedCharSequence text, int width, double x, double y, double z, float scale, int argb)
            implements Draw {}

    private record BlockDraw(BlockState block, double x, double y, double z, float scale, float yaw) implements Draw {}

    private record QuadsDraw(ModShaderPipelines.Quads quads) implements Draw {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            BlockEntity blockEntity,
            State state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.draws.clear();
        if (failed.get() || !(blockEntity instanceof ModBlockEntity modEntity) || blockEntity.getLevel() == null) {
            return;
        }
        float time = blockEntity.getLevel().getGameTime() + partialTicks;
        int seed = (int) blockEntity.getBlockPos().asLong();
        try {
            declaration.painter().paint(modEntity.view(), new BlockEntityCanvas() {
                @Override
                public void item(ResourceId item, double x, double y, double z, float scale, float yawDegrees) {
                    ItemStackRenderState itemState = new ItemStackRenderState();
                    items.updateForTopItem(
                            itemState,
                            new ItemStack(Views.item(item)),
                            ItemDisplayContext.FIXED,
                            blockEntity.getLevel(),
                            null,
                            seed + state.draws.size());
                    state.draws.add(new ItemDraw(itemState, x, y, z, scale, yawDegrees));
                }

                @Override
                public void text(String text, double x, double y, double z, float scale, int argb) {
                    FormattedCharSequence line = Component.literal(text).getVisualOrderText();
                    state.draws.add(new TextDraw(line, font.width(line), x, y, z, scale, argb));
                }

                @Override
                public void block(BlockStateView block, double x, double y, double z, float scale, float yawDegrees) {
                    // 1.21.11 submits a block state as is (SubmitNodeCollector.submitBlock); 26.x resolves its
                    // model into a BlockModelRenderState first.
                    state.draws.add(new BlockDraw(BlockStates.gameState(block), x, y, z, scale, yawDegrees));
                }

                @Override
                public BlockStateView defaultState(ResourceId block) {
                    return BlockStates.view(Views.block(block).defaultBlockState());
                }

                @Override
                public void quads(ShaderPipeline pipeline, Consumer<VertexSink> vertices) {
                    state.draws.add(new QuadsDraw(ModShaderPipelines.record(pipeline, vertices, 0, 0, 0)));
                }

                @Override
                public float animationTime() {
                    return time;
                }
            });
        } catch (RuntimeException | LinkageError e) {
            failed.set(true);
            state.draws.clear();
            LOGGER.error(
                    "[Poly Mod Loader] PLEXUS-CLIENT-003 block entity renderer of {} ({}) failed and is disabled",
                    declaration.block(),
                    declaration.ownerModId(),
                    e);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (Draw draw : state.draws) {
            poseStack.pushPose();
            switch (draw) {
                case ItemDraw item -> {
                    poseStack.translate(item.x(), item.y(), item.z());
                    poseStack.mulPose(Axis.YP.rotationDegrees(item.yaw()));
                    poseStack.scale(item.scale(), item.scale(), item.scale());
                    item.item().submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                }
                case BlockDraw block -> {
                    poseStack.translate(block.x(), block.y(), block.z());
                    poseStack.mulPose(Axis.YP.rotationDegrees(block.yaw()));
                    poseStack.scale(block.scale(), block.scale(), block.scale());
                    poseStack.translate(-0.5, -0.5, -0.5);
                    collector.submitBlock(poseStack, block.block(), state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                }
                case QuadsDraw quads -> ModShaderPipelines.submit(quads.quads(), poseStack, collector);
                case TextDraw text -> {
                    poseStack.translate(text.x(), text.y(), text.z());
                    poseStack.mulPose(camera.orientation);
                    poseStack.scale(text.scale(), -text.scale(), text.scale());
                    collector.submitText(
                            poseStack,
                            -text.width() / 2.0f,
                            0,
                            text.text(),
                            false,
                            Font.DisplayMode.NORMAL,
                            state.lightCoords,
                            text.argb(),
                            0,
                            0);
                }
            }
            poseStack.popPose();
        }
        if (!state.draws.isEmpty() && reported.compareAndSet(false, true)) {
            LOGGER.info(
                    "[Poly Mod Loader] Drew block entity renderer of {} ({} draw(s))",
                    declaration.block(),
                    state.draws.size());
        }
    }
}
