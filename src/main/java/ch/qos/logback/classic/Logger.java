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
package ch.qos.logback.classic;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.spi.LocationAwareLogger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LoggerNameUtil;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import ch.qos.logback.core.spi.FilterReply;

public final class Logger implements org.slf4j.Logger, LocationAwareLogger, AppenderAttachable<ILoggingEvent>, Serializable {

    private static final long serialVersionUID = 5454405123156820674L; // 8745934908040027998L;

    /**
     * The fully qualified name of this class. Used in gathering caller
     * information.
     */
    public static final String FQCN = ch.qos.logback.classic.Logger.class.getName();

    /**
     * logger的名称：
     */
    private String name;

    // logger的日志级别：
    transient private Level level;

    // The effective levelInt is the assigned levelInt and if null, a levelInt is
    // inherited form a parent.
    transient private int effectiveLevelInt;

    /**
     * logger有父子关系级别：parent为该logger的父日志
     */
    transient private Logger parent;

    /**
     * 该日志对象的子日志对象集合：其实现类为 CopyOnWriteArrayList对象；
     */
    transient private List<Logger> childrenList;

    /**
     * aai表示为 该日志对象下所属的 appender （日志输出目的地）；
     */
    transient private AppenderAttachableImpl<ILoggingEvent> aai;
    /**
     * Additivity is set to true by default, that is children inherit the
     * appenders of their ancestors by default. If this variable is set to
     * <code>false</code> then the appenders located in the ancestors of this
     * logger will not be used. However, the children of this logger will inherit
     * its appenders, unless the children have their additivity flag set to
     * <code>false</code> too. See the user manual for more details.
     */
    transient private boolean additive = true;

    //存有日志工厂的引用；
    final transient LoggerContext loggerContext;

    Logger(String name, Logger parent, LoggerContext loggerContext) {
        this.name = name;
        this.parent = parent;
        this.loggerContext = loggerContext;
    }

    public Level getEffectiveLevel() {
        return Level.toLevel(effectiveLevelInt);
    }

    int getEffectiveLevelInt() {
        return effectiveLevelInt;
    }

    public Level getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    private boolean isRootLogger() {
        // only the root logger has a null parent
        return parent == null;
    }

    //通过名称，获取对应的子logger；  例如：com.jiaboyan.logback.logTest,第一次传递com进来获取子logger;
    Logger getChildByName(final String childName) {
        //第一次访问进来，为null，直接返回；
        if (childrenList == null) {
            return null;
        } else {
            int len = this.childrenList.size();
            for (int i = 0; i < len; i++) {
                final Logger childLogger_i = (Logger) childrenList.get(i);
                final String childName_i = childLogger_i.getName();

                if (childName.equals(childName_i)) {
                    return childLogger_i;
                }
            }
            // no child found
            return null;
        }
    }

    public synchronized void setLevel(Level newLevel) {
        if (level == newLevel) {
            // nothing to do;
            return;
        }
        if (newLevel == null && isRootLogger()) {
            throw new IllegalArgumentException("The level of the root logger cannot be set to null");
        }

        level = newLevel;
        if (newLevel == null) {
            effectiveLevelInt = parent.effectiveLevelInt;
            newLevel = parent.getEffectiveLevel();
        } else {
            effectiveLevelInt = newLevel.levelInt;
        }

        if (childrenList != null) {
            int len = childrenList.size();
            for (int i = 0; i < len; i++) {
                Logger child = (Logger) childrenList.get(i);
                // tell child to handle parent levelInt change
                child.handleParentLevelChange(effectiveLevelInt);
            }
        }
        // inform listeners
        loggerContext.fireOnLevelChange(this, newLevel);
    }

    /**
     * This method is invoked by parent logger to let this logger know that the
     * prent's levelInt changed.
     * 
     * @param newParentLevelInt
     */
    private synchronized void handleParentLevelChange(int newParentLevelInt) {
        // changes in the parent levelInt affect children only if their levelInt is
        // null
        if (level == null) {
            effectiveLevelInt = newParentLevelInt;

            // propagate the parent levelInt change to this logger's children
            if (childrenList != null) {
                int len = childrenList.size();
                for (int i = 0; i < len; i++) {
                    Logger child = (Logger) childrenList.get(i);
                    child.handleParentLevelChange(newParentLevelInt);
                }
            }
        }
    }

