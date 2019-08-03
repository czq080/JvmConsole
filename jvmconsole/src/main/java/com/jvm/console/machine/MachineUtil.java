package com.jvm.console.machine;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import sun.jvmstat.monitor.*;
import sun.management.ConnectorAddressLink;

import java.io.IOException;
import java.util.*;

import static com.jvm.console.common.Constant.LOCAL_CONNECTOR_ADDRESS_PROP;

/**
 * @author xyt
 * @date 2019/8/2
 */
public class MachineUtil {
    public static Map<Integer, MachinePower> getMonitoredVMs() {
        MonitoredHost host;
        Set activeVmsPid;
        Map<Integer, MachinePower> vmMap = new HashMap<Integer, MachinePower>();
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

    public static void main(String[] args) {
        Map<Integer, MachinePower> machineMap = MachineUtil.getMonitoredVMs();
        for (Map.Entry<Integer, MachinePower> machineEntry : machineMap.entrySet()) {
            Integer pid = machineEntry.getKey();
            MachinePower machine = machineEntry.getValue();
            System.out.println(String.format("jvm pid is %s, info[%s]", pid, machine));
        }
    }
}
