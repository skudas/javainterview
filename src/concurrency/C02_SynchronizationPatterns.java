package concurrency;

import java.util.concurrent.locks.*;

/**
 * C02 — synchronized, ReentrantLock, ReadWriteLock, Condition
 *
 * Three levels of locking (coarse → fine):
 *   synchronized method   → locks the whole method on `this`
 *   synchronized block    → locks a specific object, for a specific scope
 *   ReentrantLock         → explicit lock with extra superpowers
 *
 * Interview rules:
 *   - synchronized(this) and synchronized method lock the SAME object → they block each other
 *   - synchronized(SomeClass.class) is the static equivalent
 *   - Locks protect data, not code — always think "which object am I locking?"
 */
public class C02_SynchronizationPatterns {

    public static void main(String[] args) throws InterruptedException {
        demo1_synchronizedMethodVsBlock();
        demo2_wrongLockObject();
        demo3_reentrantLock();
        demo4_readWriteLock();
        demo5_condition_boundedBuffer();
        demo6_deadlockAndFix();
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 1 — synchronized method vs block
    // Both lock on `this` — they are mutually exclusive.
    // The block form is preferred: it locks for the shortest possible scope.
    // ─────────────────────────────────────────────────────────────────
    static void demo1_synchronizedMethodVsBlock() throws InterruptedException {
        System.out.println("===== 1. synchronized method vs block =====");

        class Counter {
            private int count = 0;
            private final Object lock = new Object();

            // locks the whole method
            public synchronized void incrementMethod() { count++; }

            // locks only what needs protecting — better for long methods
            public void incrementBlock() {
                // do pre-processing (no lock needed)...
                synchronized (lock) { count++; }
                // do post-processing (no lock needed)...
            }

            public int get() { return count; }
        }

        Counter c = new Counter();
        Thread t1 = new Thread(() -> { for (int i=0;i<10000;i++) c.incrementMethod(); });
        Thread t2 = new Thread(() -> { for (int i=0;i<10000;i++) c.incrementMethod(); });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("Count (should be 20000): " + c.get());
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 2 — The #1 mistake: locking on the WRONG object
    // Interview Q: Why doesn't this synchronization work?
    // ─────────────────────────────────────────────────────────────────
    static void demo2_wrongLockObject() throws InterruptedException {
        System.out.println("\n===== 2. Wrong lock object — common bug =====");

        class BrokenCounter {
            private int count = 0;

            // BUG: each thread creates its own Integer lock — they never contend
            public void increment() {
                Integer lock = count; // NEW object each time → different lock each time
                synchronized (lock) { count++; }
            }
        }

        class FixedCounter {
            private int count = 0;
            private final Object lock = new Object(); // single shared lock

            public void increment() {
                synchronized (lock) { count++; }
            }
        }

        BrokenCounter broken = new BrokenCounter();
        Thread t1 = new Thread(() -> { for (int i=0;i<10000;i++) broken.increment(); });
        Thread t2 = new Thread(() -> { for (int i=0;i<10000;i++) broken.increment(); });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("Broken count (not 20000): " + broken.count); // race condition!

        FixedCounter fixed = new FixedCounter();
        t1 = new Thread(() -> { for (int i=0;i<10000;i++) fixed.increment(); });
        t2 = new Thread(() -> { for (int i=0;i<10000;i++) fixed.increment(); });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("Fixed count (exactly 20000): " + fixed.count);
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 3 — ReentrantLock: same as synchronized + extra powers
    //
    // Extra over synchronized:
    //   tryLock(timeout)     → give up if can't acquire within time limit
    //   lockInterruptibly()  → respond to Thread.interrupt() while waiting
    //   new ReentrantLock(true) → fair mode (FIFO, prevents thread starvation)
    //   lock.getQueueLength()   → inspect how many threads are waiting
    // ─────────────────────────────────────────────────────────────────
    static void demo3_reentrantLock() throws InterruptedException {
        System.out.println("\n===== 3. ReentrantLock =====");

        class SafeCounter {
            private int count = 0;
            private final ReentrantLock lock = new ReentrantLock();

            public void increment() {
                lock.lock();
                try {
                    count++;
                } finally {
                    lock.unlock(); // ALWAYS in finally — never skip, even on exception
                }
            }

            public boolean tryIncrement() {
                if (lock.tryLock()) {  // non-blocking attempt
                    try { count++; return true; }
                    finally { lock.unlock(); }
                }
                System.out.println(Thread.currentThread().getName() + ": couldn't acquire lock, skipping");
                return false;
            }

            public int get() { return count; }
        }

        SafeCounter c = new SafeCounter();
        Thread t1 = new Thread(() -> { for (int i=0;i<5000;i++) c.increment(); }, "A");
        Thread t2 = new Thread(() -> { for (int i=0;i<5000;i++) c.increment(); }, "B");
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("ReentrantLock count (should be 10000): " + c.get());

        // tryLock demo — one thread holds it, others try and give up
        ReentrantLock busyLock = new ReentrantLock();
        busyLock.lock();
        new Thread(() -> {
            boolean got = busyLock.tryLock();
            System.out.println("tryLock while held: " + got); // false
        }).start();
        Thread.sleep(100);
        busyLock.unlock();
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 4 — ReadWriteLock
    // Interview Q: When would you use ReadWriteLock over ReentrantLock?
    //   → When reads are frequent and writes are rare.
    //     Multiple readers can hold the read lock simultaneously.
    //     A writer gets exclusive access (blocks all readers and other writers).
    //
    // Real-world: in-memory cache, configuration store, routing table
    // ─────────────────────────────────────────────────────────────────
    static void demo4_readWriteLock() throws InterruptedException {
        System.out.println("\n===== 4. ReadWriteLock — shared reads, exclusive writes =====");

        class Cache {
            private String data = "initial";
            private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
            private final Lock readLock  = rwLock.readLock();
            private final Lock writeLock = rwLock.writeLock();

            public String read() {
                readLock.lock();
                try { return data; }
                finally { readLock.unlock(); }
            }

            public void write(String newData) {
                writeLock.lock();
                try { data = newData; }
                finally { writeLock.unlock(); }
            }
        }

        Cache cache = new Cache();

        // 3 concurrent readers — all run in parallel (no blocking between them)
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            new Thread(() -> System.out.println("Reader-" + id + ": " + cache.read())).start();
        }
        Thread.sleep(50);

        // Writer gets exclusive access
        new Thread(() -> {
            cache.write("updated-data");
            System.out.println("Writer: updated cache");
        }).start();

        Thread.sleep(100);
        System.out.println("Final cache value: " + cache.read());
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 5 — Condition: the ReentrantLock replacement for wait/notify
    // Interview Q: What's the advantage of Condition over wait/notify?
    //   → You can have MULTIPLE conditions per lock.
    //     With synchronized you only get one wait-set per object.
    //
    // Classic use: Producer-Consumer with separate "not full" and "not empty" signals
    // ─────────────────────────────────────────────────────────────────
    static void demo5_condition_boundedBuffer() throws InterruptedException {
        System.out.println("\n===== 5. Condition — fine-grained wait/signal =====");

        class BoundedBuffer {
            private final int[] buf = new int[3];
            private int head, tail, count;
            private final ReentrantLock lock = new ReentrantLock();
            private final Condition notFull  = lock.newCondition(); // producer waits here
            private final Condition notEmpty = lock.newCondition(); // consumer waits here

            public void put(int x) throws InterruptedException {
                lock.lock();
                try {
                    while (count == buf.length) notFull.await();  // wait — still in while loop (spurious wakeup safe)
                    buf[tail++ % buf.length] = x;
                    count++;
                    System.out.println("Put " + x + "  (size=" + count + ")");
                    notEmpty.signal();  // wake ONE consumer
                } finally { lock.unlock(); }
            }

            public int take() throws InterruptedException {
                lock.lock();
                try {
                    while (count == 0) notEmpty.await();
                    int x = buf[head++ % buf.length];
                    count--;
                    System.out.println("Took " + x + " (size=" + count + ")");
                    notFull.signal();  // wake ONE producer
                    return x;
                } finally { lock.unlock(); }
            }
        }

        BoundedBuffer buf = new BoundedBuffer();
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 6; i++) { buf.put(i); Thread.sleep(100); }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 6; i++) { buf.take(); Thread.sleep(200); }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        producer.start(); consumer.start();
        producer.join(); consumer.join();
    }

    // ─────────────────────────────────────────────────────────────────
    // DEMO 6 — Deadlock and how to fix it
    // Interview Q: How do you prevent deadlocks?
    //   → 1. Always acquire locks in the SAME global order
    //   → 2. Use tryLock(timeout) and back off if you can't get both
    //   → 3. Use a single lock for both resources (when possible)
    // ─────────────────────────────────────────────────────────────────
    static void demo6_deadlockAndFix() throws InterruptedException {
        System.out.println("\n===== 6. Deadlock and the fix =====");

        Object lockA = new Object();
        Object lockB = new Object();

        // DEADLOCK — thread1 locks A then B, thread2 locks B then A
        // Uncomment to see it hang:
        /*
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                simulateWork(50);
                synchronized (lockB) { System.out.println("t1 done"); }
            }
        });
        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                simulateWork(50);
                synchronized (lockA) { System.out.println("t2 done"); }  // ← deadlock
            }
        });
        */

        // FIX 1: Consistent lock ordering — BOTH threads acquire A before B
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                simulateWork(50);
                synchronized (lockB) { System.out.println("t1 done (safe order)"); }
            }
        });
        Thread t2 = new Thread(() -> {
            synchronized (lockA) {  // same order as t1 — now safe
                simulateWork(50);
                synchronized (lockB) { System.out.println("t2 done (safe order)"); }
            }
        });

        t1.start(); t2.start();
        t1.join();  t2.join();

        // FIX 2: tryLock with timeout (when you can't control lock order)
        ReentrantLock la = new ReentrantLock();
        ReentrantLock lb = new ReentrantLock();

        Runnable tryLockTask = () -> {
            try {
                while (true) {
                    boolean gotA = la.tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (!gotA) continue;
                    try {
                        boolean gotB = lb.tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (!gotB) continue;  // back off, try again
                        try {
                            System.out.println(Thread.currentThread().getName() + ": got both locks");
                            return;
                        } finally { lb.unlock(); }
                    } finally { la.unlock(); }
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };

        Thread ta = new Thread(tryLockTask, "tryLock-A");
        Thread tb = new Thread(tryLockTask, "tryLock-B");
        ta.start(); tb.start();
        ta.join(); tb.join();
        System.out.println("tryLock approach: both completed without deadlock");
    }

    static void simulateWork(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
