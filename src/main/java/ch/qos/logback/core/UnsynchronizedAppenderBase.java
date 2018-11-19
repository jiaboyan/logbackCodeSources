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
package ch.qos.logback.core;

import java.util.List;

import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.spi.FilterAttachableImpl;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.WarnStatus;

/**
 * Similar to AppenderBase except that derived appenders need to handle 
 * thread synchronization on their own.
 * 
 * @author Ceki G&uuml;lc&uuml;
 * @author Ralph Goers
 */
abstract public class UnsynchronizedAppenderBase<E> extends ContextAwareBase implements Appender<E> {

    protected boolean started = false;

    // using a ThreadLocal instead of a boolean add 75 nanoseconds per
    // doAppend invocation. This is tolerable as doAppend takes at least a few microseconds
    // on a real appender
    /**
     * The guard prevents an appender from repeatedly calling its own doAppend
     * method.
     */
    private ThreadLocal<Boolean> guard = new ThreadLocal<Boolean>();

    /**
     * Appenders are named.
     */
    protected String name;

    private FilterAttachableImpl<E> fai = new FilterAttachableImpl<E>();

    public String getName() {
        return name;
    }

    private int statusRepeatCount = 0;
    private int exceptionCount = 0;

    static final int ALLOWED_REPEATS = 3;

    //RollingFileAppender ConsoleAppender���������������
    public void doAppend(E eventObject) {
        //guard���̱߳��ر������Ӹö����л�ȡ�洢��ֵ�����Ƿ�Ϊtrue;
        if (Boolean.TRUE.equals(guard.get())) {
            return;
        }
        try {
            //�����Ϊtrue������guard������һ���̱߳��ر���Ϊtrue��ֵ��
            guard.set(Boolean.TRUE);
            if (!this.started) {
                if (statusRepeatCount++ < ALLOWED_REPEATS) {
                    addStatus(new WarnStatus("Attempted to append to non started appender [" + name + "].", this));
                }
                return;
            }
            //��ȡ����Appender�ϵ� ��������������ڹ������ͽ��е��ã�
            //FilterReply�����ˣ���DENY(�ܾ���־��¼����), NEUTRAL(ͨ����־��¼����)��ACCEPT(����������һ��filter)��
            if (getFilterChainDecision(eventObject) == FilterReply.DENY) {
                return;
            }
            //RollingFileAppender ConsoleAppender������OutputStreamAppender���append������
            this.append(eventObject);
        } catch (Exception e) {
            if (exceptionCount++ < ALLOWED_REPEATS) {
                addError("Appender [" + name + "] failed to append.", e);
            }
        } finally {
            guard.set(Boolean.FALSE);
        }
    }

    abstract protected void append(E eventObject);

    /**
     * Set the name of this appender.
     */
    public void setName(String name) {
        this.name = name;
    }

    public void start() {
        started = true;
    }

    public void stop() {
        started = false;
    }

    public boolean isStarted() {
        return started;
    }

    public String toString() {
        return this.getClass().getName() + "[" + name + "]";
    }

    public void addFilter(Filter<E> newFilter) {
        fai.addFilter(newFilter);
    }

    public void clearAllFilters() {
        fai.clearAllFilters();
    }

    public List<Filter<E>> getCopyOfAttachedFiltersList() {
        return fai.getCopyOfAttachedFiltersList();
    }

    public FilterReply getFilterChainDecision(E event) {
        return fai.getFilterChainDecision(event);
    }
}
