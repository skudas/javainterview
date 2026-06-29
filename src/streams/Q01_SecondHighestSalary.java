package streams;

import java.util.*;
import java.util.stream.Collectors;

public class Q01_SecondHighestSalary {

    record Employee(String name, double salary) {}

    /*
     * Q1: Find the second highest salary from the list.
     *
     * Q2: Find the employee(s) who earn that second highest salary.
     *
     * Note: Bob and Dave both earn 120k — handle duplicate highest salaries correctly.
     * Expected second highest: 95000.0 (Eve)
     */
    public static void main(String[] args) {
        List<Employee> employees = Arrays.asList(
            new Employee("Alice",  90_000),
            new Employee("Bob",   120_000),
            new Employee("Carol",  5_000),
            new Employee("Dave",  120_000),
            new Employee("Eve",    95_000)
        );
        // ── your attempt (BUG: sorted() on DoubleStream is ascending → skip(1) skips the LOWEST) ──
        OptionalDouble wrong = employees.stream()
            .mapToDouble(Employee::salary)
            .distinct()
            .sorted()       // [5k, 90k, 95k, 120k] — ascending
            .skip(1)        // skips 5k, NOT 120k
            .findFirst();
        System.out.println("Wrong (skips lowest): " + wrong); // 90000 ← incorrect

        // ── FIX: box → sort descending → skip highest → findFirst ──
        // distinct() handles ANY number of people sharing the max salary (2, 3, 100 — doesn't matter)
        OptionalDouble secondHighest = employees.stream()
            .mapToDouble(Employee::salary)
            .distinct()                          // [120k, 95k, 90k, 5k] — removes duplicates
            .boxed()                             // DoubleStream → Stream<Double> (needed for reverseOrder)
            .sorted(Comparator.reverseOrder())   // [120k, 95k, 90k, 5k] — descending
            .skip(1)                             // skip 120k
            .mapToDouble(Double::doubleValue)
            .findFirst();
        System.out.println("Second highest salary: " + secondHighest.orElseThrow()); // 95000

        // ── find the employees who earn that second highest salary ──
        secondHighest.ifPresent(salary ->
            employees.stream()
                .filter(e -> e.salary() == salary)
                .forEach(e -> System.out.println("Employee: " + e.name()))
        );
    }
}
