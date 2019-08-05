package com.jvm.console.machine;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.jvm.console.common.Constant.LOCAL_CONNECTOR_ADDRESS_PROP;

/**
 * @Author:xyt
 * @Description:
 * @Date: 11:11 2019/8/2
 * @Modified By:
 */
public class LocalJvmMachine extends Machine {

    private String address;
    private String commandLine;
    private String name;
    private int pid;
    private boolean isAttachSupported;


    public LocalJvmMachine(String address, String commandLine, int pid, boolean isAttachSupported) {
        this.address = address;
        this.commandLine = commandLine;
        this.pid = pid;
        this.isAttachSupported = isAttachSupported;
    }

    @Override
    public String id() {
        return String.valueOf(this.pid);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public boolean useCredentials() {
        return false;
    }

    private static String getDisplayName(String commandLine) {
        // trim the pathname of jar file if it's a jar
        String[] res = commandLine.split(" ", 2);
        if (res[0].endsWith(".jar")) {
            File jarfile = new File(res[0]);
            String displayName = jarfile.getName();
            if (res.length == 2) {
                displayName += " " + res[1];
            }
            return displayName;
        }
        return commandLine;
    }

    @Override
    public void open() throws IOException {
        if (this.address == null && (this.address = findAddressByPid(pid)) == null) {
            String msg = String.format("could not get address for jvm[%s][%s]", this.name, this.pid);
            throw new IOException(msg);
        }
    }

    @Override
    public void close() {

    }

    private String findAddressByPid(int pid) throws IOException {

        VirtualMachine vm = null;
        String address = null;
        try {
            String pidStr = String.valueOf(pid);
            try {
                vm = VirtualMachine.attach(pidStr);
                address = vm.getAgentProperties().getProperty(LOCAL_CONNECTOR_ADDRESS_PROP);
            } catch (AttachNotSupportedException e) {
                throw new IOException(e.getMessage(), e);
            }

            if (address != null)
                return address;
            System.out.println("load local jvm jmx address from management-agent.jar");
            //load jar from jre/lib/management-agent.jar or lib/management-agent
            String javaHome = vm.getSystemProperties().getProperty("java.home");
            String managementAgentPath = javaHome + File.separator + "jre" + File.separator +
                    "lib" + File.separator + "management-agent.jar";
            File managementAgentFile = new File(managementAgentPath);
            if (!managementAgentFile.exists()) {
                managementAgentPath = javaHome + File.separator + "lib" + File.separator +
                        "management-agent.jar";
                managementAgentFile = new File(managementAgentPath);
                if (!managementAgentFile.exists()) {
                    throw new IOException("Management agent not found");
                }
            }
            managementAgentPath = managementAgentFile.getCanonicalPath();
            try {
                vm.loadAgent(managementAgentPath, "com.sun.management.jmxremote");
            } catch (AgentLoadException e) {
                throw new IOException(e.getMessage(), e);
            } catch (AgentInitializationException e) {
                throw new IOException(e.getMessage(), e);
            }
            //address
            Properties agentProps = vm.getAgentProperties();
            address = (String) agentProps.get(LOCAL_CONNECTOR_ADDRESS_PROP);
        } finally {
            if (vm != null)
                vm.detach();
        }
        return address;
    }

    public String getAddress() {
        return address;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public String getName() {
        return name;
    }

    public int getPid() {
        return pid;
    }

    public boolean isAttachSupported() {
        return isAttachSupported;
    }

    @Override
    public String toString() {
        return "LocalJvmMachine{" +
                "address='" + address + '\'' +
                ", commandLine='" + commandLine + '\'' +
                ", name='" + name + '\'' +
                ", pid=" + pid +
                ", isAttachSupported=" + isAttachSupported +
                "} " + super.toString();
    }
}
