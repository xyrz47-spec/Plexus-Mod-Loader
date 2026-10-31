package dev.plexusmodloader.runtime.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs mod shutdown tasks once, in reverse registration order, so a mod that initialised after its
 * dependencies is cleaned up before them (design doc §11 step 13). A failing task is reported with
 * its mod ID and does not prevent the remaining tasks from running.
 */
public final class ShutdownCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plexus Mod Loader");

    /**
     * A registered task.
     *
     * @param modId mod that registered the task
     * @param description what the task cleans up, for diagnostics
     * @param task the work
     */
    public record ShutdownTask(String modId, String description, Runnable task) {
        public ShutdownTask {
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(task, "task");
        }
    }

    /**
     * Outcome of a shutdown run.
     *
     * @param completed tasks that finished, in execution order
     * @param failed problem lines for tasks that threw
     */
    public record Result(List<ShutdownTask> completed, List<String> failed) {
        public Result {
            completed = List.copyOf(completed);
            failed = List.copyOf(failed);
        }
    }

    private final List<ShutdownTask> tasks = new ArrayList<>();
    private final AtomicBoolean ran = new AtomicBoolean();

    /** Registers a task. Tasks registered after shutdown started are rejected. */
    public synchronized void register(String modId, String description, Runnable task) {
        if (ran.get()) {
            throw new IllegalStateException("PLEXUS-SHUTDOWN-002 mod=" + modId + " registered shutdown task '"
                    + description + "' after shutdown started");
        }
        tasks.add(new ShutdownTask(modId, description, task));
    }

    /** Registers a JVM shutdown hook that calls {@link #runShutdownTasks()}. */
    public void installJvmShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::runShutdownTasks, "Plexus Mod Loader Shutdown"));
    }

    /** Runs every task once; later calls do nothing and return an empty result. */
    public Result runShutdownTasks() {
        if (!ran.compareAndSet(false, true)) {
            return new Result(List.of(), List.of());
        }
        List<ShutdownTask> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(tasks);
        }
        LOGGER.info("[Plexus Mod Loader] Phase {}: {} task(s)", RuntimePhase.SHUTDOWN, snapshot.size());
        List<ShutdownTask> completed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (ShutdownTask task : snapshot.reversed()) {
            try {
                task.task().run();
                completed.add(task);
            } catch (RuntimeException | LinkageError e) {
                String problem =
                        "PLEXUS-SHUTDOWN-001 mod=" + task.modId() + " task='" + task.description() + "' threw " + e;
                failed.add(problem);
                LOGGER.error("[Plexus Mod Loader] {}", problem, e);
            }
        }
        return new Result(completed, failed);
    }
}
