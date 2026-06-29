import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorServiceExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. Fixed Thread Pool: Parallel Image Processing ==========");
        fixedThreadPoolExample();

        System.out.println("\n========== 2. Cached Thread Pool: Handling Burst Web Requests ==========");
        cachedThreadPoolExample();

        System.out.println("\n========== 3. Scheduled Thread Pool: Periodic Health Check ==========");
        scheduledThreadPoolExample();

        System.out.println("\n========== 4. Single Thread Executor: Sequential Order Processing ==========");
        singleThreadExecutorExample();

        System.out.println("\n========== 5. invokeAll: Parallel Database Queries ==========");
        invokeAllExample();

        System.out.println("\n========== 6. invokeAny: Fastest Payment Gateway ==========");
        invokeAnyExample();

        System.out.println("\n========== 7. Future with Timeout: External API Call ==========");
        futureWithTimeoutExample();

        System.out.println("\n========== 8. Work Stealing Pool: Recursive File Search ==========");
        workStealingPoolExample();
    }

    // --- 1. Fixed Thread Pool ---
    // Real-time use: Resize/compress N images using exactly 4 worker threads (CPU-bound tasks)
    static void fixedThreadPoolExample() throws InterruptedException {
        List<String> images = Arrays.asList("img1.jpg", "img2.jpg", "img3.jpg", "img4.jpg", "img5.jpg", "img6.jpg");
        ExecutorService pool = Executors.newFixedThreadPool(4);

        for (String image : images) {
            pool.submit(() -> {
                System.out.println("Processing " + image + " on " + Thread.currentThread().getName());
                simulateWork(300);
                System.out.println("Done: " + image);
            });
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    // --- 2. Cached Thread Pool ---
    // Real-time use: Handle unpredictable bursts of short-lived HTTP requests
    // Threads are reused if idle; new ones created on demand; idle threads removed after 60s
    static void cachedThreadPoolExample() throws InterruptedException {
        ExecutorService pool = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 1; i <= 10; i++) {
            final int requestId = i;
            pool.submit(() -> {
                System.out.println("Handling request-" + requestId + " on " + Thread.currentThread().getName());
                simulateWork(200);
                latch.countDown();
            });
        }

        latch.await();
        pool.shutdown();
    }

    // --- 3. Scheduled Thread Pool ---
    // Real-time use: Run a health-check ping every 2 seconds, and a report after 1 second delay
    static void scheduledThreadPoolExample() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        AtomicInteger pingCount = new AtomicInteger(0);

        // Runs once after a 1-second delay
        scheduler.schedule(() ->
            System.out.println("One-time report generated at startup"), 1, TimeUnit.SECONDS);

        // Runs every 2 seconds after an initial 0-second delay
        ScheduledFuture<?> healthCheck = scheduler.scheduleAtFixedRate(() -> {
            int count = pingCount.incrementAndGet();
            System.out.println("Health check ping #" + count + " — server OK");
        }, 0, 2, TimeUnit.SECONDS);

        TimeUnit.SECONDS.sleep(7);
        healthCheck.cancel(false);
        scheduler.shutdown();
    }

    // --- 4. Single Thread Executor ---
    // Real-time use: Process bank transactions sequentially to maintain order and avoid race conditions
    static void singleThreadExecutorExample() throws InterruptedException {
        ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
        String[] orders = {"Order#101", "Order#102", "Order#103", "Order#104"};

        for (String order : orders) {
            singleExecutor.submit(() -> {
                System.out.println("Processing " + order + " on " + Thread.currentThread().getName());
                simulateWork(200);
                System.out.println("Completed " + order);
            });
        }

        singleExecutor.shutdown();
        singleExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }

    // --- 5. invokeAll ---
    // Real-time use: Query 3 databases in parallel and wait for ALL results before rendering dashboard
    static void invokeAllExample() throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(3);

        List<Callable<String>> dbQueries = Arrays.asList(
            () -> { simulateWork(400); return "Users: 1500 (from UserDB)"; },
            () -> { simulateWork(600); return "Revenue: $45,000 (from SalesDB)"; },
            () -> { simulateWork(300); return "Errors: 12 (from LogDB)"; }
        );

        long start = System.currentTimeMillis();
        List<Future<String>> results = pool.invokeAll(dbQueries);

        System.out.println("Dashboard Data (fetched in parallel):");
        for (Future<String> result : results) {
            System.out.println("  -> " + result.get());
        }
        System.out.println("Total time: " + (System.currentTimeMillis() - start) + "ms");
        pool.shutdown();
    }

    // --- 6. invokeAny ---
    // Real-time use: Try 3 payment gateways simultaneously; use whichever responds first
    static void invokeAnyExample() throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        Random random = new Random();

        List<Callable<String>> gateways = Arrays.asList(
            () -> { simulateWork(500 + random.nextInt(500)); return "Stripe processed payment"; },
            () -> { simulateWork(500 + random.nextInt(500)); return "PayPal processed payment"; },
            () -> { simulateWork(500 + random.nextInt(500)); return "Razorpay processed payment"; }
        );

        String winner = pool.invokeAny(gateways);
        System.out.println("Payment result: " + winner);
        pool.shutdown();
    }

    // --- 7. Future with Timeout ---
    // Real-time use: Call an external shipping API; cancel if it takes too long
    static void futureWithTimeoutExample() {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        Future<String> shippingRate = pool.submit(() -> {
            simulateWork(3000); // Simulates a slow external API
            return "Shipping rate: $5.99";
        });

        try {
            String result = shippingRate.get(1, TimeUnit.SECONDS);
            System.out.println(result);
        } catch (TimeoutException e) {
            System.out.println("Shipping API timed out — using default rate: $9.99");
            shippingRate.cancel(true);
        } catch (Exception e) {
            System.out.println("API error: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    // --- 8. Work Stealing Pool ---
    // Real-time use: Recursively scan large directory trees; threads steal tasks from busy threads
    // Backed by ForkJoinPool — best for divide-and-conquer / uneven workloads
    static void workStealingPoolExample() throws InterruptedException {
        ExecutorService pool = Executors.newWorkStealingPool(); // uses available CPU cores

        List<String> directories = Arrays.asList(
            "/logs", "/images", "/videos", "/docs", "/cache", "/tmp", "/backup", "/archive"
        );

        CountDownLatch latch = new CountDownLatch(directories.size());

        for (String dir : directories) {
            pool.submit(() -> {
                System.out.println("Scanning " + dir + " on " + Thread.currentThread().getName());
                simulateWork(new Random().nextInt(400) + 100); // uneven workloads
                System.out.println("Finished scanning " + dir);
                latch.countDown();
            });
        }

        latch.await();
        pool.shutdown();
    }

    static void simulateWork(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
