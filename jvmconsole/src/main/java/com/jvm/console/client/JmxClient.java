package com.jvm.console.client;

import com.jvm.console.machine.Machine;
import com.jvm.console.memory.Memory;
import com.jvm.console.memory.MemoryPoolManager;
import com.sun.management.HotSpotDiagnosticMXBean;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.*;
import java.util.*;

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
    private MBeanServerConnection server = null;
    private ClassLoadingMXBean classLoadingMBean = null;
    private CompilationMXBean compilationMBean = null;
    private MemoryMXBean memoryMBean = null;
    private MemoryPoolManager memoryPoolManager = null;
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
            //动态代理增加缓存功能，是否有必要
//            MBeanServerConnectionProxy proxy = new MBeanServerConnectionProxy(this.mbsc);
            this.server = mbsc;
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

    public synchronized List<Memory> getMemoryPool() throws IOException {
        if (hasPlatformMXBeans && memoryPoolManager == null) {
            memoryPoolManager = new MemoryPoolManager(this);
        }
        return memoryPoolManager.memoryPool();
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

    public boolean isLockUsageSupported() {
        return supportsLockUsage;
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

    public synchronized ClassLoadingMXBean getClassLoadingMXBean() throws IOException {
        if (hasPlatformMXBeans && classLoadingMBean == null) {
            classLoadingMBean =
                    newPlatformMXBeanProxy(server, CLASS_LOADING_MXBEAN_NAME,
                            ClassLoadingMXBean.class);
        }
        return classLoadingMBean;
    }

    public synchronized CompilationMXBean getCompilationMXBean() throws IOException {
        if (hasCompilationMXBean && compilationMBean == null) {
            compilationMBean =
                    newPlatformMXBeanProxy(server, COMPILATION_MXBEAN_NAME,
                            CompilationMXBean.class);
        }
        return compilationMBean;
    }

    public <T> T getMXBean(ObjectName objName, Class<T> interfaceClass)
            throws IOException {
        return newPlatformMXBeanProxy(server,
                objName.toString(),
                interfaceClass);
    }

    public long[] findDeadlockedThreads() throws IOException {
        ThreadMXBean tm = getThreadMXBean();
        if (supportsLockUsage && tm.isSynchronizerUsageSupported()) {
            return tm.findDeadlockedThreads();
        } else {
            return tm.findMonitorDeadlockedThreads();
        }
    }

    /**
     * Returns a list of attributes of a named MBean.
     */
    public AttributeList getAttributes(ObjectName name, String[] attributes)
            throws IOException {
        AttributeList list = null;
        try {
            list = server.getAttributes(name, attributes);
        } catch (InstanceNotFoundException e) {
            // TODO: A MBean may have been unregistered.
            // need to set up listener to listen for MBeanServerNotification.
        } catch (ReflectionException e) {
            // TODO: should log the error
        }
        return list;
    }

    public Map<ObjectName, MBeanInfo> getMBeans(String domain, String ext)
            throws IOException {

        ObjectName name = null;
        if (domain != null) {
            try {
                name = new ObjectName(domain + ext);
            } catch (MalformedObjectNameException e) {
                // should not reach here
                assert (false);
            }
        }
        Set mbeans = server.queryNames(name, null);
        Map<ObjectName, MBeanInfo> result =
                new HashMap<ObjectName, MBeanInfo>(mbeans.size());
        Iterator iterator = mbeans.iterator();
        while (iterator.hasNext()) {
            Object object = iterator.next();
            if (object instanceof ObjectName) {
                ObjectName o = (ObjectName) object;
                try {
                    MBeanInfo info = server.getMBeanInfo(o);
                    result.put(o, info);
                } catch (IntrospectionException e) {
                    System.err.println(e);
                } catch (InstanceNotFoundException e) {
                    System.err.println(e);
                } catch (ReflectionException e) {
                    System.err.println(e);
                }
            }
        }
        return result;
    }
}
