package streams;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Q02_GroupByDepartment {

    record Employee(String name, String dept, double salary) {}

    /*
     * Q1: Group employees by department → Map<String, List<Employee>>
     *
     * Q2: Count employees per department → Map<String, Long>
     *
     * Q3: Average salary per department → Map<String, Double>
     *
     * Q4: Highest paid employee name per department → Map<String, String>
     *
     * Q5: Collect only names per department → Map<String, List<String>>
     */
    public static void main(String[] args) {
        List<Employee> employees = Arrays.asList(
            new Employee("Alice",  "Engineering", 90_000),
            new Employee("Bob",    "Engineering", 120_000),
            new Employee("Carol",  "HR",           60_000),
            new Employee("Dave",   "HR",           65_000),
            new Employee("Eve",    "Engineering",  95_000),
            new Employee("Frank",  "Finance",      80_000),
            new Employee("Grace",  "Finance",      85_000)
        );

        // TODO


        Map<String, List<Employee>> groupByDepartments =
                employees.stream().collect(Collectors.groupingBy(Employee::dept));
        System.out.println(groupByDepartments);
        Map<String, Long> groupByDeptWithCount = employees.stream().collect(Collectors.groupingBy(Employee::dept, Collectors.counting()));
        System.out.println(groupByDeptWithCount);
        Map<String, Double> groupByDeptWithAvgSalry = employees.stream().collect(Collectors.groupingBy(
                Employee::dept, Collectors.averagingDouble(Employee::salary)
        ));
        System.out.println(groupByDeptWithAvgSalry);
        Map<String, String> collect = employees.stream().collect(Collectors.groupingBy(Employee::dept,
                Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparingDouble(Employee::salary)), opt-> opt.map(Employee::name).orElse("NA"))));
        System.out.println(collect);

        Map<String, List<String>> groupByDeptWithEmployeeNames =
                employees.stream().collect(
                        Collectors.groupingBy(
                                Employee::dept,
                                Collectors.mapping(
                                        Employee::name,
                                        Collectors.toList()
                                )
                        ));
        System.out.println(groupByDeptWithEmployeeNames);

    }
}
