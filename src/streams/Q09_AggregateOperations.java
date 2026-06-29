package streams;

import java.util.Arrays;
import java.util.List;

public class Q09_AggregateOperations {

    record Employee(String name, double salary, int age) {}

    /*
     * Q1: Find sum, min, max, average, and count of the numbers list.
     *     Expected: sum=39, min=1, max=9, avg=3.9, count=10
     *
     * Q2: Get all stats in one pass using summaryStatistics().
     *
     * Q3: Find the total payroll (sum of all salaries).
     *     Expected: $440,000
     *
     * Q4: Find the average age of all employees.
     *     Expected: 32.8
     *
     * Q5: Find the employee with the highest salary.
     *     Expected: Bob ($120,000)
     *
     * Q6: Calculate the sum of numbers using reduce() instead of sum().
     *     Expected: 39
     *
     * Q7: Find the product of all distinct numbers using reduce().
     *     Expected: 6480  (distinct: 3,1,4,5,9,2,6)
     *
     * Q8: Group employees into salary brackets: "< 80k", "80k-100k", "> 100k"
     *     and count how many fall in each bracket.
     */
    public static void main(String[] args) {
        List<Integer> numbers = Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6, 5, 3);

        List<Employee> employees = Arrays.asList(
            new Employee("Alice",  90_000, 30),
            new Employee("Bob",   120_000, 45),
            new Employee("Carol",  75_000, 27),
            new Employee("Dave",   95_000, 38),
            new Employee("Eve",    60_000, 24)
        );

        // TODO
    }
}
