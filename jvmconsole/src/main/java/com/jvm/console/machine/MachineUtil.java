package com.jvm.console.machine;

import com.jvm.console.client.JmxClient;
import com.jvm.console.client.JmxClientManager;
import com.jvm.console.memory.Memory;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import sun.jvmstat.monitor.*;
import sun.management.ConnectorAddressLink;

import java.io.IOException;
import java.lang.management.*;
import java.util.*;

import static com.jvm.console.common.Constant.LOCAL_CONNECTOR_ADDRESS_PROP;

/**
 * @author xyt
 * @date 2019/8/2
 */
public class MachineUtil {
    public static Map<Integer, Machine> getMonitoredVMs() {
        MonitoredHost host;
        Set activeVmsPid;
        Map<Integer, Machine> vmMap = new HashMap<Integer, Machine>();
        try {
            host = MonitoredHost.getMonitoredHost(new HostIdentifier((String) null));
            activeVmsPid = host.activeVms();
        } catch (java.net.URISyntaxException sx) {
            throw new InternalError(sx.getMessage());
        } catch (MonitorException mx) {
            throw new InternalError(mx.getMessage());
        }
        for (Object pid : activeVmsPid) {
            if (pid instanceof Integer) {
                int pidInt = (Integer) pid;
                String name = pid.toString(); // default to pid if name not available
                boolean attachable = false;
                String address = null;
                try {
                    MonitoredVm mvm = host.getMonitoredVm(new VmIdentifier(name));
                    // use the command line as the display name
                    name = MonitoredVmUtil.commandLine(mvm);
                    attachable = MonitoredVmUtil.isAttachable(mvm);
                    address = ConnectorAddressLink.importFrom(pidInt);
                    mvm.detach();
                } catch (Exception x) {
                    // ignore
                }
                vmMap.put(pidInt, new LocalJvmMachine(address, name, pidInt, attachable));
            }
        }
        List<VirtualMachineDescriptor> virtualMachineDescriptors = VirtualMachine.list();
        for (VirtualMachineDescriptor vmd : virtualMachineDescriptors) {
            try {
                Integer vmid = Integer.valueOf(vmd.id());
                if (vmMap.containsKey(vmid))
                    continue;
                boolean attachable = false;
                String address = null;
                try {
                    VirtualMachine vm = VirtualMachine.attach(vmd);
                    attachable = true;
                    Properties agentProps = vm.getAgentProperties();
                    address = (String) agentProps.get(LOCAL_CONNECTOR_ADDRESS_PROP);
                    vm.detach();
                } catch (AttachNotSupportedException x) {
                    // not attachable
                } catch (IOException x) {
                    // ignore
                }
                vmMap.put(vmid, new LocalJvmMachine(address, vmd.displayName(), vmid, attachable));
            } catch (NumberFormatException e) {
                // do not support vmid different than pid
            }
        }
        return vmMap;
    }

    public static void main(String[] args) throws IOException {
        Map<Integer, Machine> machineMap = MachineUtil.getMonitoredVMs();
        for (Map.Entry<Integer, Machine> machineEntry : machineMap.entrySet()) {
            Integer pid = machineEntry.getKey();
            MachinePower machine = machineEntry.getValue();
            System.out.println(String.format("jvm pid is %s, info[%s]", pid, machine));
        }
        JmxClientManager jmxClientManager = JmxClientManager.getInstance();
        final JmxClient jmxClient = jmxClientManager.init(machineMap.get(7436));
        jmxClientManager.connect(jmxClient);
        Timer memoryTimer = new Timer();
        memoryTimer.schedule(new TimerTask() {
            public void run() {
                try {
                    System.out.println("Memory------------------------");
                    MemoryMXBean memoryMXBean = jmxClient.getMemoryMXBean();
                    MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
                    MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
                    String heapMsg = "堆区：" +
                            "\n\t已使用:%s bytes(%s Kb)" +
                            "\n\t分配:%s bytes(%s Kb)" +
                            "\n\t最大值:%s bytes(%s Kb)" +
                            "\n\t使用阀值:%s bytes(%s Kb)";
                    String nonHeapMsg = "非堆区：" +
                            "\n\t已使用:%s bytes(%s Kb)" +
                            "\n\t分配:%s bytes(%s Kb)" +
                            "\n\t最大值:%s bytes(%s Kb)" +
                            "\n\t使用阀值:%s bytes(%s Kb)";
                    System.out.println(String.format(heapMsg, heap.getUsed(), (heap.getUsed() >> 10),
                            heap.getCommitted(), (heap.getCommitted() >> 10),
                            heap.getMax(), (heap.getMax() >> 10), "不支持", "不支持"));
                    System.out.println(String.format(nonHeapMsg, nonHeap.getUsed(), (nonHeap.getUsed() >> 10),
                            nonHeap.getCommitted(), (nonHeap.getCommitted() >> 10),
                            nonHeap.getMax(), (nonHeap.getMax() >> 10), "不支持", "不支持"));
                    for (Memory memory : jmxClient.getMemoryPool()) {
                        String memoryMsg = "%s区：" +
                                "\n\t已使用:%s bytes(%s Kb)" +
                                "\n\t分配:%s bytes(%s Kb)" +
                                "\n\t最大值:%s bytes(%s Kb)" +
                                "\n\t使用阀值:%s bytes(%s Kb)";
                        MemoryUsage usage = memory.getUsage();
                        long usageThreshold = memory.getUsageThreshold();
                        long max = usage.getMax();
                        System.out.println(String.format(memoryMsg, memory.getPoolName(), usage.getUsed(), (usage.getUsed() >> 10),
                                usage.getCommitted(), (usage.getCommitted() >> 10),
                                max == -1 ? "不支持" : max, max == -1 ? "不支持" : (usage.getMax() >> 10), usageThreshold == -1 ? "不支持" : usageThreshold, usageThreshold == -1 ? "不支持" : usageThreshold >> 10));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("------------------------------");
                System.out.println("Thread------------------------");
                try {
                    ThreadMXBean threadMXBean = jmxClient.getThreadMXBean();
                    long[] threads = threadMXBean.getAllThreadIds();
                    for (long threadID : threads) {
                        StringBuilder sb = new StringBuilder();
                        ThreadInfo threadInfo = null;
                        MonitorInfo[] monitors = null;
                        if (jmxClient.isLockUsageSupported() &&
                                threadMXBean.isObjectMonitorUsageSupported()) {
                            // VMs that support the monitor usage monitoring
                            ThreadInfo[] infos = threadMXBean.dumpAllThreads(true, false);
                            for (ThreadInfo info : infos) {
                                if (info.getThreadId() == threadID) {
                                    threadInfo = info;
                                    monitors = info.getLockedMonitors();
                                    break;
                                }
                            }
                        } else {
                            threadInfo = threadMXBean.getThreadInfo(threadID, Integer.MAX_VALUE);
                        }
                        sb.append(
                                String.format("名称：%s\n状态:%s\n", threadInfo.getThreadName(),
                                        threadInfo.getThreadState().toString())
                        );
                        sb.append("堆栈追踪:\n");
                        int index = 0;
                        for (StackTraceElement e : threadInfo.getStackTrace()) {
                            sb.append(e.toString()).append("\n");
                            if (monitors != null) {
                                for (MonitorInfo mi : monitors) {
                                    if (mi.getLockedStackDepth() == index) {
                                        sb.append("   - 已锁定 " + mi.toString());
                                    }
                                }
                            }
                            index++;
                        }
                        System.out.println(sb.toString());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("------------------------------");
            }
        }, 2000, 3000);
    }
}
