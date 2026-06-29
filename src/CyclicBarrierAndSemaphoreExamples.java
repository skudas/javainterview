import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CyclicBarrier vs Semaphore — Real-Time Usage Examples
 *
 * CountDownLatch  → one-time gate: threads wait for N events to happen (count never resets)
 * CyclicBarrier   → reusable meeting point: N threads wait for EACH OTHER before proceeding together
 * Semaphore       → resource throttle: limits how many threads access a resource concurrently
 */
public class CyclicBarrierAndSemaphoreExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║           CYCLIC BARRIER EXAMPLES            ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        barrier1_gameRoundSync();
        barrier2_parallelDataPipeline();
        barrier3_distributedSystemBootstrap();

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║             SEMAPHORE EXAMPLES               ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        semaphore1_databaseConnectionPool();
        semaphore2_apiRateLimiting();
        semaphore3_printSpooler();
        semaphore4_binaryMutex();
    }

    // =========================================================================
    //  CYCLIC BARRIER EXAMPLES
    //
    //  CyclicBarrier(N, barrierAction)
    //    - N threads each call barrier.await()
    //    - All N block until the last one arrives
    //    - Optional barrierAction runs once when all arrive (in the last thread)
    //    - Resets automatically → can be reused for the next "round"
    // =========================================================================

    // ─────────────────────────────────────────────────────────────────────────
    // BARRIER 1: Multiplayer Game Round Synchronization
    // Real-time: 4 players must all finish loading before the round starts.
    //            After each round, the barrier resets for the next one.
    // ─────────────────────────────────────────────────────────────────────────
    static void barrier1_gameRoundSync() throws InterruptedException {
        System.out.println("\n----- Barrier 1: Multiplayer Game Round Sync -----");

        int playerCount = 4;
        int totalRounds = 2;

        CyclicBarrier barrier = new CyclicBarrier(playerCount,
            () -> System.out.println("  [Barrier] All players ready — ROUND STARTS!\n")); // runs when all arrive

        ExecutorService pool = Executors.newFixedThreadPool(playerCount);

        for (int round = 1; round <= totalRounds; round++) {
            final int r = round;
            System.out.println("--- Round " + r + " loading ---");

            for (int p = 1; p <= playerCount; p++) {
                final int player = p;
                pool.submit(() -> {
                    try {
                        int loadTime = 200 + new java.util.Random().nextInt(600);
                        Thread.sleep(loadTime);
                        System.out.printf("  Player-%d finished loading (%dms)%n", player, loadTime);
                        barrier.await(); // wait for all players
                        System.out.printf("  Player-%d playing round %d%n", player, r);
                    } catch (Exception e) { Thread.currentThread().interrupt(); }
                });
            }
            Thread.sleep(1800); // let round finish before next round submission
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BARRIER 2: Parallel Data Pipeline with Phase Sync
    // Real-time: ETL pipeline — 3 workers each process a data partition.
    //            Phase 1 (Extract) must fully complete before Phase 2 (Transform)
    //            begins, and Phase 2 before Phase 3 (Load).
    // ─────────────────────────────────────────────────────────────────────────
    static void barrier2_parallelDataPipeline() throws InterruptedException {
        System.out.println("\n----- Barrier 2: ETL Pipeline Phase Synchronization -----");

        int workers = 3;
        CyclicBarrier phaseGate = new CyclicBarrier(workers);
        ExecutorService pool = Executors.newFixedThreadPool(workers);

        String[] partitions = {"Partition-A", "Partition-B", "Partition-C"};

        for (int i = 0; i < workers; i++) {
            final String partition = partitions[i];
            pool.submit(() -> {
                try {
                    // Phase 1: Extract
                    Thread.sleep(300 + new java.util.Random().nextInt(300));
                    System.out.println("  [Extract] " + partition + " done");
                    phaseGate.await(); // all must finish extracting before any transforms

                    // Phase 2: Transform
                    Thread.sleep(200 + new java.util.Random().nextInt(300));
                    System.out.println("  [Transform] " + partition + " done");
                    phaseGate.await(); // all must finish transforming before any loads

                    // Phase 3: Load
                    Thread.sleep(100 + new java.util.Random().nextInt(200));
                    System.out.println("  [Load] " + partition + " done");

                } catch (Exception e) { Thread.currentThread().interrupt(); }
            });
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  ETL pipeline complete.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BARRIER 3: Distributed System Bootstrap
    // Real-time: Microservices startup — Config, Database, and Cache services
    //            must ALL be initialized before the HTTP server accepts traffic.
    // ─────────────────────────────────────────────────────────────────────────
    static void barrier3_distributedSystemBootstrap() throws InterruptedException {
        System.out.println("\n----- Barrier 3: Microservice Bootstrap Sync -----");

        String[] services = {"ConfigService", "DatabaseService", "CacheService"};
        CyclicBarrier startGate = new CyclicBarrier(services.length,
            () -> System.out.println("  [Barrier] All services up — HTTP server now accepting traffic!"));

        ExecutorService pool = Executors.newFixedThreadPool(services.length);

        for (String service : services) {
            pool.submit(() -> {
                try {
                    int bootTime = 300 + new java.util.Random().nextInt(700);
                    Thread.sleep(bootTime);
                    System.out.printf("  %s initialized (%dms)%n", service, bootTime);
                    startGate.await();
                } catch (Exception e) { Thread.currentThread().interrupt(); }
            });
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    // =========================================================================
    //  SEMAPHORE EXAMPLES
    //
    //  Semaphore(permits)
    //    - acquire() → blocks if no permits available (decrements count)
    //    - release() → returns a permit (increments count)
    //    - Controls how many threads can access a resource AT THE SAME TIME
    //    - fair=true → FIFO queue (prevents starvation)
    // =========================================================================

    // ─────────────────────────────────────────────────────────────────────────
    // SEMAPHORE 1: Database Connection Pool
    // Real-time: DB pool has only 3 connections. 8 threads want to query.
    //            Only 3 can hold a connection at a time; rest wait in line.
    // ─────────────────────────────────────────────────────────────────────────
    static void semaphore1_databaseConnectionPool() throws InterruptedException {
        System.out.println("\n----- Semaphore 1: Database Connection Pool (3 connections, 8 threads) -----");

        Semaphore connectionPool = new Semaphore(3, true); // 3 permits, fair queue
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger activeConnections = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(8);

        for (int i = 1; i <= 8; i++) {
            final int threadId = i;
            pool.submit(() -> {
                try {
                    connectionPool.acquire(); // blocks if all 3 connections are in use
                    int active = activeConnections.incrementAndGet();
                    System.out.printf("  Thread-%d acquired connection (active: %d/3)%n", threadId, active);

                    Thread.sleep(400); // simulate DB query

                    activeConnections.decrementAndGet();
                    System.out.printf("  Thread-%d released connection%n", threadId);
                    connectionPool.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEMAPHORE 2: API Rate Limiting
    // Real-time: Third-party API allows only 3 concurrent requests.
    //            10 microservice calls must be throttled to respect the limit.
    // ─────────────────────────────────────────────────────────────────────────
    static void semaphore2_apiRateLimiting() throws InterruptedException {
        System.out.println("\n----- Semaphore 2: API Rate Limiter (max 3 concurrent calls) -----");

        Semaphore rateLimiter = new Semaphore(3);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger concurrentCalls = new AtomicInteger(0);

        for (int i = 1; i <= 10; i++) {
            final int requestId = i;
            pool.submit(() -> {
                try {
                    rateLimiter.acquire();
                    int concurrent = concurrentCalls.incrementAndGet();
                    System.out.printf("  Request-%d started  (concurrent: %d)%n", requestId, concurrent);

                    Thread.sleep(500); // simulate API call latency

                    concurrentCalls.decrementAndGet();
                    System.out.printf("  Request-%d completed%n", requestId);
                    rateLimiter.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEMAPHORE 3: Print Spooler (bounded resource with tryAcquire)
    // Real-time: Office has 2 printers. If both are busy, the job is rejected
    //            immediately instead of waiting (non-blocking tryAcquire).
    // ─────────────────────────────────────────────────────────────────────────
    static void semaphore3_printSpooler() throws InterruptedException {
        System.out.println("\n----- Semaphore 3: Print Spooler — 2 printers, tryAcquire (non-blocking) -----");

        Semaphore printers = new Semaphore(2);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch latch = new CountDownLatch(6);

        for (int i = 1; i <= 6; i++) {
            final int jobId = i;
            pool.submit(() -> {
                try {
                    boolean acquired = printers.tryAcquire(200, TimeUnit.MILLISECONDS); // don't wait forever
                    if (acquired) {
                        System.out.printf("  PrintJob-%d started on a printer%n", jobId);
                        Thread.sleep(600);
                        System.out.printf("  PrintJob-%d finished%n", jobId);
                        printers.release();
                    } else {
                        System.out.printf("  PrintJob-%d REJECTED — no printer available%n", jobId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEMAPHORE 4: Binary Semaphore as Mutex
    // Real-time: Two threads update a shared bank account balance.
    //            Semaphore(1) acts like a lock — only one thread writes at a time.
    //            Unlike synchronized, the release can come from a DIFFERENT thread.
    // ─────────────────────────────────────────────────────────────────────────
    static void semaphore4_binaryMutex() throws InterruptedException {
        System.out.println("\n----- Semaphore 4: Binary Semaphore as Mutex (shared bank balance) -----");

        Semaphore mutex = new Semaphore(1); // only 1 permit = mutual exclusion
        AtomicInteger balance = new AtomicInteger(1000);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);

        for (int i = 1; i <= 5; i++) {
            final int amount = i * 100;
            final String op = (i % 2 == 0) ? "DEPOSIT" : "WITHDRAW";
            pool.submit(() -> {
                try {
                    mutex.acquire(); // enter critical section
                    int before = balance.get();
                    Thread.sleep(100); // simulate processing delay
                    int after = op.equals("DEPOSIT")
                        ? balance.addAndGet(amount)
                        : balance.addAndGet(-amount);
                    System.out.printf("  %-8s $%d  |  balance: $%d → $%d%n", op, amount, before, after);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    mutex.release(); // exit critical section
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        System.out.println("  Final balance: $" + balance.get());
    }
}
