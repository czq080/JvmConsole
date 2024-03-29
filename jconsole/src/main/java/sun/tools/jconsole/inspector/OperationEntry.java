/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.tools.jconsole.inspector;


// java import

import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.swing.*;
import java.awt.*;

//
// swing import
//
// jmx import
//


@SuppressWarnings("serial")
public class OperationEntry extends JPanel {
    private MBeanOperationInfo operation;
    private JComboBox sigs;
    private Dimension preferredSize;
    private XTextField[] inputs;

    public OperationEntry(MBeanOperationInfo operation,
                          boolean isCallable,
                          JButton button,
                          XOperations xoperations) {
        super(new BorderLayout());
        this.operation = operation;
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setPanel(isCallable, button, xoperations);
    }

    /**
     * This method chops off the throws exceptions, removes "java.lang".
     */
    private String preProcessSignature(String signature) {
        int index;
        if ((index = signature.indexOf(" throws")) > 0) {
            signature = signature.substring(0, index);
        }
        while ((index = signature.indexOf("java.lang.")) > 0) {
            signature = signature.substring(0, index) +
                    signature.substring(index + 10);
        }
        return signature;
    }

    private void setPanel(boolean isCallable,
                          JButton button,
                          XOperations xoperations) {
        try {
            String defaultVal;
            MBeanParameterInfo[] params = operation.getSignature();
            add(new JLabel("(", JLabel.CENTER));
            inputs = new XTextField[params.length];
            for (int i = 0; i < params.length; i++) {
                if (params[i].getName() != null) {
                    JLabel name =
                            new JLabel(params[i].getName(), JLabel.CENTER);
                    name.setToolTipText(params[i].getDescription());
                    add(name);
                }

                String defaultTextValue =
                        Utils.getDefaultValue(params[i].getType());
                int fieldWidth = defaultTextValue.length();
                if (fieldWidth > 15) fieldWidth = 15;
                else if (fieldWidth < 10) fieldWidth = 10;

                add(inputs[i] =
                        new XTextField(Utils.getReadableClassName(defaultTextValue),
                                Utils.getClass(params[i].getType()),
                                fieldWidth,
                                isCallable,
                                button,
                                xoperations));
                inputs[i].setHorizontalAlignment(SwingConstants.CENTER);

                if (i < params.length - 1)
                    add(new JLabel(",", JLabel.CENTER));
            }
            add(new JLabel(")", JLabel.CENTER));
            validate();
            doLayout();
        } catch (Exception e) {
            System.out.println("Error setting Operation panel :" +
                    e.getMessage());
        }
    }

    public String[] getSignature() {
        MBeanParameterInfo[] params = operation.getSignature();
        String[] result = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            result[i] = params[i].getType();
        }
        return result;
    }

    public Object[] getParameters() throws Exception {
        MBeanParameterInfo[] params = operation.getSignature();
        String[] signature = new String[params.length];
        for (int i = 0; i < params.length; i++)
            signature[i] = params[i].getType();
        return Utils.getParameters(inputs, signature);
    }

    public String getReturnType() {
        return operation.getReturnType();
    }
}
