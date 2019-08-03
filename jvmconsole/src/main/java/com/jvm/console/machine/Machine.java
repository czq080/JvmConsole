package com.jvm.console.machine;

/**
 * @author xyt
 * @date 2019/8/3
 */
public abstract class Machine implements MachinePower {
    public abstract String id();

    public abstract String name();

    public abstract String address();

    public abstract boolean useCredentials();
}
