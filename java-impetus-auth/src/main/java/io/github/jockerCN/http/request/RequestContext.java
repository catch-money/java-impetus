package io.github.jockerCN.http.request;

public class RequestContext {


    private static final ThreadLocal<RequestInfo> USER_CONTEXT_THREAD_LOCAL = new InheritableThreadLocal<>();


    public static RequestInfo getRequestContext() {
        return USER_CONTEXT_THREAD_LOCAL.get();
    }


    public static void setRequestContext(RequestInfo requestInfo) {
        clear();
        USER_CONTEXT_THREAD_LOCAL.set(requestInfo);
    }


    public static void clear() {
        USER_CONTEXT_THREAD_LOCAL.remove();
    }

}