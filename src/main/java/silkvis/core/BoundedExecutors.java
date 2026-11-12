package silkvis.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded background executors (ARCH-05, PERF-04). Queues are bounded on purpose:
 * overflowing work is rejected instead of accumulating an unbounded backlog.
 */
public final class BoundedExecutors implements AutoCloseable {
    private final ThreadPoolExecutor cpu;
    private final ThreadPoolExecutor io;

    public BoundedExecutors(int cpuThreads, int cpuQueue, int ioThreads, int ioQueue) {
        if (cpuThreads < 1 || ioThreads < 1 || cpuQueue < 1 || ioQueue < 1) {
            throw new IllegalArgumentException("executor sizes must be >= 1");
        }
        this.cpu = new ThreadPoolExecutor(
                cpuThreads, cpuThreads, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(cpuQueue), named("silkvis-cpu"),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        this.io = new ThreadPoolExecutor(
                ioThreads, ioThreads, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(ioQueue), named("silkvis-io"),
                new ThreadPoolExecutor.AbortPolicy());
        this.cpu.allowCoreThreadTimeOut(true);
        this.io.allowCoreThreadTimeOut(true);
    }

    /** Defaults chosen for a client mod: small pools, hard bounded queues. */
    public static BoundedExecutors defaults() {
        int cores = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        return new BoundedExecutors(cores, 64, 2, 32);
    }

    public ExecutorService cpu() {
        return cpu;
    }

    public ExecutorService io() {
        return io;
    }

    public static ThreadFactory named(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
    }

    public int queuedCpuTasks() {
        return cpu.getQueue().size();
    }

    public int queuedIoTasks() {
        return io.getQueue().size();
    }

    @Override
    public void close() {
        cpu.shutdownNow();
        io.shutdownNow();
    }
}
