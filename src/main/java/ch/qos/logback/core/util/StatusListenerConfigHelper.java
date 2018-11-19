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

    //传入的context为 private LoggerContext defaultLoggerContext = new LoggerContext();
    // 默认情况下，在Logback运行过程中，如果内部碰到Warning和Error状态，那么相应的内部状态信息会被打印出来；
    // 否则，不会有任何内部状态信息被打印出来。假如想要打印任何内部状态下的信息，可以通过设置监听器的方式来实现：OnConsoleStatusListener
    // 一种方式是 在配置文件中添加属性<configuration debug="true">
    // 还有一种方式是添加系统变量：java -Dlogback.statusListenerClass=ch.qos.logback.core.status.OnConsoleStatusListener
    public static void installIfAsked(Context context) {
        //从系统属性中获取：logback.statusListenerClass的value：
        String slClass = OptionHelper.getSystemProperty(CoreConstants.STATUS_LISTENER_CLASS);
        //如果不为空，就进入：
        if (!OptionHelper.isEmpty(slClass)) {
            addStatusListener(context, slClass);
        }
    }
    private static void addStatusListener(Context context, String listenerClassName) {
        StatusListener listener = null;
        // SYSOUT 是否与 传入的 listenerClassName相同：
        if (CoreConstants.SYSOUT.equalsIgnoreCase(listenerClassName)) {
            //创建 OnConsoleStatusListener 监听器
            listener = new OnConsoleStatusListener();
        } else {
            //根据传入的className创建监听器：
            listener = createListenerPerClassName(context, listenerClassName);
        }
        initAndAddListener(context, listener);
    }
    //为监听器 添加需要监听的对象：context
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
