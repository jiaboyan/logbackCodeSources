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


    //��logback�����ļ��еı�ǩ���д���
    public void play(List<SaxEvent> aSaxEventList) {
        //����������SaxEvent���󼯺ϣ���ֵ��EventPlayer�е�eventList���ԣ�
        eventList = aSaxEventList;
        SaxEvent se;
        //����SaxEvent���϶���
        for (currentIndex = 0; currentIndex < eventList.size(); currentIndex++) {
            //�õ������еĵ�һ��Ԫ�أ�
            se = eventList.get(currentIndex);
            // ����SaxEvent���ԣ�������ģʽ��������ʼԪ�ر�ǩ,������Ԫ�ر�ǩ����������Ԫ�ر�ǩ��
            // ���磺 ��ʼ��<configuration>   ��Ԫ�أ�<appender>  ����Ԫ�أ�</configuration>
            if (se instanceof StartEvent) {
                //��ʼ��ǩԪ�أ�
                interpreter.startElement((StartEvent) se);
                interpreter.getInterpretationContext().fireInPlay(se);
            }
            if (se instanceof BodyEvent) {
                // ��Ԫ�ر�ǩ:
                interpreter.getInterpretationContext().fireInPlay(se);
                interpreter.characters((BodyEvent) se);
            }
            if (se instanceof EndEvent) {
                // ����Ԫ�ر�ǩ:
                interpreter.getInterpretationContext().fireInPlay(se);
                interpreter.endElement((EndEvent) se);
            }
        }
    }

    public void addEventsDynamically(List<SaxEvent> eventList, int offset) {
        this.eventList.addAll(currentIndex + offset, eventList);
    }
}
