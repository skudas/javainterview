import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * CompletableFuture Real-Time Usage Examples
 *
 * Key method categories:
 *  Creation   : supplyAsync, runAsync
 *  Transform  : thenApply, thenApplyAsync
 *  Chain      : thenCompose
 *  Combine    : thenCombine, thenAcceptBoth, allOf, anyOf
 *  Consume    : thenAccept, thenRun
 *  Errors     : exceptionally, handle, whenComplete
 *  Timeout    : orTimeout, completeOnTimeout  (Java 9+)
 */
public class CompletableFutureExamples {

    static final ExecutorService pool = Executors.newFixedThreadPool(8);
    static final Random random = new Random();

    public static void main(String[] args) throws Exception {
        try {
            example1_supplyAsync_thenApply();
            example2_thenCompose_chaining();
            example3_thenCombine_merge();
            example4_allOf_parallel_notifications();
            example5_anyOf_fastest_cache();
            example6_exceptionally_fallback();
            example7_handle_success_and_failure();
            example8_runAsync_fire_and_forget();
            example9_orTimeout();
            example10_completeOnTimeout_default();
            example11_full_pipeline_order_placement();
        } finally {
            pool.shutdown();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. supplyAsync + thenApply
    // Real-time: Fetch raw product data from DB, then transform to a display DTO
    // supplyAsync  → runs a Supplier in background thread
    // thenApply    → transforms the result (like map in streams), stays async
    // ─────────────────────────────────────────────────────────────────────────
    static void example1_supplyAsync_thenApply() throws Exception {
        System.out.println("===== 1. supplyAsync + thenApply: Fetch & Transform Product =====");

        CompletableFuture<String> future = CompletableFuture
            .supplyAsync(() -> {
                simulateWork(300);
                return "{ id:101, name:'Laptop', priceUSD:1200 }"; // raw DB row
            }, pool)
            .thenApply(raw -> "DisplayDTO[" + raw.replace("priceUSD", "price") + "]"); // transform

        System.out.println("Result: " + future.get());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. thenCompose  (flatMap for CompletableFuture)
    // Real-time: Get userId from auth service → use it to fetch that user's orders
    // thenCompose  → chains two async operations sequentially (avoids nested futures)
    // ─────────────────────────────────────────────────────────────────────────
    static void example2_thenCompose_chaining() throws Exception {
        System.out.println("\n===== 2. thenCompose: Auth → Fetch Orders =====");

        CompletableFuture<String> result = fetchUserId("auth-token-xyz")
            .thenCompose(userId -> fetchOrders(userId)); // second async call depends on first

        System.out.println("Result: " + result.get());
    }

    static CompletableFuture<String> fetchUserId(String token) {
        return CompletableFuture.supplyAsync(() -> {
            simulateWork(200);
            return "user-42"; // resolved from auth token
        }, pool);
    }

    static CompletableFuture<String> fetchOrders(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            simulateWork(300);
            return "Orders[Order#1, Order#2] for " + userId;
        }, pool);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. thenCombine
    // Real-time: Fetch base price and discount in parallel → compute final price
    // thenCombine  → waits for TWO independent futures, then merges their results
    // ─────────────────────────────────────────────────────────────────────────
    static void example3_thenCombine_merge() throws Exception {
        System.out.println("\n===== 3. thenCombine: Price + Discount → Final Price =====");

        CompletableFuture<Double> priceFuture = CompletableFuture.supplyAsync(() -> {
            simulateWork(400);
            return 1200.0; // base price from pricing service
        }, pool);

        CompletableFuture<Double> discountFuture = CompletableFuture.supplyAsync(() -> {
            simulateWork(300);
            return 0.15; // 15% discount from promotions service
        }, pool);

        double finalPrice = priceFuture
            .thenCombine(discountFuture, (price, discount) -> price - (price * discount))
            .get();

        System.out.printf("Final price after discount: $%.2f%n", finalPrice);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. allOf
    // Real-time: Send order confirmation via Email, SMS, and Push — all in parallel;
    //            wait until every notification is delivered before returning response
    // allOf        → waits for ALL futures to complete (returns CompletableFuture<Void>)
    // ─────────────────────────────────────────────────────────────────────────
    static void example4_allOf_parallel_notifications() throws Exception {
        System.out.println("\n===== 4. allOf: Parallel Notifications (Email + SMS + Push) =====");

        CompletableFuture<String> email = CompletableFuture.supplyAsync(() -> {
            simulateWork(500); return "Email sent to user@example.com";
        }, pool);

        CompletableFuture<String> sms = CompletableFuture.supplyAsync(() -> {
            simulateWork(300); return "SMS sent to +91-9876543210";
        }, pool);

        CompletableFuture<String> push = CompletableFuture.supplyAsync(() -> {
            simulateWork(200); return "Push notification delivered";
        }, pool);

        long start = System.currentTimeMillis();
        CompletableFuture.allOf(email, sms, push).get(); // blocks until all done

        // collect individual results after allOf
        List<String> results = List.of(email.get(), sms.get(), push.get());
        results.forEach(r -> System.out.println("  -> " + r));
        System.out.println("All notifications sent in " + (System.currentTimeMillis() - start) + "ms");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. anyOf
    // Real-time: Check local cache, Redis, and DB simultaneously — use whichever
    //            responds first (fastest read wins)
    // anyOf        → returns as soon as ANY future completes
    // ─────────────────────────────────────────────────────────────────────────
    static void example5_anyOf_fastest_cache() throws Exception {
        System.out.println("\n===== 5. anyOf: Fastest Cache Wins (Local / Redis / DB) =====");

        CompletableFuture<Object> localCache = CompletableFuture.supplyAsync(() -> {
            simulateWork(100 + random.nextInt(300)); return "HIT: local-cache";
        }, pool);

        CompletableFuture<Object> redis = CompletableFuture.supplyAsync(() -> {
            simulateWork(100 + random.nextInt(300)); return "HIT: Redis";
        }, pool);

        CompletableFuture<Object> database = CompletableFuture.supplyAsync(() -> {
            simulateWork(100 + random.nextInt(300)); return "HIT: PostgreSQL";
        }, pool);

        Object result = CompletableFuture.anyOf(localCache, redis, database).get();
        System.out.println("Fastest response: " + result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. exceptionally
    // Real-time: Call weather API; if it throws, return a cached/default response
    // exceptionally → handles exceptions only, passes through successful results
    // ─────────────────────────────────────────────────────────────────────────
    static void example6_exceptionally_fallback() throws Exception {
        System.out.println("\n===== 6. exceptionally: Weather API with Fallback =====");

        CompletableFuture<String> weather = CompletableFuture
            .supplyAsync(() -> {
                simulateWork(200);
                if (random.nextBoolean()) throw new RuntimeException("Weather API is down");
                return "Live: Sunny, 32°C";
            }, pool)
            .exceptionally(ex -> {
                System.out.println("  [fallback triggered] " + ex.getMessage());
                return "Cached: Partly cloudy, 29°C";
            });

        System.out.println("Weather data: " + weather.get());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. handle
    // Real-time: Process payment; log success or failure in the same callback
    // handle       → called for BOTH success and failure (unlike exceptionally)
    //               receives (result, exception) — one will always be null
    // ─────────────────────────────────────────────────────────────────────────
    static void example7_handle_success_and_failure() throws Exception {
        System.out.println("\n===== 7. handle: Payment Processing (success + failure in one place) =====");

        for (int attempt = 1; attempt <= 2; attempt++) {
            final boolean shouldFail = attempt == 1;

            String outcome = CompletableFuture
                .supplyAsync(() -> {
                    simulateWork(200);
                    if (shouldFail) throw new RuntimeException("Card declined");
                    return "txn-id-99887";
                }, pool)
                .handle((txnId, ex) -> {
                    if (ex != null) {
                        return "FAILED: " + ex.getMessage(); // log and return safe value
                    }
                    return "SUCCESS: transaction " + txnId;
                })
                .get();

            System.out.println("  Attempt " + attempt + " -> " + outcome);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. runAsync  (fire-and-forget)
    // Real-time: Write an audit log entry after user login — don't block the login response
    // runAsync     → runs a Runnable asynchronously, returns CompletableFuture<Void>
    // ─────────────────────────────────────────────────────────────────────────
    static void example8_runAsync_fire_and_forget() throws Exception {
        System.out.println("\n===== 8. runAsync: Fire-and-Forget Audit Logging =====");

        System.out.println("User logged in — sending response immediately");

        CompletableFuture.runAsync(() -> {
            simulateWork(500); // slow audit log write — doesn't block the caller
            System.out.println("  [async] Audit log written: user login at " + System.currentTimeMillis());
        }, pool);

        System.out.println("Login response returned to client (audit log writing in background)");
        simulateWork(600); // keep main thread alive long enough to see the log
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. orTimeout  (Java 9+)
    // Real-time: Call inventory service; throw TimeoutException if it takes > 1s
    // orTimeout    → completes exceptionally with TimeoutException after deadline
    // ─────────────────────────────────────────────────────────────────────────
    static void example9_orTimeout() throws Exception {
        System.out.println("\n===== 9. orTimeout: Inventory Service SLA Enforcement =====");

        CompletableFuture<String> inventory = CompletableFuture
            .supplyAsync(() -> {
                simulateWork(2000); // slow service
                return "Stock: 50 units";
            }, pool)
            .orTimeout(1, TimeUnit.SECONDS)
            .exceptionally(ex -> "Inventory check timed out — assuming in stock");

        System.out.println("Inventory: " + inventory.get());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. completeOnTimeout  (Java 9+)
    // Real-time: Load personalized recommendations; show defaults if ML service is slow
    // completeOnTimeout → returns a default value on timeout instead of an exception
    // ─────────────────────────────────────────────────────────────────────────
    static void example10_completeOnTimeout_default() throws Exception {
        System.out.println("\n===== 10. completeOnTimeout: ML Recommendations with Safe Default =====");

        List<String> defaultRecs = List.of("iPhone 15", "MacBook Air", "AirPods Pro");

        CompletableFuture<List<String>> recommendations = CompletableFuture
            .supplyAsync(() -> {
                simulateWork(3000); // ML model is slow
                return List.of("Sony Camera", "Nikon Lens", "Tripod");
            }, pool)
            .completeOnTimeout(defaultRecs, 500, TimeUnit.MILLISECONDS);

        System.out.println("Recommendations: " + recommendations.get());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. Full Pipeline: E-commerce Order Placement
    // Real-time: Validate user → check stock → charge payment → send confirmation
    //            Steps 1 & 2 run in parallel; step 3 needs both; step 4 is async
    // ─────────────────────────────────────────────────────────────────────────
    static void example11_full_pipeline_order_placement() throws Exception {
        System.out.println("\n===== 11. Full Pipeline: E-commerce Order Placement =====");

        long start = System.currentTimeMillis();

        // Step 1 & 2 run in parallel (independent)
        CompletableFuture<String> userValidation = CompletableFuture.supplyAsync(() -> {
            simulateWork(300);
            System.out.println("  [1] User validated");
            return "user-42";
        }, pool);

        CompletableFuture<Integer> stockCheck = CompletableFuture.supplyAsync(() -> {
            simulateWork(400);
            System.out.println("  [2] Stock verified: 10 units available");
            return 10;
        }, pool);

        // Step 3: charge payment — needs both user and stock confirmed
        CompletableFuture<String> payment = userValidation
            .thenCombine(stockCheck, (userId, stock) -> userId + "|stock:" + stock)
            .thenCompose(context -> CompletableFuture.supplyAsync(() -> {
                simulateWork(500);
                System.out.println("  [3] Payment charged for " + context);
                return "txn-55321";
            }, pool));

        // Step 4: send confirmation email — fire-and-forget after payment
        String txnId = payment.get();
        CompletableFuture.runAsync(() -> {
            simulateWork(300);
            System.out.println("  [4] Confirmation email sent for " + txnId);
        }, pool);

        System.out.printf("Order placed! TxnId: %s  (Total: %dms)%n",
            txnId, System.currentTimeMillis() - start);

        simulateWork(400); // let the async email log appear
    }

    static void simulateWork(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
