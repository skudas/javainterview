package streams;

import java.util.Arrays;
import java.util.List;

public class Q07_PartitioningBy {

    record Employee(String name, String dept, double salary) {}
    record Student(String name, int marks) {}

    /*
     * Q1: Partition numbers into even and odd.
     *     Expected — even: [2,4,6,8,10]  odd: [1,3,5,7,9]
     *
     * Q2: Partition students into pass (marks >= 50) and fail.
     *     Expected — pass: [Alice, Carol, Eve]  fail: [Bob, Dave]
     *
     * Q3: Count students in each partition using a downstream collector.
     *     Expected: pass=3, fail=2
     *
     * Q4: Partition employees by salary > 80k, collecting only their names.
     *     Expected — > 80k: [Alice, Carol]  <= 80k: [Bob, Dave]
     *
     * Q5: Partition words by length > 4.
     *     Expected — > 4: [elephant, butterfly]  <= 4: [cat, dog, ant, lion]
     *
     * Think about: when would you use partitioningBy vs groupingBy?
     */
    public static void main(String[] args) {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        List<Student> students = Arrays.asList(
            new Student("Alice",  75),
            new Student("Bob",    45),
            new Student("Carol",  90),
            new Student("Dave",   38),
            new Student("Eve",    62)
        );

        List<Employee> employees = Arrays.asList(
            new Employee("Alice",  "Eng", 90_000),
            new Employee("Bob",    "HR",  55_000),
            new Employee("Carol",  "Eng", 130_000),
            new Employee("Dave",   "Fin",  70_000)
        );

        List<String> words = Arrays.asList("cat", "elephant", "dog", "butterfly", "ant", "lion");

        // TODO
    }
}
