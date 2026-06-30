package concurrency;

import java.util.concurrent.atomic.*;

/**
 * C03 — volatile, Atomic classes, CAS, LongAdder
 *
 * Memory visibility problem: CPUs cache registers. Thread A writes to a field,
 * but Thread B still reads the stale value from its cache.
 *
 * Three levels of solution (cheapest → most powerful):
 *   volatile         → visibility only, no atomicity
 *   Atomic*          → visibility + atomicity via CAS (no OS lock)
 *   synchronized     → visibility + atomicity + mutual exclusion (OS lock)
 *
 * Interview rule: reach for Atomic* when you need thread-safe ops on a single variable.
 * Synchronized when you need to guard a multi-step transaction.
 */
public class C03_AtomicsAndVolatile {

    public static void main(String[] args) throws InterruptedException {
        demo1_volatileVisibility();
        demo2_volatileNotAtomic();
        demo3_atomicInteger();
        demo4_cas_compareAndSet();
        demo5_atomicReference();
        demo6_longAdder_highContention();
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 1 — volatile: guarantees visibility, not atomicity
    // Interview Q: What problem does volatile solve?
    //   → Without volatile, a thread may read a stale cached value from its
    //     CPU register instead of main memory.
    //   → volatile forces every read/write to go through main memory.
    //
    // Classic use: stop-flag pattern
    // ─────────────────────────────────────────────────────────────────
    static volatile boolean stopFlag = false;   // volatile — visible across threads

    static void demo1_volatileVisibility() throws InterruptedException {
        System.out.println("===== 1. volatile stop-flag =====");

        Thread worker = new Thread(() -> {
            int i = 0;
            while (!stopFlag) { i++; }  // reads stopFlag from main memory each iteration
            System.out.println("Worker stopped after " + i + " iterations");
        });
        worker.start();
        Thread.sleep(10);
        stopFlag = true;   // write flushed to main memory immediately
        worker.join();
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 2 — volatile does NOT make compound ops atomic
    // Interview Q: Is volatile int counter++ thread-safe?
    //   → NO. ++ is 3 ops: read → add → write. Two threads can interleave them.
    //
    // volatile guarantees each individual read and write is visible,
    // but doesn't prevent the interleaving of read-modify-write sequences.
    // ─────────────────────────────────────────────────────────────────
    static volatile int volatileCounter = 0;

    static void demo2_volatileNotAtomic() throws InterruptedException {
        System.out.println("\n===== 2. volatile ≠ atomic (the gotcha) =====");

        volatileCounter = 0;
        Thread t1 = new Thread(() -> { for (int i=0;i<10000;i++) volatileCounter++; });
        Thread t2 = new Thread(() -> { for (int i=0;i<10000;i++) volatileCounter++; });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("volatile counter (NOT 20000): " + volatileCounter);  // race condition
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 3 — AtomicInteger: thread-safe single-variable ops
    // All operations are atomic — no synchronized needed.
    // Backed by CAS (Compare-And-Swap) CPU instruction — no OS lock.
    //
    // Key methods:
    //   get()                → read
    //   set(x)               → write
    //   incrementAndGet()    → ++i (returns new value)
    //   getAndIncrement()    → i++ (returns old value)
    //   addAndGet(n)         → += n
    //   compareAndSet(e, u)  → CAS: set to u only if current == e
    //   updateAndGet(fn)     → apply a function atomically
    // ─────────────────────────────────────────────────────────────────
    static void demo3_atomicInteger() throws InterruptedException {
        System.out.println("\n===== 3. AtomicInteger =====");

        AtomicInteger counter = new AtomicInteger(0);
        Thread t1 = new Thread(() -> { for (int i=0;i<10000;i++) counter.incrementAndGet(); });
        Thread t2 = new Thread(() -> { for (int i=0;i<10000;i++) counter.incrementAndGet(); });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("AtomicInteger counter (exactly 20000): " + counter.get());

        // updateAndGet — apply a function atomically (e.g., cap at a max value)
        AtomicInteger capped = new AtomicInteger(95);
        for (int i = 0; i < 10; i++) {
            capped.updateAndGet(v -> Math.min(v + 2, 100));
        }
        System.out.println("Capped counter (should be 100): " + capped.get());
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 4 — compareAndSet (CAS): the primitive behind all atomics
    // Interview Q: How does AtomicInteger work internally?
    //   → compareAndSet(expected, update):
    //     "If the current value IS expected, swap it to update atomically."
    //     Returns true if the swap happened, false if current ≠ expected.
    //
    // This is a single CPU instruction (LOCK CMPXCHG on x86) — very fast.
    // Used to build lock-free algorithms.
    // ─────────────────────────────────────────────────────────────────
    static void demo4_cas_compareAndSet() throws InterruptedException {
        System.out.println("\n===== 4. CAS — compareAndSet =====");

        AtomicInteger value = new AtomicInteger(10);

        boolean swapped = value.compareAndSet(10, 20);  // current==10 → set to 20
        System.out.println("CAS(10→20): swapped=" + swapped + ", value=" + value.get()); // true, 20

        swapped = value.compareAndSet(10, 99);  // current==20, expected 10 → fails
        System.out.println("CAS(10→99): swapped=" + swapped + ", value=" + value.get()); // false, 20

        // Manually implement a spin-lock counter using CAS (shows what AtomicInteger does internally)
        AtomicInteger spinCounter = new AtomicInteger(0);
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                int current, next;
                do {
                    current = spinCounter.get();
                    next = current + 1;
                } while (!spinCounter.compareAndSet(current, next)); // retry if another thread beat us
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                int current, next;
                do {
                    current = spinCounter.get();
                    next = current + 1;
                } while (!spinCounter.compareAndSet(current, next));
            }
        });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("Manual CAS counter (exactly 2000): " + spinCounter.get());
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 5 — AtomicReference: CAS for object references
    // Interview Q: How would you implement a thread-safe "lazy singleton"?
    //   → Use AtomicReference.compareAndSet to ensure only one instance is created.
    //
    // Also useful for: lock-free stack, lock-free linked list
    // ─────────────────────────────────────────────────────────────────
    static void demo5_atomicReference() throws InterruptedException {
        System.out.println("\n===== 5. AtomicReference =====");

        // Thread-safe lazy singleton using CAS (no synchronized needed)
        class Config {
            final String env;
            Config(String env) { this.env = env; }
        }

        AtomicReference<Config> configRef = new AtomicReference<>(null);

        Runnable initConfig = () -> {
            Config newConfig = new Config("production");
            boolean set = configRef.compareAndSet(null, newConfig);
            System.out.println(Thread.currentThread().getName()
                + ": CAS set=" + set + "  env=" + configRef.get().env);
        };

        Thread t1 = new Thread(initConfig, "thread-1");
        Thread t2 = new Thread(initConfig, "thread-2");
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("Only one wins the CAS — both see same config: " + configRef.get().env);

        // AtomicReference<V> also has getAndSet, updateAndGet, accumulateAndGet
        AtomicReference<String> state = new AtomicReference<>("IDLE");
        state.updateAndGet(s -> s.equals("IDLE") ? "RUNNING" : s);
        System.out.println("State after update: " + state.get()); // RUNNING
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 6 — LongAdder: faster than AtomicLong under high contention
    // Interview Q: When would you prefer LongAdder over AtomicLong?
    //   → High-contention counters (many threads incrementing simultaneously).
    //     LongAdder maintains per-thread cells — threads increment their own cell,
    //     reducing CAS retries. sum() merges all cells at read time.
    //   → Trade-off: sum() is not instantaneously consistent (ok for metrics/stats).
    //
    // Use AtomicLong when you need exact current value frequently.
    // Use LongAdder when throughput matters more than exact reads.
    // ─────────────────────────────────────────────────────────────────
    static void demo6_longAdder_highContention() throws InterruptedException {
        System.out.println("\n===== 6. LongAdder vs AtomicLong =====");

        int threads = 8;
        int ops = 100_000;

        // AtomicLong — all threads contend on ONE CAS location
        AtomicLong atomicLong = new AtomicLong(0);
        long start = System.nanoTime();
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> { for (int j=0;j<ops;j++) atomicLong.incrementAndGet(); });
            workers[i].start();
        }
        for (Thread t : workers) t.join();
        long atomicTime = System.nanoTime() - start;

        // LongAdder — threads increment their own cell (striped)
        LongAdder longAdder = new LongAdder();
        start = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> { for (int j=0;j<ops;j++) longAdder.increment(); });
            workers[i].start();
        }
        for (Thread t : workers) t.join();
        long adderTime = System.nanoTime() - start;

        long expected = (long) threads * ops;
        System.out.printf("AtomicLong : %,d  time=%,dns%n", atomicLong.get(), atomicTime);
        System.out.printf("LongAdder  : %,d  time=%,dns%n", longAdder.sum(), adderTime);
        System.out.println("LongAdder is typically 2-5x faster under high contention");
    }
}
