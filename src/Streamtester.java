import java.util.*;
import java.util.stream.Collectors;

public class Streamtester {

    //What is lambda

    // lambda is concise representation of anonymous methods or function interface methods

    //what is function interfaces -> interface with single method

    //If we annotated the class with @FunctionalInterfaces Compiler will try give the error if any new methods are added by accidentantly
    static void main() {
        String s = "Hello";

       Map<Character, Long> map = s.chars().mapToObj(c-> (char) c).
               collect(Collectors.groupingBy(c-> c, Collectors.counting()));
       map = map.entrySet().stream().filter(i-> i.getValue() >= 2).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        System.out.println(map);
    }
}
