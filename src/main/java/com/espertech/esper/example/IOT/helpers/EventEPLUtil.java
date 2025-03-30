package com.espertech.esper.example.IOT.helpers;

import com.espertech.esper.example.IOT.SensorData.SensorData;
import com.espertech.esper.example.IOT.streams.DeviceCommand;
import com.espertech.esper.example.IOT.streams.PersonView;
import com.espertech.esper.example.IOT.streams.EmbeddingFeature;
import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.common.client.util.NameAccessModifier;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.example.IOT.listeners.ClustersListeners.CluStreamListener;
import com.espertech.esper.example.IOT.listeners.ClustersListeners.AgglomerativeClusteringListener;
import com.espertech.esper.example.IOT.listeners.ClustersListeners.ClusTreeListener;
import com.espertech.esper.runtime.client.EPDeployment;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPStatement;
import com.espertech.esper.runtime.client.UpdateListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventEPLUtil {
    private static final Logger log = LoggerFactory.getLogger(EventEPLUtil.class);

    public static Configuration getConfiguration() {
        Configuration configuration = new Configuration();
        configuration.getCommon().addEventType("sensorData", SensorData.class);
        configuration.getCommon().addEventType("deviceCommand", DeviceCommand.class);
        configuration.getCommon().addEventType("personView", PersonView.class);
        configuration.getCommon().addEventType("embeddingFeature", EmbeddingFeature.class);
        return configuration;
    }

    public static void compileDeployAddListener(EPRuntime runtime, String eplQuery, UpdateListener listener){
        EPStatement statement;
        statement = EventEPLUtil.compileDeploy(runtime, eplQuery);
        EventEPLUtil.add_listener(statement, listener);
    }

    public static void compileDeployAddListener_with_Agglomerative_clustering(EPRuntime runtime, String eplQuery){
        EPStatement statement;
        statement = EventEPLUtil.compileDeploy(runtime, eplQuery);
        AgglomerativeClusteringListener clusterListener = new AgglomerativeClusteringListener();
        clusterListener.addListener(statement);
    }

    public static void compileDeployAddListener_with_clu_clustering(EPRuntime runtime, String eplQuery, int numClusters){
        EPStatement statement;
        statement = EventEPLUtil.compileDeploy(runtime, eplQuery);
        CluStreamListener clusterListener = new CluStreamListener(numClusters);
        clusterListener.addListener(statement);
    }

    public static void compileDeployAddListener_with_ClusTree(EPRuntime runtime, String eplQuery){
        EPStatement statement;
        statement = EventEPLUtil.compileDeploy(runtime, eplQuery);
        ClusTreeListener clusterListener = new ClusTreeListener();
        clusterListener.addListener(statement);
    }

    public static EPStatement compileDeploy(EPRuntime runtime, String epl) {
        try {
            CompilerArguments args = new CompilerArguments();
            args.getPath().add(runtime.getRuntimePath());
            args.getOptions().setAccessModifierEventType(env -> NameAccessModifier.PUBLIC);

            EPCompiled compiled = EPCompilerProvider.getCompiler().compile(epl, args);
            EPDeployment deployment = runtime.getDeploymentService().deploy(compiled);
            return deployment.getStatements()[0];
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void add_listener(EPStatement statement, UpdateListener listener){
        // EPStatement statement = runtime.getDeploymentService().getStatement(deploymentId, eplQuery_name);
        if (statement != null) {
            statement.addListener(listener);
        } else {
            log.error("Statement not found: 'out'");
        }
    }
}
