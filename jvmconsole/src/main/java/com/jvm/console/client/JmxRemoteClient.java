package com.jvm.console.client;

import com.jvm.console.machine.Machine;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;

/**
 * @author xyt
 * @date 2019/8/3
 */
// TODO: 2019/8/3 远程jmx查看
public class JmxRemoteClient extends JmxClient implements Connection {

    protected JmxRemoteClient(Machine machine) {
        super(machine);
    }

    @Override
    protected boolean isMonitor() {
        return false;
    }

    @Override
    protected boolean isRemote() {
        return false;
    }

    @Override
    protected JMXServiceURL jmxUrl() throws IOException {
        return null;
    }

    @Override
    protected JMXConnector jmxConnect(JMXServiceURL jmxServiceURL) throws IOException {
        return null;
    }

    @Override
    protected void tryClose() throws IOException {

    }
}
