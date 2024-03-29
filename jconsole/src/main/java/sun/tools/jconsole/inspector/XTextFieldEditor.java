/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.tools.jconsole.inspector;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.EventListenerList;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.EventObject;

@SuppressWarnings("serial")
public class XTextFieldEditor extends XTextField implements TableCellEditor {

    protected EventListenerList listenerList = new EventListenerList();
    protected ChangeEvent changeEvent = new ChangeEvent(this);

    private FocusListener editorFocusListener = new FocusAdapter() {
        public void focusLost(FocusEvent e) {
            fireEditingStopped();
        }
    };

    public XTextFieldEditor() {
        super();
        textField.addFocusListener(editorFocusListener);
    }

    //edition stopped ou JMenuItem selection & JTextField selection
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        if ((e.getSource() instanceof JMenuItem) ||
                (e.getSource() instanceof JTextField)) {
            fireEditingStopped();
        }
    }

    //edition stopped on drag & drop success
    protected void dropSuccess() {
        fireEditingStopped();
    }

    //TableCellEditor implementation

    public void addCellEditorListener(CellEditorListener listener) {
        listenerList.add(CellEditorListener.class, listener);
    }

    public void removeCellEditorListener(CellEditorListener listener) {
        listenerList.remove(CellEditorListener.class, listener);
    }

    protected void fireEditingStopped() {
        CellEditorListener listener;
        Object[] listeners = listenerList.getListenerList();
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] == CellEditorListener.class) {
                listener = (CellEditorListener) listeners[i + 1];
                listener.editingStopped(changeEvent);
            }
        }
    }

    protected void fireEditingCanceled() {
        CellEditorListener listener;
        Object[] listeners = listenerList.getListenerList();
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] == CellEditorListener.class) {
                listener = (CellEditorListener) listeners[i + 1];
                listener.editingCanceled(changeEvent);
            }
        }
    }

    public void cancelCellEditing() {
        fireEditingCanceled();
    }

    public boolean stopCellEditing() {
        fireEditingStopped();
        return true;
    }

    public boolean isCellEditable(EventObject event) {
        return true;
    }

    public boolean shouldSelectCell(EventObject event) {
        return true;
    }

    public Object getCellEditorValue() {
        Object object = getValue();

        if (object instanceof XObject) {
            return ((XObject) object).getObject();
        } else {
            return object;
        }
    }

    public Component getTableCellEditorComponent(JTable table,
                                                 Object value,
                                                 boolean isSelected,
                                                 int row,
                                                 int column) {
        String className;
        if (table instanceof XTable) {
            XTable mytable = (XTable) table;
            className = mytable.getClassName(row);
        } else {
            className = String.class.getName();
        }
        try {
            init(value, Utils.getClass(className));
        } catch (Exception e) {
        }

        return this;
    }

}
	
	

    
	

    

