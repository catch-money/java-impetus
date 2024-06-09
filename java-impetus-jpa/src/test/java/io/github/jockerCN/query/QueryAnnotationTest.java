package io.github.jockerCN.query;

import org.junit.jupiter.api.Assertions;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */


public interface QueryAnnotationTest {


    void run();


    default void asserts(boolean b, String annotation) {
        Assertions.assertTrue(b, String.format("@JpaQuery %s Test failed",annotation));
    }

}
