package com.jvm.console.client;

import javax.management.MBeanServerConnection;

/**
 * @author xyt
 * @date 2019/8/3
 */
public interface SnapshotMBeanServerConnection extends MBeanServerConnection {
    /**
     * Flush all cached values of attributes.
     */
    void flush();
}