    /**
     * Remove all previously added appenders from this logger instance.
     * <p/>
     * This is useful when re-reading configuration information.
     */
    public void detachAndStopAllAppenders() {
        if (aai != null) {
            aai.detachAndStopAllAppenders();
        }
    }

    public boolean detachAppender(String name) {
        if (aai == null) {
            return false;
        }
        return aai.detachAppender(name);
    }

    // this method MUST be synchronized. See comments on 'aai' field for further
    // details.
    public synchronized void addAppender(Appender<ILoggingEvent> newAppender) {
        if (aai == null) {
            aai = new AppenderAttachableImpl<ILoggingEvent>();
        }
        aai.addAppender(newAppender);
    }

    public boolean isAttached(Appender<ILoggingEvent> appender) {
        if (aai == null) {
            return false;
        }
        return aai.isAttached(appender);
    }

    @SuppressWarnings("unchecked")
    public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
        if (aai == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        return aai.iteratorForAppenders();
    }

    public Appender<ILoggingEvent> getAppender(String name) {
        if (aai == null) {
            return null;
        }
        return aai.getAppender(name);
    }

    //从当前Logger开始，通过每个logger对象的parent属性依次向上游历所有的相关logger，直到root logger；
    //依次调用logger的appender对象，一般情况下只有根root上绑定有appender:
    public void callAppenders(ILoggingEvent event) {
        int writes = 0;
        //从此logger依次向上遍历，每一次将其自身的parent日志对象，作为下一次遍历的目标：
        for (Logger l = this; l != null; l = l.parent) {
            //依次调用每个日志对象的appendLoopOnAppenders方法：调用了几个Appender对象就将writes加几：
            writes += l.appendLoopOnAppenders(event);
            if (!l.additive) {
                break;
            }
        }
        //如果配置文件中，没有设置appender标签，这块就会为0，进行警告日志的打印：
        if (writes == 0) {
            loggerContext.noAppenderDefinedWarning(this);
        }
    }


    private int appendLoopOnAppenders(ILoggingEvent event) {
        //判断当前日志对象中aai是否为空，aai属性可简化为我们logback配置文件中appender标签：
        if (aai != null) {
            //如果存在，调用appendLoopOnAppenders方法：
            return aai.appendLoopOnAppenders(event);
        } else {
            return 0;
        }
    }

    /**
     * Remove the appender passed as parameter form the list of appenders.
     */
    public boolean detachAppender(Appender<ILoggingEvent> appender) {
        if (aai == null) {
            return false;
        }
        return aai.detachAppender(appender);
    }

    /**
     * Create a child of this logger by suffix, that is, the part of the name
     * extending this logger. For example, if this logger is named "x.y" and the
     * lastPart is "z", then the created child logger will be named "x.y.z".
     * 
     * <p>
     * IMPORTANT: Calls to this method must be within a synchronized block on this
     * logger.
     * 
     * @param lastPart
     *          the suffix (i.e. last part) of the child logger name. This
     *          parameter may not include dots, i.e. the logger separator
     *          character.
     * @return
     */
    Logger createChildByLastNamePart(final String lastPart) {
        int i_index = LoggerNameUtil.getFirstSeparatorIndexOf(lastPart);
        if (i_index != -1) {
            throw new IllegalArgumentException("Child name [" + lastPart + " passed as parameter, may not include [" + CoreConstants.DOT + "]");
        }

        if (childrenList == null) {
            childrenList = new CopyOnWriteArrayList<Logger>();
        }
        Logger childLogger;
        if (this.isRootLogger()) {
            childLogger = new Logger(lastPart, this, this.loggerContext);
        } else {
            childLogger = new Logger(name + CoreConstants.DOT + lastPart, this, this.loggerContext);
        }
        childrenList.add(childLogger);
        childLogger.effectiveLevelInt = this.effectiveLevelInt;
        return childLogger;
    }

