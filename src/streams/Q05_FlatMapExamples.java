package streams;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Q05_FlatMapExamples {

    record Employee(String name, List<String> skills) {}

    /*
     * Q1: Flatten the nested list into a single List<Integer>.
     *     Expected: [1, 2, 3, 4, 5, 6, 7, 8, 9]
     *
     * Q2: Extract all individual words from the sentences list.
     *     Expected: [Java, is, powerful, Streams, are, lazy, FlatMap, is, useful]
     *
     * Q3: Get distinct words across all sentences, sorted alphabetically.
     *
     * Q4: Collect all unique skills across all employees, sorted.
     *     Expected: [Docker, Java, Kafka, Python, SQL, Spark, Spring]
     *
     * Q5: Find names of employees who know Java.
     *     Expected: [Alice, Bob]
     */
    public static void main(String[] args) {
        List<List<Integer>> nested = Arrays.asList(
            Arrays.asList(1, 2, 3),
            Arrays.asList(4, 5),
            Arrays.asList(6, 7, 8, 9)
        );
        List<Integer> flatList = nested.stream().flatMap(Collection::stream).collect(Collectors.toList());
        List<String> sentences = Arrays.asList(
            "Java is powerful",
            "Streams are lazy",
            "FlatMap is useful"
        );
        List<String> indList = sentences.stream().flatMap(e->Arrays.stream(e.split(" "))).collect(Collectors.toList());
        String sentence = sentences.stream().collect(Collectors.joining(" "));
        System.out.println(flatList);
        List<Employee> employees = Arrays.asList(
            new Employee("Alice", Arrays.asList("Java", "Spring", "SQL")),
            new Employee("Bob",   Arrays.asList("Java", "Kafka", "Docker")),
            new Employee("Carol", Arrays.asList("Python", "SQL", "Spark"))
        );
        System.out.println(employees.stream().filter(e->e.skills.contains("Java")).map(e->e.name).collect(Collectors.toList()));

        System.out.println(employees.stream().flatMap(e->e.skills.stream()).distinct().sorted().collect(Collectors.toSet()));
        // TODO
    }
}
