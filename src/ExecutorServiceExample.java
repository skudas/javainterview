import java.util.concurrent.*;

public class ExecutorServiceExample {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService  executorService = Executors.newFixedThreadPool(5);
        ExecutorService  executorService1 = Executors.newCachedThreadPool();
        ExecutorService executorServiceS = Executors.newScheduledThreadPool(2);
        Future<String> future = executorService1.submit(()-> "Hello");
        String result = future.get();
       // executorService.shutdown();
        System.out.println(result);


        CountDownLatch latch = new CountDownLatch(3); // wait for 3 tasks

        executorService1.submit(() -> {
            try {
                Thread.sleep(1003);
                System.out.println("1!");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            latch.countDown(); }); // task 1
        executorService1.submit(() -> {
            try {
                Thread.sleep(2003);
                System.out.println("2!");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            latch.countDown(); }); // task 2
        executorService1.submit(() -> {
            try {
                Thread.sleep(1543);
                System.out.println("3!");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            latch.countDown(); }); // task 3

        latch.await(); // main thread blocks here until all 3 countDown() calls happen
        System.out.println("All tasks done!");
    }

}
