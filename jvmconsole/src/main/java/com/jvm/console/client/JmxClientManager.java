package com.jvm.console.client;

import com.jvm.console.machine.LocalJvmMachine;
import com.jvm.console.machine.Machine;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author chenzhiqiang
 * @date 2019/8/4
 */
public class JmxClientManager {
    private volatile Map<String, JmxClient> jmxClientMap = new HashMap<String, JmxClient>();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private JmxClientManager() {
    }

    private static class JmxClientManagerHandle {
        public static final JmxClientManager HANDLE = new JmxClientManager();
    }

    public static JmxClientManager getInstance() {
        return JmxClientManagerHandle.HANDLE;
    }


    public JmxClient init(Machine machine) {
        JmxClient jmxClient = jmxClientMap.get(machine.id());
        if (jmxClient != null) {
            return jmxClient;
        }
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            JmxClient tmp = jmxClientMap.get(machine.id());
            if (tmp != null) {
                return tmp;
            }
            JmxClient newJmxClient = new JmxLocalClient(machine);
            jmxClientMap.put(machine.id(), newJmxClient);
            return newJmxClient;
        } finally {
            writeLock.unlock();
        }
    }

    public void connect(JmxClient jmxClient) throws IOException {
        jmxClient.connect();
    }

    public void close(JmxClient jmxClient) throws IOException {
        jmxClient.close();
    }
}
