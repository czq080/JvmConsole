package com.jvm.console.memory;

import java.lang.management.MemoryUsage;

/**
 * @author chenzhiqiang
 * @date 2019/8/4
 */
public class Memory {
    private String poolName;
    private MemoryUsage usage;
    private long usageThreshold;
    private long collectThreshold;

    Memory(String name,
           MemoryUsage usage, long usageThreshold, long collectThreshold) {
        this.poolName = name;
        this.usage = usage;
        this.usageThreshold = usageThreshold;
        this.collectThreshold = collectThreshold;
    }

    public String getPoolName() {
        return poolName;
    }

    public MemoryUsage getUsage() {
        return usage;
    }

    public long getUsageThreshold() {
        return usageThreshold;
    }

    public long getCollectThreshold() {
        return collectThreshold;
    }
}
