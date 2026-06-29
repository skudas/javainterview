package streams;

public class Q11_InfiniteStreams {

    /*
     * All questions use Stream.iterate() or Stream.generate().
     * Always bound with limit() or takeWhile() — never consume an infinite stream without a terminal bound.
     *
     * Q1: Print the first 10 natural numbers using Stream.iterate().
     *     Expected: 1 2 3 4 5 6 7 8 9 10
     *
     * Q2: Print the first 8 even numbers.
     *     Expected: 0 2 4 6 8 10 12 14
     *
     * Q3: Print the first 12 Fibonacci numbers.
     *     Hint: use iterate with a long[]{prev, curr} pair.
     *     Expected: 0 1 1 2 3 5 8 13 21 34 55 89
     *
     * Q4: Print the first 10 powers of 2.
     *     Expected: 1 2 4 8 16 32 64 128 256 512
     *
     * Q5: Print all Fibonacci numbers below 100 using takeWhile().
     *     Expected: 0 1 1 2 3 5 8 13 21 34 55 89
     *
     * Q6: Use the Java 9 three-argument iterate(seed, predicate, next)
     *     to print multiples of 3 up to 30.
     *     Expected: 3 6 9 12 15 18 21 24 27 30
     *
     * Q7: Generate 5 random numbers between 0–99 using Stream.generate().
     *
     * Q8: Print the first 10 prime numbers using an infinite stream + filter.
     *     Expected: 2 3 5 7 11 13 17 19 23 29
     *     Hint: write a helper isPrime(int n) method.
     */
    public static void main(String[] args) {
        // TODO
    }
}
