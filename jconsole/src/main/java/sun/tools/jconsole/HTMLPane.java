/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.tools.jconsole;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.DefaultCaret;

@SuppressWarnings("serial")
public class HTMLPane extends JEditorPane {
    private boolean hasSelection = false;

    public HTMLPane() {
        setContentType("text/html");
        setEditable(false);
        ((DefaultCaret) getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        addCaretListener(new CaretListener() {
            // Listen for selection changes
            public void caretUpdate(CaretEvent e) {
                setHasSelection(e.getDot() != e.getMark());
            }
        });
    }

    public synchronized boolean getHasSelection() {
        return hasSelection;
    }

    public synchronized void setHasSelection(boolean b) {
        hasSelection = b;
    }

    public void setText(String text) {
        // Apply update only if a selection is not active
        if (!getHasSelection()) {
            // JEditorPane does not automatically pick up fg color
            String textColor =
                    String.format("%06x", getForeground().getRGB() & 0xFFFFFF);
            super.setText("<html><body text=#" + textColor + ">" + text + "</body></html>");
        }
    }
}
