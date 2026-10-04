package testfixture.webpage.external;

import io.github.jockerCN.page.PageModule;
import testfixture.webpage.modules.TestPageParam;

import java.util.concurrent.atomic.AtomicInteger;

@PageModule("external")
public class ExternalPageParam extends TestPageParam {

    public static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    public ExternalPageParam() {
        CONSTRUCTIONS.incrementAndGet();
    }
}
