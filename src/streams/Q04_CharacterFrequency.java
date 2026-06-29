package streams;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Q04_CharacterFrequency {

    /*
     * Input: "programming"
     *
     * Q1: Build a character frequency map preserving insertion order.
     *     Expected: {p=1, r=2, o=1, g=2, a=1, m=2, i=1, n=1}
     *
     * Q2: Sort the map by frequency descending and print each entry.
     *
     * Q3: Find the most frequent character.
     *     Expected: 'r' (or 'g' or 'm' — any of the chars with count 2)
     *
     * Q4: Find the first non-repeated character (order matters).
     *     Expected: 'p'
     *
     * Q5: Print only the repeated characters.
     *     Expected: r, g, m
     *
     * Q6: For "Hello World", build a case-insensitive frequency map ignoring spaces.
     */
    public static void main(String[] args) {
        String input = "programming";

        // TODO
        Map<Character, Long> mapSeq = input.chars().boxed().collect(
                Collectors.collectingAndThen(
                        Collectors.groupingBy(i -> (char) i.intValue(), Collectors.counting()),
                        map -> map.entrySet().stream()
                              //  .sorted(Map.Entry.<Character, Long>comparingByValue().reversed()) // Add .reversed() for descending
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (e1, e2) -> e1,       // Merge function (required)
                                        LinkedHashMap::new    // Map supplier (required to preserve order)
                                ))
                )
        );

        List<Character> mapSeq2 = input.chars().boxed().collect(
                Collectors.collectingAndThen(
                        Collectors.groupingBy(i -> (char) i.intValue(), Collectors.counting()),
                        map -> map.entrySet().stream()
                                .filter(e-> e.getValue() >=2)
                                .map(Map.Entry::getKey)// Add .reversed() for descending
                                .collect(Collectors.toList())
                )
        );
        System.out.println(mapSeq);
        System.out.println(mapSeq2);

        char ck = input.chars().mapToObj(c-> (char)c).collect(

                        Collectors.groupingBy(
                                Function.identity(),
                                LinkedHashMap::new,
                                Collectors.counting()
        )).entrySet().stream().filter(e-> e.getValue()==1).map(Map.Entry::getKey).findFirst().orElse(null);
        System.out.println(ck);

    }
}
