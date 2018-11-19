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
package org.slf4j.impl;

import ch.qos.logback.core.status.StatusUtil;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.Util;
import org.slf4j.spi.LoggerFactoryBinder;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.classic.util.ContextSelectorStaticBinder;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

/**
 * 
 * The binding of {@link LoggerFactory} class with an actual instance of
 * {@link ILoggerFactory} is performed using information returned by this class.
 * 
 * @author Ceki G&uuml;lc&uuml;</a>
 */
public class StaticLoggerBinder implements LoggerFactoryBinder {

    /**
     * Declare the version of the SLF4J API this implementation is compiled
     * against. The value of this field is usually modified with each release.
     */
    // to avoid constant folding by the compiler, this field must *not* be final
    public static String REQUESTED_API_VERSION = "1.7.16"; // !final

    final static String NULL_CS_URL = CoreConstants.CODES_URL + "#null_CS";

    /**
     * 创建一个单利对象：
     */
    private static StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

    private static Object KEY = new Object();

    //静态代码块：首先执行init方法，此类是logback中实现的类；

    static {
        //执行StaticLoggerBinder对象的init方法：
        SINGLETON.init();
    }

    private boolean initialized = false;
    private LoggerContext defaultLoggerContext = new LoggerContext();
    private final ContextSelectorStaticBinder contextSelectorBinder = ContextSelectorStaticBinder.getSingleton();

    private StaticLoggerBinder() {
        defaultLoggerContext.setName(CoreConstants.DEFAULT_CONTEXT_NAME);
    }

    //首先执行 静态代码块中的init方法：之后在返回LoggerContext对象，此时已经被处理过了；
    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    /**
     * Package access for testing purposes.
     */
    static void reset() {
        SINGLETON = new StaticLoggerBinder();
        SINGLETON.init();
    }

    /**
     *首先委托ContextInitializer类对defaultLoggerContext进行初始化。
     * 这里如果找到了任一配置文件，就会根据配置文件去初始化LoggerContext，
     * 如果没找到，会使用默认配置。关于LoggerContext是怎么根据配置文件进行配置的，
     * 在后面的博客中介绍，这里先略过；
     *
     * 其次是对ContextSelectorStaticBinder类进行初始化
     */
    void init() {
        try {
            try {
                //委托ContextInitializer类对defaultLoggerContext(LoggerContext对象)进行初始化:
                //logback框架的初始化是由ContextInitializer类来负责完成的，而实际进行配置的是GenericConfigurator类，
                // 它调用SaxEventRecorder类来负责读取logback.xml文件，然后由Interpreter类来进行解析，
                // 而最后真正的初始化工作，是由一系列Action组件来完成；

                //传入ContextInitializer构造的参数为private LoggerContext defaultLoggerContext = new LoggerContext();
                new ContextInitializer(defaultLoggerContext).autoConfig();
            } catch (JoranException je) {
                Util.report("Failed to auto configure default logger context", je);
            }
            if (!StatusUtil.contextHasStatusListener(defaultLoggerContext)) {
                StatusPrinter.printInCaseOfErrorsOrWarnings(defaultLoggerContext);
            }
            //对 ContextSelectorStaticBinder 类进行初始化:做Context Selector的绑定
            // 参数为 loggerContext  private static Object KEY = new Object();
            contextSelectorBinder.init(defaultLoggerContext, KEY);
            //initialized 属性保存初始化结果:
            initialized = true;
        } catch (Throwable t) {
            // we should never get here
            Util.report("Failed to instantiate [" + LoggerContext.class.getName() + "]", t);
        }
    }


    //在logback中，LoggerFactory是由LoggerContext来实现的；
    // LoggerContext没有显式地采用单例模式，但实际上应用从来不会直接获取到LoggerContext类的实例，
    // 都是通过StaticLoggerBinder内的defaultLoggerContext字段来间接引用LoggerContext的实例;
    // StaticLoggerBinder本身是单例的，所以程序中用到的LoggerContext也始终是同一个实例，这点很重要
    public ILoggerFactory getLoggerFactory() {
        //如果initialized是false，那么会直接返回defaultLoggerContext
        if (!initialized) {
            return defaultLoggerContext;
        }

        if (contextSelectorBinder.getContextSelector() == null) {
            throw new IllegalStateException("contextSelector cannot be null. See also " + NULL_CS_URL);
        }
        //否则就委托contextSelectorBinder返回一个contextSelector（一般就是DefaultContextSelector对象），
        // 由DefaultContextSelector对象来返回LoggerContext；   在init（）方法中实现的；
        return contextSelectorBinder.getContextSelector().getLoggerContext();
    }

    public String getLoggerFactoryClassStr() {
        return contextSelectorBinder.getClass().getName();
    }

}
