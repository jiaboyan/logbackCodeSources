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

import static ch.qos.logback.core.CoreConstants.EVALUATOR_MAP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.ILoggerFactory;
import org.slf4j.Marker;

import ch.qos.logback.classic.spi.LoggerComparator;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.TurboFilterList;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.classic.util.LoggerNameUtil;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.boolex.EventEvaluator;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.spi.LifeCycle;
import ch.qos.logback.core.status.StatusListener;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.status.WarnStatus;

/**
 * LoggerContext没有显式地采用单例模式，但实际上应用从来不会直接获取到LoggerContext类的实例，
 * 都是通过StaticLoggerBinder内的defaultLoggerContext字段来间接引用LoggerContext的实例，
 * 而StaticLoggerBinder本身是单例的，所以程序中用到的LoggerContext也始终是同一个实例，
 */
public class LoggerContext extends ContextBase implements ILoggerFactory, LifeCycle {

    /** Default setting of packaging data in stack traces */
    public static final boolean DEFAULT_PACKAGING_DATA = false;

    final Logger root;//root是根Logger
    private int size;//size用来表示LoggerContext一共创建了几个Logger
    private int noAppenderWarning = 0;
    final private List<LoggerContextListener> loggerContextListenerList = new ArrayList<LoggerContextListener>();
    //保存所有 创建的所有Logger:  用ConcurrentHashMap对象；
    private Map<String, Logger> loggerCache;
    //loggerContextRemoteView保存了LoggerContext的一些值，比如name、birthTime等
    private LoggerContextVO loggerContextRemoteView;
    //turboFilterList保存所有的TurboFilter，TurboFilter是一种快速过滤器，对是否记录日志有一票通过和一票否决的权力;
    private final TurboFilterList turboFilterList = new TurboFilterList();
    private boolean packagingDataEnabled = DEFAULT_PACKAGING_DATA;

    private int maxCallerDataDepth = ClassicConstants.DEFAULT_MAX_CALLEDER_DATA_DEPTH;
    //resetCount是用来统计该LoggerContext调用过几次reset()方法;
    int resetCount = 0;
    private List<String> frameworkPackages;

    //在SaticLoggerBinder类中初始化了LoggerContext,设置了根Root;只会调用一次；
    public LoggerContext() {
        super();
        this.loggerCache = new ConcurrentHashMap<String, Logger>();

        this.loggerContextRemoteView = new LoggerContextVO(this);
        this.root = new Logger(Logger.ROOT_LOGGER_NAME, null, this);
        this.root.setLevel(Level.DEBUG);
        loggerCache.put(Logger.ROOT_LOGGER_NAME, root);
        initEvaluatorMap();
        size = 1;
        this.frameworkPackages = new ArrayList<String>();
    }

    void initEvaluatorMap() {
        putObject(EVALUATOR_MAP, new HashMap<String, EventEvaluator<?>>());
    }

    /**
     * A new instance of LoggerContextRemoteView needs to be created each time the
     * name or propertyMap (including keys or values) changes.
     */
    private void updateLoggerContextVO() {
        loggerContextRemoteView = new LoggerContextVO(this);
    }

    @Override
    public void putProperty(String key, String val) {
        super.putProperty(key, val);
        updateLoggerContextVO();
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        updateLoggerContextVO();
    }

