package io.github.jockerCN.common;


import io.github.jockerCN.Result;
import io.github.jockerCN.type.TypeConvert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@Component
public class SpringExecutorHandle {


    @Transactional(rollbackFor = Exception.class)
    public void executeThrows(Runnable runnable) {
        runnable.run();
    }

    @Transactional(rollbackFor = Exception.class)
    public void execute(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.error("SpringExecutorHandle execute Runnable failed:{}", e.getMessage(), e);
            TransactionProvider.setIfRollbackOnly();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public <T> T executeThrows(Supplier<T> supplier) {
        return supplier.get();
    }

    @Transactional(rollbackFor = Exception.class)
    public <T> T execute(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.error("SpringExecutorHandle execute Supplier failed:{}", e.getMessage(), e);
            TransactionProvider.setIfRollbackOnly();
            return TypeConvert.cast(Result.failWithMsg(e.getMessage()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public <T, R> R execute(T object, FunctionWrapper<T, R> wrapper, FunctionWrapper<?, ?> afterCommit) {
        try {
            R run = wrapper.run(object);
            if (Objects.nonNull(afterCommit)) {
                TransactionProvider.doAfterCommit(afterCommit, run);
            }
            return run;
        } catch (Exception e) {
            log.error("SpringExecutorHandle execute FunctionWrapper failed:{}", e.getMessage(), e);
            TransactionProvider.setIfRollbackOnly();
            return TypeConvert.cast(Result.failWithMsg(e.getMessage()));
        }
    }

}
