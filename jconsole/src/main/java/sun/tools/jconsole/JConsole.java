/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.tools.jconsole;

import com.sun.tools.jconsole.JConsolePlugin;
import sun.net.util.IPAddressUtil;

import javax.net.ssl.SSLHandshakeException;
import javax.security.auth.login.FailedLoginException;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.plaf.BorderUIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import static sun.tools.jconsole.Resources.getMnemonicInt;
import static sun.tools.jconsole.Resources.getText;
import static sun.tools.jconsole.Utilities.setAccessibleDescription;

@SuppressWarnings("serial")
public class JConsole extends JFrame
        implements ActionListener, InternalFrameListener {

    public final static String ROOT_URL =
            "service:jmx:";
    private final static String title =
            Resources.getText("Java Monitoring & Management Console");
    private static final boolean localAttachmentSupported;
    static /*final*/ boolean IS_GTK;
    static /*final*/ boolean IS_WIN;
    static boolean debug;
    private static int updateInterval = 4000;
    private static String pluginPath = "";
    private static ServiceLoader<JConsolePlugin> pluginService = null;

    static {
        // Apply the system L&F if it is GTK or Windows, and
        // the L&F is not specified using a system property.
        if (System.getProperty("swing.defaultlaf") == null) {
            String systemLaF = UIManager.getSystemLookAndFeelClassName();
            if (systemLaF.equals("com.sun.java.swing.plaf.gtk.GTKLookAndFeel") ||
                    systemLaF.equals("com.sun.java.swing.plaf.windows.WindowsLookAndFeel")) {

                try {
                    UIManager.setLookAndFeel(systemLaF);
                } catch (Exception e) {
                    System.err.println(Resources.getText("JConsole: ", e.getMessage()));
                }
            }
        }

        updateLafValues();
    }

    static {
        boolean supported;
        try {
            Class.forName("com.sun.tools.attach.VirtualMachine");
            Class.forName("sun.management.ConnectorAddressLink");
            supported = true;
        } catch (NoClassDefFoundError x) {
            x.printStackTrace();
            supported = false;
        } catch (ClassNotFoundException x) {
            x.printStackTrace();
            supported = false;
        }
        localAttachmentSupported = supported;
    }

    private JMenuBar menuBar;
    private JMenuItem hotspotMI, connectMI, exitMI;
    private WindowMenu windowMenu;
    private JMenuItem tileMI, cascadeMI, minimizeAllMI, restoreAllMI;
    private JMenuItem userGuideMI, aboutMI;
    private JButton connectButton;
    private JDesktopPane desktop;
    private ConnectDialog connectDialog;
    private CreateMBeanDialog createDialog;
    private ArrayList<VMInternalFrame> windows =
            new ArrayList<VMInternalFrame>();
    private int frameLoc = 5;

    public JConsole(boolean hotspot) {
        super(title);

        setRootPane(new FixedJRootPane());
        setAccessibleDescription(this,
                getText("JConsole.accessibleDescription"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        // TODO: Use Actions !

        JMenu connectionMenu = new JMenu(getText("Connection"));
        connectionMenu.setMnemonic(getMnemonicInt("Connection"));
        menuBar.add(connectionMenu);
        if (hotspot) {
            hotspotMI = new JMenuItem(getText("Hotspot MBeans..."));
            hotspotMI.setMnemonic(getMnemonicInt("Hotspot MBeans..."));
            hotspotMI.setAccelerator(KeyStroke.
                    getKeyStroke(KeyEvent.VK_H,
                            InputEvent.CTRL_MASK));
            hotspotMI.addActionListener(this);
            connectionMenu.add(hotspotMI);

            connectionMenu.addSeparator();
        }

        connectMI = new JMenuItem(Resources.getText("New Connection..."));
        connectMI.setMnemonic(getMnemonicInt("New Connection..."));
        connectMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,
                InputEvent.CTRL_MASK));
        connectMI.addActionListener(this);
        connectionMenu.add(connectMI);

        connectionMenu.addSeparator();

        exitMI = new JMenuItem(Resources.getText("Exit"));
        exitMI.setMnemonic(getMnemonicInt("Exit"));
        exitMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4,
                InputEvent.ALT_MASK));
        exitMI.addActionListener(this);
        connectionMenu.add(exitMI);


        JMenu helpMenu = new JMenu(getText("HelpMenu.title"));
        helpMenu.setMnemonic(getMnemonicInt("HelpMenu.title"));
        menuBar.add(helpMenu);

        if (AboutDialog.isBrowseSupported()) {
            userGuideMI = new JMenuItem(getText("HelpMenu.UserGuide.title"));
            userGuideMI.setMnemonic(getMnemonicInt("HelpMenu.UserGuide.title"));
            userGuideMI.addActionListener(this);
            helpMenu.add(userGuideMI);
            helpMenu.addSeparator();
        }
        aboutMI = new JMenuItem(getText("HelpMenu.About.title"));
        aboutMI.setMnemonic(getMnemonicInt("HelpMenu.About.title"));
        aboutMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        aboutMI.addActionListener(this);
        helpMenu.add(aboutMI);
    }

    static void updateLafValues() {
        String lafName = UIManager.getLookAndFeel().getClass().getName();
        IS_GTK = lafName.equals("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        IS_WIN = lafName.equals("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

        //BorderedComponent.updateLafValues();
    }

    static boolean isValidRemoteString(String txt) {
        boolean valid = false;
        if (txt != null) {
            txt = txt.trim();
            if (txt.startsWith(ROOT_URL)) {
                if (txt.length() > ROOT_URL.length()) {
                    valid = true;
                }
            } else {
                //---------------------------------------
                // Supported host and port combinations:
                //     hostname:port
                //     IPv4Address:port
                //     [IPv6Address]:port
                //---------------------------------------

                // Is literal IPv6 address?
                //
                if (txt.startsWith("[")) {
                    int index = txt.indexOf("]:");
                    if (index != -1) {
                        // Extract literal IPv6 address
                        //
                        String address = txt.substring(1, index);
                        if (IPAddressUtil.isIPv6LiteralAddress(address)) {
                            // Extract port
                            //
                            try {
                                String portStr = txt.substring(index + 2);
                                int port = Integer.parseInt(portStr);
                                if (port >= 0 && port <= 0xFFFF) {
                                    valid = true;
                                }
                            } catch (NumberFormatException ex) {
                                valid = false;
                            }
                        }
                    }
                } else {
                    String[] s = txt.split(":");
                    if (s.length == 2) {
                        try {
                            int port = Integer.parseInt(s[1]);
                            if (port >= 0 && port <= 0xFFFF) {
                                valid = true;
                            }
                        } catch (NumberFormatException ex) {
                            valid = false;
                        }
                    }
                }
            }
        }
        return valid;
    }

    private static void usage() {
        System.err.println(Resources.getText("zz usage text", "jconsole"));
    }

    private static void mainInit(final List<String> urls,
                                 final List<String> hostNames,
                                 final List<Integer> ports,
                                 final List<LocalVirtualMachine> vmids,
                                 final ProxyClient proxyClient,
                                 final boolean noTile,
                                 final boolean hotspot) {


        // Always create Swing GUI on the Event Dispatching Thread
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JConsole jConsole = new JConsole(hotspot);

                // Center the window on screen, taking into account screen
                // size and insets.
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                GraphicsConfiguration gc = jConsole.getGraphicsConfiguration();
                Dimension scrSize = toolkit.getScreenSize();
                Insets scrInsets = toolkit.getScreenInsets(gc);
                Rectangle scrBounds =
                        new Rectangle(scrInsets.left, scrInsets.top,
                                scrSize.width - scrInsets.left - scrInsets.right,
                                scrSize.height - scrInsets.top - scrInsets.bottom);
                int w = Math.min(900, scrBounds.width);
                int h = Math.min(750, scrBounds.height);
                jConsole.setBounds(scrBounds.x + (scrBounds.width - w) / 2,
                        scrBounds.y + (scrBounds.height - h) / 2,
                        w, h);

                jConsole.setVisible(true);
                jConsole.createMDI();

                for (int i = 0; i < hostNames.size(); i++) {
                    jConsole.addHost(hostNames.get(i), ports.get(i),
                            null, null,
                            (i == hostNames.size() - 1) && !noTile);
                }

                for (int i = 0; i < urls.size(); i++) {
                    jConsole.addUrl(urls.get(i),
                            null,
                            null,
                            (i == urls.size() - 1) && !noTile);
                }

                for (int i = 0; i < vmids.size(); i++) {
                    jConsole.addVmid(vmids.get(i),
                            (i == vmids.size() - 1) && !noTile);
                }

                if (vmids.size() == 0 &&
                        hostNames.size() == 0 &&
                        urls.size() == 0) {
                    jConsole.showConnectDialog(null,
                            null,
                            0,
                            null,
                            null,
                            null);
                }
            }
        });
    }

    public static void main(String[] args) {
        boolean noTile = false, hotspot = false;
        int argIndex = 0;
        ProxyClient proxyClient = null;
        System.out.println("run");
        if (System.getProperty("jconsole.showOutputViewer") != null) {
            OutputViewer.init();
        }

        while (args.length - argIndex > 0 && args[argIndex].startsWith("-")) {
            String arg = args[argIndex++];
            if (arg.equals("-h") ||
                    arg.equals("-help") ||
                    arg.equals("-?")) {

                usage();
                return;
            } else if (arg.startsWith("-interval=")) {
                try {
                    updateInterval = Integer.parseInt(arg.substring(10)) *
                            1000;
                } catch (NumberFormatException ex) {
                    usage();
                    return;
                }
            } else if (arg.equals("-pluginpath")) {
                if (argIndex < args.length && !args[argIndex].startsWith("-")) {
                    pluginPath = args[argIndex++];
                } else {
                    // Invalid argument
                    usage();
                    return;
                }
            } else if (arg.equals("-notile")) {
                noTile = true;
            } else if (arg.equals("-version")) {
                Version.print(System.err);
                return;
            } else if (arg.equals("-debug")) {
                debug = true;
            } else if (arg.equals("-fullversion")) {
                Version.printFullVersion(System.err);
                return;
            } else {
                // Unknown switch
                usage();
                return;
            }
        }

        if (System.getProperty("jconsole.showUnsupported") != null) {
            hotspot = true;
        }

        List<String> urls = new ArrayList<String>();
        List<String> hostNames = new ArrayList<String>();
        List<Integer> ports = new ArrayList<Integer>();
        List<LocalVirtualMachine> vms = new ArrayList<LocalVirtualMachine>();

        for (int i = argIndex; i < args.length; i++) {
            String arg = args[i];
            if (isValidRemoteString(arg)) {
                if (arg.startsWith(ROOT_URL)) {
                    urls.add(arg);
                } else if (arg.matches(".*:[0-9]*")) {
                    int p = arg.lastIndexOf(':');
                    hostNames.add(arg.substring(0, p));
                    try {
                        ports.add(Integer.parseInt(arg.substring(p + 1)));
                    } catch (NumberFormatException ex) {
                        usage();
                        return;
                    }
                }
            } else {
                if (!isLocalAttachAvailable()) {
                    System.err.println("Local process monitoring is not supported");
                    return;
                }
                try {
                    int vmid = Integer.parseInt(arg);
                    LocalVirtualMachine lvm =
                            LocalVirtualMachine.getLocalVirtualMachine(vmid);
                    if (lvm == null) {
                        System.err.println("Invalid process id:" + vmid);
                        return;
                    }
                    vms.add(lvm);
                } catch (NumberFormatException ex) {
                    usage();
                    return;
                }
            }
        }

        mainInit(urls, hostNames, ports, vms, proxyClient, noTile, hotspot);
    }

    public static boolean isDebug() {
        return debug;
    }

    private static void dbgStackTrace(Exception ex) {
        if (debug) {
            ex.printStackTrace();
        }
    }

    public static boolean isLocalAttachAvailable() {
        return localAttachmentSupported;
    }

    // Return a list of newly instantiated JConsolePlugin objects
    static synchronized List<JConsolePlugin> getPlugins() {
        if (pluginService == null) {
            // First time loading and initializing the plugins
            initPluginService(pluginPath);
        } else {
            // reload the plugin so that new instances will be created
            pluginService.reload();
        }

        List<JConsolePlugin> plugins = new ArrayList<JConsolePlugin>();
        for (JConsolePlugin p : pluginService) {
            plugins.add(p);
        }
        return plugins;
    }

    private static void initPluginService(String pluginPath) {
        if (pluginPath.length() > 0) {
            try {
                ClassLoader pluginCL = new URLClassLoader(pathToURLs(pluginPath));
                ServiceLoader<JConsolePlugin> plugins =
                        ServiceLoader.load(JConsolePlugin.class, pluginCL);
                // validate all plugins
                for (JConsolePlugin p : plugins) {
                    if (isDebug()) {
                        System.out.println("Plugin " + p.getClass() + " loaded.");
                    }
                }
                pluginService = plugins;
            } catch (ServiceConfigurationError e) {
                // Error occurs during initialization of plugin
                System.out.println(Resources.getText("Fail to load plugin",
                        e.getMessage()));
            } catch (MalformedURLException e) {
                if (JConsole.isDebug()) {
                    e.printStackTrace();
                }
                System.out.println(Resources.getText("Invalid plugin path",
                        e.getMessage()));
            }
        }

        if (pluginService == null) {
            initEmptyPlugin();
        }
    }

    private static void initEmptyPlugin() {
        ClassLoader pluginCL = new URLClassLoader(new URL[0]);
        pluginService = ServiceLoader.load(JConsolePlugin.class, pluginCL);
    }

    /**
     * Utility method for converting a search path string to an array
     * of directory and JAR file URLs.
     *
     * @param path the search path string
     * @return the resulting array of directory and JAR file URLs
     */
    private static URL[] pathToURLs(String path) throws MalformedURLException {
        String[] names = path.split(File.pathSeparator);
        URL[] urls = new URL[names.length];
        int count = 0;
        for (String f : names) {
            URL url = fileToURL(new File(f));
            urls[count++] = url;
        }
        return urls;
    }

    /**
     * Returns the directory or JAR file URL corresponding to the specified
     * local file name.
     *
     * @param file the File object
     * @return the resulting directory or JAR file URL, or null if unknown
     */
    private static URL fileToURL(File file) throws MalformedURLException {
        String name;
        try {
            name = file.getCanonicalPath();
        } catch (IOException e) {
            name = file.getAbsolutePath();
        }
        name = name.replace(File.separatorChar, '/');
        if (!name.startsWith("/")) {
            name = "/" + name;
        }
        // If the file does not exist, then assume that it's a directory
        if (!file.isFile()) {
            name = name + "/";
        }
        return new URL("file", "", name);
    }

    public JDesktopPane getDesktopPane() {
        return desktop;
    }

    public List<VMInternalFrame> getInternalFrames() {
        return windows;
    }

    private void createMDI() {
        // Restore title - we now show connection name on internal frames
        setTitle(title);

        Container cp = getContentPane();
        Component oldCenter =
                ((BorderLayout) cp.getLayout()).
                        getLayoutComponent(BorderLayout.CENTER);

        windowMenu = new WindowMenu(Resources.getText("Window"));
        windowMenu.setMnemonic(getMnemonicInt("Window"));
        // Add Window menu before Help menu
        menuBar.add(windowMenu, menuBar.getComponentCount() - 1);

        desktop = new JDesktopPane();
        desktop.setBackground(new Color(235, 245, 255));

        cp.add(desktop, BorderLayout.CENTER);

        if (oldCenter instanceof VMPanel) {
            addFrame((VMPanel) oldCenter);
        }
    }

    public void actionPerformed(ActionEvent ev) {
        Object src = ev.getSource();
        if (src == hotspotMI) {
            showCreateMBeanDialog();
        }

        if (src == connectButton || src == connectMI) {
            VMPanel vmPanel = null;
            JInternalFrame vmIF = desktop.getSelectedFrame();
            if (vmIF instanceof VMInternalFrame) {
                vmPanel = ((VMInternalFrame) vmIF).getVMPanel();
            }
            String hostName = "";
            String url = "";
            if (vmPanel != null) {
                hostName = vmPanel.getHostName();
                if (vmPanel.getUrl() != null)
                    url = vmPanel.getUrl();
            }
            showConnectDialog(url, hostName, 0, null, null, null);
        } else if (src == tileMI) {
            tileWindows();
        } else if (src == cascadeMI) {
            cascadeWindows();
        } else if (src == minimizeAllMI) {
            for (VMInternalFrame vmIF : windows) {
                try {
                    vmIF.setIcon(true);
                } catch (PropertyVetoException ex) {
                    // Ignore
                }
            }
        } else if (src == restoreAllMI) {
            for (VMInternalFrame vmIF : windows) {
                try {
                    vmIF.setIcon(false);
                } catch (PropertyVetoException ex) {
                    // Ignore
                }
            }
        } else if (src == exitMI) {
            System.exit(0);
        } else if (src == userGuideMI) {
            AboutDialog.browseUserGuide(this);
        } else if (src == aboutMI) {
            AboutDialog.showAboutDialog(this);
        } else if (src instanceof JMenuItem) {
            JMenuItem mi = (JMenuItem) src;
            VMInternalFrame vmIF = (VMInternalFrame) mi.
                    getClientProperty("JConsole.vmIF");
            if (vmIF != null) {
                try {
                    vmIF.setIcon(false);
                    vmIF.setSelected(true);
                } catch (PropertyVetoException ex) {
                    // Ignore
                }
                vmIF.moveToFront();
            }
        }
    }

    public void tileWindows() {
        int w = -1;
        int h = -1;
        int n = 0;
        for (VMInternalFrame vmIF : windows) {
            if (!vmIF.isIcon()) {
                n++;
                if (w == -1) {
                    try {
                        vmIF.setMaximum(true);
                        w = vmIF.getWidth();
                        h = vmIF.getHeight();
                    } catch (PropertyVetoException ex) {
                        // Ignore
                    }
                }
            }
        }
        if (n > 0 && w > 0 && h > 0) {
            int rows = (int) Math.ceil(Math.sqrt(n));
            int cols = n / rows;
            if (rows * cols < n) cols++;
            int x = 0;
            int y = 0;
            w /= cols;
            h /= rows;
            int col = 0;
            for (VMInternalFrame vmIF : windows) {
                if (!vmIF.isIcon()) {
                    try {
                        vmIF.setMaximum(n == 1);
                    } catch (PropertyVetoException ex) {
                        // Ignore
                    }
                    if (n > 1) {
                        vmIF.setBounds(x, y, w, h);
                    }
                    if (col < cols - 1) {
                        col++;
                        x += w;
                    } else {
                        col = 0;
                        x = 0;
                        y += h;
                    }
                }
            }
        }
    }

    public void cascadeWindows() {
        int n = 0;
        int w = -1;
        int h = -1;
        for (VMInternalFrame vmIF : windows) {
            if (!vmIF.isIcon()) {
                try {
                    vmIF.setMaximum(false);
                } catch (PropertyVetoException ex) {
                    // Ignore
                }
                n++;
                vmIF.pack();
                if (w == -1) {
                    try {
                        w = vmIF.getWidth();
                        h = vmIF.getHeight();
                        vmIF.setMaximum(true);
                        w = vmIF.getWidth() - w;
                        h = vmIF.getHeight() - h;
                        vmIF.pack();
                    } catch (PropertyVetoException ex) {
                        // Ignore
                    }
                }
            }
        }
        int x = 0;
        int y = 0;
        int dX = (n > 1) ? (w / (n - 1)) : 0;
        int dY = (n > 1) ? (h / (n - 1)) : 0;
        for (VMInternalFrame vmIF : windows) {
            if (!vmIF.isIcon()) {
                vmIF.setLocation(x, y);
                vmIF.moveToFront();
                x += dX;
                y += dY;
            }
        }
    }


    // InternalFrameListener interface

    // Call on EDT
    void addHost(String hostName, int port,
                 String userName, String password) {
        addHost(hostName, port, userName, password, false);
    }

    // Call on EDT
    void addVmid(LocalVirtualMachine lvm) {
        addVmid(lvm, false);
    }

    // Call on EDT
    void addVmid(final LocalVirtualMachine lvm, final boolean tile) {
        new Thread("JConsole.addVmid") {
            public void run() {
                try {
                    addProxyClient(ProxyClient.getProxyClient(lvm), tile);
                } catch (final SecurityException ex) {
                    failed(ex, null, null, null);
                } catch (final IOException ex) {
                    failed(ex, null, null, null);
                }
            }
        }.start();
    }

    // Call on EDT
    void addUrl(final String url,
                final String userName,
                final String password,
                final boolean tile) {
        new Thread("JConsole.addUrl") {
            public void run() {
                try {
                    addProxyClient(ProxyClient.getProxyClient(url, userName, password),
                            tile);
                } catch (final MalformedURLException ex) {
                    failed(ex, url, userName, password);
                } catch (final SecurityException ex) {
                    failed(ex, url, userName, password);
                } catch (final IOException ex) {
                    failed(ex, url, userName, password);
                }
            }
        }.start();
    }

    // Call on EDT
    void addHost(final String hostName, final int port,
                 final String userName, final String password,
                 final boolean tile) {
        new Thread("JConsole.addHost") {
            public void run() {
                try {
                    addProxyClient(ProxyClient.getProxyClient(hostName, port,
                            userName, password),
                            tile);
                } catch (final IOException ex) {
                    dbgStackTrace(ex);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            showConnectDialog(null, hostName, port,
                                    userName, password, errorMessage(ex));
                        }
                    });
                }
            }
        }.start();
    }

    // Call on worker thread
    void addProxyClient(final ProxyClient proxyClient, final boolean tile) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                VMPanel vmPanel = new VMPanel(proxyClient, updateInterval);
                addFrame(vmPanel);

                if (tile) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            tileWindows();
                        }
                    });
                }
                vmPanel.connect();
            }
        });
    }

    // Call on worker thread
    private void failed(final Exception ex,
                        final String url,
                        final String userName,
                        final String password) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                dbgStackTrace(ex);
                showConnectDialog(url,
                        null,
                        -1,
                        userName,
                        password,
                        errorMessage(ex));
            }
        });
    }

    private VMInternalFrame addFrame(VMPanel vmPanel) {
        final VMInternalFrame vmIF = new VMInternalFrame(vmPanel);

        for (VMInternalFrame f : windows) {
            try {
                f.setMaximum(false);
            } catch (PropertyVetoException ex) {
                // Ignore
            }
        }
        desktop.add(vmIF);

        vmIF.setLocation(frameLoc, frameLoc);
        frameLoc += 30;
        vmIF.setVisible(true);
        windows.add(vmIF);
        if (windows.size() == 1) {
            try {
                vmIF.setMaximum(true);
            } catch (PropertyVetoException ex) {
                // Ignore
            }
        }
        vmIF.addInternalFrameListener(this);
        windowMenu.add(vmIF);

        return vmIF;
    }

    private void showConnectDialog(String url,
                                   String hostName,
                                   int port,
                                   String userName,
                                   String password,
                                   String msg) {
        if (connectDialog == null) {
            connectDialog = new ConnectDialog(this);
        }
        connectDialog.setConnectionParameters(url,
                hostName,
                port,
                userName,
                password,
                msg);

        connectDialog.refresh();
        connectDialog.setVisible(true);
        try {
            // Bring to front of other dialogs
            connectDialog.setSelected(true);
        } catch (PropertyVetoException e) {
        }
    }

    private void showCreateMBeanDialog() {
        if (createDialog == null) {
            createDialog = new CreateMBeanDialog(this);
        }
        createDialog.setVisible(true);
        try {
            // Bring to front of other dialogs
            createDialog.setSelected(true);
        } catch (PropertyVetoException e) {
        }
    }

    private void removeVMInternalFrame(VMInternalFrame vmIF) {
        windowMenu.remove(vmIF);
        desktop.remove(vmIF);
        desktop.repaint();
        vmIF.getVMPanel().cleanUp();
        vmIF.dispose();
    }

    private boolean isProxyClientUsed(ProxyClient client) {
        for (VMInternalFrame frame : windows) {
            ProxyClient cli = frame.getVMPanel().getProxyClient(false);
            if (client == cli)
                return true;
        }
        return false;
    }

    private String errorMessage(Exception ex) {
        String msg = Resources.getText("Connection failed");
        if (ex instanceof IOException || ex instanceof SecurityException) {
            Throwable cause = null;
            Throwable c = ex.getCause();
            while (c != null) {
                cause = c;
                c = c.getCause();
            }
            if (cause instanceof ConnectException) {
                return msg + ": " + cause.getMessage();
            } else if (cause instanceof UnknownHostException) {
                return Resources.getText("Unknown Host", cause.getMessage());
            } else if (cause instanceof NoRouteToHostException) {
                return msg + ": " + cause.getMessage();
            } else if (cause instanceof FailedLoginException) {
                return msg + ": " + cause.getMessage();
            } else if (cause instanceof SSLHandshakeException) {
                return msg + ": " + cause.getMessage();
            }
        } else if (ex instanceof MalformedURLException) {
            return Resources.getText("Invalid URL", ex.getMessage());
        }
        return msg + ": " + ex.getMessage();
    }

    public void internalFrameClosing(InternalFrameEvent e) {
        VMInternalFrame vmIF = (VMInternalFrame) e.getInternalFrame();
        removeVMInternalFrame(vmIF);
        windows.remove(vmIF);
        ProxyClient client = vmIF.getVMPanel().getProxyClient(false);
        if (!isProxyClientUsed(client))
            client.markAsDead();
        if (windows.size() == 0) {
            showConnectDialog("", "", 0, null, null, null);
        }
    }

    public void internalFrameOpened(InternalFrameEvent e) {
    }

    public void internalFrameClosed(InternalFrameEvent e) {
    }

    public void internalFrameIconified(InternalFrameEvent e) {
    }

    public void internalFrameDeiconified(InternalFrameEvent e) {
    }

    public void internalFrameActivated(InternalFrameEvent e) {
    }

    public void internalFrameDeactivated(InternalFrameEvent e) {
    }

    private static class FixedJRootPane extends JRootPane {
        public void updateUI() {
            updateLafValues();
            super.updateUI();
        }

        /**
         * The revalidate method seems to be the only one that gets
         * called whenever there is a change of L&F or change of theme
         * in Windows L&F and GTK L&F.
         */
        @Override
        public void revalidate() {
            // Workaround for Swing bug where the titledborder in both
            // GTK and Windows L&F's use calculated colors instead of
            // the highlight/shadow colors from the theme.
            //
            // Putting null removes any previous override and causes a
            // fallback to the current L&F's value.
            UIManager.put("TitledBorder.border", null);
            Border border = UIManager.getBorder("TitledBorder.border");
            if (border instanceof BorderUIResource.EtchedBorderUIResource) {
                Color highlight = UIManager.getColor("ToolBar.highlight");
                Color shadow = UIManager.getColor("ToolBar.shadow");
                border = new BorderUIResource.EtchedBorderUIResource(highlight,
                        shadow);
                UIManager.put("TitledBorder.border", border);
            }

            if (IS_GTK) {
                // Workaround for Swing bug where the titledborder in
                // GTK L&F use hardcoded color and font for the title
                // instead of getting them from the theme.
                UIManager.put("TitledBorder.titleColor",
                        UIManager.getColor("Label.foreground"));
                UIManager.put("TitledBorder.font",
                        UIManager.getFont("Label.font"));
            }
            super.revalidate();
        }
    }

    private class WindowMenu extends JMenu {
        VMInternalFrame[] windowMenuWindows = new VMInternalFrame[0];
        int separatorPosition;

        // The width value of viewR is used to truncate long menu items.
        // The rest are placeholders and are ignored for this purpose.
        Rectangle viewR = new Rectangle(0, 0, 400, 20);
        Rectangle textR = new Rectangle(0, 0, 0, 0);
        Rectangle iconR = new Rectangle(0, 0, 0, 0);

        WindowMenu(String text) {
            super(text);

            cascadeMI = new JMenuItem(Resources.getText("Cascade"));
            cascadeMI.setMnemonic(getMnemonicInt("Cascade"));
            cascadeMI.addActionListener(JConsole.this);
            add(cascadeMI);

            tileMI = new JMenuItem(Resources.getText("Tile"));
            tileMI.setMnemonic(getMnemonicInt("Tile"));
            tileMI.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                    InputEvent.CTRL_MASK));
            tileMI.addActionListener(JConsole.this);
            add(tileMI);

            minimizeAllMI = new JMenuItem(Resources.getText("Minimize All"));
            minimizeAllMI.setMnemonic(getMnemonicInt("Minimize All"));
            minimizeAllMI.addActionListener(JConsole.this);
            add(minimizeAllMI);

            restoreAllMI = new JMenuItem(Resources.getText("Restore All"));
            restoreAllMI.setMnemonic(getMnemonicInt("Restore All"));
            restoreAllMI.addActionListener(JConsole.this);
            add(restoreAllMI);

            separatorPosition = getMenuComponentCount();
        }

        private void add(VMInternalFrame vmIF) {
            if (separatorPosition == getMenuComponentCount()) {
                addSeparator();
            }

            int index = -1;
            int position = separatorPosition + 1;
            int n = windowMenuWindows.length;

            for (int i = 0; i < n; i++) {
                if (windowMenuWindows[i] != null) {
                    // Slot is in use, try next
                    position++;
                } else {
                    // Found a free slot
                    index = i;
                    break;
                }
            }

            if (index == -1) {
                // Create a slot at the end
                VMInternalFrame[] newArray = new VMInternalFrame[n + 1];
                System.arraycopy(windowMenuWindows, 0, newArray, 0, n);
                windowMenuWindows = newArray;
                index = n;
            }

            windowMenuWindows[index] = vmIF;

            String indexString = "" + (index + 1);
            String vmName = vmIF.getVMPanel().getDisplayName();
            // Maybe truncate menu item string and end with "..."
            String text =
                    SwingUtilities.layoutCompoundLabel(this,
                            getGraphics().getFontMetrics(getFont()),
                            indexString + " " + vmName,
                            null, 0, 0, 0, 0,
                            viewR, iconR, textR, 0);
            JMenuItem mi = new JMenuItem(text);
            if (text.endsWith("...")) {
                mi.setToolTipText(vmName);
            }

            // Set mnemonic using last digit of number
            int nDigits = indexString.length();
            mi.setMnemonic(indexString.charAt(nDigits - 1));
            mi.setDisplayedMnemonicIndex(nDigits - 1);

            mi.putClientProperty("JConsole.vmIF", vmIF);
            mi.addActionListener(JConsole.this);
            vmIF.putClientProperty("JConsole.menuItem", mi);
            add(mi, position);
        }

        private void remove(VMInternalFrame vmIF) {
            for (int i = 0; i < windowMenuWindows.length; i++) {
                if (windowMenuWindows[i] == vmIF) {
                    windowMenuWindows[i] = null;
                }
            }
            JMenuItem mi = (JMenuItem) vmIF.getClientProperty("JConsole.menuItem");
            remove(mi);
            mi.putClientProperty("JConsole.vmIF", null);
            vmIF.putClientProperty("JConsole.menuItem", null);

            if (separatorPosition == getMenuComponentCount() - 1) {
                remove(getMenuComponent(getMenuComponentCount() - 1));
            }
        }
    }
}
