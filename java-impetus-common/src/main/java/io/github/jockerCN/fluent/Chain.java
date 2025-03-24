package io.github.jockerCN.fluent;

import io.github.jockerCN.function.FunctionWrapper;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class Chain {

    private final Object t;

    private List<ChainNode> chains = new ArrayList<>();


    protected static class ChainNode {

        private final Supplier<Object> supplier;

        private final FunctionWrapper<Object, Object> functionWrapper;
        @Getter
        private Object result;

        private final Predicate<Object> predicate;

        private FunctionWrapper<Object, Object> elseFunctionWrapper = (t) -> t;

        private Function<Object, Predicate<Object>> predicateFunction;

        public ChainNode(Supplier<Object> supplier, Predicate<Object> predicate, FunctionWrapper<Object, Object> functionWrapper) {
            this.supplier = supplier;
            this.functionWrapper = functionWrapper;
            this.predicate = (t)->{
                Object o = supplier.get();
                return WhenOperator.empty(o.getClass()).test(t);
            };
        }


        public void el(FunctionWrapper<Object, Object> functionWrapper) {
            this.elseFunctionWrapper = functionWrapper;
        }

        public void executor() {
            Object object = supplier.get();
            if (predicate.test(object)) {
                result = functionWrapper.run(object);
            } else {
                result = elseFunctionWrapper.run(object);
            }
        }
    }

    public Chain(Object t) {
        this.t = t;
        chains.add(new ChainNode(() -> t, (t1) -> true, (o) -> o));
    }

    public static Chain bind(Object t) {
        return new Chain(t) {
        };
    }

    public static Chain anewBind(Object t) {
        return new Chain(t) {
        };
    }

    public Chain streamMapper(Function<?, ?> mapper) {
        return this;
    }

    public Chain whenEmpty(Runnable runnable, Class<?> type) {
        ChainNode pre = chains.getLast();
        chains.add(new ChainNode(pre::getResult,
                WhenOperator.empty(type)
                , FunctionWrapper.wrap(runnable)));
        return this;
    }


    public Chain whenNotEmpty(Object t) {
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
