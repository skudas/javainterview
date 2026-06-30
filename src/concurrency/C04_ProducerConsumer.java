package concurrency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * C04 — Producer-Consumer: the most common concurrency pattern in real systems.
 *
 * Real-world examples of producer-consumer:
 *   - Web server: request handler (producer) → worker thread pool (consumer)
 *   - Kafka consumer: poll loop (producer) → processing threads (consumer)
 *   - Logging: application threads (producers) → async log writer (consumer)
 *   - Order service: checkout API (producer) → payment processor (consumer)
 *
 * Three implementations (low → high level):
 *   1. wait/notify     → manual, error-prone, good to know for interviews
 *   2. BlockingQueue   → preferred in production (handles all the edge cases)
 *   3. Disruptor-style → ring buffer pattern for ultra-high throughput
 */
public class C04_ProducerConsumer {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   REAL-WORLD PRODUCER-CONSUMER PATTERNS              ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        scenario1_orderProcessingWithWaitNotify();
        scenario2_asyncLoggingWithBlockingQueue();
        scenario3_kafkaStylePipeline();
        scenario4_rateLimitedApiCaller();
    }

    // ─────────────────────────────────────────────────────────────────
    // SCENARIO 1 — Order Processing System (wait/notify style)
    //
    // Problem: Checkout endpoint creates orders faster than the payment
    //          processor can handle. We need a bounded queue so the checkout
    //          API backs off (blocks) when the processor is overwhelmed.
    //
    // Rules:
    //   - wait() must be in a WHILE loop, never if — spurious wakeups are real
    //   - notify() only wakes ONE thread; notifyAll() wakes all
    //   - Both must be called while holding the lock (inside synchronized)
    // ─────────────────────────────────────────────────────────────────
    static void scenario1_orderProcessingWithWaitNotify() throws InterruptedException {
        System.out.println("\n===== Scenario 1: Order Processing (wait/notify) =====");

        class OrderQueue {
            private final Queue<String> queue = new LinkedList<>();
            private final int MAX_SIZE = 3;   // bounded: processor can't keep up with checkout

            // Called by checkout threads (producers)
            public synchronized void placeOrder(String orderId) throws InterruptedException {
                while (queue.size() == MAX_SIZE) {
                    System.out.println("  [Checkout] Queue full — backing off for " + orderId);
                    wait(); // release lock + suspend until a consumer calls notify()
                }
                queue.add(orderId);
                System.out.println("  [Checkout] Placed " + orderId + "  (queue size=" + queue.size() + ")");
                notifyAll(); // wake up waiting payment processors
            }

            // Called by payment processor threads (consumers)
            public synchronized String processNext() throws InterruptedException {
                while (queue.isEmpty()) {
                    wait(); // release lock + suspend until a producer calls notify()
                }
                String orderId = queue.poll();
                System.out.println("  [Payment]  Processing " + orderId + " (queue size=" + queue.size() + ")");
                notifyAll(); // wake up blocked checkout threads
                return orderId;
            }
        }

        OrderQueue orderQueue = new OrderQueue();
        AtomicBoolean done = new AtomicBoolean(false);

        // Checkout thread (producer) — fast
        Thread checkout = new Thread(() -> {
            try {
                for (int i = 1; i <= 6; i++) {
                    orderQueue.placeOrder("ORD-" + i);
                    Thread.sleep(100);
                }
                done.set(true);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Checkout");

        // Payment processor thread (consumer) — slow
        Thread payment = new Thread(() -> {
            try {
                while (!done.get() || !Thread.currentThread().isInterrupted()) {
                    orderQueue.processNext();
                    Thread.sleep(300);  // payment is slower than checkout
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "PaymentProcessor");

        checkout.start();
        payment.start();
        checkout.join();
        Thread.sleep(1500); // let processor drain the queue
        payment.interrupt();
        payment.join();
        System.out.println("  Order processing complete.");
    }

    // ─────────────────────────────────────────────────────────────────
    // SCENARIO 2 — Async Logging Service (BlockingQueue style)
    //
    // Problem: Application threads must not block while writing logs to disk.
    //          We push log entries to a queue and a single background writer
    //          flushes them asynchronously.
    //
    // This is exactly how Logback's AsyncAppender works.
    //
    // BlockingQueue handles all the wait/notify for you:
    //   put(e)   → blocks when full  (use for producers that must not lose data)
    //   take()   → blocks when empty (use for consumers waiting for work)
    //   offer(e) → returns false if full, non-blocking (use for fire-and-forget)
    //   poll()   → returns null if empty, non-blocking
    // ─────────────────────────────────────────────────────────────────
    static void scenario2_asyncLoggingWithBlockingQueue() throws InterruptedException {
        System.out.println("\n===== Scenario 2: Async Logging Service (BlockingQueue) =====");

        final String POISON_PILL = "SHUTDOWN"; // sentinel to stop the consumer
        BlockingQueue<String> logQueue = new ArrayBlockingQueue<>(100);

        // Consumer: single background writer thread
        Thread logWriter = new Thread(() -> {
            try {
                while (true) {
                    String entry = logQueue.take(); // blocks until there's something to write
                    if (POISON_PILL.equals(entry)) {
                        System.out.println("  [LogWriter] Shutdown signal received. Flushing and exiting.");
                        break;
                    }
                    // Simulate writing to disk (slow I/O)
                    Thread.sleep(20);
                    System.out.println("  [LogWriter] Wrote: " + entry);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "LogWriter");
        logWriter.setDaemon(true);
        logWriter.start();

        // Producers: application threads (HTTP handlers, service methods, etc.)
        ExecutorService appThreads = Executors.newFixedThreadPool(3);
        CountDownLatch allLogged = new CountDownLatch(9);

        for (int t = 1; t <= 3; t++) {
            final int threadId = t;
            appThreads.submit(() -> {
                try {
                    for (int i = 1; i <= 3; i++) {
                        String entry = String.format("INFO [thread-%d] Request #%d processed in %dms",
                            threadId, i, 50 + new Random().nextInt(200));
                        logQueue.put(entry);   // blocks if queue is full (backpressure)
                        allLogged.countDown();
                        Thread.sleep(50);
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }

        allLogged.await();
        logQueue.put(POISON_PILL); // graceful shutdown
        logWriter.join();
        appThreads.shutdown();
        System.out.println("  Async logging complete.");
    }

    // ─────────────────────────────────────────────────────────────────
    // SCENARIO 3 — Kafka-style Pipeline: poll → process → commit
    //
    // Problem: Consumer poll loop fetches batches of events from a broker.
    //          Each event is processed by a worker thread pool.
    //          We must not commit the offset until ALL events in a batch are done.
    //
    // Pattern: poll() → hand off to pool → CountDownLatch → commit
    // ─────────────────────────────────────────────────────────────────
    static void scenario3_kafkaStylePipeline() throws InterruptedException {
        System.out.println("\n===== Scenario 3: Kafka-style Event Pipeline =====");

        record Event(int id, String type) {}

        ExecutorService workerPool = Executors.newFixedThreadPool(4);

        // Simulate 3 poll batches (like Kafka consumer.poll())
        for (int batch = 1; batch <= 3; batch++) {
            List<Event> events = new ArrayList<>();
            for (int e = 1; e <= 4; e++) {
                events.add(new Event((batch - 1) * 4 + e, "ORDER_PLACED"));
            }

            System.out.println("  [Poll] Batch " + batch + ": fetched " + events.size() + " events");

            // All events in the batch must complete before we "commit the offset"
            CountDownLatch batchLatch = new CountDownLatch(events.size());

            for (Event event : events) {
                workerPool.submit(() -> {
                    try {
                        Thread.sleep(50 + new Random().nextInt(150)); // simulate processing
                        System.out.printf("  [Worker] Processed event %d (%s)%n", event.id(), event.type());
                    } catch (InterruptedException e2) {
                        Thread.currentThread().interrupt();
                    } finally {
                        batchLatch.countDown();
                    }
                });
            }

            batchLatch.await(); // block poll loop until entire batch is processed
            System.out.println("  [Commit] Offset committed for batch " + batch);
        }

        workerPool.shutdown();
        System.out.println("  Pipeline complete.");
    }

    // ─────────────────────────────────────────────────────────────────
    // SCENARIO 4 — Rate-Limited External API Caller
    //
    // Problem: You can make at most 5 concurrent calls to an external payment
    //          API. 20 orders arrive simultaneously. Excess callers must wait,
    //          not fail.
    //
    // Pattern: Semaphore(5) as a rate-limiter + BlockingQueue for ordering
    // ─────────────────────────────────────────────────────────────────
    static void scenario4_rateLimitedApiCaller() throws InterruptedException {
        System.out.println("\n===== Scenario 4: Rate-Limited Payment API (max 5 concurrent) =====");

        final int MAX_CONCURRENT = 5;
        final int TOTAL_ORDERS = 12;
        Semaphore apiSlots = new Semaphore(MAX_CONCURRENT, true); // fair = FIFO
        AtomicInteger active = new AtomicInteger(0);
        CountDownLatch allDone = new CountDownLatch(TOTAL_ORDERS);
        ExecutorService pool = Executors.newFixedThreadPool(TOTAL_ORDERS);

        for (int i = 1; i <= TOTAL_ORDERS; i++) {
            final int orderId = i;
            pool.submit(() -> {
                try {
                    apiSlots.acquire(); // wait for a slot (max 5 concurrent)
                    int concurrent = active.incrementAndGet();
                    System.out.printf("  [API] Order #%02d started  (concurrent: %d/%d)%n",
                        orderId, concurrent, MAX_CONCURRENT);

                    Thread.sleep(300 + new Random().nextInt(300)); // simulate API latency

                    active.decrementAndGet();
                    System.out.printf("  [API] Order #%02d complete%n", orderId);
                    apiSlots.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            });
        }

        allDone.await();
        pool.shutdown();
        System.out.println("  All orders processed. Max observed concurrent: " + MAX_CONCURRENT);
    }
}
