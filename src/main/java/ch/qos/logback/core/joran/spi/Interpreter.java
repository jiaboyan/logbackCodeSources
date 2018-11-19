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
package ch.qos.logback.core.joran.spi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.joran.action.Action;
import ch.qos.logback.core.joran.action.ImplicitAction;
import ch.qos.logback.core.joran.event.BodyEvent;
import ch.qos.logback.core.joran.event.EndEvent;
import ch.qos.logback.core.joran.event.StartEvent;
import ch.qos.logback.core.spi.ContextAwareImpl;

/**
 * <id>Interpreter</id> is Joran's main driving class. It extends SAX
 * {@link org.xml.sax.helpers.DefaultHandler DefaultHandler} which invokes
 * various {@link Action actions} according to predefined patterns.
 * 
 * <p>
 * Patterns are kept in a {@link RuleStore} which is programmed to store and
 * then later produce the applicable actions for a given pattern.
 * 
 * <p>
 * The pattern corresponding to a top level &lt;a&gt; element is the string
 * <id>"a"</id>.
 * 
 * <p>
 * The pattern corresponding to an element &lt;b&gt; embedded within a top level
 * &lt;a&gt; element is the string <id>"a/b"</id>.
 * 
 * <p>
 * The pattern corresponding to an &lt;b&gt; and any level of nesting is
 * "&#42;/b. Thus, the &#42; character placed at the beginning of a pattern
 * serves as a wildcard for the level of nesting.
 * 
 * Conceptually, this is very similar to the API of commons-digester. Joran
 * offers several small advantages. First and foremost, it offers support for
 * implicit actions which result in a significant leap in flexibility. Second,
 * in our opinion better error reporting capability. Third, it is self-reliant.
 * It does not depend on other APIs, in particular commons-logging which is too
 * unreliable. Last but not least, Joran is quite tiny and is expected to remain
 * so.
 * 
 * @author Ceki G&uuml;lcu&uuml;
 * 
 */
public class Interpreter {
    private static List<Action> EMPTY_LIST = new Vector<Action>(0);

    final private RuleStore ruleStore;
    final private InterpretationContext interpretationContext;
    final private ArrayList<ImplicitAction> implicitActions;
    final private CAI_WithLocatorSupport cai;
    private ElementPath elementPath;
    Locator locator;
    EventPlayer eventPlayer;

    /**
     * The <id>actionListStack</id> contains a list of actions that are executing
     * for the given XML element.
     * 
     * A list of actions is pushed by the {link #startElement} and popped by
     * {@link #endElement}.
     * 
     */
    Stack<List<Action>> actionListStack;

    /**
     * If the skip nested is set, then we skip all its nested elements until it is
     * set back to null at when the element's end is reached.
     */
    ElementPath skip = null;

    public Interpreter(Context context, RuleStore rs, ElementPath initialElementPath) {
        this.cai = new CAI_WithLocatorSupport(context, this);
        ruleStore = rs;
        interpretationContext = new InterpretationContext(context, this);
        implicitActions = new ArrayList<ImplicitAction>(3);
        this.elementPath = initialElementPath;
        actionListStack = new Stack<List<Action>>();
        eventPlayer = new EventPlayer(this);
    }

    public EventPlayer getEventPlayer() {
        return eventPlayer;
    }

    public void setInterpretationContextPropertiesMap(Map<String, String> propertiesMap) {
        interpretationContext.setPropertiesMap(propertiesMap);
    }

    /**
     * @deprecated replaced by {@link #getInterpretationContext()}
     */
    public InterpretationContext getExecutionContext() {
        return getInterpretationContext();
    }

    public InterpretationContext getInterpretationContext() {
        return interpretationContext;
    }

    public void startDocument() {
    }

    public void startElement(StartEvent se) {
        //设置文档的Locator:这个类中可以确定当前元素在文档的中正确位置；包含着当前元素的行号、列号
        // Locator类有几个很有用的方法，例如getLineNumber()和getColumnNumber()，它们可以返回调用时正在解析的XML文档的位置
        setDocumentLocator(se.getLocator());
        //任何处理过程都有开始和结束。这两个重要的事件都只会发生一次：前者在所有其他解析事件发生之前发生，而后者则是在所有解析事件之后发生。
        // SAX解析提供了回调方法：startDocument()和endDocument()来表示这些事件。
        startElement(se.namespaceURI, se.localName, se.qName, se.attributes);
    }
    //startDocument()方法在所有其他解析事件的回调方法之前被调用，这样就保证了解析有一个明确的起始点。
    private void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
        //解析得到标签名：TagName：
        String tagName = getTagName(localName, qName);
        //将 标签名字 添加到elementPath属性的arrayList集合中：
        elementPath.push(tagName);

