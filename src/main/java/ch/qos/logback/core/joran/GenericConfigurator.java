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

    //����logback�����ļ��Ľ�����
    public final void doConfigure(URL url) throws JoranException {
        InputStream in = null;
        try {
            //Ϊcontext��������ConfigurationWatchList���ԣ�ConfigurationWatchList�б�����logback�������ļ���ַ��
            informContextOfURLUsedForConfiguration(getContext(), url);
            //�������ļ���URL��ת����InputStream��
            URLConnection urlConnection = url.openConnection();
            urlConnection.setUseCaches(false);
            in = urlConnection.getInputStream();
            //��������doConfigure����:
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

    //���������ļ���
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

    //logback�����ļ�������
    public final void doConfigure(final InputSource inputSource) throws JoranException {
        long threshold = System.currentTimeMillis();
        //SaxEventRecorder�࣬���������ȡXML�ļ���
        SaxEventRecorder recorder = new SaxEventRecorder(context);
        //���������е����ݽ���xml������SAX��ʽ��
        // �����ļ���ÿ����ǩΪһ��SaxEvent���󣬲���ӵ�saxEventList������ȥ��
        recorder.recordEvents(inputSource);
        //��������GenericConfigurator�� doConfigure����:�������б�ǩ������SaxEvent���󼯺ϣ�
        doConfigure(recorder.saxEventList);
        StatusUtil statusUtil = new StatusUtil(context);
        if (statusUtil.noXMLParsingErrorsOccurred(threshold)) {
            addInfo("Registering current configuration as safe fallback point");
            registerSafeConfiguration(recorder.saxEventList);
        }
    }

    //���ķ��� buildInterpreter() ����ʼ����interpreter�ֶΣ�
    protected void buildInterpreter() {
        //RuleStoreֻ��һ��Ψһ��ʵ����SimpleRuleStore
        //����һ��logback�����ļ��� ��ǩ�������ֿ⣺
        RuleStore rs = new SimpleRuleStore(context);
        // ���磺<appender>��<appender-ref>��ǩ������һ����Ӧ��Action�����������Ԫ�ؽ��д���
        // �� ����ǩԪ�أ�����Ԫ�ص�Action����ӵ�RuleStore���е�hashMap��
        addInstanceRules(rs);
        this.interpreter = new Interpreter(context, rs, initialElementPath());
        //interpretationContext��Action���ʵ�ʴ�������У�����󲿷ֶ���ĳ�ջ����ջ���������������ʵ�֣���Ϊ���а�����һ��Stack���ϣ�
        InterpretationContext interpretationContext = interpreter.getInterpretationContext();
        interpretationContext.setContext(context);
        addImplicitRules(interpreter);
        addDefaultNestedComponentRegistryRules(interpretationContext.getDefaultNestedComponentRegistry());
    }

    //�������������ݵ���logback�����ļ��� �����б�ǩת���ɵ�SaxEvent���󼯺ϣ�
    public void doConfigure(final List<SaxEvent> eventList) throws JoranException {
        //���ķ���GenericConfigurator���buildInterpreter() ��
        // ��ʼ����interpreter�����Լ�interpretationContext���ԣ�
        //interpreter���Դ�����Interpreter���󣬸ö���Ϊ���������ļ��ṩ�˷���
        //interpretationContext���Դ�����InterpretationContext���󣬸ö����ṩ��LoggerContext�ĳ���ջ������
        buildInterpreter();
        //����ͬ������new LogbackLock()����
        synchronized (context.getConfigurationLock()) {
            // ����EventPlayer(��buildInterpreter�г�ʼ����)�������� ��ǩת���ɵ�SaxEvent���󼯺ϣ��������б�ǩ��Ԫ���߼�����ĵط���
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
