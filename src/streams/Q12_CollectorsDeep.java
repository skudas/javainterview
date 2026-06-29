package streams;

import java.util.Arrays;
import java.util.List;

public class Q12_CollectorsDeep {

    record Employee(String name, String dept, String city, double salary) {}

    /*
     * Q1: Two-level grouping — group by dept, then by city within each dept.
     *     Result type: Map<String, Map<String, List<String>>>  (dept → city → names)
     *
     * Q2: For each department, find the name of the highest paid employee.
     *     Hint: maxBy returns Optional — use collectingAndThen to unwrap it.
     *     Expected: {Engineering=Bob, Finance=Frank, HR=Dave}
     *
     * Q3: Group by department and count, but keep the keys sorted alphabetically.
     *     Hint: supply a TreeMap as the map factory.
     *     Expected: {Engineering=3, Finance=2, HR=2}
     *
     * Q4: For each department, get full salary statistics (count, sum, avg, min, max)
     *     in a single stream pass. Use summarizingDouble.
     *
     * Q5: Collect employee names into an unmodifiable list.
     *
     * Q6: Find the minimum and maximum salary in a SINGLE stream pass using teeing() (Java 12+).
     *     Expected: min=$60,000  max=$120,000
     *
     * Q7: Group employees by city, then within each city sort by salary descending and print.
     */
    public static void main(String[] args) {
        List<Employee> employees = Arrays.asList(
            new Employee("Alice",  "Engineering", "Bangalore",  90_000),
            new Employee("Bob",    "Engineering", "Mumbai",    120_000),
            new Employee("Carol",  "HR",          "Bangalore",  60_000),
            new Employee("Dave",   "HR",          "Mumbai",     65_000),
            new Employee("Eve",    "Finance",     "Bangalore",  80_000),
            new Employee("Frank",  "Finance",     "Mumbai",     85_000),
            new Employee("Grace",  "Engineering", "Bangalore",  95_000)
        );

        // TODO
    }
}
