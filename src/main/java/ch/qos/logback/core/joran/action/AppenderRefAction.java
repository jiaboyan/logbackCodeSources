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
package ch.qos.logback.core.joran.action;

import org.xml.sax.Attributes;

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.joran.spi.InterpretationContext;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.util.OptionHelper;

import java.util.HashMap;

// 举例： <appender-ref ref="FILE" />
public class AppenderRefAction<E> extends Action {
    boolean inError = false;

    @SuppressWarnings("unchecked")
    public void begin(InterpretationContext ec, String tagName, Attributes attributes) {
        // Let us forget about previous errors (in this object)
        inError = false;
        //由于<appender-ref>元素肯定是嵌套在<logger>里，所以ec.peekObject()方法取出的就是刚刚创建的Logger实例
        Object o = ec.peekObject();
        if (!(o instanceof AppenderAttachable)) {
            String errMsg = "Could not find an AppenderAttachable at the top of execution stack. Near [" + tagName + "] line " + getLineNumber(ec);
            inError = true;
            addError(errMsg);
            return;
        }
        AppenderAttachable<E> appenderAttachable = (AppenderAttachable<E>) o;
        String appenderName = ec.subst(attributes.getValue(ActionConst.REF_ATTRIBUTE));
        if (OptionHelper.isEmpty(appenderName)) {
            // print a meaningful error message and return
            String errMsg = "Missing appender ref attribute in <appender-ref> tag.";
            inError = true;
            addError(errMsg);
            return;
        }
        HashMap<String, Appender<E>> appenderBag = (HashMap<String, Appender<E>>) ec.getObjectMap().get(ActionConst.APPENDER_BAG);
        // 从InterpretationContext的AppenderBag中取出来：之前创建的Appender实例；
        Appender<E> appender = (Appender<E>) appenderBag.get(appenderName);
        if (appender == null) {
            String msg = "Could not find an appender named [" + appenderName + "]. Did you define it below instead of above in the configuration file?";
            inError = true;
            addError(msg);
            addError("See " + CoreConstants.CODES_URL + "#appender_order for more details.");
            return;
        }
        addInfo("Attaching appender named [" + appenderName + "] to " + appenderAttachable);
        //将此appender赋值给logger：
        appenderAttachable.addAppender(appender);
    }

    public void end(InterpretationContext ec, String n) {
    }

}
