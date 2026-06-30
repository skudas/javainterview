package concurrency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * C06 — Classic Interview Traps & Gotchas
 *
 * These are the bugs interviewers love to ask about because they look correct
 * but silently break under concurrent access. Each demo shows:
 *   BROKEN → why it fails
 *   FIXED  → the correct pattern
 */
public class C06_InterviewGotchas {

    public static void main(String[] args) throws InterruptedException {
        trap1_checkThenAct();
        trap2_doubleCheckedLocking();
        trap3_iteratorConcurrentModification();
        trap4_threadPoolSizing();
        trap5_futureGetBlocking();
        trap6_synchronizedOnBoxedType();
        trap7_threadLocalLeak();
    }

    // ─────────────────────────────────────────────────────────────────
    // TRAP 1 — Check-Then-Act (non-atomic compound operation)
    //
    // Problem: An inventory service checks stock before reserving it.
    //          Two requests for the last item arrive at the same time.
    //
    // Both threads pass the if-check, both reserve the item → oversell!
    // ─────────────────────────────────────────────────────────────────
    static void trap1_checkThenAct() throws InterruptedException {
        System.out.println("===== TRAP 1: Check-Then-Act Race Condition =====");

        // BROKEN: check and act are two separate steps — not atomic
        class BrokenInventory {
            int stock = 1; // last item

            boolean reserve() {
                if (stock > 0) {          // Thread A passes here
                    // ... context switch, Thread B also passes here ...
                    stock--;              // both decrement → stock = -1 (oversold!)
                    return true;
                }
                return false;
            }
        }

        // FIXED: make the check+act atomic with synchronized
        class FixedInventory {
            private int stock = 1;

            synchronized boolean reserve() {
                if (stock > 0) { stock--; return true; }
                return false;
            }
        }

        // FIXED ALTERNATIVE: use AtomicInteger's CAS for lock-free check-then-act
        class LockFreeInventory {
            private final AtomicInteger stock = new AtomicInteger(1);

            boolean reserve() {
                while (true) {
                    int current = stock.get();
                    if (current <= 0) return false;
                    if (stock.compareAndSet(current, current - 1)) return true;
                    // CAS failed — another thread modified it, retry
                }
            }
        }

        LockFreeInventory inv = new LockFreeInventory();
        Thread t1 = new Thread(() -> System.out.println("T1 reserved: " + inv.reserve()));
        Thread t2 = new Thread(() -> System.out.println("T2 reserved: " + inv.reserve()));
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("Stock after: " + inv.stock.get() + " (exactly 0 — never negative)");
    }

    // ─────────────────────────────────────────────────────────────────
    // TRAP 2 — Double-Checked Locking (the famous broken singleton)
    //
    // Problem: You want a lazy singleton that avoids synchronization overhead
    //          on every call. The naive fix is still broken without volatile.
    //
    // Why it breaks: the JVM can reorder instructions. `instance` can be
    // visible to Thread B as non-null BEFORE the constructor finishes executing.
    // Thread B then uses a partially-initialized object.
    //
    // Fix: `volatile` prevents the reordering.
    // ─────────────────────────────────────────────────────────────────
    static void trap2_doubleCheckedLocking() {
        System.out.println("\n===== TRAP 2: Double-Checked Locking =====");

        // BROKEN — no volatile
        class BrokenSingleton {
            private static BrokenSingleton instance; // missing volatile!

            static BrokenSingleton getInstance() {
                if (instance == null) {               // check 1 (outside lock)
                    synchronized (BrokenSingleton.class) {
                        if (instance == null) {       // check 2 (inside lock)
                            instance = new BrokenSingleton(); // can be seen as non-null
                            // before constructor finishes!
                        }
                    }
                }
                return instance;
            }
        }

        // FIXED — volatile prevents the reorder
        class FixedSingleton {
            private static volatile FixedSingleton instance; // volatile is the fix

            static FixedSingleton getInstance() {
                if (instance == null) {
                    synchronized (FixedSingleton.class) {
                        if (instance == null) {
                            instance = new FixedSingleton(); // safe — no reordering
                        }
                    }
                }
                return instance;
            }
        }

        // BEST: initialization-on-demand holder (no synchronization needed at all)
        class BestSingleton {
            private BestSingleton() {}

            // Inner class is loaded only when getInstance() is first called.
            // Class loading is thread-safe by JVM guarantee.
            private static class Holder {
                static final BestSingleton INSTANCE = new BestSingleton();
            }

            static BestSingleton getInstance() { return Holder.INSTANCE; }
        }

        System.out.println("Fixed singleton: " + FixedSingleton.getInstance());
        System.out.println("Best singleton (holder): " + BestSingleton.getInstance());
        System.out.println("Both calls return same instance: "
            + (BestSingleton.getInstance() == BestSingleton.getInstance()));
    }