    public final Logger getLogger(final Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    //通过工厂对象LoggerContext：得到对应的日志对象；最终形成了以root为顶级父日志为首的日志树；
    public final Logger getLogger(final String name) {
        //如果name是null，抛出异常；
        if (name == null) {
            throw new IllegalArgumentException("name argument cannot be null");
        }
        //如果类名为root,返回根root:
        if (Logger.ROOT_LOGGER_NAME.equalsIgnoreCase(name)) {
            return root;
        }
        int i = 0;
        Logger logger = root;
        //检查一下请求的Logger是否已经创建过了，如果已经创建过，就直接从loggerCache中返回
        Logger childLogger = (Logger) loggerCache.get(name);
        if (childLogger != null) {
            return childLogger;
        }
        String childName;
        //如果还没创建过，那就开始逐层创建:
        //比如请求的Logger的name是com.company.package.ClassName，那么一共会创建4个Logger，
        // 分别是Logger[com]、Logger[com.company]、Logger[com.company.package]、Logger[com.company.package.ClassName]
        while (true) {
            //第一次循环 i=0,则截取com.jiaboyan.log.logTest中的com,将其设置为childName，并创建日志对象；
            // 此后依次截取的值为com.jiaboyan、com.jiaboyan.log、com.jiaboyan.log.logTest，来创建对象；
            // 并将子对象添加到其父对象的childrenList集合中去；
            int h = LoggerNameUtil.getSeparatorIndexOf(name, i);//因为i不断增加，也就是截取com.jiaboyan.log.logTest字符串的起始角标不断增加，所以就出现了h为-1的情况
            if (h == -1) {
                //当h为-1的时候，也就是创建com.jiaboyan.log.logTest日志对象时候：
                //下面会初始化com.jiaboyan.log日志对象的childrenList属性，创建其子日志对象（com.jiaboyan.log.logTest）
                //最后返回：
                childName = name;
            } else {
                childName = name.substring(0, h);
            }
            i = h + 1;
            //设置同步锁，根root:
            synchronized (logger) {
                //通过前面截取出来的 名称 从根root中获取对应的子logger:
                childLogger = logger.getChildByName(childName);
                if (childLogger == null) {
                    //如果为空，就创建此日志对象的子logger;
                    //一般都是先建立在根root对象上；初始化了根对象的子日志对象集合；
                    childLogger = logger.createChildByName(childName);
                    //将子logger对象添加 logger对象缓存中去；
                    loggerCache.put(childName, childLogger);
                    //第一次的话，从0增加到1，代表创建的子日志对象数量；
                    incSize();
                }
            }
            //第一次创建完子日志对象后，将logger参数设置为子日志对象，逐渐对根root的子日志对象来操作；
            logger = childLogger;
            if (h == -1) {
                //返回我们的日志对象：
                return childLogger;
            }
        }
    }

    private void incSize() {
        size++;
    }

    int size() {
        return size;
    }

    /**
     * Check if the named logger exists in the hierarchy. If so return its
     * reference, otherwise returns <code>null</code>.
     *
     * @param name the name of the logger to search for.
     */
    public Logger exists(String name) {
        return (Logger) loggerCache.get(name);
    }

    final void noAppenderDefinedWarning(final Logger logger) {
        if (noAppenderWarning++ == 0) {
            getStatusManager().add(new WarnStatus("No appenders present in context [" + getName() + "] for logger [" + logger.getName() + "].", logger));
        }
    }

    public List<Logger> getLoggerList() {
        Collection<Logger> collection = loggerCache.values();
        List<Logger> loggerList = new ArrayList<Logger>(collection);
        Collections.sort(loggerList, new LoggerComparator());
        return loggerList;
    }

    public LoggerContextVO getLoggerContextRemoteView() {
        return loggerContextRemoteView;
    }

    public void setPackagingDataEnabled(boolean packagingDataEnabled) {
        this.packagingDataEnabled = packagingDataEnabled;
    }

    public boolean isPackagingDataEnabled() {
        return packagingDataEnabled;
    }

    /**
     * This method clears all internal properties, except internal status messages,
     * closes all appenders, removes any turboFilters, fires an OnReset event,
     * removes all status listeners, removes all context listeners
     * (except those which are reset resistant).
     * <p/>
     * As mentioned above, internal status messages survive resets.
     */
    @Override
    public void reset() {
        resetCount++;
        super.reset();
        initEvaluatorMap();
        initCollisionMaps();
        root.recursiveReset();
        resetTurboFilterList();
        cancelScheduledTasks();
        fireOnReset();
        resetListenersExceptResetResistant();
        resetStatusListeners();
    }

    private void cancelScheduledTasks() {
        for(ScheduledFuture<?> sf: scheduledFutures) {
            sf.cancel(false);
        }
        scheduledFutures.clear();
    }

    private void resetStatusListeners() {
        StatusManager sm = getStatusManager();
        for (StatusListener sl : sm.getCopyOfStatusListenerList()) {
            sm.remove(sl);
        }
    }

    public TurboFilterList getTurboFilterList() {
        return turboFilterList;
    }

    public void addTurboFilter(TurboFilter newFilter) {
        turboFilterList.add(newFilter);
    }

    /**
     * First processPriorToRemoval all registered turbo filters and then clear the registration
     * list.
     */
    public void resetTurboFilterList() {
        for (TurboFilter tf : turboFilterList) {
            tf.stop();
        }
        turboFilterList.clear();
    }

    //该方法主要是在Logger要记录日志之前，用TurboFilter做一下过滤先，
    // 如果没有配置TurboFilter，就跳过过滤；否则的话，用TurboFilter做一下过滤。这部分内容涉及到fitler过滤
    final FilterReply getTurboFilterChainDecision_0_3OrMore(final Marker marker, final Logger logger, final Level level, final String format,
                    final Object[] params, final Throwable t) {
        if (turboFilterList.size() == 0) {
            return FilterReply.NEUTRAL;
        }
        return turboFilterList.getTurboFilterChainDecision(marker, logger, level, format, params, t);
    }

    final FilterReply getTurboFilterChainDecision_1(final Marker marker, final Logger logger, final Level level, final String format, final Object param,
                    final Throwable t) {
        if (turboFilterList.size() == 0) {
            return FilterReply.NEUTRAL;
        }
        return turboFilterList.getTurboFilterChainDecision(marker, logger, level, format, new Object[] { param }, t);
    }

    final FilterReply getTurboFilterChainDecision_2(final Marker marker, final Logger logger, final Level level, final String format, final Object param1,
                    final Object param2, final Throwable t) {
        if (turboFilterList.size() == 0) {
            return FilterReply.NEUTRAL;
        }
        return turboFilterList.getTurboFilterChainDecision(marker, logger, level, format, new Object[] { param1, param2 }, t);
    }

    // === start listeners ==============================================
    public void addListener(LoggerContextListener listener) {
        loggerContextListenerList.add(listener);
    }

    public void removeListener(LoggerContextListener listener) {
        loggerContextListenerList.remove(listener);
    }

    private void resetListenersExceptResetResistant() {
        List<LoggerContextListener> toRetain = new ArrayList<LoggerContextListener>();

        for (LoggerContextListener lcl : loggerContextListenerList) {
            if (lcl.isResetResistant()) {
                toRetain.add(lcl);
            }
        }
        loggerContextListenerList.retainAll(toRetain);
    }

    private void resetAllListeners() {
        loggerContextListenerList.clear();
    }

    public List<LoggerContextListener> getCopyOfListenerList() {
        return new ArrayList<LoggerContextListener>(loggerContextListenerList);
    }

    void fireOnLevelChange(Logger logger, Level level) {
        for (LoggerContextListener listener : loggerContextListenerList) {
            listener.onLevelChange(logger, level);
        }
    }

    private void fireOnReset() {
        for (LoggerContextListener listener : loggerContextListenerList) {
            listener.onReset(this);
        }
    }

    private void fireOnStart() {
        for (LoggerContextListener listener : loggerContextListenerList) {
            listener.onStart(this);
        }
    }

    private void fireOnStop() {
        for (LoggerContextListener listener : loggerContextListenerList) {
            listener.onStop(this);
        }
    }

    // === end listeners ==============================================

    public void start() {
        super.start();
        fireOnStart();
    }

    public void stop() {
        reset();
        fireOnStop();
        resetAllListeners();
        super.stop();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + getName() + "]";
    }

    public int getMaxCallerDataDepth() {
        return maxCallerDataDepth;
    }

    public void setMaxCallerDataDepth(int maxCallerDataDepth) {
        this.maxCallerDataDepth = maxCallerDataDepth;
    }

    /**
     * List of packages considered part of the logging framework such that they are never considered
     * as callers of the logging framework. This list used to compute the caller for logging events.
     * <p/>
     * To designate package "com.foo" as well as all its subpackages as being part of the logging framework, simply add
     * "com.foo" to this list.
     *
     * @return list of framework packages
     */
    public List<String> getFrameworkPackages() {
        return frameworkPackages;
    }
}
