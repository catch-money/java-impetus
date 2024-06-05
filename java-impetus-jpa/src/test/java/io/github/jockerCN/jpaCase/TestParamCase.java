package io.github.jockerCN.jpaCase;

import io.github.jockerCN.JpaTestBase;
import io.github.jockerCN.customize.PojoCache;
import io.github.jockerCN.customize.annotation.JpaQuery;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;


/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */


public class TestParamCase extends JpaTestBase {

    @Test
    void test() {
        JpaQuery annotation = AnnotationUtils.findAnnotation(PojoCache.class, JpaQuery.class);
        System.out.println("test ----------");
    }
}
