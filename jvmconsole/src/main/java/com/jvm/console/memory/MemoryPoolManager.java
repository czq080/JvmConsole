package com.jvm.console.memory;

import com.jvm.console.client.JmxClient;

import javax.management.MBeanInfo;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.management.ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE;

/**
 * @author chenzhiqiang
 * @date 2019/8/4
 */
public class MemoryPoolManager {
    private List<MemoryPoolWrapper> memoryPoolWrappers = new ArrayList<MemoryPoolWrapper>();
    private JmxClient jmxClient;

    public MemoryPoolManager(JmxClient jmxClient) throws IOException {
        this.jmxClient = jmxClient;
        Map<ObjectName, MBeanInfo> mBeanInfoMap = jmxClient.getMBeans(MEMORY_POOL_MXBEAN_DOMAIN_TYPE, ",*");
        for (Map.Entry<ObjectName, MBeanInfo> mBeanInfoEntry : mBeanInfoMap.entrySet()) {
            ObjectName objectName = mBeanInfoEntry.getKey();
            MBeanInfo mBeanInfo = mBeanInfoEntry.getValue();
            MemoryPoolWrapper memoryPoolWrapper = new MemoryPoolWrapper(this, objectName);
            memoryPoolWrappers.add(memoryPoolWrapper);
        }
    }

    private Memory memory(MemoryPoolWrapper wrapper, MemoryPoolMXBean poolMXBean) throws IOException {
        long usageThreshold = (poolMXBean.isUsageThresholdSupported()
                ? poolMXBean.getUsageThreshold()
                : -1);
        long collectThreshold = (poolMXBean.isCollectionUsageThresholdSupported()
                ? poolMXBean.getCollectionUsageThreshold()
                : -1);
        return new Memory(wrapper.getPoolName(), poolMXBean.getUsage(), usageThreshold, collectThreshold);
    }

    public List<Memory> memoryPool() throws IOException {
        List<Memory> memories = new ArrayList<Memory>(memoryPoolWrappers.size());
        for (MemoryPoolWrapper memoryPoolWrapper : memoryPoolWrappers) {
            memories.add(memoryPoolWrapper.memory());
        }
        return memories;
    }

    private <T> T getMXBean(ObjectName objectName, Class<T> clazz) throws IOException {
        return jmxClient.getMXBean(objectName, clazz);
    }

    private static class MemoryPoolWrapper implements Comparable<MemoryPoolWrapper> {
        private String poolName;
        private ObjectName objName;
        private MemoryPoolManager poolManager;
        private MemoryPoolMXBean pool;
        private boolean isHeap;

        public MemoryPoolWrapper(MemoryPoolManager poolManager, ObjectName objName) throws IOException {
            this.objName = objName;
            this.poolManager = poolManager;
            this.pool = poolManager.getMXBean(objName, MemoryPoolMXBean.class);
            this.isHeap = MemoryType.HEAP.equals(pool.getType());
            this.poolName = pool.getName();
        }

        public String getPoolName() {
            return poolName;
        }

        public ObjectName getObjName() {
            return objName;
        }

        public Memory memory() throws IOException {
            return poolManager.memory(this, pool);
        }

        public boolean isHeap() {
            return isHeap;
        }

        @Override
        public int compareTo(MemoryPoolWrapper o) {
            return isHeap() ? 1 : o.isHeap ? 1 : 0;
        }
    }
}