    // ─────────────────────────────────────────────────────────────────
    // TRAP 3 — Iterator + Structural Modification
    //
    // Problem: A service iterates over its subscriber list to send notifications.
    //          Another thread removes a subscriber mid-iteration.
    //          → ConcurrentModificationException on ArrayList / HashMap.
    //
    // Solutions depend on write/read ratio (same as C05):
    //   Low writes, many reads → CopyOnWriteArrayList
    //   Balanced R/W          → ConcurrentHashMap, synchronized block
    //   Only one thread reads  → synchronized on the list
    // ─────────────────────────────────────────────────────────────────
    static void trap3_iteratorConcurrentModification() throws InterruptedException {
        System.out.println("\n===== TRAP 3: ConcurrentModificationException =====");

        // BROKEN: ArrayList + concurrent modification
        List<String> broken = new ArrayList<>(Arrays.asList("a","b","c","d","e"));
        Thread modifier = new Thread(() -> {
            try { Thread.sleep(5); broken.remove("c"); }
            catch (InterruptedException | java.util.ConcurrentModificationException e) {}
        });
        modifier.start();
        try {
            for (String s : broken) {
                Thread.sleep(2);
                // may throw ConcurrentModificationException
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("  BROKEN: ConcurrentModificationException caught (as expected)");
        }
        modifier.join();

        // FIXED: CopyOnWriteArrayList — iterator works on a snapshot
        List<String> safe = new CopyOnWriteArrayList<>(Arrays.asList("a","b","c","d","e"));
        Thread safeModifier = new Thread(() -> {
            try { Thread.sleep(5); safe.remove("c"); System.out.println("  Removed c"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        safeModifier.start();
        for (String s : safe) {
            Thread.sleep(2);
            System.out.print("  " + s); // sees snapshot: a b c d e (before removal)
        }
        safeModifier.join();
        System.out.println("\n  After iteration: " + safe); // [a, b, d, e]
    }

    // ─────────────────────────────────────────────────────────────────
    // TRAP 4 — Thread Pool Sizing: IO-bound vs CPU-bound
    //
    // Interview Q: How do you size a thread pool?
    //
    // CPU-bound tasks (image processing, encryption, sorting):
    //   threads = CPU cores (or cores+1 to cover one stall)
    //   → More threads = context switching overhead, not more parallelism
    //
    // IO-bound tasks (DB calls, HTTP requests, file reads):
    //   threads = cores × (1 + wait_time / compute_time)
    //   → If 90% of time is waiting, you can have 10× more threads than cores
    //
    // Example: 4-core machine, each task waits 900ms, computes 100ms
    //   threads = 4 × (1 + 9) = 40
    // ─────────────────────────────────────────────────────────────────
    static void trap4_threadPoolSizing() throws InterruptedException {
        System.out.println("\n===== TRAP 4: Thread Pool Sizing =====");

        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("  Available CPU cores: " + cores);

        // CPU-bound: 4 cores → 4-5 threads max
        ExecutorService cpuPool = Executors.newFixedThreadPool(cores + 1);
        System.out.println("  CPU-bound pool size: " + (cores + 1));

        // IO-bound: if 90% wait time, 10× multiplier
        double waitRatio = 9.0; // waitTime / computeTime
        int ioPoolSize = (int)(cores * (1 + waitRatio));
        ExecutorService ioPool = Executors.newFixedThreadPool(ioPoolSize);
        System.out.println("  IO-bound pool size (90% wait): " + ioPoolSize);

        // Using CachedThreadPool for IO-bound (creates threads on demand, recycles them)
        // Good for burst workloads where you don't know peak thread count in advance
        ExecutorService cachedPool = Executors.newCachedThreadPool();
        System.out.println("  CachedThreadPool: unbounded (risky under extreme load — can OOM)");

        cpuPool.shutdown(); ioPool.shutdown(); cachedPool.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────
    // TRAP 5 — Future.get() Blocking the Caller
    //
    // Problem: You submit 3 tasks and call get() on each Future sequentially.
    //          If task1 takes 5s and task2 takes 1s, you wait 5s before you even
    //          start waiting for task2 — even though task2 is already done.
    //
    // Fix: collect all futures first, then call get() in order (or use allOf).
    //      Or: use CompletableFuture.allOf() and join() after.
    // ─────────────────────────────────────────────────────────────────
    static void trap5_futureGetBlocking() throws InterruptedException, ExecutionException {
        System.out.println("\n===== TRAP 5: Future.get() sequential blocking =====");

        ExecutorService pool = Executors.newFixedThreadPool(3);

        // WRONG: get() blocks immediately — waits for f1 to finish before even checking f2
        long start = System.currentTimeMillis();
        Future<String> f1 = pool.submit(() -> { Thread.sleep(500); return "slow-result"; });
        Future<String> f2 = pool.submit(() -> { Thread.sleep(100); return "fast-result"; });
        Future<String> f3 = pool.submit(() -> { Thread.sleep(200); return "medium-result"; });

        // f2 and f3 are DONE by the time get(f1) returns, but we're waiting in order
        String r1 = f1.get(); // blocks 500ms
        String r2 = f2.get(); // already done, returns immediately
        String r3 = f3.get(); // already done, returns immediately
        System.out.printf("  Sequential get() total time: %dms (all ran in parallel, we waited wrong)%n",
            System.currentTimeMillis() - start);

        // CORRECT: submit all, then collect — total time = max(individual times)
        // This is correct too (we still submitted all 3 first), but the pattern is clear:
        // submit ALL tasks before calling get() on ANY of them.
        System.out.println("  Results: " + r1 + ", " + r2 + ", " + r3);

        // EVEN BETTER: CompletableFuture.allOf() — see CompletableFutureExamples.java
        pool.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────
    // TRAP 6 — Synchronizing on a Boxed/Interned Type
    //
    // Problem: Two different locks that happen to be equal can be the SAME object
    //          due to Java's integer caching (-128 to 127) or String interning.
    //          This causes unexpected mutual exclusion between unrelated code.
    //
    // The flip side: locking on a new Integer(x) every time gives you a
    //                different object each time — no mutual exclusion at all!
    // ─────────────────────────────────────────────────────────────────
    static void trap6_synchronizedOnBoxedType() {
        System.out.println("\n===== TRAP 6: Synchronized on boxed/interned type =====");

        // Java caches Integer.valueOf(-128..127)
        Integer lock1 = 42;   // same cached object
        Integer lock2 = 42;   // same cached object!
        System.out.println("lock1 == lock2 (both are 42): " + (lock1 == lock2));  // true — SAME object!

        // This means two unrelated pieces of code synchronizing on "42" will block each other
        // synchronized(lock1) and synchronized(lock2) contend on the SAME monitor

        Integer bigLock1 = 200;  // outside cache range
        Integer bigLock2 = 200;
        System.out.println("bigLock1 == bigLock2 (200): " + (bigLock1 == bigLock2)); // false — different objects

        // RULE: always use a private final Object as your lock, never a String, Integer,
        // Boolean, or any type that might be cached or shared.

        // WRONG:
        // synchronized ("ACCOUNT_LOCK") { ... }  — String literals are interned → shared globally
        // synchronized (Boolean.TRUE) { ... }    — only 2 Boolean objects exist in the JVM

        // RIGHT:
        final Object correctLock = new Object(); // always a new, unique object
        System.out.println("  Use: private final Object lock = new Object();");
    }

    // ─────────────────────────────────────────────────────────────────
    // TRAP 7 — ThreadLocal Memory Leak in Thread Pools
    //
    // Problem: You set a ThreadLocal value in a request handler.
    //          The thread pool reuses the thread for the next request.
    //          The new request sees the PREVIOUS request's ThreadLocal value!
    //          In frameworks like Spring, this can leak user context across requests.
    //
    // Fix: ALWAYS call remove() in a finally block when using thread pools.
    // ─────────────────────────────────────────────────────────────────
    static void trap7_threadLocalLeak() throws InterruptedException {
        System.out.println("\n===== TRAP 7: ThreadLocal leak in thread pool =====");

        ThreadLocal<String> userContext = new ThreadLocal<>();
        ExecutorService pool = Executors.newFixedThreadPool(1); // single thread — reused!

        // BROKEN: first request sets context, never removes it
        pool.submit(() -> {
            userContext.set("alice");  // request 1 sets user
            System.out.println("  Request 1 context: " + userContext.get()); // alice
            // Missing: userContext.remove() ← the bug
        }).get(); // wait for it to finish

        // Same thread handles request 2 — sees alice's context!
        pool.submit(() -> {
            System.out.println("  Request 2 context (LEAKED): " + userContext.get()); // alice! wrong!
        }).get();

        // FIXED: always remove in finally
        pool.submit(() -> {
            userContext.set("bob");
            try {
                System.out.println("  Request 3 context: " + userContext.get()); // bob
            } finally {
                userContext.remove(); // ← cleans up before thread is reused
            }
        }).get();

        pool.submit(() -> {
            System.out.println("  Request 4 context (clean): " + userContext.get()); // null
        }).get();

        pool.shutdown();
    }
}
