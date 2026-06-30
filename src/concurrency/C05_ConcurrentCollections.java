package concurrency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * C05 — ConcurrentHashMap, CopyOnWriteArrayList, ConcurrentLinkedQueue
 *
 * Never use these in multithreaded code — they are NOT thread-safe:
 *   HashMap, ArrayList, LinkedList, HashSet, TreeMap
 *
 * Collections.synchronizedMap/List wraps every method in synchronized(this)
 * — coarse-grained, all threads contend on one lock. Fine for low contention.
 *
 * The concurrent collections below are purpose-built with fine-grained locking
 * or lock-free algorithms for high throughput.
 */
public class C05_ConcurrentCollections {

    public static void main(String[] args) throws InterruptedException {
        scenario1_sessionStore_concurrentHashMap();
        scenario2_listenerRegistry_copyOnWrite();
        scenario3_taskQueue_concurrentLinkedQueue();
        scenario4_frequencyCounter_computeIfAbsent();
    }

    // ─────────────────────────────────────────────────────────────────
    // SCENARIO 1 — HTTP Session Store (ConcurrentHashMap)
    //
    // Problem: A web server handles 1000s of requests per second.
    //          Each request reads or writes to a shared in-memory session store.
    //          HashMap would corrupt data; synchronizedMap would bottleneck.
    //
    // ConcurrentHashMap internals:
    //   Java 8+: lock-free reads (no lock on get), CAS on writes
    //   Writes lock only the affected "bin" (hash bucket), not the whole map
    //   → Readers never block each other; writers rarely block each other
    //
    // Key atomic operations (use these instead of check-then-act):
    //   putIfAbsent(k, v)          → insert only if key absent (atomic)
    //   computeIfAbsent(k, fn)     → compute and insert if absent (atomic)
    //   compute(k, fn)             → update existing value atomically
    //   merge(k, v, fn)            → merge new value with existing (atomic)
    //   remove(k, v)               → remove only if current value matches
    // ─────────────────────────────────────────────────────────────────
    static void scenario1_sessionStore_concurrentHashMap() throws InterruptedException {
        System.out.println("===== Scenario 1: HTTP Session Store (ConcurrentHashMap) =====");

        ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();

        // Simulate 50 concurrent requests creating/reading sessions
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(50);

        for (int i = 0; i < 50; i++) {
            final int reqId = i;
            pool.submit(() -> {
                try {
                    String sessionId = "sess-" + (reqId % 10); // 10 sessions, 50 requests

                    // WRONG (not atomic — check-then-act race):
                    // if (!sessions.containsKey(sessionId)) sessions.put(sessionId, "user-" + reqId);

                    // RIGHT: putIfAbsent is a single atomic operation
                    String existing = sessions.putIfAbsent(sessionId, "user-" + reqId);
                    if (existing == null) {
                        System.out.printf("  [Req %02d] Created session %s%n", reqId, sessionId);
                    }

                    Thread.sleep(10);
                    String user = sessions.get(sessionId); // lock-free read
                    System.out.printf("  [Req %02d] Read session %s → %s%n", reqId, sessionId, user);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        System.out.println("  Sessions created: " + sessions.size() + " (expected 10)");
    }

    // ─────────────────────────────────────────────────────────────────
    // SCENARIO 2 — Event Listener Registry (CopyOnWriteArrayList)
    //
    // Problem: A notification service manages a list of WebSocket listeners.
    //          Listeners are added/removed rarely, but events are broadcast
    //          to ALL listeners on every message (very frequent reads).
    //
    // CopyOnWriteArrayList internals:
    //   Read  → zero locking, reads the stable underlying array snapshot
    //   Write → acquires a lock, copies the ENTIRE array, then swaps the reference
    //
    // Trade-off:
    //   Reads: O(1), no locking — perfect for read-heavy scenarios
    //   Writes: O(n) — expensive, so it's only suitable when writes are rare
    //
    // Iterator is SNAPSHOT-based — will never throw ConcurrentModificationException
    // (but won't see mid-iteration additions either)
    // ─────────────────────────────────────────────────────────────────
    static void scenario2_listenerRegistry_copyOnWrite() throws InterruptedException {
        System.out.println("\n===== Scenario 2: WebSocket Listener Registry (CopyOnWriteArrayList) =====");

        List<String> listeners = new CopyOnWriteArrayList<>();

        // Pre-register some listeners
        listeners.add("user-alice");
        listeners.add("user-bob");
        listeners.add("user-carol");

        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(10);

        // Broadcaster threads — read the list and send to each listener (frequent)
        for (int msg = 1; msg <= 8; msg++) {
            final int msgId = msg;
            pool.submit(() -> {
                try {
                    // Iterating CopyOnWriteArrayList is always safe from CME
                    for (String listener : listeners) {
                        System.out.printf("  [Broadcast] msg#%d → %s%n", msgId, listener);
                    }
                } finally { latch.countDown(); }
            });
        }

        // Admin threads — occasionally add/remove listeners (rare)
        pool.submit(() -> {
            listeners.add("user-dave");
            System.out.println("  [Admin] Added user-dave");
            latch.countDown();
        });
        pool.submit(() -> {
            listeners.remove("user-bob");
            System.out.println("  [Admin] Removed user-bob");
            latch.countDown();
        });

        latch.await();
        pool.shutdown();
        System.out.println("  Final listeners: " + listeners);
    }

    // ─────────────────────────────────────────────────────────────────
    // SCENARIO 3 — Background Task Queue (ConcurrentLinkedQueue)
    //
    // Problem: Multiple microservices push background jobs (email, thumbnail
    //          generation) into a shared queue. A pool of worker threads drains it.
    //
    // ConcurrentLinkedQueue:
    //   Lock-free, based on CAS. O(1) offer/poll. Unbounded.
    //   No blocking — poll() returns null if empty (unlike BlockingQueue.take())
    //   Use when you don't want consumers to block when idle.
    //
    // vs BlockingQueue: use ConcurrentLinkedQueue when you're already
    //   managing waiting externally (e.g. scheduled polling).
    // ─────────────────────────────────────────────────────────────────
    static void scenario3_taskQueue_concurrentLinkedQueue() throws InterruptedException {
        System.out.println("\n===== Scenario 3: Background Job Queue (ConcurrentLinkedQueue) =====");

        record Job(int id, String type) {}

        ConcurrentLinkedQueue<Job> jobQueue = new ConcurrentLinkedQueue<>();
        AtomicBoolean accepting = new AtomicBoolean(true);
        AtomicInteger processed = new AtomicInteger(0);

        // Producers: 3 microservices pushing jobs
        ExecutorService producers = Executors.newFixedThreadPool(3);
        for (int svc = 1; svc <= 3; svc++) {
            final int serviceId = svc;
            producers.submit(() -> {
                for (int i = 1; i <= 4; i++) {
                    Job job = new Job(serviceId * 10 + i, serviceId == 1 ? "EMAIL" : serviceId == 2 ? "THUMBNAIL" : "REPORT");
                    jobQueue.offer(job); // never blocks, never throws
                    System.out.printf("  [Service-%d] Queued %s job #%d%n", serviceId, job.type(), job.id());
                    try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            });
        }

        // Consumer: worker thread polling the queue
        Thread worker = new Thread(() -> {
            while (accepting.get() || !jobQueue.isEmpty()) {
                Job job = jobQueue.poll(); // non-blocking: returns null if empty
                if (job != null) {
                    System.out.printf("  [Worker] Processing %s job #%d%n", job.type(), job.id());
                    processed.incrementAndGet();
                    try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                // if null, just loop again (spin) — or add a short sleep to reduce CPU
            }
            System.out.println("  [Worker] Done. Processed " + processed.get() + " jobs.");
        });
        worker.start();

        producers.shutdown();
        producers.awaitTermination(5, TimeUnit.SECONDS);
        accepting.set(false);
        worker.join();
    }

    // ─────────────────────────────────────────────────────────────────
    // SCENARIO 4 — Real-Time Request Frequency Counter
    //
    // Problem: An API gateway tracks how many requests each user makes per minute
    //          for rate limiting. Thousands of threads increment counts concurrently.
    //
    // Pattern: ConcurrentHashMap + computeIfAbsent + AtomicInteger
    // This is the idiomatic way to do "concurrent frequency counting" in Java.
    // ─────────────────────────────────────────────────────────────────
    static void scenario4_frequencyCounter_computeIfAbsent() throws InterruptedException {
        System.out.println("\n===== Scenario 4: Request Frequency Counter (compute idioms) =====");

        ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);

        String[] users = {"alice", "bob", "carol", "dave", "eve"};
        Random rng = new Random();

        for (int i = 0; i < 100; i++) {
            final String userId = users[rng.nextInt(users.length)];
            pool.submit(() -> {
                try {
                    // computeIfAbsent: atomically creates the AtomicInteger if missing,
                    // then we atomically increment it — no race condition possible
                    requestCounts
                        .computeIfAbsent(userId, k -> new AtomicInteger(0))
                        .incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        System.out.println("  Request counts per user:");
        requestCounts.forEach((user, count) ->
            System.out.printf("    %-8s: %d requests%n", user, count.get()));

        // Bonus: check rate limit
        String targetUser = "alice";
        int count = requestCounts.getOrDefault(targetUser, new AtomicInteger(0)).get();
        System.out.printf("  Rate limit check for %s: %d requests (limit=30) → %s%n",
            targetUser, count, count > 30 ? "BLOCKED" : "OK");

        // merge() — alternative to computeIfAbsent + AtomicInteger for simple counting
        ConcurrentHashMap<String, Integer> simpleCounts = new ConcurrentHashMap<>();
        simpleCounts.merge("alice", 1, Integer::sum); // atomic: alice=0+1=1
        simpleCounts.merge("alice", 1, Integer::sum); // atomic: alice=1+1=2
        System.out.println("  merge() count for alice: " + simpleCounts.get("alice")); // 2
    }
}
