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
     * ����һ����������
     */
    private static StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

    private static Object KEY = new Object();

    //��̬����飺����ִ��init������������logback��ʵ�ֵ��ࣻ

    static {
        //ִ��StaticLoggerBinder�����init������
        SINGLETON.init();
    }

    private boolean initialized = false;
    private LoggerContext defaultLoggerContext = new LoggerContext();
    private final ContextSelectorStaticBinder contextSelectorBinder = ContextSelectorStaticBinder.getSingleton();

    private StaticLoggerBinder() {
        defaultLoggerContext.setName(CoreConstants.DEFAULT_CONTEXT_NAME);
    }

    //����ִ�� ��̬������е�init������֮���ڷ���LoggerContext���󣬴�ʱ�Ѿ���������ˣ�
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
     *����ί��ContextInitializer���defaultLoggerContext���г�ʼ����
     * ��������ҵ�����һ�����ļ����ͻ���������ļ�ȥ��ʼ��LoggerContext��
     * ���û�ҵ�����ʹ��Ĭ�����á�����LoggerContext����ô���������ļ��������õģ�
     * �ں���Ĳ����н��ܣ��������Թ���
     *
     * ����Ƕ�ContextSelectorStaticBinder����г�ʼ��
     */
    void init() {
        try {
            try {
                //ί��ContextInitializer���defaultLoggerContext(LoggerContext����)���г�ʼ��:
                //logback��ܵĳ�ʼ������ContextInitializer����������ɵģ���ʵ�ʽ������õ���GenericConfigurator�࣬
                // ������SaxEventRecorder���������ȡlogback.xml�ļ���Ȼ����Interpreter�������н�����
                // ����������ĳ�ʼ������������һϵ��Action�������ɣ�

                //����ContextInitializer����Ĳ���Ϊprivate LoggerContext defaultLoggerContext = new LoggerContext();
                new ContextInitializer(defaultLoggerContext).autoConfig();
            } catch (JoranException je) {
                Util.report("Failed to auto configure default logger context", je);
            }
            if (!StatusUtil.contextHasStatusListener(defaultLoggerContext)) {
                StatusPrinter.printInCaseOfErrorsOrWarnings(defaultLoggerContext);
            }
            //�� ContextSelectorStaticBinder ����г�ʼ��:��Context Selector�İ�
            // ����Ϊ loggerContext  private static Object KEY = new Object();
            contextSelectorBinder.init(defaultLoggerContext, KEY);
            //initialized ���Ա����ʼ�����:
            initialized = true;
        } catch (Throwable t) {
            // we should never get here
            Util.report("Failed to instantiate [" + LoggerContext.class.getName() + "]", t);
        }
    }


    //��logback�У�LoggerFactory����LoggerContext��ʵ�ֵģ�
    // LoggerContextû����ʽ�ز��õ���ģʽ����ʵ����Ӧ�ô�������ֱ�ӻ�ȡ��LoggerContext���ʵ����
    // ����ͨ��StaticLoggerBinder�ڵ�defaultLoggerContext�ֶ����������LoggerContext��ʵ��;
    // StaticLoggerBinder�����ǵ����ģ����Գ������õ���LoggerContextҲʼ����ͬһ��ʵ����������Ҫ
    public ILoggerFactory getLoggerFactory() {
        //���initialized��false����ô��ֱ�ӷ���defaultLoggerContext
        if (!initialized) {
            return defaultLoggerContext;
        }

        if (contextSelectorBinder.getContextSelector() == null) {
            throw new IllegalStateException("contextSelector cannot be null. See also " + NULL_CS_URL);
        }
        //�����ί��contextSelectorBinder����һ��contextSelector��һ�����DefaultContextSelector���󣩣�
        // ��DefaultContextSelector����������LoggerContext��   ��init����������ʵ�ֵģ�
        return contextSelectorBinder.getContextSelector().getLoggerContext();
    }

    public String getLoggerFactoryClassStr() {
        return contextSelectorBinder.getClass().getName();
    }

}
