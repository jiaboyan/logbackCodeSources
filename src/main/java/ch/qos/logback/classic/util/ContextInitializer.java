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

    //ContextInitializer的构造方法：对LoggerContext进行赋值操作；
    //logback传递过来的context对象--private LoggerContext defaultLoggerContext = new LoggerContext();
    public ContextInitializer(LoggerContext loggerContext) {
        this.loggerContext = loggerContext;
    }

    // 进行配置文件的解析：
    public void configureByResource(URL url) throws JoranException {
        //如果url为空，抛出异常：
        if (url == null) {
            throw new IllegalArgumentException("URL argument cannot be null");
        }
        final String urlString = url.toString();
        //如果配置文件是以groovy结尾的：
        if (urlString.endsWith("groovy")) {
            if (EnvUtil.isGroovyAvailable()) {
                GafferUtil.runGafferConfiguratorOn(loggerContext, this, url);
            } else {
                StatusManager sm = loggerContext.getStatusManager();
                sm.add(new ErrorStatus("Groovy classes are not available on the class path. ABORTING INITIALIZATION.", loggerContext));
            }
        //如果配置文件是以xml结尾的：
        } else if (urlString.endsWith("xml")) {
            //使用 JoranConfigurator 进行解析：JoranConfigurator是ContextAwareBase的子类；
            JoranConfigurator configurator = new JoranConfigurator();
            //为configurator（ContextAwareBase）对象设置context属性:
            configurator.setContext(loggerContext);
            //调用JoranConfigurator类的父类(GenericConfigurator)方法进行解析：
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

    //读取系统配置：logback.configurationFile  尝试了三次读取；
    private URL findConfigFileURLFromSystemProperties(ClassLoader classLoader, boolean updateStatus) {
        String logbackConfigFile = OptionHelper.getSystemProperty(CONFIG_FILE_PROPERTY);
        if (logbackConfigFile != null) {
            URL result = null;
            try {
                //首先 判断 获取的值 是不是 url，如果不是url抛出异常；
                result = new URL(logbackConfigFile);
                return result;
            } catch (MalformedURLException e) {
                // 再从classpath下，获取对应的资源文件；
                result = Loader.getResource(logbackConfigFile, classLoader);
                if (result != null) {
                    return result;
                }
                //如果没获取到，则在尝试看看 是不是文件路径；
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


    //读取配置文件：logback.groovy  logback-test.xml  logback.xml，或者资源参数logback.configurationFile；
    public URL findURLOfDefaultConfigurationFile(boolean updateStatus) {
        ClassLoader myClassLoader = Loader.getClassLoaderOfObject(this);
        //此步骤读取：系统配置“logback.configurationFile" 参数，一般通过 ”java -Dlogback.configurationFile=*“设置；
        URL url = findConfigFileURLFromSystemProperties(myClassLoader, updateStatus);
        if (url != null) {
            return url;
        }
        //获取 logback.groovy 资源文件
        url = getResource(GROOVY_AUTOCONFIG_FILE, myClassLoader, updateStatus);
        if (url != null) {
            return url;
        }

        //获取 logback-test.xml 资源文件：
        url = getResource(TEST_AUTOCONFIG_FILE, myClassLoader, updateStatus);
        if (url != null) {
            return url;
        }
        // 获取 logback.xml 资源文件：
        return getResource(AUTOCONFIG_FILE, myClassLoader, updateStatus);
    }

    private URL getResource(String filename, ClassLoader myClassLoader, boolean updateStatus) {
        URL url = Loader.getResource(filename, myClassLoader);
        if (updateStatus) {
            statusOnResourceSearch(filename, myClassLoader, url);
        }
        return url;
    }

    // logback创建ContextInitializer对象后，在调用此方法进行配置文件读取：
    // 对ContextInitializer类中的loggerContext属性进行初始化：
    public void autoConfig() throws JoranException {
        //默认情况下，在Logback运行过程中，如果内部碰到Warning和Error状态，那么相应的内部状态信息会被打印出来；
        // 否则，不会有任何内部状态信息被打印出来。假如想要打印任何内部状态下的信息，可以通过设置监听器的方式来实现：OnConsoleStatusListener
        StatusListenerConfigHelper.installIfAsked(loggerContext);
        //寻找资源logback的资源文件：从系统配置中获取文件的路径，
        // 从classpath下获取logback.groovy 资源文件， logback-test.xml，logback.xml ：
        URL url = findURLOfDefaultConfigurationFile(true);
        if (url != null) {
            // 进行配置文件的解析：解析成功，初始化结束loggerFactory（loggerContext）结束；
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
