package concurrency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * C07 — Practice Drills: write these from memory until they feel automatic.
 *
 * HOW TO USE:
 *   1. Read the problem comment.
 *   2. Cover the solution below it.
 *   3. Write the solution yourself.
 *   4. Compare with the provided solution.
 *   5. Repeat until you don't need to look.
 *
 * These are the ~20 patterns that cover 90% of Java concurrency interview questions.
 */
public class C07_Drills {

    public static void main(String[] args) throws Exception {
        drill01_atomicCounter();
        drill02_stopFlag();
        drill03_launchAndJoin();
        drill04_countDownLatch();
        drill05_producerConsumerBlockingQueue();
        drill06_semaphoreRateLimit();
        drill07_reentrantLockWithTry();
        drill08_threadPoolWithFuture();
        drill09_scheduledTask();
        drill10_concurrentMapCounter();
        drill11_copyOnWriteIterate();
        drill12_readWriteLockCache();
        drill13_completableFutureChain();
        drill14_allOfFanOut();
        drill15_threadLocal();
        drill16_deadlockSafeOrder();
        drill17_cyclicBarrierPhases();
        drill18_exchangerHandoff();
        drill19_lazyVolatileSingleton();
        drill20_customThreadPool();
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 01 — Thread-safe counter that 2 threads increment 10k times each
    // Expected: exactly 20,000
    // Tool: AtomicInteger
    // ─────────────────────────────────────────────────────────────────
    static void drill01_atomicCounter() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Thread t1 = new Thread(() -> { for (int i=0;i<10000;i++) counter.incrementAndGet(); });
        Thread t2 = new Thread(() -> { for (int i=0;i<10000;i++) counter.incrementAndGet(); });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("01 atomic counter: " + counter.get() + " (expected 20000)");
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 02 — Stop a running thread cleanly using a flag
    // Tool: volatile boolean
    // ─────────────────────────────────────────────────────────────────
    static volatile boolean running = false;
    static void drill02_stopFlag() throws InterruptedException {
        running = true;
        Thread worker = new Thread(() -> {
            int ticks = 0;
            while (running) ticks++;
            System.out.println("02 stop flag: stopped after " + ticks + " ticks");
        });
        worker.start();
        Thread.sleep(5);
        running = false;
        worker.join();
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 03 — Launch 5 threads, each prints its name, main waits for all
    // Tool: Thread + join()
    // ─────────────────────────────────────────────────────────────────
    static void drill03_launchAndJoin() throws InterruptedException {
        Thread[] threads = new Thread[5];
        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> System.out.print(Thread.currentThread().getName() + " "), "W" + i);
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        System.out.println("\n03 all threads done");
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 04 — 3 tasks run in parallel; main waits for all to finish
    // Tool: CountDownLatch
    // ─────────────────────────────────────────────────────────────────
    static void drill04_countDownLatch() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            pool.submit(() -> {
                try { Thread.sleep(id * 100); System.out.print("task" + id + " "); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { latch.countDown(); }
            });
        }
        latch.await();
        pool.shutdown();
        System.out.println("\n04 latch: all tasks done");
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 05 — Producer puts 5 items; Consumer takes them with a bounded queue
    // Tool: ArrayBlockingQueue (capacity 2)
    // ─────────────────────────────────────────────────────────────────
    static void drill05_producerConsumerBlockingQueue() throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
        Thread producer = new Thread(() -> {
            try {
                for (int i=1;i<=5;i++) { queue.put(i); System.out.print("P"+i+" "); }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread consumer = new Thread(() -> {
            try {
                for (int i=0;i<5;i++) {
                    int v = queue.take();
                    System.out.print("C"+v+" ");
                    Thread.sleep(80);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        producer.start(); consumer.start();
        producer.join(); consumer.join();
        System.out.println("\n05 producer-consumer done");
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 06 — 10 threads try to call an API limited to 3 concurrent slots
    // Tool: Semaphore(3)
    // ─────────────────────────────────────────────────────────────────
    static void drill06_semaphoreRateLimit() throws InterruptedException {
        Semaphore sem = new Semaphore(3);
        AtomicInteger peak = new AtomicInteger(0);
        AtomicInteger current = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(10);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        for (int i=0;i<10;i++) pool.submit(() -> {
            try {
                sem.acquire();
                int c = current.incrementAndGet();
                peak.updateAndGet(p -> Math.max(p, c));
                Thread.sleep(50);
                current.decrementAndGet();
                sem.release();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { latch.countDown(); }
        });
        latch.await(); pool.shutdown();
        System.out.println("06 semaphore: peak concurrent = " + peak.get() + " (max allowed 3)");
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 07 — Increment a counter using ReentrantLock; always unlock in finally
    // Tool: ReentrantLock
    // ─────────────────────────────────────────────────────────────────
    static void drill07_reentrantLockWithTry() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        int[] counter = {0};
        Runnable task = () -> {
            for (int i=0;i<5000;i++) {
                lock.lock();
                try { counter[0]++; }
                finally { lock.unlock(); }
            }
        };
        Thread t1 = new Thread(task); Thread t2 = new Thread(task);
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("07 ReentrantLock counter: " + counter[0] + " (expected 10000)");
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 08 — Submit 3 Callables to a pool, collect results via Future
    // Tool: ExecutorService + Callable + Future
    // ─────────────────────────────────────────────────────────────────
    static void drill08_threadPoolWithFuture() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i=1;i<=3;i++) {
            final int n = i;
            futures.add(pool.submit(() -> { Thread.sleep(n*100); return n*n; }));
        }
        // Submit ALL before calling get() on ANY
        for (Future<Integer> f : futures) System.out.print("08 result: " + f.get() + " ");
        System.out.println();
        pool.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 09 — Print "tick" every 500ms for 2 seconds, then cancel
    // Tool: ScheduledExecutorService
    // ─────────────────────────────────────────────────────────────────
    static void drill09_scheduledTask() throws InterruptedException {
        ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
        AtomicInteger ticks = new AtomicInteger(0);
        ScheduledFuture<?> task = sched.scheduleAtFixedRate(() -> {
            System.out.print("tick" + ticks.incrementAndGet() + " ");
        }, 0, 500, TimeUnit.MILLISECONDS);
        Thread.sleep(2000);
        task.cancel(false);
        sched.shutdown();
        System.out.println("\n09 scheduled: " + ticks.get() + " ticks");
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 10 — Count frequency of words across 5 concurrent threads
    // Tool: ConcurrentHashMap + computeIfAbsent + AtomicInteger
    // ─────────────────────────────────────────────────────────────────
    static void drill10_concurrentMapCounter() throws InterruptedException {
        ConcurrentHashMap<String, AtomicInteger> freq = new ConcurrentHashMap<>();
        String[] words = {"apple","banana","apple","cherry","banana","apple"};
        CountDownLatch latch = new CountDownLatch(words.length);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        for (String w : words) pool.submit(() -> {
            freq.computeIfAbsent(w, k -> new AtomicInteger()).incrementAndGet();
            latch.countDown();
        });
        latch.await(); pool.shutdown();
        freq.forEach((k,v) -> System.out.print("10 " + k + "=" + v + " "));
        System.out.println();
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 11 — Iterate a list while another thread removes elements
    // Tool: CopyOnWriteArrayList (no ConcurrentModificationException)
    // ─────────────────────────────────────────────────────────────────
    static void drill11_copyOnWriteIterate() throws InterruptedException {
        List<Integer> list = new CopyOnWriteArrayList<>(Arrays.asList(1,2,3,4,5));
        Thread remover = new Thread(() -> { list.remove(Integer.valueOf(3)); });
        remover.start();
        StringBuilder sb = new StringBuilder("11 COW iterate: ");
        for (int x : list) sb.append(x).append(" "); // snapshot — no CME
        remover.join();
        System.out.println(sb + "| after: " + list);
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 12 — Cache with ReadWriteLock: multiple readers, exclusive writer
    // Tool: ReentrantReadWriteLock
    // ─────────────────────────────────────────────────────────────────
    static void drill12_readWriteLockCache() throws InterruptedException {
        ReadWriteLock rw = new ReentrantReadWriteLock();
        String[] cache = {"v1"};

        Runnable reader = () -> {
            rw.readLock().lock();
            try { System.out.print("R:" + cache[0] + " "); }
            finally { rw.readLock().unlock(); }
        };
        Runnable writer = () -> {
            rw.writeLock().lock();
            try { cache[0] = "v2"; System.out.print("W:v2 "); }
            finally { rw.writeLock().unlock(); }
        };

        Thread r1 = new Thread(reader); Thread r2 = new Thread(reader);
        Thread w  = new Thread(writer);
        r1.start(); r2.start(); Thread.sleep(10); w.start();
        r1.join(); r2.join(); w.join();
        System.out.println("\n12 RW lock done");
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 13 — Fetch user → fetch orders for that user (dependent async chain)
    // Tool: CompletableFuture.thenCompose
    // ─────────────────────────────────────────────────────────────────
    static void drill13_completableFutureChain() throws Exception {
        String result = CompletableFuture
            .supplyAsync(() -> { sleep(100); return "user-42"; })
            .thenCompose(uid -> CompletableFuture.supplyAsync(() -> {
                sleep(100); return "orders-of-" + uid;
            }))
            .get();
        System.out.println("13 chain: " + result);
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 14 — Send email + SMS + push in parallel; wait for all 3
    // Tool: CompletableFuture.allOf
    // ─────────────────────────────────────────────────────────────────
    static void drill14_allOfFanOut() throws Exception {
        CompletableFuture<String> email = CompletableFuture.supplyAsync(() -> { sleep(200); return "email"; });
        CompletableFuture<String> sms   = CompletableFuture.supplyAsync(() -> { sleep(100); return "sms";   });
        CompletableFuture<String> push  = CompletableFuture.supplyAsync(() -> { sleep(150); return "push";  });
        CompletableFuture.allOf(email, sms, push).get();
        System.out.println("14 allOf: " + email.get() + " + " + sms.get() + " + " + push.get());
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 15 — Each thread gets its own request ID via ThreadLocal
    // Tool: ThreadLocal
    // ─────────────────────────────────────────────────────────────────
    static final ThreadLocal<String> REQ_ID = ThreadLocal.withInitial(() -> "none");
    static void drill15_threadLocal() throws InterruptedException {
        Runnable task = () -> {
            REQ_ID.set("REQ-" + Thread.currentThread().getName());
            try { System.out.print("15 " + REQ_ID.get() + " "); }
            finally { REQ_ID.remove(); }
        };
        Thread a = new Thread(task, "A"); Thread b = new Thread(task, "B");
        a.start(); b.start(); a.join(); b.join();
        System.out.println();
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 16 — Transfer between two bank accounts WITHOUT deadlock
    // Tool: consistent lock ordering (always lock lower-id account first)
    // ─────────────────────────────────────────────────────────────────
    static void drill16_deadlockSafeOrder() throws InterruptedException {
        class Account {
            final int id; int balance;
            Account(int id, int bal) { this.id=id; this.balance=bal; }
        }

        Account a1 = new Account(1, 1000);
        Account a2 = new Account(2, 500);

        Runnable transfer = (from, to, amount) -> {
            Account first  = from.id < to.id ? from : to;   // always lock smaller id first
            Account second = from.id < to.id ? to   : from;
            synchronized (first) {
                synchronized (second) {
                    from.balance -= amount;
                    to.balance   += amount;
                }
            }
        };
        // Can't use lambda with 3 params — use a helper
        Thread t1 = new Thread(() -> transfer(a1, a2, 100));
        Thread t2 = new Thread(() -> transfer(a2, a1, 200));
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("16 deadlock-safe transfer: a1=" + a1.balance + " a2=" + a2.balance);
    }

    interface Transfer { void apply(Account from, Account to, int amount); }
    static void transfer(Account from, Account to, int amount) {
        Account first  = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to   : from;
        synchronized (first) { synchronized (second) { from.balance -= amount; to.balance += amount; } }
    }
    static class Account { final int id; int balance; Account(int id, int b) { this.id=id; this.balance=b; } }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 17 — 3 ETL workers sync at end of Extract and end of Transform
    // Tool: CyclicBarrier (reusable — resets after each phase)
    // ─────────────────────────────────────────────────────────────────
    static void drill17_cyclicBarrierPhases() throws InterruptedException {
        CyclicBarrier gate = new CyclicBarrier(3);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch done = new CountDownLatch(3);
        for (int i=0;i<3;i++) {
            final int w=i;
            pool.submit(() -> {
                try {
                    Thread.sleep(100*(w+1)); System.out.print("E"+w+" ");
                    gate.await();  // wait for all to finish Extract
                    Thread.sleep(50*(w+1));  System.out.print("T"+w+" ");
                    gate.await();  // wait for all to finish Transform
                    System.out.print("L"+w+" ");
                } catch (Exception e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            });
        }
        done.await(); pool.shutdown();
        System.out.println("\n17 ETL phases complete");
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 18 — Two threads exchange data at a synchronization point
    // Tool: Exchanger (like a two-way BlockingQueue with exactly 1 slot each)
    // Real-world: ping-pong buffer, pipeline handoff between two threads
    // ─────────────────────────────────────────────────────────────────
    static void drill18_exchangerHandoff() throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        Thread producer = new Thread(() -> {
            try {
                String received = exchanger.exchange("data-from-producer");
                System.out.println("18 producer received: " + received);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread consumer = new Thread(() -> {
            try {
                String received = exchanger.exchange("ack-from-consumer");
                System.out.println("18 consumer received: " + received);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        producer.start(); consumer.start();
        producer.join(); consumer.join();
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 19 — Lazy singleton with volatile double-checked locking
    // Tool: volatile + synchronized (or static Holder pattern)
    // ─────────────────────────────────────────────────────────────────
    static volatile C07_Drills INSTANCE = null;
    static C07_Drills getInstance() {
        if (INSTANCE == null) {                   // first check (no lock — fast path)
            synchronized (C07_Drills.class) {
                if (INSTANCE == null) {           // second check (inside lock)
                    INSTANCE = new C07_Drills();
                }
            }
        }
        return INSTANCE;
    }
    static void drill19_lazyVolatileSingleton() {
        C07_Drills s1 = getInstance();
        C07_Drills s2 = getInstance();
        System.out.println("19 singleton same instance: " + (s1 == s2));
    }

    // ─────────────────────────────────────────────────────────────────
    // DRILL 20 — Custom thread pool: 3 workers, named threads, handle exceptions
    // Tool: ThreadPoolExecutor + ThreadFactory + UncaughtExceptionHandler
    // ─────────────────────────────────────────────────────────────────
    static void drill20_customThreadPool() throws InterruptedException {
        ThreadFactory factory = r -> {
            Thread t = new Thread(r);
            t.setName("worker-" + t.getId());
            t.setUncaughtExceptionHandler((thread, ex) ->
                System.out.println("  [UEH] " + thread.getName() + " threw: " + ex.getMessage()));
            return t;
        };

        ExecutorService pool = new ThreadPoolExecutor(
            3, 3,            // core and max pool size
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10),
            factory
        );

        CountDownLatch latch = new CountDownLatch(3);
        pool.submit(() -> { System.out.println("20 " + Thread.currentThread().getName() + " ok"); latch.countDown(); });
        pool.submit(() -> { System.out.println("20 " + Thread.currentThread().getName() + " ok"); latch.countDown(); });
        pool.submit(() -> { throw new RuntimeException("task failed"); }); // triggers UEH
        latch.await(2, TimeUnit.SECONDS);
        pool.shutdown();
    }

    // ── helpers ──────────────────────────────────────────────────────
    static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
