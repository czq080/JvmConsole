package com.jvm.console.client;

import com.jvm.console.machine.Machine;
import com.sun.management.HotSpotDiagnosticMXBean;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.jvm.console.common.Constant.HOTSPOT_DIAGNOSTIC_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.*;

/**
 * @author xyt
 * @date 2019/8/3
 */
public abstract class JmxClient implements Connection {

    private Machine machine;
    private JMXServiceURL jmxUrl;
    private JMXConnector jmxc;
    private boolean hasPlatformMXBeans = false;
    private boolean hasHotSpotDiagnosticMXBean = false;
    private boolean hasCompilationMXBean = false;
    private boolean supportsLockUsage = false;
    private MBeanServerConnection mbsc = null;
    private SnapshotMBeanServerConnection server = null;
    private ClassLoadingMXBean classLoadingMBean = null;
    private CompilationMXBean compilationMBean = null;
    private MemoryMXBean memoryMBean = null;
    private OperatingSystemMXBean operatingSystemMBean = null;
    private RuntimeMXBean runtimeMBean = null;
    private ThreadMXBean threadMBean = null;
    private com.sun.management.OperatingSystemMXBean sunOperatingSystemMXBean = null;
    private HotSpotDiagnosticMXBean hotspotDiagnosticMXBean = null;
    private List<GarbageCollectorMXBean> garbageCollectorMBeans = null;

    private volatile ConnectionState runState = ConnectionState.DISCONNECTED;

    protected JmxClient(Machine machine) {
        this.machine = machine;
    }

    @Override
    public synchronized void connect() throws IOException {
        if (!runState.equals(ConnectionState.DISCONNECTED)) {
            throw new IOException("client " + machine.id() + " is running");
        }
        System.out.println("client " + machine.id() + " start connect");
        runState = ConnectionState.CONNECTING;
        try {
            if (isMonitor()) {
                this.mbsc = ManagementFactory.getPlatformMBeanServer();
            } else {
                this.jmxUrl = jmxUrl();
                this.jmxc = jmxConnect(this.jmxUrl);
                this.mbsc = jmxc.getMBeanServerConnection();
            }
            MBeanServerConnectionProxy proxy = new MBeanServerConnectionProxy(this.mbsc);
            this.server = proxy.getInstace();
            try {
                ObjectName on = new ObjectName(THREAD_MXBEAN_NAME);
                this.hasPlatformMXBeans = server.isRegistered(on);
                this.hasHotSpotDiagnosticMXBean =
                        server.isRegistered(new ObjectName(HOTSPOT_DIAGNOSTIC_MXBEAN_NAME));
                // check if it has 6.0 new APIs
                if (this.hasPlatformMXBeans) {
                    MBeanOperationInfo[] mopis = server.getMBeanInfo(on).getOperations();
                    // look for findDeadlockedThreads operations;
                    for (MBeanOperationInfo op : mopis) {
                        if (op.getName().equals("findDeadlockedThreads")) {
                            this.supportsLockUsage = true;
                            break;
                        }
                    }

                    on = new ObjectName(COMPILATION_MXBEAN_NAME);
                    this.hasCompilationMXBean = server.isRegistered(on);
                }
            } catch (MalformedObjectNameException e) {
                // should not reach here
                throw new InternalError(e.getMessage());
            } catch (IntrospectionException e) {
                InternalError ie = new InternalError(e.getMessage());
                ie.initCause(e);
                throw ie;
            } catch (InstanceNotFoundException e) {
                InternalError ie = new InternalError(e.getMessage());
                ie.initCause(e);
                throw ie;
            } catch (ReflectionException e) {
                InternalError ie = new InternalError(e.getMessage());
                ie.initCause(e);
                throw ie;
            }

            if (hasPlatformMXBeans) {
                // WORKAROUND for bug 5056632
                // Check if the access role is correct by getting a RuntimeMXBean
                getRuntimeMXBean();
            }
            runState = ConnectionState.CONNECTED;
        } catch (Exception e) {
            runState = ConnectionState.DISCONNECTED;
            throw new IOException(e);
        }
    }

    protected abstract boolean isMonitor();

    protected abstract boolean isRemote();

    protected abstract JMXServiceURL jmxUrl() throws IOException;

    protected abstract JMXConnector jmxConnect(JMXServiceURL jmxServiceURL) throws IOException;

    protected abstract void tryClose() throws IOException;

