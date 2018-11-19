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
package ch.qos.logback.classic.joran.action;

import java.net.URL;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.xml.sax.Attributes;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.ReconfigureOnChangeTask;
import ch.qos.logback.classic.util.EnvUtil;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.joran.action.Action;
import ch.qos.logback.core.joran.spi.InterpretationContext;
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.util.ContextUtil;
import ch.qos.logback.core.util.Duration;
import ch.qos.logback.core.util.OptionHelper;
import ch.qos.logback.core.util.StatusListenerConfigHelper;

public class ConfigurationAction extends Action {
    static final String INTERNAL_DEBUG_ATTR = "debug";
    static final String PACKAGING_DATA_ATTR = "packagingData";
    static final String SCAN_ATTR = "scan";
    static final String SCAN_PERIOD_ATTR = "scanPeriod";
    static final String DEBUG_SYSTEM_PROPERTY_KEY = "logback.debug";

    long threshold = 0;

    //具体解析 logback配置文件 标签元素：不同的标签有不同的解析对象；
    // ConfigurationAction解析 <configuration>元素的；
    public void begin(InterpretationContext ic, String name, Attributes attributes) {
        //获得系统时间：
        threshold = System.currentTimeMillis();
        // 从系统属性中 查找  logback.debug 的值：
        String debugAttrib = getSystemProperty(DEBUG_SYSTEM_PROPERTY_KEY);
        if (debugAttrib == null) {
            // 如果没获取到的话，就从<configuration>中获取 debug 属性的值：  <configuration debug="true">
            debugAttrib = ic.subst(attributes.getValue(INTERNAL_DEBUG_ATTR));
        }
        //判断debug属性是否为空，或者值为false,又或者为null:
        if (OptionHelper.isEmpty(debugAttrib) || debugAttrib.equalsIgnoreCase("false") || debugAttrib.equalsIgnoreCase("null")) {
            addInfo(INTERNAL_DEBUG_ATTR + " attribute not set");
        } else {
            //如果不为空，或者不为null,值为true的话，就为loggerContext添加监听器：
            StatusListenerConfigHelper.addOnConsoleListenerInstance(context, new OnConsoleStatusListener());
        }

        //判断<Configutation>节点中属性，是否拥有 scan、scanPeriod属性；
        // 当scan为true时，配置文件如果发生改变，将会被重新加载，默认值为true。
        // scanPeriod设置监测文件是否有修改的时间间隔，默认单位是毫秒。当scan为true时，此属性生效。默认的时间间隔为1分钟。
        processScanAttrib(ic, attributes);

        ContextUtil contextUtil = new ContextUtil(context);
        contextUtil.addHostNameAsProperty();

        LoggerContext lc = (LoggerContext) context;
        boolean packagingData = OptionHelper.toBoolean(ic.subst(attributes.getValue(PACKAGING_DATA_ATTR)), LoggerContext.DEFAULT_PACKAGING_DATA);
        lc.setPackagingDataEnabled(packagingData);

        if (EnvUtil.isGroovyAvailable()) {
            contextUtil.addGroovyPackages(lc.getFrameworkPackages());
        }
        //pushObject(Object item)方法压入对象到堆栈的顶部，将LoggerContext压入到栈顶；
        ic.pushObject(getContext());
    }

    String getSystemProperty(String name) {
        /*
         * LOGBACK-743: accessing a system property in the presence of a SecurityManager (e.g. applet sandbox) can
         * result in a SecurityException.
         */
        try {
            return System.getProperty(name);
        } catch (SecurityException ex) {
            return null;
        }
    }

    void processScanAttrib(InterpretationContext ic, Attributes attributes) {
        String scanAttrib = ic.subst(attributes.getValue(SCAN_ATTR));
        if (!OptionHelper.isEmpty(scanAttrib) && !"false".equalsIgnoreCase(scanAttrib)) {

            ScheduledExecutorService scheduledExecutorService = context.getScheduledExecutorService();
            URL mainURL = ConfigurationWatchListUtil.getMainWatchURL(context);
            if (mainURL == null) {
                addWarn("Due to missing top level configuration file, reconfiguration on change (configuration file scanning) cannot be done.");
                return;
            }
            ReconfigureOnChangeTask rocTask = new ReconfigureOnChangeTask();
            rocTask.setContext(context);

            context.putObject(CoreConstants.RECONFIGURE_ON_CHANGE_TASK, rocTask);

            String scanPeriodAttrib = ic.subst(attributes.getValue(SCAN_PERIOD_ATTR));
            Duration duration = getDuration(scanAttrib, scanPeriodAttrib);

            if (duration == null) {
                return;
            }

            addInfo("Will scan for changes in [" + mainURL + "] ");
            // Given that included files are encountered at a later phase, the complete list of files 
            // to scan can only be determined when the configuration is loaded in full.
            // However, scan can be active if mainURL is set. Otherwise, when changes are detected
            // the top level config file cannot be accessed.
            addInfo("Setting ReconfigureOnChangeTask scanning period to " + duration);
 
            ScheduledFuture<?> scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(rocTask, duration.getMilliseconds(), duration.getMilliseconds(),
                            TimeUnit.MILLISECONDS);
            context.addScheduledFuture(scheduledFuture);
        }
    }

    private Duration getDuration(String scanAttrib, String scanPeriodAttrib) {
        Duration duration = null;

        if (!OptionHelper.isEmpty(scanPeriodAttrib)) {
            try {
                duration = Duration.valueOf(scanPeriodAttrib);

            } catch (NumberFormatException nfe) {
                addError("Error while converting [" + scanAttrib + "] to long", nfe);
            }
        }
        return duration;
    }

    public void end(InterpretationContext ec, String name) {
        addInfo("End of configuration.");
        ec.popObject();
    }
}
