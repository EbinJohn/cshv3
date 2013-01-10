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
package com.cloud.hypervisor.hyperv.test;

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
import com.cloud.agent.api.GetHostStatsAnswer;
import com.cloud.agent.api.GetHostStatsCommand;
import com.cloud.agent.api.GetStorageStatsAnswer;
import com.cloud.agent.api.GetStorageStatsCommand;
import com.cloud.agent.api.GetVmStatsAnswer;
import com.cloud.agent.api.GetVmStatsCommand;
import com.cloud.agent.api.StartAnswer;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StopAnswer;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.VmStatsEntry;
import com.cloud.agent.api.storage.PrimaryStorageDownloadAnswer;
import com.cloud.agent.api.storage.PrimaryStorageDownloadCommand;
import com.cloud.agent.api.storage.CreateAnswer;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.api.storage.DestroyAnswer;
import com.cloud.agent.api.storage.DestroyCommand;

import com.cloud.hypervisor.hyperv.resource.HypervResource;

import org.apache.log4j.Logger;

import com.cloud.serializer.GsonHelper;
import com.cloud.storage.Storage.ImageFormat;
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
 * General mechanism for calling Hyper-V agent command processing methods.
 *
 * mvn exec:java -Dexec.mainClass=com.cloud.agent.TestHyperv
 */
public class TestHyperv {
    private static final Logger s_logger = Logger.getLogger(TestHyperv.class.getName());
    
    // TODO:  make this a config parameter
    protected static final Gson s_gson = GsonHelper.getGson();
    protected static final HypervResource s_hypervresource = new HypervResource();
       
    public TestHyperv() {
    }
    
    public static void initialise() throws ConfigurationException
    {
       	// Seed /conf folder with log4j.xml into class path 
        final ComponentLocator locator = ComponentLocator.getLocator("agent");

        // Obtain script locations from agent.properties
        final Map<String, Object> params = PropertiesUtil.toMap(loadProperties());
        
        s_hypervresource.configure("hypervresource",  params);
    }
    
