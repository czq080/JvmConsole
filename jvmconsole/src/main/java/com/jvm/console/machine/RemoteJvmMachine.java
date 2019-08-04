package com.jvm.console.machine;

import java.io.IOException;

/**
 * @author xyt
 * @date 2019/8/3
 */
public class RemoteJvmMachine extends Machine {
    @Override
    public String id() {
        return null;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public String address() {
        return null;
    }

    @Override
    public boolean useCredentials() {
        return false;
    }

    @Override
    public void open() throws IOException {

    }

    @Override
    public void close() {

    }
}
