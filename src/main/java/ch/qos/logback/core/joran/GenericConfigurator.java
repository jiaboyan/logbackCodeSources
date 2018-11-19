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
package ch.qos.logback.core.joran;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.joran.event.SaxEvent;
import ch.qos.logback.core.joran.event.SaxEventRecorder;
import ch.qos.logback.core.joran.spi.*;
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.status.StatusUtil;

import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import static ch.qos.logback.core.CoreConstants.SAFE_JORAN_CONFIGURATION;

public abstract class GenericConfigurator extends ContextAwareBase {

    private final BeanDescriptionCache beanDescriptionCache = new BeanDescriptionCache();

    protected Interpreter interpreter;

    //进行logback配置文件的解析：
    public final void doConfigure(URL url) throws JoranException {
        InputStream in = null;
        try {
            //为context对象设置ConfigurationWatchList属性：ConfigurationWatchList中保存着logback的配置文件地址；
            informContextOfURLUsedForConfiguration(getContext(), url);
            //打开配置文件的URL，转化成InputStream：
            URLConnection urlConnection = url.openConnection();
            urlConnection.setUseCaches(false);
            in = urlConnection.getInputStream();
            //继续调用doConfigure方法:
            doConfigure(in);
        } catch (IOException ioe) {
            String errMsg = "Could not open URL [" + url + "].";
            addError(errMsg, ioe);
            throw new JoranException(errMsg, ioe);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ioe) {
                    String errMsg = "Could not close input stream";
                    addError(errMsg, ioe);
                    throw new JoranException(errMsg, ioe);
                }
            }
        }
    }

    public final void doConfigure(String filename) throws JoranException {
        doConfigure(new File(filename));
    }

    public final void doConfigure(File file) throws JoranException {
        FileInputStream fis = null;
        try {
            informContextOfURLUsedForConfiguration(getContext(), file.toURI().toURL());
            fis = new FileInputStream(file);
            doConfigure(fis);
        } catch (IOException ioe) {
            String errMsg = "Could not open [" + file.getPath() + "].";
            addError(errMsg, ioe);
            throw new JoranException(errMsg, ioe);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (java.io.IOException ioe) {
                    String errMsg = "Could not close [" + file.getName() + "].";
                    addError(errMsg, ioe);
                    throw new JoranException(errMsg, ioe);
                }
            }
        }
    }

    public static void informContextOfURLUsedForConfiguration(Context context, URL url) {
        ConfigurationWatchListUtil.setMainWatchURL(context, url);
    }

    //解析配置文件：
    public final void doConfigure(InputStream inputStream) throws JoranException {
        doConfigure(new InputSource(inputStream));
    }

    protected BeanDescriptionCache getBeanDescriptionCache() {
        return beanDescriptionCache;
    }

    protected abstract void addInstanceRules(RuleStore rs);

    protected abstract void addImplicitRules(Interpreter interpreter);

    protected void addDefaultNestedComponentRegistryRules(DefaultNestedComponentRegistry registry) {

    }

    protected ElementPath initialElementPath() {
        return new ElementPath();
    }

    //logback配置文件解析：
    public final void doConfigure(final InputSource inputSource) throws JoranException {
        long threshold = System.currentTimeMillis();
        //SaxEventRecorder类，由它负责读取XML文件，
        SaxEventRecorder recorder = new SaxEventRecorder(context);
        //对输入流中的内容进行xml解析，SAX方式：
        // 配置文件中每个标签为一个SaxEvent对象，并添加到saxEventList集合中去；
        recorder.recordEvents(inputSource);
        //继续调用GenericConfigurator的 doConfigure方法:传递所有标签解析成SaxEvent对象集合；
        doConfigure(recorder.saxEventList);
        StatusUtil statusUtil = new StatusUtil(context);
        if (statusUtil.noXMLParsingErrorsOccurred(threshold)) {
            addInfo("Registering current configuration as safe fallback point");
            registerSafeConfiguration(recorder.saxEventList);
        }
    }

    //核心方法 buildInterpreter() ：初始化了interpreter字段；
    protected void buildInterpreter() {
        //RuleStore只有一个唯一的实现类SimpleRuleStore
        //创建一个logback配置文件中 标签处理规则仓库：
        RuleStore rs = new SimpleRuleStore(context);
        // 例如：<appender>、<appender-ref>标签，都有一个对应的Action，负责对这种元素进行处理；
        // 将 （标签元素，处理元素的Action）添加到RuleStore类中的hashMap；
        addInstanceRules(rs);
        this.interpreter = new Interpreter(context, rs, initialElementPath());
        //interpretationContext在Action组件实际处理过程中，负责大部分对象的出栈和入栈，都依赖这个类来实现，因为其中包含了一个Stack集合；
        InterpretationContext interpretationContext = interpreter.getInterpretationContext();
        interpretationContext.setContext(context);
        addImplicitRules(interpreter);
        addDefaultNestedComponentRegistryRules(interpretationContext.getDefaultNestedComponentRegistry());
    }

    //继续解析：传递的是logback配置文件中 ，所有标签转换成的SaxEvent对象集合：
    public void doConfigure(final List<SaxEvent> eventList) throws JoranException {
        //核心方法GenericConfigurator类的buildInterpreter() ：
        // 初始化了interpreter属性以及interpretationContext属性；
        //interpreter属性创建了Interpreter对象，该对象为解析配置文件提供了方法
        //interpretationContext属性创建了InterpretationContext对象，该对象提供了LoggerContext的出入栈工作；
        buildInterpreter();
        //设置同步锁：new LogbackLock()对象；
        synchronized (context.getConfigurationLock()) {
            // 交给EventPlayer(在buildInterpreter中初始化的)类来处理 标签转换成的SaxEvent对象集合：真正进行标签内元素逻辑处理的地方；
            interpreter.getEventPlayer().play(eventList);
        }
    }

    /**
     * Register the current event list in currently in the interpreter as a safe
     * configuration point.
     *
     * @since 0.9.30
     */
    public void registerSafeConfiguration(List<SaxEvent> eventList) {
        context.putObject(SAFE_JORAN_CONFIGURATION, eventList);
    }

    /**
     * Recall the event list previously registered as a safe point.
     */
    @SuppressWarnings("unchecked")
    public List<SaxEvent> recallSafeConfiguration() {
        return (List<SaxEvent>) context.getObject(SAFE_JORAN_CONFIGURATION);
    }
}
