package io.github.jockerCN.function;


import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface FunctionWrapper<T, R> {

    R run(T t);

    static <T, R> FunctionWrapper<T, R> wrap(Runnable runnable) {
        return t -> {
            runnable.run();
            return null;
        };
    }


    static <T, R> FunctionWrapper<T, R> wrap(Supplier<R> supplier) {
        return result -> supplier.get();
    }


    static <T, R> FunctionWrapper<T, R> wrap(Function<T, R> function) {
        return function::apply;
    }

}