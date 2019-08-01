/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.tools.jconsole;

import javax.swing.*;
import java.awt.*;

import static sun.tools.jconsole.Resources.getText;
import static sun.tools.jconsole.Utilities.setAccessibleDescription;

@SuppressWarnings("serial")
public class VMInternalFrame extends MaximizableInternalFrame {
    private VMPanel vmPanel;

    public VMInternalFrame(VMPanel vmPanel) {
        super("", true, true, true, true);

        this.vmPanel = vmPanel;
        setAccessibleDescription(this,
                getText("VMInternalFrame.accessibleDescription"));
        getContentPane().add(vmPanel, BorderLayout.CENTER);
        pack();
        vmPanel.updateFrameTitle();
    }

    public VMPanel getVMPanel() {
        return vmPanel;
    }

    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        JDesktopPane desktop = getDesktopPane();
        if (desktop != null) {
            Dimension desktopSize = desktop.getSize();
            if (desktopSize.width > 0 && desktopSize.height > 0) {
                d.width = Math.min(desktopSize.width - 40, d.width);
                d.height = Math.min(desktopSize.height - 40, d.height);
            }
        }
        return d;
    }
}