    @Override
    public void close() throws IOException {
        try {
            tryClose();
            if (jmxc != null) {
                try {
                    jmxc.close();
                } catch (IOException e) {
                    // Ignore ???
                }
            }
            jmxc = null;
            classLoadingMBean = null;
            compilationMBean = null;
            memoryMBean = null;
            operatingSystemMBean = null;
            runtimeMBean = null;
            threadMBean = null;
            sunOperatingSystemMXBean = null;
            garbageCollectorMBeans = null;
        } finally {
            runState = ConnectionState.DISCONNECTED;
        }
    }

    public Machine getMachine() {
        return machine;
    }


    public synchronized Collection<GarbageCollectorMXBean> getGarbageCollectorMXBeans()
            throws IOException {

        // TODO: How to deal with changes to the list??
        if (garbageCollectorMBeans == null) {
            ObjectName gcName = null;
            try {
                gcName = new ObjectName(GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
            } catch (MalformedObjectNameException e) {
                // should not reach here
                assert (false);
            }
            Set mbeans = server.queryNames(gcName, null);
            if (mbeans != null) {
                garbageCollectorMBeans = new ArrayList<GarbageCollectorMXBean>();
                for (Object mbean : mbeans) {
                    ObjectName on = (ObjectName) mbean;
                    String name = GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE +
                            ",name=" + on.getKeyProperty("name");

                    GarbageCollectorMXBean mBean =
                            newPlatformMXBeanProxy(server, name,
                                    GarbageCollectorMXBean.class);
                    garbageCollectorMBeans.add(mBean);
                }
            }
        }
        return garbageCollectorMBeans;
    }

    public synchronized MemoryMXBean getMemoryMXBean() throws IOException {
        if (hasPlatformMXBeans && memoryMBean == null) {
            memoryMBean =
                    newPlatformMXBeanProxy(server, MEMORY_MXBEAN_NAME,
                            MemoryMXBean.class);
        }
        return memoryMBean;
    }

    public synchronized RuntimeMXBean getRuntimeMXBean() throws IOException {
        if (hasPlatformMXBeans && runtimeMBean == null) {
            runtimeMBean =
                    newPlatformMXBeanProxy(server, RUNTIME_MXBEAN_NAME,
                            RuntimeMXBean.class);
        }
        return runtimeMBean;
    }


    public synchronized ThreadMXBean getThreadMXBean() throws IOException {
        if (hasPlatformMXBeans && threadMBean == null) {
            threadMBean =
                    newPlatformMXBeanProxy(server, THREAD_MXBEAN_NAME,
                            ThreadMXBean.class);
        }
        return threadMBean;
    }

    public synchronized OperatingSystemMXBean getOperatingSystemMXBean() throws IOException {
        if (hasPlatformMXBeans && operatingSystemMBean == null) {
            operatingSystemMBean =
                    newPlatformMXBeanProxy(server, OPERATING_SYSTEM_MXBEAN_NAME,
                            OperatingSystemMXBean.class);
        }
        return operatingSystemMBean;
    }

    public synchronized com.sun.management.OperatingSystemMXBean
    getSunOperatingSystemMXBean() throws IOException {

        try {
            ObjectName on = new ObjectName(OPERATING_SYSTEM_MXBEAN_NAME);
            if (sunOperatingSystemMXBean == null) {
                if (server.isInstanceOf(on,
                        "com.sun.management.OperatingSystemMXBean")) {
                    sunOperatingSystemMXBean =
                            newPlatformMXBeanProxy(server,
                                    OPERATING_SYSTEM_MXBEAN_NAME,
                                    com.sun.management.OperatingSystemMXBean.class);
                }
            }
        } catch (InstanceNotFoundException e) {
            return null;
        } catch (MalformedObjectNameException e) {
            return null; // should never reach here
        }
        return sunOperatingSystemMXBean;
    }

    public synchronized HotSpotDiagnosticMXBean getHotSpotDiagnosticMXBean() throws IOException {
        if (hasHotSpotDiagnosticMXBean && hotspotDiagnosticMXBean == null) {
            hotspotDiagnosticMXBean =
                    newPlatformMXBeanProxy(server, HOTSPOT_DIAGNOSTIC_MXBEAN_NAME,
                            HotSpotDiagnosticMXBean.class);
        }
        return hotspotDiagnosticMXBean;
    }

    public <T> T getMXBean(ObjectName objName, Class<T> interfaceClass)
            throws IOException {
        return newPlatformMXBeanProxy(server,
                objName.toString(),
                interfaceClass);
    }
}