    private void localLevelReset() {
        effectiveLevelInt = Level.DEBUG_INT;
        if (isRootLogger()) {
            level = Level.DEBUG;
        } else {
            level = null;
        }
    }

    void recursiveReset() {
        detachAndStopAllAppenders();
        localLevelReset();
        additive = true;
        if (childrenList == null) {
            return;
        }
        for (Logger childLogger : childrenList) {
            childLogger.recursiveReset();
        }
    }

    /**
     * The default size of child list arrays. The JDK 1.5 default is 10. We use a
     * smaller value to save a little space.
     */

    Logger createChildByName(final String childName) {
        int i_index = LoggerNameUtil.getSeparatorIndexOf(childName, this.name.length() + 1);
        if (i_index != -1) {
            throw new IllegalArgumentException("For logger [" + this.name + "] child name [" + childName
                            + " passed as parameter, may not include '.' after index" + (this.name.length() + 1));
        }
        //初始化根root对象的  子日志对象集合：
        if (childrenList == null) {
            //写时拷贝数组集合，就是说在对集合中的数据进行增删改的时候，
            // 该集合都是先将其自身的数组复制出来一份，在赋值给一个新的数组对象的；并不是在原有数组对象上进行操作；
            // 并且是线程安全的，都是加了transient final ReentrantLock lock = new ReentrantLock() 锁；
            childrenList = new CopyOnWriteArrayList<Logger>();
        }
        Logger childLogger;
        //创建根root的子logger对象：在其子日志对象的构造中，也设置了自身的父日志对象；
        childLogger = new Logger(childName, this, this.loggerContext);
        //添加到子日志对象集合中去：
        childrenList.add(childLogger);
        //设置日志对象的 日志级别：由于根root设置的是debug（10000），所以如果没有别的设置，那么日志树上的所有logger都是debug级别；
        childLogger.effectiveLevelInt = this.effectiveLevelInt;
        return childLogger;
    }

    /**
     * 日志级别：ERROR > WARN > INFO > DEBUG > TRACE ,大于该级别的日志不会被打印出来；
     */
    private void filterAndLog_0_Or3Plus(final String localFQCN, final Marker marker, final Level level, final String msg, final Object[] params, final Throwable t) {
        //执行TurboFilter的处理逻辑:TurboFilter也是logback一种过滤器，它是绑定在loggerContext上的，在日志事件对象生成前调用；
        final FilterReply decision = loggerContext.getTurboFilterChainDecision_0_3OrMore(marker, this, level, msg, params, t);
        //根据TurboFilter返回的结果和Level信息做判断，如果不需要写日志则直接退出
        //FilterReply包含了：“DENY(拒绝日志记录请求), NEUTRAL(通过日志记录请求)和ACCEPT(继续调用下一个filter)”
        if (decision == FilterReply.NEUTRAL) {
            //判断此logger对象的日志级别 与调用方法的日志级别比较：如果大于则不进行日志打印操作；
            if (effectiveLevelInt > level.levelInt) {
                return;
            }
        } else if (decision == FilterReply.DENY) {
            return;
        }
        //调用buildLoggingEventAndAppend()方法来构建日志并写入:构建LogEvent对象和调用appender对象；
        buildLoggingEventAndAppend(localFQCN, marker, level, msg, params, t);
    }

    private void filterAndLog_1(final String localFQCN, final Marker marker, final Level level, final String msg, final Object param, final Throwable t) {

        final FilterReply decision = loggerContext.getTurboFilterChainDecision_1(marker, this, level, msg, param, t);

        if (decision == FilterReply.NEUTRAL) {
            if (effectiveLevelInt > level.levelInt) {
                return;
            }
        } else if (decision == FilterReply.DENY) {
            return;
        }

        buildLoggingEventAndAppend(localFQCN, marker, level, msg, new Object[] { param }, t);
    }

