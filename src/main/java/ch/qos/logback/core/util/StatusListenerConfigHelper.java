/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.util;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.spi.ContextAware;
import ch.qos.logback.core.spi.LifeCycle;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.status.StatusListener;

public class StatusListenerConfigHelper {

    //�����contextΪ private LoggerContext defaultLoggerContext = new LoggerContext();
    // Ĭ������£���Logback���й����У�����ڲ�����Warning��Error״̬����ô��Ӧ���ڲ�״̬��Ϣ�ᱻ��ӡ������
    // ���򣬲������κ��ڲ�״̬��Ϣ����ӡ������������Ҫ��ӡ�κ��ڲ�״̬�µ���Ϣ������ͨ�����ü������ķ�ʽ��ʵ�֣�OnConsoleStatusListener
    // һ�ַ�ʽ�� �������ļ����������<configuration debug="true">
    // ����һ�ַ�ʽ�����ϵͳ������java -Dlogback.statusListenerClass=ch.qos.logback.core.status.OnConsoleStatusListener
    public static void installIfAsked(Context context) {
        //��ϵͳ�����л�ȡ��logback.statusListenerClass��value��
        String slClass = OptionHelper.getSystemProperty(CoreConstants.STATUS_LISTENER_CLASS);
        //�����Ϊ�գ��ͽ��룺
        if (!OptionHelper.isEmpty(slClass)) {
            addStatusListener(context, slClass);
        }
    }
    private static void addStatusListener(Context context, String listenerClassName) {
        StatusListener listener = null;
        // SYSOUT �Ƿ��� ����� listenerClassName��ͬ��
        if (CoreConstants.SYSOUT.equalsIgnoreCase(listenerClassName)) {
            //���� OnConsoleStatusListener ������
            listener = new OnConsoleStatusListener();
        } else {
            //���ݴ����className������������
            listener = createListenerPerClassName(context, listenerClassName);
        }
        initAndAddListener(context, listener);
    }
    //Ϊ������ �����Ҫ�����Ķ���context
    private static void initAndAddListener(Context context, StatusListener listener) {
        if (listener != null) {
            if (listener instanceof ContextAware) { // LOGBACK-767
                ((ContextAware) listener).setContext(context);
            }
            boolean effectivelyAdded = context.getStatusManager().add(listener);
            effectivelyAdded = true;
            if (effectivelyAdded && (listener instanceof LifeCycle)) {
                ((LifeCycle) listener).start(); // LOGBACK-767
            }
        }
    }

    private static StatusListener createListenerPerClassName(Context context, String listenerClass) {
        try {
            return (StatusListener) OptionHelper.instantiateByClassName(listenerClass, StatusListener.class, context);
        } catch (Exception e) {
            // printing on the console is the best we can do
            e.printStackTrace();
            return null;
        }
    }

    /**
       * This utility method adds a new OnConsoleStatusListener to the context
       * passed as parameter.
       *
       * @param context
       * @since 1.0.1
       */
    static public void addOnConsoleListenerInstance(Context context, OnConsoleStatusListener onConsoleStatusListener) {
        onConsoleStatusListener.setContext(context);
        boolean effectivelyAdded = context.getStatusManager().add(onConsoleStatusListener);
        if (effectivelyAdded) {
            onConsoleStatusListener.start();
        }
    }
}
