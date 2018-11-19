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
package ch.qos.logback.classic.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.selector.ContextJNDISelector;
import ch.qos.logback.classic.selector.ContextSelector;
import ch.qos.logback.classic.selector.DefaultContextSelector;
import ch.qos.logback.core.util.Loader;
import ch.qos.logback.core.util.OptionHelper;

/**
 * Holds the context selector for use in the current environment.
 * 
 * @author Ceki G&uuml;lc&uuml;
 * @since 0.9.19
 */
public class ContextSelectorStaticBinder {

    static ContextSelectorStaticBinder singleton = new ContextSelectorStaticBinder();

    ContextSelector contextSelector;
    Object key;

    public static ContextSelectorStaticBinder getSingleton() {
        return singleton;
    }

    /**
     * ContextSelectorStaticBinder初始化方法：
     */
    public void init(LoggerContext defaultLoggerContext, Object key) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        if (this.key == null) {
            //给ContextSelectorStaticBinder类的key属性赋值：
            this.key = key;
        } else if (this.key != key) {
            throw new IllegalAccessException("Only certain classes can access this method.");
        }
        //从系统属性中 获取 logback.ContextSelector的值：
        String contextSelectorStr = OptionHelper.getSystemProperty(ClassicConstants.LOGBACK_CONTEXT_SELECTOR);
        if (contextSelectorStr == null) {
            //实际应用中，一般会得到一个DefaultContextSelector；
            //并且把已经初始化完成的defaultLoggerContext传给新创建的这个DefaultContextSelector；
            contextSelector = new DefaultContextSelector(defaultLoggerContext);
        } else if (contextSelectorStr.equals("JNDI")) {
            //系统参数中配置了JNDI，这里会得到一个ContextJNDISelector
            contextSelector = new ContextJNDISelector(defaultLoggerContext);
        } else {
            contextSelector = dynamicalContextSelector(defaultLoggerContext, contextSelectorStr);
        }
    }

    /**
     * Instantiate the context selector class designated by the user. The selector
     * must have a constructor taking a LoggerContext instance as an argument.
     * 
     * @param defaultLoggerContext
     * @param contextSelectorStr
     * @return an instance of the designated context selector class
     * @throws ClassNotFoundException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    static ContextSelector dynamicalContextSelector(LoggerContext defaultLoggerContext, String contextSelectorStr) throws ClassNotFoundException,
                    SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException,
                    InvocationTargetException {
        Class<?> contextSelectorClass = Loader.loadClass(contextSelectorStr);
        Constructor cons = contextSelectorClass.getConstructor(new Class[] { LoggerContext.class });
        return (ContextSelector) cons.newInstance(defaultLoggerContext);
    }

    public ContextSelector getContextSelector() {
        return contextSelector;
    }

}
