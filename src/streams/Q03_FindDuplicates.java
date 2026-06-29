package streams;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Q03_FindDuplicates {

    /*
     * Q1: Find all duplicate integers (elements that appear more than once).
     *     Expected: [1, 2, 3]
     *
     * Q2: Build a frequency map → Map<Integer, Long>
     *     Expected: {1=3, 2=2, 3=2, 4=1, 5=1, 6=1}
     *
     * Q3: Find elements that appear more than twice.
     *     Expected: [1]
     *
     * Q4: For the words list, find words that appear more than once with their counts.
     *     Expected: apple=3, banana=2
     */
    public static void main(String[] args) {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 2, 4, 3, 5, 1, 6, 1);
        List<String>  words   = Arrays.asList("apple", "banana", "apple", "cherry", "banana", "apple");

        // TODO

        //Q1
        List<Integer> duplicates =  numbers.stream().collect(Collectors.collectingAndThen(Collectors.groupingBy(Integer::intValue, Collectors.counting()), map->map.entrySet().stream().filter(entery->entery.getValue()>1).map(Map.Entry::getKey).collect(Collectors.toList())));

        System.out.println(duplicates);
        Map<Integer, Long> map = numbers.stream().collect(Collectors.groupingBy(Integer::intValue, Collectors.counting()));

        //Q3
        List<Integer> nisRepeatedForMoreThan2 = numbers.stream().collect(Collectors.collectingAndThen(Collectors.groupingBy(Integer::intValue, Collectors.counting()), map2->map2.entrySet().stream().filter(entry->entry.getValue()>2).map(Map.Entry::getKey).collect(Collectors.toList())));
        System.out.println(nisRepeatedForMoreThan2);

        Map<String, Long> map4 = words.stream().collect(Collectors.collectingAndThen(Collectors.groupingBy(String::toString, Collectors.counting()), map2->map2.entrySet().stream().filter(entry->entry.getValue()>=2).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        System.out.println(map4);
    }
}
