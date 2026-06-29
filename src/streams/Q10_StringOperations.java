package streams;

import java.util.Arrays;
import java.util.List;

public class Q10_StringOperations {

    /*
     * Q1: Join the names list as a CSV string, and also wrapped with [ ].
     *     Expected: "Alice, Bob, Carol, Dave"  and  "[Alice, Bob, Carol, Dave]"
     *
     * Q2: Count the number of vowels in the sentence.
     *     Expected: 9
     *
     * Q3: Reverse each word individually (not the sentence order).
     *     Expected: "avaJ smaertS era lufrewop"
     *
     * Q4: Check if "racecar" is a palindrome using streams.
     *     Expected: true
     *
     * Q5: Group the words list by their anagram key (sorted characters).
     *     Expected groups: [eat, tea, ate], [tan, nat], [bat]
     *
     * Q6: Extract only the digit characters from the mixed string as a single string.
     *     Expected: "1234567"
     *
     * Q7: Find the longest word in the sentence.
     *     Expected: "powerful"
     *
     * Q8: Build a word frequency map from the text, sorted by frequency descending.
     *     Expected: "the"=3, "cat"=2, others=1
     */
    public static void main(String[] args) {
        List<String> names    = Arrays.asList("Alice", "Bob", "Carol", "Dave");
        String sentence       = "Java Streams are powerful";
        List<String> words    = Arrays.asList("eat", "tea", "tan", "ate", "nat", "bat");
        String mixed          = "abc123def456ghi7";
        String text           = "the cat sat on the mat and the cat";

        // TODO
    }
}
