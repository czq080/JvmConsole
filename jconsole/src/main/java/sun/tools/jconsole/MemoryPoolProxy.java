/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.tools.jconsole;

import com.sun.management.GarbageCollectorMXBean;
import com.sun.management.GcInfo;

import javax.management.ObjectName;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static java.lang.management.ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE;

public class MemoryPoolProxy {
    private String poolName;
    private ProxyClient client;
    private ObjectName objName;
    private MemoryPoolMXBean pool;
    private Map<ObjectName, Long> gcMBeans;
    private GcInfo lastGcInfo;

    public MemoryPoolProxy(ProxyClient client, ObjectName poolName) throws java.io.IOException {
        this.client = client;
        this.objName = objName;
        this.pool = client.getMXBean(poolName, MemoryPoolMXBean.class);
        this.poolName = this.pool.getName();
        this.gcMBeans = new HashMap<ObjectName, Long>();
        this.lastGcInfo = null;

        String[] mgrNames = pool.getMemoryManagerNames();
        for (String name : mgrNames) {
            try {
                ObjectName mbeanName = new ObjectName(GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE +
                        ",name=" + name);
                if (client.isRegistered(mbeanName)) {
                    gcMBeans.put(mbeanName, new Long(0));
                }
            } catch (Exception e) {
                assert false;
            }

        }
    }

    public boolean isCollectedMemoryPool() {
        return (gcMBeans.size() != 0);
    }

    public ObjectName getObjectName() {
        return objName;
    }

    public MemoryPoolStat getStat() throws java.io.IOException {
        long usageThreshold = (pool.isUsageThresholdSupported()
                ? pool.getUsageThreshold()
                : -1);
        long collectThreshold = (pool.isCollectionUsageThresholdSupported()
                ? pool.getCollectionUsageThreshold()
                : -1);
        long lastGcStartTime = 0;
        long lastGcEndTime = 0;
        MemoryUsage beforeGcUsage = null;
        MemoryUsage afterGcUsage = null;
        long gcId = 0;
        if (lastGcInfo != null) {
            gcId = lastGcInfo.getId();
            lastGcStartTime = lastGcInfo.getStartTime();
            lastGcEndTime = lastGcInfo.getEndTime();
            beforeGcUsage = lastGcInfo.getMemoryUsageBeforeGc().get(poolName);
            afterGcUsage = lastGcInfo.getMemoryUsageAfterGc().get(poolName);
        }

        Set<Entry<ObjectName, Long>> set = gcMBeans.entrySet();
        for (Entry<ObjectName, Long> e : set) {
            GarbageCollectorMXBean gc =
                    client.getMXBean(e.getKey(),
                            GarbageCollectorMXBean.class);
            Long gcCount = e.getValue();
            Long newCount = gc.getCollectionCount();
            if (newCount > gcCount) {
                gcMBeans.put(e.getKey(), new Long(newCount));
                lastGcInfo = gc.getLastGcInfo();
                if (lastGcInfo.getEndTime() > lastGcEndTime) {
                    gcId = lastGcInfo.getId();
                    lastGcStartTime = lastGcInfo.getStartTime();
                    lastGcEndTime = lastGcInfo.getEndTime();
                    beforeGcUsage = lastGcInfo.getMemoryUsageBeforeGc().get(poolName);
                    afterGcUsage = lastGcInfo.getMemoryUsageAfterGc().get(poolName);
                    assert (beforeGcUsage != null);
                    assert (afterGcUsage != null);
                }
            }
        }

        MemoryUsage usage = pool.getUsage();
        return new MemoryPoolStat(poolName,
                usageThreshold,
                usage,
                gcId,
                lastGcStartTime,
                lastGcEndTime,
                collectThreshold,
                beforeGcUsage,
                afterGcUsage);
    }
}
