package dev.plexusmodloader.runtime.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShutdownCoordinatorTest {

    @Test
    void runsTasksInReverseRegistrationOrder() {
        ShutdownCoordinator coordinator = new ShutdownCoordinator();
        List<String> calls = new ArrayList<>();
        coordinator.register("library", "close pool", () -> calls.add("library"));
        coordinator.register("addon", "flush cache", () -> calls.add("addon"));

        coordinator.runShutdownTasks();

        assertEquals(List.of("addon", "library"), calls, "dependents are cleaned up before their dependencies");
    }

    @Test
    void aFailingTaskDoesNotStopTheOthers() {
        ShutdownCoordinator coordinator = new ShutdownCoordinator();
        List<String> calls = new ArrayList<>();
        coordinator.register("first", "ok", () -> calls.add("first"));
        coordinator.register("broken", "explode", () -> {
            throw new IllegalStateException("boom");
        });

        ShutdownCoordinator.Result result = coordinator.runShutdownTasks();

        assertEquals(List.of("first"), calls);
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().getFirst().startsWith("PLEXUS-SHUTDOWN-001 mod=broken task='explode'"));
    }

    @Test
    void runsOnlyOnce() {
        ShutdownCoordinator coordinator = new ShutdownCoordinator();
        List<String> calls = new ArrayList<>();
        coordinator.register("mod", "count", () -> calls.add("run"));

        coordinator.runShutdownTasks();
        coordinator.runShutdownTasks();

        assertEquals(1, calls.size());
    }

    @Test
    void rejectsRegistrationAfterShutdownStarted() {
        ShutdownCoordinator coordinator = new ShutdownCoordinator();
        coordinator.runShutdownTasks();

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> coordinator.register("late", "too late", () -> {}));

        assertTrue(failure.getMessage().startsWith("PLEXUS-SHUTDOWN-002 mod=late"));
    }
}
