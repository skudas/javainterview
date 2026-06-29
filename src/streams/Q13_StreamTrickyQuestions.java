package streams;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Q13_StreamTrickyQuestions {

    /*
     * These are concept / predict-the-output questions — no data transformation needed.
     *
     * Q1 — Lazy evaluation:
     *   How many times does the map() lambda execute for the pipeline below?
     *   numbers.stream().map(n -> { print(n); return n*2; }).filter(n -> n > 4).count()
     *   numbers = [1, 2, 3, 4, 5]
     *   Answer: ___   Reason: ___
     *
     * Q2 — Short-circuit:
     *   How many elements does map() process before findFirst() returns?
     *   numbers.stream().map(n -> { print(n); return n; }).filter(n -> n > 3).findFirst()
     *   numbers = [1, 2, 3, 4, 5]
     *   Answer: ___   Reason: ___
     *
     * Q3 — peek():
     *   What is the output order of the two peek() calls below?
     *   Stream.of("Alice","Bob","Carol")
     *         .peek(s -> print("before: " + s))
     *         .filter(s -> s.length() > 3)
     *         .peek(s -> print("after: " + s))
     *         .toList()
     *
     * Q4 — Stream reuse:
     *   What happens when you call two terminal operations on the same Stream instance?
     *   Stream<String> s = Stream.of("a","b","c");
     *   s.forEach(...);
     *   s.count();   // ← ?
     *
     * Q5 — findFirst vs findAny:
     *   What is the difference between findFirst() and findAny() on a parallel stream?
     *   When would you prefer findAny()?
     *
     * Q6 — Parallel stream mutable state (BUG):
     *   Why is the code below incorrect? How do you fix it?
     *   List<Integer> result = new ArrayList<>();
     *   numbers.parallelStream().forEach(result::add);  // BUG — why?
     *
     * Q7 — Optional chaining:
     *   Write a chain on Optional.of("  alice  ") that:
     *   trims → uppercases → filters length > 3 → returns "DEFAULT" if empty.
     *   Expected: "ALICE"
     *
     * Q8 — map vs flatMap on Optional:
     *   What is the difference between opt.map(f) and opt.flatMap(f)?
     *   When does flatMap avoid nested Optional<Optional<T>>?
     *
     * Q9 — Match operations:
     *   For numbers = [2, 4, 6, 8, 10], predict true/false for:
     *   anyMatch(n -> n % 2 != 0)  →  ___
     *   allMatch(n -> n % 2 == 0)  →  ___
     *   noneMatch(n -> n < 0)      →  ___
     *   Do these short-circuit? When?
     */
    public static void main(String[] args) {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);

        // Write your answers / experiments here:
        // TODO
    }
}
