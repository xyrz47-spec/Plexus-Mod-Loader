package dev.polymodloader.game.layer.v26_3.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polymodloader.api.client.ShaderPipeline;
import dev.polymodloader.api.client.VertexSink;
import dev.polymodloader.api.client.WorldCanvas;
import dev.polymodloader.game.version.layer.NativeDeclarations.WorldPainterDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs mods' world painters once per frame while the level submits its features. Lines, boxes,
 * points and text become the game's gizmos for this frame; shader quads are submitted as custom
 * geometry relative to the camera.
 */
final class WorldPainting {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final float TEXT_SCALE = TextGizmo.Style.DEFAULT_SCALE;

    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private WorldPainting() {}

    static void paint(List<WorldPainterDeclaration> painters, LevelRenderState state, SubmitNodeCollector collector) {
        Vec3 camera = state.cameraRenderState.pos;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float time = minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (WorldPainterDeclaration painter : painters) {
            String key = painter.id().toString();
            if (FAILED.contains(key)) {
                continue;
            }
            List<ModShaderPipelines.Quads> quads = new ArrayList<>();
            int[] shapes = {0};
            try {
                painter.painter().paint(new Canvas(camera, time, false, quads, shapes));
            } catch (RuntimeException | LinkageError e) {
                FAILED.add(key);
                LOGGER.error(
                        "[Poly Mod Loader] POLY-CLIENT-003 world painter {} ({}) failed and is disabled",
                        painter.id(),
                        painter.ownerModId(),
                        e);
                continue;
            }
            PoseStack poseStack = new PoseStack();
            for (ModShaderPipelines.Quads batch : quads) {
                ModShaderPipelines.submit(batch, poseStack, collector);
            }
            if (shapes[0] + quads.size() > 0 && REPORTED.add(key)) {
                LOGGER.info(
                        "[Poly Mod Loader] World painter {} drew {} shape(s) and {} quad batch(es)",
                        painter.id(),
                        shapes[0],
                        quads.size());
            }
        }
    }

    private record Canvas(
            Vec3 camera, float time, boolean alwaysOnTop, List<ModShaderPipelines.Quads> quads, int[] shapes)
            implements WorldCanvas {

        private void add(GizmoProperties properties) {
            shapes[0]++;
            if (alwaysOnTop) {
                properties.setAlwaysOnTop();
            }
        }

        @Override
        public double cameraX() {
            return camera.x;
        }

        @Override
        public double cameraY() {
            return camera.y;
        }

        @Override
        public double cameraZ() {
            return camera.z;
        }

        @Override
        public float animationTime() {
            return time;
        }

        @Override
        public void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float width) {
            add(Gizmos.line(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), argb, width));
        }

        @Override
        public void box(
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ,
                int strokeArgb,
                float width,
                int fillArgb) {
            add(Gizmos.cuboid(
                    new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                    GizmoStyle.strokeAndFill(strokeArgb, width, fillArgb)));
        }

        @Override
        public void point(double x, double y, double z, int argb, float size) {
            add(Gizmos.point(new Vec3(x, y, z), argb, size));
        }

        @Override
        public void text(String text, double x, double y, double z, int argb, float scale) {
            add(Gizmos.billboardText(
                    text, new Vec3(x, y, z), new TextGizmo.Style(argb, TEXT_SCALE * scale, OptionalDouble.empty())));
        }

        @Override
        public void quads(ShaderPipeline pipeline, Consumer<VertexSink> vertices) {
            quads.add(ModShaderPipelines.record(pipeline, vertices, camera.x, camera.y, camera.z));
        }

        @Override
        public WorldCanvas onTop() {
            return new Canvas(camera, time, true, quads, shapes);
        }
    }
}