        if (skip != null) {
            pushEmptyActionList();
            return;
        }
        // elementPath属性中保存有解析到的标签元素：
        // 例如“configuration”,通过标签元素的名称从RoleStore中获取对应的 元素标签处理Action集合；
        List<Action> applicableActionList = getApplicableActionList(elementPath, atts);
        if (applicableActionList != null) {
            //actionListStack是 Satck对象实例；
            // Stack继承Vector类，栈的特点是后进先出。
            actionListStack.add(applicableActionList);
            //然后调用callBeginAction()方法来处理，如果SaxEvent类型是EndEvent，则这里是调用callEndAction()方法
            callBeginAction(applicableActionList, tagName, atts);
        } else {
            // every startElement pushes an action list
            pushEmptyActionList();
            String errMsg = "no applicable action for [" + tagName + "], current ElementPath  is [" + elementPath + "]";
            cai.addError(errMsg);
        }
    }

    /**
     * This method is used to
     */
    private void pushEmptyActionList() {
        actionListStack.add(EMPTY_LIST);
    }

    public void characters(BodyEvent be) {

        setDocumentLocator(be.locator);

        String body = be.getText();
        List<Action> applicableActionList = actionListStack.peek();

        if (body != null) {
            body = body.trim();
            if (body.length() > 0) {
                // System.out.println("calling body method with ["+body+ "]");
                callBodyAction(applicableActionList, body);
            }
        }
    }

    public void endElement(EndEvent endEvent) {
        setDocumentLocator(endEvent.locator);
        endElement(endEvent.namespaceURI, endEvent.localName, endEvent.qName);
    }

    private void endElement(String namespaceURI, String localName, String qName) {
        // given that an action list is always pushed for every startElement, we
        // need
        // to always pop for every endElement
        List<Action> applicableActionList = (List<Action>) actionListStack.pop();

        if (skip != null) {
            if (skip.equals(elementPath)) {
                skip = null;
            }
        } else if (applicableActionList != EMPTY_LIST) {
            callEndAction(applicableActionList, getTagName(localName, qName));
        }

        // given that we always push, we must also pop the pattern
        elementPath.pop();
    }

    public Locator getLocator() {
        return locator;
    }

    public void setDocumentLocator(Locator l) {
        locator = l;
    }

    String getTagName(String localName, String qName) {
        String tagName = localName;

        if ((tagName == null) || (tagName.length() < 1)) {
            tagName = qName;
        }

        return tagName;
    }

    public void addImplicitAction(ImplicitAction ia) {
        implicitActions.add(ia);
    }

    /**
     * Check if any implicit actions are applicable. As soon as an applicable
     * action is found, it is returned. Thus, the returned list will have at most
     * one element.
     */
    List<Action> lookupImplicitAction(ElementPath elementPath, Attributes attributes, InterpretationContext ec) {
        int len = implicitActions.size();

        for (int i = 0; i < len; i++) {
            ImplicitAction ia = (ImplicitAction) implicitActions.get(i);

            if (ia.isApplicable(elementPath, attributes, ec)) {
                List<Action> actionList = new ArrayList<Action>(1);
                actionList.add(ia);

                return actionList;
            }
        }

        return null;
    }

    /**
     * Attributes就是解析出来的标签中的属性：<configuration level="debug">，只有为空的时候才有用
     */
    List<Action> getApplicableActionList(ElementPath elementPath, Attributes attributes) {
        List<Action> applicableActionList = ruleStore.matchActions(elementPath);
        if (applicableActionList == null) {
            applicableActionList = lookupImplicitAction(elementPath, attributes, interpretationContext);
        }

        return applicableActionList;
    }

    //遍历 标签处理的Action集合对象，对对应的标签进行处理：
    void callBeginAction(List<Action> applicableActionList, String tagName, Attributes atts) {
        if (applicableActionList == null) {
            return;
        }
        // 遍历 配置文件 标签元素对应的Action：
        Iterator<Action> i = applicableActionList.iterator();
        while (i.hasNext()) {
            Action action = (Action) i.next();
            try {
                //根据得到不同的Action:调用其自身的begin方法:每一个action都有着不同的逻辑处理；
                // 例如:标签configuration,对应的Action就是 ConfigurationAction
                action.begin(interpretationContext, tagName, atts);
            } catch (ActionException e) {
                skip = elementPath.duplicate();
                cai.addError("ActionException in Action for tag [" + tagName + "]", e);
            } catch (RuntimeException e) {
                skip = elementPath.duplicate();
                cai.addError("RuntimeException in Action for tag [" + tagName + "]", e);
            }
        }
    }

    private void callBodyAction(List<Action> applicableActionList, String body) {
        if (applicableActionList == null) {
            return;
        }
        Iterator<Action> i = applicableActionList.iterator();

        while (i.hasNext()) {
            Action action = i.next();
            try {
                action.body(interpretationContext, body);
            } catch (ActionException ae) {
                cai.addError("Exception in end() methd for action [" + action + "]", ae);
            }
        }
    }

    private void callEndAction(List<Action> applicableActionList, String tagName) {
        if (applicableActionList == null) {
            return;
        }

        // logger.debug("About to call end actions on node: [" + localName + "]");
        Iterator<Action> i = applicableActionList.iterator();

        while (i.hasNext()) {
            Action action = i.next();
            // now let us invoke the end method of the action. We catch and report
            // any eventual exceptions
            try {
                action.end(interpretationContext, tagName);
            } catch (ActionException ae) {
                // at this point endAction, there is no point in skipping children as
                // they have been already processed
                cai.addError("ActionException in Action for tag [" + tagName + "]", ae);
            } catch (RuntimeException e) {
                // no point in setting skip
                cai.addError("RuntimeException in Action for tag [" + tagName + "]", e);
            }
        }
    }

    public RuleStore getRuleStore() {
        return ruleStore;
    }
}

/**
 * When {@link Interpreter} class is used as the origin of an
 * {@link ContextAwareImpl} instance, then XML locator information is lost. This
 * class preserves locator information (as a string).
 * 
 * @author ceki
 */
class CAI_WithLocatorSupport extends ContextAwareImpl {

    CAI_WithLocatorSupport(Context context, Interpreter interpreter) {
        super(context, interpreter);
    }

    @Override
    protected Object getOrigin() {
        Interpreter i = (Interpreter) super.getOrigin();
        Locator locator = i.locator;
        if (locator != null) {
            return Interpreter.class.getName() + "@" + locator.getLineNumber() + ":" + locator.getColumnNumber();
        } else {
            return Interpreter.class.getName() + "@NA:NA";
        }
    }
}
