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
package com.espertech.esper.example.IOT;

import com.espertech.esper.example.IOT.IotEvent.IotEventListener;
import com.espertech.esper.example.IOT.IotEvent.IotEventGenerator;
import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.runtime.client.DeploymentOptions;
import com.espertech.esper.runtime.client.EPDeployException;
import com.espertech.esper.runtime.client.EPDeployment;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPRuntimeProvider;
import com.espertech.esper.runtime.client.EPStatement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

public class IotMain implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(IotMain.class);

    private final String runtimeURI;

    public static void main(String[] args) {
        new IotMain("IotEventRuntime").run();
    }

    public IotMain(String runtimeURI) {
        this.runtimeURI = runtimeURI;
    }

    public void run() {
        Configuration configuration = EventEPLUtil.getConfiguration();
        EPCompiled compiled = EventEPLUtil.compileEPL(configuration);
    
        log.info("Setting up runtime");
        EPRuntime runtime = EPRuntimeProvider.getRuntime(runtimeURI, configuration);
        runtime.initialize();
    
        log.info("Deploying compiled EPL");
        DeploymentOptions options = new DeploymentOptions();
        options.setDeploymentId("MatchQuery");

        try {
            runtime.getDeploymentService().deploy(compiled, options);
        } catch (EPDeployException e) {
            log.error("Deployment failed", e);
            return; // Stop further processing since deployment failed
        }
    
        EPStatement statement = runtime.getDeploymentService().getStatement("MatchQuery", "out");
        if (statement != null) {
            statement.addListener(new IotEventListener());
        } else {
            log.error("Statement not found: 'out'");
        }
    
        log.info("Generating and sending events with time advancement");
        IotEventGenerator generator = new IotEventGenerator();
        generator.generateEvents(runtime);
    
        log.info("Done.");
    }    
}
