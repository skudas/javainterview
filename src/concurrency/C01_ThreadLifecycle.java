package concurrency;

/**
 * C01 — Thread Lifecycle, Creation Styles, join/interrupt/daemon, ThreadLocal
 *
 * Thread states:
 *   NEW → RUNNABLE → (BLOCKED | WAITING | TIMED_WAITING) → TERMINATED
 *
 * BLOCKED      : waiting to acquire a synchronized lock
 * WAITING      : waiting indefinitely — via wait(), join(), LockSupport.park()
 * TIMED_WAITING: waiting with a timeout — sleep(), wait(ms), join(ms)
 */
public class C01_ThreadLifecycle {

    public static void main(String[] args) throws InterruptedException {
        demo1_threeWaysToCreateThread();
        demo2_joinAndState();
        demo3_interruptCooperation();
        demo4_daemonThread();
        demo5_threadLocal();
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 1 — Three ways to create a thread
    // Interview Q: What's the difference between Runnable and Callable?
    //   → Callable returns a value and can throw checked exceptions.
    //     Runnable cannot.
    // ─────────────────────────────────────────────────────────────────
    static void demo1_threeWaysToCreateThread() throws InterruptedException {
        System.out.println("===== 1. Three ways to create a thread =====");

        // Way 1: extend Thread (avoid — wastes your one inheritance slot)
        Thread t1 = new Thread("t1") {
            @Override public void run() {
                System.out.println(getName() + " (extends Thread)");
            }
        };

        // Way 2: Runnable lambda (preferred for fire-and-forget tasks)
        Thread t2 = new Thread(() -> System.out.println(Thread.currentThread().getName() + " (Runnable)"), "t2");

        // Way 3: start() vs run() — the most common gotcha
        // t2.run()  → executes on THIS thread, no new thread created
        // t2.start() → creates a new OS thread, calls run() on it
        t1.start();
        t2.start();
        t1.join();  // main waits for t1 to finish
        t2.join();
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 2 — join() and Thread.State
    // Interview Q: What does join() do?
    //   → The calling thread blocks until the target thread reaches TERMINATED.
    // ─────────────────────────────────────────────────────────────────
    static void demo2_joinAndState() throws InterruptedException {
        System.out.println("\n===== 2. join() and Thread.State =====");

        Thread worker = new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "worker");

        System.out.println("Before start: " + worker.getState());   // NEW
        worker.start();
        System.out.println("After  start: " + worker.getState());   // RUNNABLE or TIMED_WAITING
        worker.join();
        System.out.println("After  join : " + worker.getState());   // TERMINATED
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 3 — Interruption (cooperative cancellation)
    // Interview Q: Can you force a thread to stop?
    //   → No. Thread.stop() is deprecated (leaves locks unreleased).
    //     Interruption is a cooperative signal — the thread must check it.
    //
    // Two ways a thread sees the interrupt:
    //   a) It's in a blocking call (sleep/wait/join) → InterruptedException thrown
    //   b) It's running → check Thread.currentThread().isInterrupted() manually
    // ─────────────────────────────────────────────────────────────────
    static void demo3_interruptCooperation() throws InterruptedException {
        System.out.println("\n===== 3. Interrupt — cooperative cancellation =====");

        // Pattern A: interrupted during sleep
        Thread sleeper = new Thread(() -> {
            try {
                System.out.println("Sleeper: going to sleep...");
                Thread.sleep(5000);
                System.out.println("Sleeper: woke up normally");
            } catch (InterruptedException e) {
                // IMPORTANT: restore the interrupt flag — callers up the chain may need it
                Thread.currentThread().interrupt();
                System.out.println("Sleeper: interrupted during sleep, shutting down cleanly");
            }
        }, "sleeper");
        sleeper.start();
        Thread.sleep(100);
        sleeper.interrupt();   // sends the signal
        sleeper.join();

        // Pattern B: interrupted during CPU work (busy loop)
        Thread worker = new Thread(() -> {
            int i = 0;
            while (!Thread.currentThread().isInterrupted()) {  // check the flag
                i++;  // simulate CPU-bound work
            }
            System.out.println("Worker: saw interrupt flag, stopping after " + i + " iterations");
        }, "worker");
        worker.start();
        Thread.sleep(10);
        worker.interrupt();
        worker.join();
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 4 — Daemon threads
    // Interview Q: What is a daemon thread?
    //   → A background thread. The JVM exits when ALL non-daemon threads finish,
    //     even if daemon threads are still running.
    //   → Use for: background cleanup, GC helpers, heartbeat monitors.
    //   → Must call setDaemon(true) BEFORE start().
    // ─────────────────────────────────────────────────────────────────
    static void demo4_daemonThread() throws InterruptedException {
        System.out.println("\n===== 4. Daemon thread =====");

        Thread daemon = new Thread(() -> {
            while (true) {
                System.out.println("Daemon: tick (will be killed when main exits)");
                try { Thread.sleep(200); } catch (InterruptedException e) { break; }
            }
        });
        daemon.setDaemon(true);   // must be BEFORE start()
        daemon.start();

        Thread.sleep(500);   // let it tick a couple of times
        System.out.println("Main: done — JVM will exit, daemon gets killed");
        // no join() needed — JVM exits when main (non-daemon) thread finishes
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 5 — ThreadLocal: per-thread storage
    // Interview Q: When would you use ThreadLocal?
    //   → When you need per-thread state without passing it as a parameter:
    //     - Database connections per request thread
    //     - SimpleDateFormat (which is not thread-safe)
    //     - User context / request ID in web frameworks
    //
    // WARNING: Always remove() in a finally block when using thread pools,
    //          or the value leaks to the next task reusing that thread.
    // ─────────────────────────────────────────────────────────────────
    static final ThreadLocal<String> REQUEST_ID = ThreadLocal.withInitial(() -> "none");

    static void demo5_threadLocal() throws InterruptedException {
        System.out.println("\n===== 5. ThreadLocal =====");

        Runnable task = () -> {
            REQUEST_ID.set("REQ-" + Thread.currentThread().getName());
            try {
                simulateWork(100);
                System.out.println(Thread.currentThread().getName()
                    + " sees requestId: " + REQUEST_ID.get());
            } finally {
                REQUEST_ID.remove();  // critical in thread pools — prevents value bleed
            }
        };

        Thread t1 = new Thread(task, "thread-A");
        Thread t2 = new Thread(task, "thread-B");
        t1.start(); t2.start();
        t1.join();  t2.join();

        System.out.println("Main sees requestId: " + REQUEST_ID.get()); // "none" — unaffected
    }

    static void simulateWork(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
