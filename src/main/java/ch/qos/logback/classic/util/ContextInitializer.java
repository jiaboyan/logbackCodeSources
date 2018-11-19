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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

import ch.qos.logback.classic.BasicConfigurator;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.gaffer.GafferUtil;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.core.LogbackException;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.status.WarnStatus;
import ch.qos.logback.core.util.Loader;
import ch.qos.logback.core.util.OptionHelper;
import ch.qos.logback.core.util.StatusListenerConfigHelper;

// contributors
// Ted Graham, Matt Fowles, see also http://jira.qos.ch/browse/LBCORE-32

/**
 * This class contains logback's logic for automatic configuration
 *
 * @author Ceki Gulcu
 */
public class ContextInitializer {

    final public static String GROOVY_AUTOCONFIG_FILE = "logback.groovy";
    final public static String AUTOCONFIG_FILE = "logback.xml";
    final public static String TEST_AUTOCONFIG_FILE = "logback-test.xml";
    final public static String CONFIG_FILE_PROPERTY = "logback.configurationFile";

    final LoggerContext loggerContext;

    //ContextInitializer�Ĺ��췽������LoggerContext���и�ֵ������
    //logback���ݹ�����context����--private LoggerContext defaultLoggerContext = new LoggerContext();
    public ContextInitializer(LoggerContext loggerContext) {
        this.loggerContext = loggerContext;
    }

    // ���������ļ��Ľ�����
    public void configureByResource(URL url) throws JoranException {
        //���urlΪ�գ��׳��쳣��
        if (url == null) {
            throw new IllegalArgumentException("URL argument cannot be null");
        }
        final String urlString = url.toString();
        //��������ļ�����groovy��β�ģ�
        if (urlString.endsWith("groovy")) {
            if (EnvUtil.isGroovyAvailable()) {
                GafferUtil.runGafferConfiguratorOn(loggerContext, this, url);
            } else {
                StatusManager sm = loggerContext.getStatusManager();
                sm.add(new ErrorStatus("Groovy classes are not available on the class path. ABORTING INITIALIZATION.", loggerContext));
            }
        //��������ļ�����xml��β�ģ�
        } else if (urlString.endsWith("xml")) {
            //ʹ�� JoranConfigurator ���н�����JoranConfigurator��ContextAwareBase�����ࣻ
            JoranConfigurator configurator = new JoranConfigurator();
            //Ϊconfigurator��ContextAwareBase����������context����:
            configurator.setContext(loggerContext);
            //����JoranConfigurator��ĸ���(GenericConfigurator)�������н�����
            configurator.doConfigure(url);
        } else {
            throw new LogbackException("Unexpected filename extension of file [" + url.toString() + "]. Should be either .groovy or .xml");
        }
    }

