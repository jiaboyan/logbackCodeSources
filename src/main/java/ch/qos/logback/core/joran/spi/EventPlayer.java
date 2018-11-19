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
import java.util.List;

import ch.qos.logback.core.joran.event.BodyEvent;
import ch.qos.logback.core.joran.event.EndEvent;
import ch.qos.logback.core.joran.event.SaxEvent;
import ch.qos.logback.core.joran.event.StartEvent;

public class EventPlayer {

    final Interpreter interpreter;
    List<SaxEvent> eventList;
    int currentIndex;

    public EventPlayer(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    /**
     * Return a copy of the current event list in the player.
     * @return
     * @since 0.9.20
     */
    public List<SaxEvent> getCopyOfPlayerEventList() {
        return new ArrayList<SaxEvent>(eventList);
    }


    //对logback配置文件中的标签进行处理：
    public void play(List<SaxEvent> aSaxEventList) {
        //将传过来的SaxEvent对象集合，赋值给EventPlayer中的eventList属性；
        eventList = aSaxEventList;
        SaxEvent se;
        //遍历SaxEvent集合对象：
        for (currentIndex = 0; currentIndex < eventList.size(); currentIndex++) {
            //得到集合中的第一个元素：
            se = eventList.get(currentIndex);
            // 解析SaxEvent属性，有三种模式：解析开始元素标签,解析子元素标签，解析结束元素标签；
            // 例如： 开始：<configuration>   子元素：<appender>  结束元素：</configuration>
            if (se instanceof StartEvent) {
                //开始标签元素：
                interpreter.startElement((StartEvent) se);
                interpreter.getInterpretationContext().fireInPlay(se);
            }
            if (se instanceof BodyEvent) {
                // 子元素标签:
                interpreter.getInterpretationContext().fireInPlay(se);
                interpreter.characters((BodyEvent) se);
            }
            if (se instanceof EndEvent) {
                // 结束元素标签:
                interpreter.getInterpretationContext().fireInPlay(se);
                interpreter.endElement((EndEvent) se);
            }
        }
    }

    public void addEventsDynamically(List<SaxEvent> eventList, int offset) {
        this.eventList.addAll(currentIndex + offset, eventList);
    }
}
