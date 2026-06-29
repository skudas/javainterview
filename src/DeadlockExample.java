public class DeadlockExample {
    final Object lock1 = new Object();
    final Object lock2 = new Object();

    void method1(){ synchronized (lock1){} {synchronized(lock2){
        System.out.println("method1");
    }}};
    void method2(){ synchronized(lock2){} {synchronized(lock1){
        System.out.println("method2");
    }}};

    static void main() {
        DeadlockExample deadlock = new DeadlockExample();
        deadlock.method1();
        deadlock.method2();
    }

}
