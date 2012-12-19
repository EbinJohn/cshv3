// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.agent;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.Process;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.naming.ConfigurationException;

import com.cloud.agent.api.Answer;

import com.cloud.agent.api.Command;
import com.cloud.agent.api.GetVmStatsAnswer;
import com.cloud.agent.api.GetVmStatsCommand;
import com.cloud.agent.api.VmStatsEntry;
import com.cloud.agent.resource.HypervResource;

import org.apache.log4j.Logger;

import com.cloud.serializer.GsonHelper;
import com.cloud.utils.PropertiesUtil;
import com.cloud.utils.component.ComponentLocator;
import com.cloud.utils.exception.CloudRuntimeException;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;


/*
 * General mechanism for calling Python scripts.
 * 
 * mvn exec:java -Dexec.mainClass=com.cloud.agent.PythonLaunch
 */
public class PythonLaunch {
    private static final Logger s_logger = Logger.getLogger(PythonLaunch.class.getName());
    
    // TODO:  make this a config parameter
    protected static final Gson s_gson = GsonHelper.getGson();
    protected static final HypervResource s_hypervresource = new HypervResource();
       
    public PythonLaunch() {
    }
    
    public static void initialise() throws ConfigurationException
    {
       	// Seed /conf folder with log4j.xml into class path 
        final ComponentLocator locator = ComponentLocator.getLocator("agent");

        // Obtain script locations from agent.properties
        final Map<String, Object> params = PropertiesUtil.toMap(PythonLaunch.loadProperties());
        
        s_hypervresource.configure("hypervresource",  params);
    }
    
    public static void main(String[] args) throws ConfigurationException {
    	PythonLaunch.initialise();
        
    	PythonLaunch.TestGetVmStatsCommand();
        return;
    }
    
    public static void TestGetVmStatsCommand()
    {
       	// Sample GetVmStatsCommand
    	List<String> vmNames = new ArrayList<String>();
    	vmNames.add("TestCentOS6.3");
    	GetVmStatsCommand vmStatsCmd = new GetVmStatsCommand(vmNames, "1", "localhost");

    	s_hypervresource.execute(vmStatsCmd);
    }
    
    public static String SampleJsonFromGetVmStatsAnswer()
    {
    	// Sample GetVmStatsCommand
    	List<String> vmNames = new ArrayList<String>();
    	vmNames.add("TestCentOS6.3");
    	vmNames.add("otherVM");
    	GetVmStatsCommand vmStatsCmd = new GetVmStatsCommand(vmNames, "1", "localhost");

    	VmStatsEntry vmInfo = new VmStatsEntry(69, 69.9, 69.9, 1, "vm");
    	VmStatsEntry vmInfo2 = new VmStatsEntry(100, 100.0, 100.0, 2, "vm");
    	
    	HashMap<String, VmStatsEntry> vmStatsMap = new HashMap<String, VmStatsEntry>();
    	vmStatsMap.put("TestCentOS6.3", vmInfo);
    	vmStatsMap.put("otherVM", vmInfo2);
    	
    	GetVmStatsAnswer answer = new GetVmStatsAnswer(vmStatsCmd, vmStatsMap);

    	return PythonLaunch.toJson(answer);
   }
    
    // TODO: Unicode issues?
    public static String toJson(Command cmd) {
        String result = s_gson.toJson(cmd, cmd.getClass());
    	s_logger.debug("Converting a " + cmd.getClass().getName() + " to JSON: " + result );
        return result;
    }
   
    public static Properties loadProperties() throws ConfigurationException {
    	Properties _properties = new Properties();
        final File file = PropertiesUtil.findConfigFile("agent.properties");
        if (file == null) {
            throw new ConfigurationException("Unable to find agent.properties.");
        }

        s_logger.info("agent.properties found at " + file.getAbsolutePath());

        try {
            _properties.load(new FileInputStream(file));
        } catch (final FileNotFoundException ex) {
            throw new CloudRuntimeException("Cannot find the file: "
                    + file.getAbsolutePath(), ex);
        } catch (final IOException ex) {
            throw new CloudRuntimeException("IOException in reading "
                    + file.getAbsolutePath(), ex);
        }
		return _properties;
    }

}
