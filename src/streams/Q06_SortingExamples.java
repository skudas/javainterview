package streams;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Map.Entry.comparingByKey;
import static java.util.Map.Entry.comparingByValue;

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
     *    List<Employee> e3 = employees.stream().sorted(Comparator.comparing(Employee::salary ).reversed().thenComparing(Employee::name)).toList();

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

        List<Employee> e1 = employees.stream().sorted(Comparator.comparing(Employee::salary)).toList();

        System.out.println("e1" + e1);

        List<Employee> e2 = employees.stream().sorted(Comparator.comparing(Employee::salary).reversed()).toList();

        System.out.println(e2);


        List<Employee> e3 = employees.stream().sorted(Comparator.comparing(Employee::salary ).reversed().thenComparing(Employee::name)).toList();

        System.out.println("e 3  " + e3);
        List<String> words = Arrays.asList("banana", "kiwi", "apple", "fig", "mango", "pear");

        System.out.println(words.stream().sorted(Comparator.comparing(String::length)).collect(Collectors.toList()));

        Map<String, Integer> scores = Map.of("Alice", 85, "Bob", 92, "Carol", 78, "Dave", 92);

        Map<String, Integer> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.<String, Integer>comparingByKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e15, e25) -> e15,
                        LinkedHashMap::new
                ));

        System.out.println(sorted);
       // System.out.println(scores.entrySet().stream().
         //       sorted(Map.Entry.comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e12, e23) -> e12, LinkedHashMap::new));
         // TODO

        System.out.println(employees.stream().sorted(Comparator.comparing(Employee::dept).thenComparing(Employee::salary, Comparator.reverseOrder())).collect(Collectors.toList()));
    }
}