    private void filterAndLog_2(final String localFQCN, final Marker marker, final Level level, final String msg, final Object param1, final Object param2,
                    final Throwable t) {

        final FilterReply decision = loggerContext.getTurboFilterChainDecision_2(marker, this, level, msg, param1, param2, t);

        if (decision == FilterReply.NEUTRAL) {
            if (effectiveLevelInt > level.levelInt) {
                return;
            }
        } else if (decision == FilterReply.DENY) {
            return;
        }

        buildLoggingEventAndAppend(localFQCN, marker, level, msg, new Object[] { param1, param2 }, t);
    }

    //如果logger的日志级别<=对应调用日志方法的级别，则调用此方法：
    private void buildLoggingEventAndAppend(final String localFQCN, final Marker marker, final Level level, final String msg, final Object[] params, final Throwable t) {
        //首先构建LoggingEvent对象：也就是日志打印事件对象；
        LoggingEvent le = new LoggingEvent(localFQCN, this, level, msg, t, params);
        le.setMarker(marker);
        //callAppenders()方法中从当前Logger开始，通过每个logger对象的parent属性依次向上游历所有的相关logger，直到root logger;
        //如果遇到有某个logger的additive属性被设置为false，则中断这个向上的游历过程。
        callAppenders(le);
    }

    public void trace(String msg) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.TRACE, msg, null, null);
    }

    public void trace(String format, Object arg) {
        filterAndLog_1(FQCN, null, Level.TRACE, format, arg, null);
    }

    public void trace(String format, Object arg1, Object arg2) {
        filterAndLog_2(FQCN, null, Level.TRACE, format, arg1, arg2, null);
    }

    public void trace(String format, Object... argArray) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.TRACE, format, argArray, null);
    }

    public void trace(String msg, Throwable t) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.TRACE, msg, null, t);
    }

    public void trace(Marker marker, String msg) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.TRACE, msg, null, null);
    }

    public void trace(Marker marker, String format, Object arg) {
        filterAndLog_1(FQCN, marker, Level.TRACE, format, arg, null);
    }

    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        filterAndLog_2(FQCN, marker, Level.TRACE, format, arg1, arg2, null);
    }

    public void trace(Marker marker, String format, Object... argArray) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.TRACE, format, argArray, null);
    }

    public void trace(Marker marker, String msg, Throwable t) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.TRACE, msg, null, t);
    }

    public boolean isDebugEnabled() {
        return isDebugEnabled(null);
    }

    public boolean isDebugEnabled(Marker marker) {
        final FilterReply decision = callTurboFilters(marker, Level.DEBUG);
        if (decision == FilterReply.NEUTRAL) {
            return effectiveLevelInt <= Level.DEBUG_INT;
        } else if (decision == FilterReply.DENY) {
            return false;
        } else if (decision == FilterReply.ACCEPT) {
            return true;
        } else {
            throw new IllegalStateException("Unknown FilterReply value: " + decision);
        }
    }

    //日志打印，调用debug方法：
    public void debug(String msg) {
        //实际调用的是 filterAndLog_0_Or3Plus方法: 传递了Level.DEBUG的日志级别，也代表debug方法的日志级别：
        filterAndLog_0_Or3Plus(FQCN, null, Level.DEBUG, msg, null, null);
    }

    public void debug(String format, Object arg) {
        filterAndLog_1(FQCN, null, Level.DEBUG, format, arg, null);
    }

    public void debug(String format, Object arg1, Object arg2) {
        filterAndLog_2(FQCN, null, Level.DEBUG, format, arg1, arg2, null);
    }

    public void debug(String format, Object... argArray) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.DEBUG, format, argArray, null);
    }

    public void debug(String msg, Throwable t) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.DEBUG, msg, null, t);
    }

    public void debug(Marker marker, String msg) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.DEBUG, msg, null, null);
    }

    public void debug(Marker marker, String format, Object arg) {
        filterAndLog_1(FQCN, marker, Level.DEBUG, format, arg, null);
    }

    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        filterAndLog_2(FQCN, marker, Level.DEBUG, format, arg1, arg2, null);
    }

    public void debug(Marker marker, String format, Object... argArray) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.DEBUG, format, argArray, null);
    }

    public void debug(Marker marker, String msg, Throwable t) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.DEBUG, msg, null, t);
    }

    public void error(String msg) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.ERROR, msg, null, null);
    }

    public void error(String format, Object arg) {
        filterAndLog_1(FQCN, null, Level.ERROR, format, arg, null);
    }

    public void error(String format, Object arg1, Object arg2) {
        filterAndLog_2(FQCN, null, Level.ERROR, format, arg1, arg2, null);
    }

    public void error(String format, Object... argArray) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.ERROR, format, argArray, null);
    }

    public void error(String msg, Throwable t) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.ERROR, msg, null, t);
    }

    public void error(Marker marker, String msg) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.ERROR, msg, null, null);
    }

    public void error(Marker marker, String format, Object arg) {
        filterAndLog_1(FQCN, marker, Level.ERROR, format, arg, null);
    }

    public void error(Marker marker, String format, Object arg1, Object arg2) {
        filterAndLog_2(FQCN, marker, Level.ERROR, format, arg1, arg2, null);
    }

    public void error(Marker marker, String format, Object... argArray) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.ERROR, format, argArray, null);
    }

    public void error(Marker marker, String msg, Throwable t) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.ERROR, msg, null, t);
    }

    public boolean isInfoEnabled() {
        return isInfoEnabled(null);
    }

    public boolean isInfoEnabled(Marker marker) {
        FilterReply decision = callTurboFilters(marker, Level.INFO);
        if (decision == FilterReply.NEUTRAL) {
            return effectiveLevelInt <= Level.INFO_INT;
        } else if (decision == FilterReply.DENY) {
            return false;
        } else if (decision == FilterReply.ACCEPT) {
            return true;
        } else {
            throw new IllegalStateException("Unknown FilterReply value: " + decision);
        }
    }

    public void info(String msg) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.INFO, msg, null, null);
    }

    public void info(String format, Object arg) {
        filterAndLog_1(FQCN, null, Level.INFO, format, arg, null);
    }

    public void info(String format, Object arg1, Object arg2) {
        filterAndLog_2(FQCN, null, Level.INFO, format, arg1, arg2, null);
    }

    public void info(String format, Object... argArray) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.INFO, format, argArray, null);
    }

    public void info(String msg, Throwable t) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.INFO, msg, null, t);
    }

    public void info(Marker marker, String msg) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.INFO, msg, null, null);
    }

    public void info(Marker marker, String format, Object arg) {
        filterAndLog_1(FQCN, marker, Level.INFO, format, arg, null);
    }

    public void info(Marker marker, String format, Object arg1, Object arg2) {
        filterAndLog_2(FQCN, marker, Level.INFO, format, arg1, arg2, null);
    }

    public void info(Marker marker, String format, Object... argArray) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.INFO, format, argArray, null);
    }

    public void info(Marker marker, String msg, Throwable t) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.INFO, msg, null, t);
    }

    public boolean isTraceEnabled() {
        return isTraceEnabled(null);
    }

    public boolean isTraceEnabled(Marker marker) {
        final FilterReply decision = callTurboFilters(marker, Level.TRACE);
        if (decision == FilterReply.NEUTRAL) {
            return effectiveLevelInt <= Level.TRACE_INT;
        } else if (decision == FilterReply.DENY) {
            return false;
        } else if (decision == FilterReply.ACCEPT) {
            return true;
        } else {
            throw new IllegalStateException("Unknown FilterReply value: " + decision);
        }
    }

    public boolean isErrorEnabled() {
        return isErrorEnabled(null);
    }

    public boolean isErrorEnabled(Marker marker) {
        FilterReply decision = callTurboFilters(marker, Level.ERROR);
        if (decision == FilterReply.NEUTRAL) {
            return effectiveLevelInt <= Level.ERROR_INT;
        } else if (decision == FilterReply.DENY) {
            return false;
        } else if (decision == FilterReply.ACCEPT) {
            return true;
        } else {
            throw new IllegalStateException("Unknown FilterReply value: " + decision);
        }
    }

    public boolean isWarnEnabled() {
        return isWarnEnabled(null);
    }

    public boolean isWarnEnabled(Marker marker) {
        FilterReply decision = callTurboFilters(marker, Level.WARN);
        if (decision == FilterReply.NEUTRAL) {
            return effectiveLevelInt <= Level.WARN_INT;
        } else if (decision == FilterReply.DENY) {
            return false;
        } else if (decision == FilterReply.ACCEPT) {
            return true;
        } else {
            throw new IllegalStateException("Unknown FilterReply value: " + decision);
        }

    }

    public boolean isEnabledFor(Marker marker, Level level) {
        FilterReply decision = callTurboFilters(marker, level);
        if (decision == FilterReply.NEUTRAL) {
            return effectiveLevelInt <= level.levelInt;
        } else if (decision == FilterReply.DENY) {
            return false;
        } else if (decision == FilterReply.ACCEPT) {
            return true;
        } else {
            throw new IllegalStateException("Unknown FilterReply value: " + decision);
        }
    }

    public boolean isEnabledFor(Level level) {
        return isEnabledFor(null, level);
    }

    public void warn(String msg) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.WARN, msg, null, null);
    }

    public void warn(String msg, Throwable t) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.WARN, msg, null, t);
    }

    public void warn(String format, Object arg) {
        filterAndLog_1(FQCN, null, Level.WARN, format, arg, null);
    }

    public void warn(String format, Object arg1, Object arg2) {
        filterAndLog_2(FQCN, null, Level.WARN, format, arg1, arg2, null);
    }

    public void warn(String format, Object... argArray) {
        filterAndLog_0_Or3Plus(FQCN, null, Level.WARN, format, argArray, null);
    }

    public void warn(Marker marker, String msg) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.WARN, msg, null, null);
    }

    public void warn(Marker marker, String format, Object arg) {
        filterAndLog_1(FQCN, marker, Level.WARN, format, arg, null);
    }

    public void warn(Marker marker, String format, Object... argArray) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.WARN, format, argArray, null);
    }

    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        filterAndLog_2(FQCN, marker, Level.WARN, format, arg1, arg2, null);
    }

    public void warn(Marker marker, String msg, Throwable t) {
        filterAndLog_0_Or3Plus(FQCN, marker, Level.WARN, msg, null, t);
    }

    public boolean isAdditive() {
        return additive;
    }

    public void setAdditive(boolean additive) {
        this.additive = additive;
    }

    public String toString() {
        return "Logger[" + name + "]";
    }

    /**
     * Method that calls the attached TurboFilter objects based on the logger and
     * the level.
     * 
     * It is used by isYYYEnabled() methods.
     * 
     * It returns the typical FilterReply values: ACCEPT, NEUTRAL or DENY.
     * 
     * @param level
     * @return the reply given by the TurboFilters
     */
    private FilterReply callTurboFilters(Marker marker, Level level) {
        return loggerContext.getTurboFilterChainDecision_0_3OrMore(marker, this, level, null, null, null);
    }

    /**
     * Return the context for this logger.
     * 
     * @return the context
     */
    public LoggerContext getLoggerContext() {
        return loggerContext;
    }

    public void log(Marker marker, String fqcn, int levelInt, String message, Object[] argArray, Throwable t) {
        Level level = Level.fromLocationAwareLoggerInteger(levelInt);
        filterAndLog_0_Or3Plus(fqcn, marker, level, message, argArray, t);
    }

    /**
     * Support SLF4J interception during initialization as introduced in SLF4J version 1.7.15
     * @since 1.1.4 
     * @param slf4jEvent
     */
    public void log(org.slf4j.event.LoggingEvent slf4jEvent) {
        Level level = Level.fromLocationAwareLoggerInteger(slf4jEvent.getLevel().toInt());
        filterAndLog_0_Or3Plus(FQCN, slf4jEvent.getMarker(), level, slf4jEvent.getMessage(), slf4jEvent.getArgumentArray(), slf4jEvent.getThrowable());
    }

    /**
     * After serialization, the logger instance does not know its LoggerContext.
     * The best we can do here, is to return a logger with the same name
     * returned by org.slf4j.LoggerFactory.
     * 
     * @return Logger instance with the same name
     * @throws ObjectStreamException
     */
    protected Object readResolve() throws ObjectStreamException {
        return LoggerFactory.getLogger(getName());
    }
}
