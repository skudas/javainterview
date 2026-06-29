package streams;

import java.util.Arrays;
import java.util.List;

public class Q08_ListToMap {

    record Employee(int id, String name, String dept, double salary) {}

    /*
     * Q1: Convert to Map<Integer, Employee> keyed by id.
     *     Then look up employee with id=3.  Expected: Carol
     *
     * Q2: Convert to Map<Integer, String> — id → name.
     *
     * Q3: Convert to Map<String, Double> — name → salary.
     *
     * Q4: Convert to Map<String, Double> — dept → total salary.
     *     Departments repeat — use a merge function to sum salaries.
     *     Expected: {Engineering=185000, Finance=80000, HR=125000}
     *
     * Q5: Convert to Map<String, Double> — dept → max salary.
     *     Expected: {Engineering=95000, Finance=80000, HR=65000}
     *
     * Q6: Build a word frequency map from the words list.
     *     Expected: {apple=3, banana=2, cherry=1}
     *
     * Q7: Build a reverse map — name → id.
     *
     * Think about: what happens if you use toMap() with duplicate keys and no merge function?
     */
    public static void main(String[] args) {
        List<Employee> employees = Arrays.asList(
            new Employee(1, "Alice",  "Engineering", 90_000),
            new Employee(2, "Bob",    "HR",          60_000),
            new Employee(3, "Carol",  "Engineering", 95_000),
            new Employee(4, "Dave",   "Finance",     80_000),
            new Employee(5, "Eve",    "HR",          65_000)
        );

        List<String> words = Arrays.asList("apple", "banana", "apple", "cherry", "banana", "apple");

        // TODO
    }
}
