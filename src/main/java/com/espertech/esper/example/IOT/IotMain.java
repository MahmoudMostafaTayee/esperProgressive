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

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.runtime.client.DeploymentOptions;
import com.espertech.esper.runtime.client.EPDeployException;
import com.espertech.esper.runtime.client.EPDeployment;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPRuntimeProvider;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

public class IotMain implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(IotMain.class);

    private final String runtimeURI;
    private EPRuntime runtime;
    private Configuration configuration;

    public static void main(String[] args) {
        new IotMain("IotEventRuntime").run();
    }

    public IotMain(String runtimeURI) {
        this.runtimeURI = runtimeURI;
    }

    private void initiateRunTime(){
        configuration = EventEPLUtil.getConfiguration();
        log.info("Setting up runtime");

        runtime = EPRuntimeProvider.getRuntime(runtimeURI, configuration);
        runtime.initialize();
    }

    private void deploy(String eplQuery, String deploymentId){
        EPCompiled compiled = EventEPLUtil.compileEPL(configuration, eplQuery);
        log.info("Deploying compiled EPL");
        DeploymentOptions options = new DeploymentOptions();
        options.setDeploymentId(deploymentId);

        try {
            runtime.getDeploymentService().deploy(compiled, options);
        } catch (EPDeployException e) {
            log.error("Deployment failed", e);
            return; // Stop further processing since deployment failed
        }
    }

    private void add_listener(String deploymentId, String eplQuery_name, UpdateListener listener){
        EPStatement statement = runtime.getDeploymentService().getStatement(deploymentId, eplQuery_name);
        if (statement != null) {
            statement.addListener(listener);
        } else {
            log.error("Statement not found: 'out'");
        }
    }

    private void add_generator(IotStreamGenerator generator){
        log.info("Generating and sending events with time advancement");
        generator.generateEvents(runtime);
    }

    public void run() {
        initiateRunTime();

        String deploymentId = "MatchQuery";

        String eplQuery_name = "out"; 
        String eplQuery = "@name('" + eplQuery_name + "') select * from sensorData output all every 4 seconds order by timestamp;";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as total from sensorData output last every 2 seconds;";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as total from sensorData#time(4);";
        // String eplQuery = "@name('out') select count(*) as count_num, sum(value) as total from sensorData#time(5);";
        
        deploy(eplQuery, deploymentId);
        add_listener(deploymentId, eplQuery_name, new IotEventListener());
        add_generator(new IotStreamGenerator());
        
    
        log.info("Done.");
    }    
}
