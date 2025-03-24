package io.github.jockerCN.fluent;

import io.github.jockerCN.function.FunctionWrapper;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class Chain {

    private final Object t;

    private List<ChainNode> chains = new ArrayList<>();


    protected static class ChainNode {

        private final Supplier<Object> supplier;

        private final FunctionWrapper<Object,Object> functionWrapper;
        @Getter
        private Object result;

        public ChainNode(Supplier<Object> supplier, FunctionWrapper<Object,Object> functionWrapper) {
            this.supplier = supplier;
            this.functionWrapper = functionWrapper;
        }

    }

    public Chain(Object t) {
        this.t = t;
        chains.add(new ChainNode(() -> t,(o)-> o));
    }

    public static Chain bind(Object t) {
        return new Chain(t) {};
    }

    public Chain streamMapper(Function<?, ?> mapper) {
        return this;
    }

    public Chain whenEmpty(Runnable runnable) {
        ChainNode pre = chains.getLast();
        chains.add(new ChainNode(pre::getResult,FunctionWrapper.wrap(runnable)));
        return this;
    }

    public Chain WhenNotEmpty(Object t) {
        return this;
    }


    public Chain whenNull(Object t) {
        return this;
    }


    public Chain whenNonNull(Object t) {
        return this;
    }


    public Chain then(Object t) {
        return this;
    }


}
