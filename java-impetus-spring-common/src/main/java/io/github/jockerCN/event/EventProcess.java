package io.github.jockerCN.event;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface EventProcess {


    void process(Object source);


    boolean isProcess(Object source);
}