    void joranConfigureByResource(URL url) throws JoranException {
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);
        configurator.doConfigure(url);
    }

    //��ȡϵͳ���ã�logback.configurationFile  ���������ζ�ȡ��
    private URL findConfigFileURLFromSystemProperties(ClassLoader classLoader, boolean updateStatus) {
        String logbackConfigFile = OptionHelper.getSystemProperty(CONFIG_FILE_PROPERTY);
        if (logbackConfigFile != null) {
            URL result = null;
            try {
                //���� �ж� ��ȡ��ֵ �ǲ��� url���������url�׳��쳣��
                result = new URL(logbackConfigFile);
                return result;
            } catch (MalformedURLException e) {
                // �ٴ�classpath�£���ȡ��Ӧ����Դ�ļ���
                result = Loader.getResource(logbackConfigFile, classLoader);
                if (result != null) {
                    return result;
                }
                //���û��ȡ�������ڳ��Կ��� �ǲ����ļ�·����
                File f = new File(logbackConfigFile);
                if (f.exists() && f.isFile()) {
                    try {
                        result = f.toURI().toURL();
                        return result;
                    } catch (MalformedURLException e1) {
                    }
                }
            } finally {
                if (updateStatus) {
                    statusOnResourceSearch(logbackConfigFile, classLoader, result);
                }
            }
        }
        return null;
    }


    //��ȡ�����ļ���logback.groovy  logback-test.xml  logback.xml��������Դ����logback.configurationFile��
    public URL findURLOfDefaultConfigurationFile(boolean updateStatus) {
        ClassLoader myClassLoader = Loader.getClassLoaderOfObject(this);
        //�˲����ȡ��ϵͳ���á�logback.configurationFile" ������һ��ͨ�� ��java -Dlogback.configurationFile=*�����ã�
        URL url = findConfigFileURLFromSystemProperties(myClassLoader, updateStatus);
        if (url != null) {
            return url;
        }
        //��ȡ logback.groovy ��Դ�ļ�
        url = getResource(GROOVY_AUTOCONFIG_FILE, myClassLoader, updateStatus);
        if (url != null) {
            return url;
        }

        //��ȡ logback-test.xml ��Դ�ļ���
        url = getResource(TEST_AUTOCONFIG_FILE, myClassLoader, updateStatus);
        if (url != null) {
            return url;
        }
        // ��ȡ logback.xml ��Դ�ļ���
        return getResource(AUTOCONFIG_FILE, myClassLoader, updateStatus);
    }

    private URL getResource(String filename, ClassLoader myClassLoader, boolean updateStatus) {
        URL url = Loader.getResource(filename, myClassLoader);
        if (updateStatus) {
            statusOnResourceSearch(filename, myClassLoader, url);
        }
        return url;
    }

    // logback����ContextInitializer������ڵ��ô˷������������ļ���ȡ��
    // ��ContextInitializer���е�loggerContext���Խ��г�ʼ����
    public void autoConfig() throws JoranException {
        //Ĭ������£���Logback���й����У�����ڲ�����Warning��Error״̬����ô��Ӧ���ڲ�״̬��Ϣ�ᱻ��ӡ������
        // ���򣬲������κ��ڲ�״̬��Ϣ����ӡ������������Ҫ��ӡ�κ��ڲ�״̬�µ���Ϣ������ͨ�����ü������ķ�ʽ��ʵ�֣�OnConsoleStatusListener
        StatusListenerConfigHelper.installIfAsked(loggerContext);
        //Ѱ����Դlogback����Դ�ļ�����ϵͳ�����л�ȡ�ļ���·����
        // ��classpath�»�ȡlogback.groovy ��Դ�ļ��� logback-test.xml��logback.xml ��
        URL url = findURLOfDefaultConfigurationFile(true);
        if (url != null) {
            // ���������ļ��Ľ����������ɹ�����ʼ������loggerFactory��loggerContext��������
            configureByResource(url);
        } else {
            Configurator c = EnvUtil.loadFromServiceLoader(Configurator.class);
            if (c != null) {
                try {
                    c.setContext(loggerContext);
                    c.configure(loggerContext);
                } catch (Exception e) {
                    throw new LogbackException(String.format("Failed to initialize Configurator: %s using ServiceLoader", c != null ? c.getClass().getCanonicalName() : "null"), e);
                }
            } else {
                BasicConfigurator basicConfigurator = new BasicConfigurator();
                basicConfigurator.setContext(loggerContext);
                basicConfigurator.configure(loggerContext);
            }
        }
    }

    private void statusOnResourceSearch(String resourceName, ClassLoader classLoader, URL url) {
        StatusManager sm = loggerContext.getStatusManager();
        if (url == null) {
            sm.add(new InfoStatus("Could NOT find resource [" + resourceName + "]", loggerContext));
        } else {
            sm.add(new InfoStatus("Found resource [" + resourceName + "] at [" + url.toString() + "]", loggerContext));
            multiplicityWarning(resourceName, classLoader);
        }
    }

    private void multiplicityWarning(String resourceName, ClassLoader classLoader) {
        Set<URL> urlSet = null;
        StatusManager sm = loggerContext.getStatusManager();
        try {
            urlSet = Loader.getResources(resourceName, classLoader);
        } catch (IOException e) {
            sm.add(new ErrorStatus("Failed to get url list for resource [" + resourceName + "]", loggerContext, e));
        }
        if (urlSet != null && urlSet.size() > 1) {
            sm.add(new WarnStatus("Resource [" + resourceName + "] occurs multiple times on the classpath.", loggerContext));
            for (URL url : urlSet) {
                sm.add(new WarnStatus("Resource [" + resourceName + "] occurs at [" + url.toString() + "]", loggerContext));
            }
        }
    }
}
