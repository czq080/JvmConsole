/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.tools.jconsole;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * A JConsole plugin class.  JConsole uses the
 * <a href="{@docRoot}/../../../../api/java/util/ServiceLoader.html">
 * service provider</a> mechanism to search the JConsole plugins.
 * Users can provide their JConsole plugins in a jar file
 * containing a file named
 *
 * <blockquote><pre>
 * META-INF/services/com.sun.tools.jconsole.JConsolePlugin</pre></blockquote>
 *
 * <p> This file contains one line for each plugin, for example,
 *
 * <blockquote><pre>
 * com.sun.example.JTop</pre></blockquote>
 * <p> which is the fully qualified class name of the class implementing
 * {@code JConsolePlugin}.
 *
 * <p> To load the JConsole plugins in JConsole, run:
 *
 * <blockquote><pre>
 * jconsole -pluginpath &lt;plugin-path&gt; </pre></blockquote>
 *
 * <p> where <tt>&lt;plugin-path&gt;</tt> specifies the paths of JConsole
 * plugins to look up which can be a directory or a jar file. Multiple
 * paths are separated by the path separator character of the platform.
 *
 * <p> When a new JConsole window is created for a connection,
 * an instance of each {@code JConsolePlugin} will be created.
 * The {@code JConsoleContext} object is not available at its
 * construction time.
 * JConsole will set the {@link JConsoleContext} object for
 * a plugin after the plugin object is created.  It will then
 * call its {@link #getTabs getTabs} method and add the returned
 * tabs to the JConsole window.
 *
 * @see <a href="{@docRoot}/../../../../api/java/util/ServiceLoader.html">
 * java.util.ServiceLoader</a>
 * @since 1.6
 */
public abstract class JConsolePlugin {
    private volatile JConsoleContext context = null;
    private List<PropertyChangeListener> listeners = null;

    /**
     * Constructor.
     */
    protected JConsolePlugin() {
    }

    /**
     * Sets the {@link JConsoleContext JConsoleContext} object representing
     * the connection to an application.  This method will be called
     * only once after the plugin is created and before the {@link #getTabs}
     * is called. The given {@code context} can be in any
     * {@link JConsoleContext#getConnectionState connection state} when
     * this method is called.
     *
     * @param context a {@code JConsoleContext} object
     */
    public final synchronized void setContext(JConsoleContext context) {
        this.context = context;
        if (listeners != null) {
            for (PropertyChangeListener l : listeners) {
                context.addPropertyChangeListener(l);
            }
            // throw away the listener list
            listeners = null;
        }
    }

    /**
     * Returns the {@link JConsoleContext JConsoleContext} object representing
     * the connection to an application.  This method may return <tt>null</tt>
     * if it is called before the {@link #setContext context} is initialized.
     *
     * @return the {@link JConsoleContext JConsoleContext} object representing
     * the connection to an application.
     */
    public final JConsoleContext getContext() {
        return context;
    }

    /**
     * Returns the tabs to be added in JConsole window.
     * <p>
     * The returned map contains one entry for each tab
     * to be added in the tabbed pane in a JConsole window with
     * the tab name as the key
     * and the {@link JPanel} object as the value.
     * This method returns an empty map if no tab is added by this plugin.
     * This method will be called from the <i>Event Dispatch Thread</i>
     * once at the new connection time.
     *
     * @return a map of a tab name and a {@link JPanel} object
     * representing the tabs to be added in the JConsole window;
     * or an empty map.
     */
    public abstract java.util.Map<String, JPanel> getTabs();

    /**
     * Returns a {@link SwingWorker} to perform
     * the GUI update for this plugin at the same interval
     * as JConsole updates the GUI.
     * <p>
     * JConsole schedules the GUI update at an interval specified
     * for a connection.  This method will be called at every
     * update to obtain a {@code SwingWorker} for each plugin.
     * <p>
     * JConsole will invoke the {@link SwingWorker#execute execute()}
     * method to schedule the returned {@code SwingWorker} for execution
     * if:
     * <ul>
     * <li> the <tt>SwingWorker</tt> object has not been executed
     * (i.e. the {@link SwingWorker#getState} method
     * returns {@link SwingWorker.StateValue#PENDING PENDING}
     * state); and</li>
     * <li> the <tt>SwingWorker</tt> object returned in the previous
     * update has completed the task if it was not <tt>null</tt>
     * (i.e. the {@link SwingWorker#isDone SwingWorker.isDone} method
     * returns <tt>true</tt>).</li>
     * </ul>
     * <br>
     * Otherwise, <tt>SwingWorker</tt> object will not be scheduled to work.
     *
     * <p>
     * A plugin can schedule its own GUI update and this method
     * will return <tt>null</tt>.
     *
     * @return a <tt>SwingWorker</tt> to perform the GUI update; or
     * <tt>null</tt>.
     */
    public abstract SwingWorker<?, ?> newSwingWorker();

    /**
     * Dispose this plugin. This method is called by JConsole to inform
     * that this plugin will be discarded and that it should free
     * any resources that it has allocated.
     * The {@link #getContext JConsoleContext} can be in any
     * {@link JConsoleContext#getConnectionState connection state} when
     * this method is called.
     */
    public void dispose() {
        // Default nop implementation
    }

    /**
     * Adds a {@link PropertyChangeListener PropertyChangeListener}
     * to the {@link #getContext JConsoleContext} object for this plugin.
     * This method is a convenient method for this plugin to register
     * a listener when the {@code JConsoleContext} object may or
     * may not be available.
     *
     * <p>For example, a plugin constructor can
     * call this method to register a listener to listen to the
     * {@link JConsoleContext.ConnectionState connectionState}
     * property changes and the listener will be added to the
     * {@link JConsoleContext#addPropertyChangeListener JConsoleContext}
     * object when it is available.
     *
     * @param listener The {@code PropertyChangeListener} to be added
     * @throws NullPointerException if {@code listener} is {@code null}.
     */
    public final void addContextPropertyChangeListener(PropertyChangeListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener is null");
        }

        if (context == null) {
            // defer registration of the listener until setContext() is called 
            synchronized (this) {
                // check again if context is not set
                if (context == null) {
                    // maintain a listener list to be added later
                    if (listeners == null) {
                        listeners = new ArrayList<PropertyChangeListener>();
                    }
                    listeners.add(listener);
                    return;
                }
            }
        }
        context.addPropertyChangeListener(listener);
    }

    /**
     * Removes a {@link PropertyChangeListener PropertyChangeListener}
     * from the listener list of the {@link #getContext JConsoleContext}
     * object for this plugin.
     * If {@code listener} was never added, no exception is
     * thrown and no action is taken.
     *
     * @param listener the {@code PropertyChangeListener} to be removed
     * @throws NullPointerException if {@code listener} is {@code null}.
     */
    public final void removeContextPropertyChangeListener(PropertyChangeListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener is null");
        }

        if (context == null) {
            // defer registration of the listener until setContext() is called 
            synchronized (this) {
                // check again if context is not set
                if (context == null) {
                    if (listeners != null) {
                        listeners.remove(listener);
                    }
                    return;
                }
            }
        }
        context.removePropertyChangeListener(listener);
    }
}
