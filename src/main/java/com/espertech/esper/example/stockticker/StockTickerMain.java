/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.example.stockticker;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPRuntimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

public class StockTickerMain implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(StockTickerMain.class);

    private final String runtimeURI;

    public static void main(String[] args) {
        new StockTickerMain("IotEventRuntime").run();
    }

    public StockTickerMain(String runtimeURI) {
        this.runtimeURI = runtimeURI;
    }

    public void run() {
        Configuration configuration = IotEventEPLUtil.getConfiguration();
        EPCompiled compiled = IotEventEPLUtil.compileEPL(configuration);

        log.info("Setting up runtime");
        EPRuntime runtime = EPRuntimeProvider.getRuntime(runtimeURI, configuration);
        runtime.initialize();

        log.info("Deploying compiled EPL");
        IotEventEPLUtil.deploy(runtime, compiled);

        log.info("Generating test events");
        IotEventGenerator generator = new IotEventGenerator();
        LinkedList<Object> stream = generator.createEventStream();
        log.info("Generated " + stream.size() + " events");

        log.info("Sending events");
        for (Object event : stream) {
            runtime.getEventService().sendEventBean(event, "iotEvent");
        }

        log.info("Done.");
    }
}
