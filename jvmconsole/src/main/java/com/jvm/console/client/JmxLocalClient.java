package com.jvm.console.client;

import com.jvm.console.machine.Machine;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author xyt
 * @date 2019/8/3
 */
public class JmxLocalClient extends JmxClient implements Connection {

    protected JmxLocalClient(Machine machine) {
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
        Machine machine = getMachine();
        machine.open();
        return new JMXServiceURL(machine.address());
    }

    @Override
    protected JMXConnector jmxConnect(JMXServiceURL jmxServiceURL) throws IOException {
        if (getMachine().useCredentials()) {
            //需要验证凭证
            Map<String, String[]> env = new HashMap<String, String[]>();
            env.put(JMXConnector.CREDENTIALS,
                    new String[]{"username", "password"});
            return JMXConnectorFactory.connect(jmxServiceURL, env);
        } else {
            return JMXConnectorFactory.connect(jmxServiceURL);
        }
    }

    @Override
    protected void tryClose() {
        Machine machine = getMachine();
        machine.close();
    }

}
