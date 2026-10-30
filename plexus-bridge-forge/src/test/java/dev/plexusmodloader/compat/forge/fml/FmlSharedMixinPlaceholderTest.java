package dev.plexusmodloader.compat.forge.fml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.toposort.TopologicalSort;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.junit.jupiter.api.Test;

/**
 * The placeholders for FML's Mixin processors ({@code NeoForgeSharedMixinPatch}) against the ordering of FML's own
 * processors, checked with {@code javap} on {@code loader} 12.0.12: {@code FMLMixinClassProcessor} is {@code MIXIN}
 * with {@code ClassProcessor}'s default {@code runsAfter} ({@code COMPUTING_FRAMES}); {@code
 * FMLMixinGeneratingClassProcessor} is {@code mixin_frame_context}, runs after nothing and before {@code
 * COMPUTING_FRAMES} and {@code MIXIN}, late among equals.
 */
class FmlSharedMixinPlaceholderTest {

    @Test
    void theGeneratingPlaceholderHasTheNameAndOrderOfFmlsProcessor() {
        ClassProcessor generating = FmlSharedMixin.placeholderGeneratingProcessor();

        assertEquals(new ProcessorName("neoforge", "mixin_frame_context"), generating.name());
        assertEquals(Set.of(), generating.runsAfter());
        assertEquals(Set.of(ClassProcessorIds.COMPUTING_FRAMES, ClassProcessorIds.MIXIN), generating.runsBefore());
        assertEquals(ClassProcessor.OrderingHint.LATE, generating.orderingHint());
        assertEquals(Set.of(), generating.generatesPackages());
        assertFalse(generating.handlesClass(null));
    }

    @Test
    void theMixinPlaceholderHasTheNameAndOrderOfFmlsProcessor() {
        ClassProcessor mixin = FmlSharedMixin.placeholderProcessor();

        assertEquals(ClassProcessorIds.MIXIN, mixin.name());
        assertEquals(Set.of(ClassProcessorIds.COMPUTING_FRAMES), mixin.runsAfter());
        assertEquals(Set.of(), mixin.runsBefore());
        assertFalse(mixin.handlesClass(null));
    }

    /**
     * Both placeholders sort as {@code ClassProcessorSet.Builder.sortProcessors} sorts them (FML 12): its node for
     * {@code COMPUTING_FRAMES}, an edge for each {@code runsBefore} and {@code runsAfter} to a present processor, and
     * FML's own topological sort. A placeholder that runs both before and after frame computation is a cycle.
     */
    @Test
    void bothPlaceholdersSortAsFmlSortsItsProcessors() {
        ClassProcessor mixin = FmlSharedMixin.placeholderProcessor();
        ClassProcessor generating = FmlSharedMixin.placeholderGeneratingProcessor();

        List<ClassProcessor> sorted = sort(List.of(mixin, generating));

        assertTrue(sorted.indexOf(generating) < sorted.indexOf(mixin), () -> "sorted: " + names(sorted));
        assertTrue(sorted.indexOf(generating) < frames(sorted), () -> "sorted: " + names(sorted));
        assertTrue(frames(sorted) < sorted.indexOf(mixin), () -> "sorted: " + names(sorted));
    }

    private static List<ClassProcessor> sort(List<ClassProcessor> processors) {
        Map<ProcessorName, ClassProcessor> byName = new HashMap<>();
        MutableGraph<ClassProcessor> graph = GraphBuilder.directed().build();
        ClassProcessor frames = new ClassProcessor() {
            @Override
            public ProcessorName name() {
                return ClassProcessorIds.COMPUTING_FRAMES;
            }

            @Override
            public Set<ProcessorName> runsAfter() {
                return Set.of();
            }

            @Override
            public OrderingHint orderingHint() {
                return OrderingHint.EARLY;
            }

            @Override
            public boolean handlesClass(SelectionContext context) {
                return false;
            }

            @Override
            public ComputeFlags processClass(TransformationContext context) {
                return ComputeFlags.NO_REWRITE;
            }
        };
        graph.addNode(frames);
        byName.put(frames.name(), frames);
        for (ClassProcessor processor : processors) {
            assertFalse(byName.containsKey(processor.name()), () -> "two processors named " + processor.name());
            graph.addNode(processor);
            byName.put(processor.name(), processor);
        }
        for (ClassProcessor processor : byName.values()) {
            for (ProcessorName name : processor.runsBefore()) {
                ClassProcessor other = byName.get(name);
                if (other != null && other != processor) {
                    graph.putEdge(processor, other);
                }
            }
            for (ProcessorName name : processor.runsAfter()) {
                ClassProcessor other = byName.get(name);
                if (other != null && other != processor) {
                    graph.putEdge(other, processor);
                }
            }
        }
        return TopologicalSort.topologicalSort(
                graph, Comparator.comparing(ClassProcessor::orderingHint).thenComparing(ClassProcessor::name));
    }

    private static int frames(List<ClassProcessor> sorted) {
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).name().equals(ClassProcessorIds.COMPUTING_FRAMES)) {
                return i;
            }
        }
        throw new AssertionError("no " + ClassProcessorIds.COMPUTING_FRAMES);
    }

    private static List<ProcessorName> names(List<ClassProcessor> sorted) {
        return sorted.stream().map(ClassProcessor::name).toList();
    }
}