    public static void main(String[] args) throws ConfigurationException {
    	initialise();
//    	SampleJsonFromPrimaryStorageDownloadCommand();
    	TestGetHostStatsCommand();
    	TestGetVmStatsCommand();
    	TestGetStorageStatsCommand();
    	TestCreateCommand();
    	TestStartCommand();
    	TestStopCommand();
    	TestDestroyCommand();
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

    public static void TestStartCommand()
    {
       	String sample = "{\"vm\":{\"id\":6,\"name\":\"i-2-6-VM\",\"type\":\"User\",\"cpus\":1,\"speed\":500," +
       	             "\"minRam\":536870912,\"maxRam\":536870912,\"arch\":\"x86_64\"," +
       	             "\"os\":\"CentOS 6.0 (64-bit)\",\"bootArgs\":\"\",\"rebootOnCrash\":false," +
       	             "\"enableHA\":false,\"limitCpuUse\":false,\"vncPassword\":\"7e24c0da0e848ad4\"," +
       	             "\"params\":{},\"uuid\":\"3ff475a7-0ee8-44d6-970d-64fe776beb92\"," +
       	             "\"disks\":[" +
       	                     "{\"id\":6,\"name\":\"E:\\\\Disks\\\\Disks\",\"mountPoint\":\"FakeVolume\"," +
       	             "\"path\":\"FakeVolume\",\"size\":0,\"type\":\"ROOT\",\"storagePoolType\":\"Filesystem\"," +
       	             "\"storagePoolUuid\":\"5fe2bad3-d785-394e-9949-89786b8a63d2\",\"deviceId\":0}," +
       	                     "{\"id\":6,\"name\":\"Hyper-V Sample1\",\"size\":0,\"type\":\"ISO\",\"storagePoolType\":\"ISO\",\"deviceId\":3}" +
       	                     "]," +
       	             "\"nics\":[" +
       	                     "{\"deviceId\":0,\"networkRateMbps\":100,\"defaultNic\":true,\"uuid\":" +
       	             "\"e146bb95-4ee4-4b9f-8d61-62cb21f7224e\",\"ip\":\"10.1.1.164\",\"netmask\":\"255.255.255.0\"," +
       	             "\"gateway\":\"10.1.1.1\",\"mac\":\"02:00:67:06:00:04\",\"dns1\":\"4.4.4.4\",\"broadcastType\":\"Vlan\"," +
       	             "\"type\":\"Guest\",\"broadcastUri\":\"vlan://261\",\"isolationUri\":\"vlan://261\"," +
       	             "\"isSecurityGroupEnabled\":false}" +
       	                     "]" +
       	             "},\"contextMap\":{},\"wait\":0}";
        s_logger.info("Sample JSON: " + sample );

       	StartCommand cmd = s_gson.fromJson(sample, StartCommand.class);
    	s_hypervresource.execute(cmd);
    }
    
    public static void TestStopCommand()
    {
    	String sample = "{\"isProxy\":false,\"vmName\":\"i-2-6-VM\",\"contextMap\":{},\"wait\":0}";
    
    	s_logger.info("Sample JSON: " + sample );

    	StopCommand cmd = s_gson.fromJson(sample, StopCommand.class);
    	s_hypervresource.execute(cmd);
    }

    public static void TestCreateCommand()
    {
    	// TODO:  update when CreateStoragePool works.
    	String sample = "{\"volId\":10,\"pool\":{\"id\":201,\"uuid\":\"5fe2bad3-d785-394e-9949-89786b8a63d2\",\"host\":\"10.70.176.29\"" +
    					",\"path\":\"E:\\\\Disks\\\\Disks\",\"port\":0,\"type\":\"Filesystem\"},\"diskCharacteristics\":{\"size\":0," +
    					"\"tags\":[],\"type\":\"ROOT\",\"name\":\"ROOT-9\",\"useLocalStorage\":true,\"recreatable\":true,\"diskOfferingId\":11," +
    					"\"volumeId\":10,\"hyperType\":\"Hyperv\"},\"templateUrl\":\"e:\\\\Disks\\\\Disks\\\\SampleHyperVCentOS63VM.vhdx\"," +
    					"\"contextMap\":{},\"wait\":0}";

    	s_logger.info("Sample JSON: " + sample );

    	CreateCommand cmd = s_gson.fromJson(sample, CreateCommand.class);
    	s_hypervresource.execute(cmd);
    }

    public static void TestDestroyCommand()
    {
    	// TODO:  update when CreateStoragePool works.
    	// TODO:  how does the command vary when we are only deleting a volume?
    	String sample = "{\"vmName\":\"i-2-6-VM\",\"volume\":{\"id\":9,\"name\":\"ROOT-8\",\"mountPoint\":\"E:\\\\Disks\\\\Disks\"," +
    					"\"path\":\"FakeVolume\",\"size\":0,\"type\":\"ROOT\",\"storagePoolType\":\"Filesystem\"," +
    					"\"storagePoolUuid\":\"5fe2bad3-d785-394e-9949-89786b8a63d2\",\"deviceId\":0},\"contextMap\":{},\"wait\":0}";

    	s_logger.info("Sample JSON: " + sample );

    	DestroyCommand cmd = s_gson.fromJson(sample, DestroyCommand.class);
    	s_hypervresource.execute(cmd);
    }

    public static void TestGetStorageStatsCommand()
    {
    	// TODO:  Update sample data to unsure it is using correct info.
    	String sample = "{\"id\":\"5fe2bad3-d785-394e-9949-89786b8a63d2\",\"localPath\":\"E:\\\\Disks\\\\Disks\"," +
    					"\"pooltype\":\"Filesystem\",\"contextMap\":{},\"wait\":0}";
    	       
    	s_logger.info("Sample JSON: " + sample );

    	GetStorageStatsCommand cmd = s_gson.fromJson(sample, GetStorageStatsCommand.class);
    	s_hypervresource.execute(cmd);
    }
    
    public static void TestGetHostStatsCommand()
    {
    	String sample = "{\"hostGuid\":\"B4AE5970-FCBF-4780-9F8A-2D2E04FECC34-HypervResource\",\"hostName\":\"CC-SVR11\",\"hostId\":5,\"contextMap\":{},\"wait\":0}";
    
    	s_logger.info("Sample JSON: " + sample );

    	GetHostStatsCommand cmd = s_gson.fromJson(sample, GetHostStatsCommand.class);
    	s_hypervresource.execute(cmd);
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

    	return toJson(answer);
   }

    public static String SampleJsonFromPrimaryStorageDownloadCommand()
    {
    	PrimaryStorageDownloadCommand cmd = new PrimaryStorageDownloadCommand(
    			"routing-9",
    			"nfs://10.70.176.4/CSHV3/template/tmpl/1/9/",
    			ImageFormat.VHD,
        		1, 
        		201, 
        		"5fe2bad3-d785-394e-9949-89786b8a63d2", 
        		10800);
    	String result = toJson(cmd);
    	s_logger.debug("Converting a " + cmd.getClass().getName() + " to JSON: " + result );
    	return result;
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
