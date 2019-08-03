package com.jvm.console.client;

import javax.management.*;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * @author xyt
 * @date 2019/8/3
 */
public class MBeanServerConnectionProxy implements InvocationHandler {

    private final MBeanServerConnection target;
    private Map<ObjectName, Map<String, Object>> cachedValues = new HashMap<ObjectName, Map<String, Object>>();
    private Map<ObjectName, Set<String>> cachedNames = new HashMap<ObjectName, Set<String>>();

    public MBeanServerConnectionProxy(MBeanServerConnection target) {
        this.target = target;
    }

    public SnapshotMBeanServerConnection getInstace() {
        return (SnapshotMBeanServerConnection) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{SnapshotMBeanServerConnection.class}, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final String methodName = method.getName();
        if (methodName.equals("getAttribute")) {
            return getAttribute((ObjectName) args[0], (String) args[1]);
        } else if (methodName.equals("getAttributes")) {
            return getAttributes((ObjectName) args[0], (String[]) args[1]);
        } else if (methodName.equals("flush")) {
            synchronized (this) {
                cachedValues = new HashMap<ObjectName, Map<String, Object>>();
            }
            return null;
        } else {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private Object getAttribute(ObjectName objName, String attrName)
            throws MBeanException, InstanceNotFoundException,
            AttributeNotFoundException, ReflectionException, IOException {
        final Map<String, Object> values = getCachedAttributes(
                objName, Collections.singleton(attrName));
        Object value = values.get(attrName);
        if (value != null || values.containsKey(attrName)) {
            return value;
        }
        // Not in cache, presumably because it was omitted from the
        // getAttributes result because of an exception.  Following
        // call will probably provoke the same exception.
        return target.getAttribute(objName, attrName);
    }

    private AttributeList getAttributes(
            ObjectName objName, String[] attrNames) throws
            InstanceNotFoundException, ReflectionException, IOException {
        final Map<String, Object> values = getCachedAttributes(
                objName,
                new TreeSet<String>(Arrays.asList(attrNames)));
        final AttributeList list = new AttributeList();
        for (String attrName : attrNames) {
            final Object value = values.get(attrName);
            if (value != null || values.containsKey(attrName)) {
                list.add(new Attribute(attrName, value));
            }
        }
        return list;
    }

    private synchronized Map<String, Object> getCachedAttributes(
            ObjectName objName, Set<String> attrNames) throws
            InstanceNotFoundException, ReflectionException, IOException {
        Map<String, Object> values = cachedValues.get(objName);
        if (values != null && values.keySet().containsAll(attrNames)) {
            return values;
        }
        attrNames = new TreeSet<String>(attrNames);
        Set<String> oldNames = cachedNames.get(objName);
        if (oldNames != null) {
            attrNames.addAll(oldNames);
        }
        values = new HashMap<String, Object>();
        final AttributeList attrs = target.getAttributes(
                objName,
                attrNames.toArray(new String[attrNames.size()]));
        for (Attribute attr : attrs.asList()) {
            values.put(attr.getName(), attr.getValue());
        }
        cachedValues.put(objName, values);
        cachedNames.put(objName, attrNames);
        return values;
    }
}
