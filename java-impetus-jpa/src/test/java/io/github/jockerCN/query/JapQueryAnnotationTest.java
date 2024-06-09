package io.github.jockerCN.query;

import io.github.jockerCN.JpaTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class JapQueryAnnotationTest extends JpaTestBase {


    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void run() {
        Map<String, QueryAnnotationTest> beansOfType = applicationContext.getBeansOfType(QueryAnnotationTest.class);
        for (Map.Entry<String, QueryAnnotationTest> queryAnnotationTestEntry : beansOfType.entrySet()) {
            QueryAnnotationTest annotationTest = queryAnnotationTestEntry.getValue();
            annotationTest.run();
        }
    }
}
