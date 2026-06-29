package streams;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Q06_SortingExamples {

    record Employee(String name, String dept, double salary, int age) {}

    /*
     * Q1: Sort employees by salary ascending.
     *
     * Q2: Sort employees by salary descending.
     *
     * Q3: Sort by salary descending; where salary is equal, sort by name ascending.
     *     Expected order: Bob(95k), Charlie(95k), Dave(80k), Eve(75k), Alice(60k)
     *
     * Q4: Sort the words list by length, then alphabetically for same-length words.
     *     Expected: fig, kiwi, pear, apple, mango, banana
     *
     * Q5: Sort the scores map by value descending; for equal values sort by key ascending.
     *     Expected: Bob=92, Dave=92, Alice=85, Carol=78
     *
     * Q6: Sort employees by department alphabetically, then by salary descending within each dept.
     */
    public static void main(String[] args) {
        List<Employee> employees = Arrays.asList(
            new Employee("Charlie", "Engineering", 95_000, 30),
            new Employee("Alice",   "HR",          60_000, 25),
            new Employee("Bob",     "Engineering", 95_000, 28),
            new Employee("Dave",    "Finance",     80_000, 35),
            new Employee("Eve",     "HR",          75_000, 27)
        );

        List<String> words = Arrays.asList("banana", "kiwi", "apple", "fig", "mango", "pear");

        Map<String, Integer> scores = Map.of("Alice", 85, "Bob", 92, "Carol", 78, "Dave", 92);

        // TODO
    }
}
