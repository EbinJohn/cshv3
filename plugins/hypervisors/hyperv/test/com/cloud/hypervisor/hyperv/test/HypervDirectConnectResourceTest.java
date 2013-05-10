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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.cloud.agent.AgentShell;
import com.cloud.agent.api.Answer;

import com.cloud.agent.api.Command;
import com.cloud.agent.api.CreateStoragePoolCommand;
import com.cloud.agent.api.DeleteStoragePoolCommand;
import com.cloud.agent.api.GetHostStatsAnswer;
import com.cloud.agent.api.GetHostStatsCommand;
import com.cloud.agent.api.GetStorageStatsAnswer;
import com.cloud.agent.api.GetStorageStatsCommand;
import com.cloud.agent.api.GetVmStatsAnswer;
import com.cloud.agent.api.GetVmStatsCommand;
import com.cloud.agent.api.ModifyStoragePoolCommand;
import com.cloud.agent.api.StartAnswer;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.StartupStorageCommand;
import com.cloud.agent.api.StopAnswer;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.StoragePoolInfo;
import com.cloud.agent.api.VmStatsEntry;
import com.cloud.agent.api.StartupRoutingCommand.VmState;
import com.cloud.agent.api.storage.PrimaryStorageDownloadAnswer;
import com.cloud.agent.api.storage.PrimaryStorageDownloadCommand;

import com.cloud.agent.api.storage.CreateAnswer;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.api.storage.DestroyAnswer;
import com.cloud.agent.api.storage.DestroyCommand;

import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.hyperv.discoverer.HypervServerDiscoverer;
import com.cloud.hypervisor.hyperv.resource.HypervDirectConnectResource;
import com.cloud.hypervisor.hyperv.resource.HypervResource;
import com.cloud.hypervisor.hyperv.storage.HypervStoragePool;

import org.apache.log4j.Logger;

import com.cloud.network.Networks.RouterPrivateIpStrategy;
import com.cloud.serializer.GsonHelper;
import com.cloud.storage.Storage;
import com.cloud.storage.StoragePoolVO;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.utils.PropertiesUtil;
import com.cloud.utils.exception.CloudRuntimeException;

import com.google.gson.Gson;

/*
 * General mechanism for calling Hyper-V agent command processing methods.
 *
 * mvn exec:java -Dexec.mainClass=com.cloud.agent.TestHyperv
 */
public class HypervDirectConnectResourceTest {
    private static final Logger s_logger = Logger.getLogger(HypervDirectConnectResourceTest.class.getName());
    
    // TODO:  make this a config parameter
    protected static final Gson s_gson = GsonHelper.getGson();
    protected static final HypervDirectConnectResource s_hypervresource = new HypervDirectConnectResource();
    
    protected static final String testLocalStoreUUID = "5fe2bad3-d785-394e-9949-89786b8a63d2";
    protected static String testLocalStorePath = "." + File.separator + 
    		"var" + File.separator + "test" + File.separator + "storagepool";
    protected static final String testSecondaryStoreLocalPath = "." + File.separator + 
    		"var" + File.separator + "test" + File.separator + "secondary";
    
    // TODO: differentiate between NFS and HTTP template URLs.
    protected static final String testSampleTemplateUUID = "TestCopiedLocalTemplate.vhdx";
    protected static final String testSampleTemplateURL = testSampleTemplateUUID;
    
    // test volumes are both a minimal size vhdx.  Changing the extension to .vhd makes on corrupt.
    protected static final String testSampleVolumeWorkingUUID = "TestVolumeLegit.vhdx";
    protected static final String testSampleVolumeCorruptUUID = "TestVolumeCorrupt.vhd";
    protected static final String testSampleVolumeTempUUID = "TestVolumeTemp.vhdx";
    protected static String testSampleVolumeWorkingURIJSON;
    protected static String testSampleVolumeCorruptURIJSON;
    protected static String testSampleVolumeTempURIJSON;
    
    protected static String testSampleTemplateURLJSON;
    protected static String testLocalStorePathJSON;
    
    public HypervDirectConnectResourceTest() {
    }
    
    @Before
    public void setUp() throws ConfigurationException
    {
            // Obtain script locations from agent.properties
            final Map<String, Object> params = PropertiesUtil.toMap(loadProperties());
	        // Used to create existing StoragePool in preparation for the ModifyStoragePool
	        params.put("local.storage.uuid", testLocalStoreUUID);
	      
	        // Make sure secondary store is available.
	        File testSecondarStoreDir = new File(testSecondaryStoreLocalPath);
	        if (!testSecondarStoreDir.exists()) {
	        	testSecondarStoreDir.mkdir();
	        }
	        Assert.assertTrue("Need to be able to create the folder " + testSecondaryStoreLocalPath, 
	        				testSecondarStoreDir.exists());
	        try {
				params.put("local.secondary.storage.path", testSecondarStoreDir.getCanonicalPath());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
	        	Assert.fail("No canonical path for " + testSecondarStoreDir.getAbsolutePath());
			}

	        // Clean up old test files in local storage folder:
	        File testPoolDir = new File(testLocalStorePath);
	        Assert.assertTrue("To simulate local file system Storage Pool, you need folder at "  
	        			+ testPoolDir.getPath(), testPoolDir.exists() && testPoolDir.isDirectory());
	        try {
		        testLocalStorePath = testPoolDir.getCanonicalPath();
	        }
	        catch (IOException e)
	        {
	        	Assert.fail("No canonical path for " + testPoolDir.getAbsolutePath());
        	}
	        params.put("local.storage.path", testLocalStorePath);
	        
	        File testVolWorks = new File(testLocalStorePath + File.separator + testSampleVolumeWorkingUUID);
	        Assert.assertTrue("Create a corrupt virtual disk (by changing extension of vhdx to vhd) at "
	        					+ testVolWorks.getPath(), testVolWorks.exists());
	        try {
	        testSampleVolumeWorkingURIJSON  = s_gson.toJson(testVolWorks.getCanonicalPath());
		    }
		    catch (IOException e)
		    {
		    	Assert.fail("No canonical path for " + testPoolDir.getAbsolutePath());
		    }

	        FilenameFilter vhdsFilt = new FilenameFilter(){
	        	public boolean accept(File directory, String fileName) {
	        	    return fileName.endsWith(".vhdx") || fileName.endsWith(".vhd");
	        	}
	        };
	        for (File file : testPoolDir.listFiles(vhdsFilt)) {
	        	if (file.getName().equals(testVolWorks.getName()))
	        		continue;
	        	Assert.assertTrue("Should have deleted file "+file.getPath(), file.delete());
	        	s_logger.info("Cleaned up by delete file " + file.getPath() );
	        }

	        testSampleVolumeTempURIJSON = CreateTestDiskImageFromExistingImage(testVolWorks, testLocalStorePath, testSampleVolumeTempUUID);
        	s_logger.info("Created " + testSampleVolumeTempURIJSON );
	        testSampleVolumeCorruptURIJSON = CreateTestDiskImageFromExistingImage(testVolWorks, testLocalStorePath, testSampleVolumeCorruptUUID);
        	s_logger.info("Created " + testSampleVolumeCorruptURIJSON );
        	CreateTestDiskImageFromExistingImage(testVolWorks, testLocalStorePath, testSampleTemplateUUID);
	        testSampleTemplateURLJSON = testSampleTemplateUUID;
        	s_logger.info("Created " + testSampleTemplateURLJSON + " in local storage.");
	        
	        // Create secondary storage template:
        	CreateTestDiskImageFromExistingImage(testVolWorks, testSecondarStoreDir.getAbsolutePath(), "af39aa7f-2b12-37e1-86d3-e23f2f005101.vhdx");
        	s_logger.info("Created " + "af39aa7f-2b12-37e1-86d3-e23f2f005101.vhdx" + " in secondary (NFS) storage.");
        	
        	testLocalStorePathJSON = s_gson.toJson(testLocalStorePath);

        	params.put("agentIp", "localhost");
        	SetTestJsonResult(params);
        	s_hypervresource.configure("hypervresource",  params);
	        // Verify sample template is in place storage pool
        	s_logger.info("setUp complete, sample StoragePool at " + testLocalStorePathJSON 
        			+ " sample template at " + testSampleTemplateURLJSON);
    }

	private String CreateTestDiskImageFromExistingImage(File srcFile,
			String dstPath,
			String dstFileName) {
		String newFileURIJSON = null;
		{
		    File testVolTemp = new File(dstPath + File.separator + dstFileName);
		    try {
		        	Files.copy(srcFile.toPath(), testVolTemp.toPath());
		        }
		        catch (IOException e){
		        }
		    Assert.assertTrue("Should be a temporary file created from the valid volume) at "
		    					+ testVolTemp.getPath(), testVolTemp.exists());
		    try {
		    	newFileURIJSON  = s_gson.toJson(testVolTemp.getCanonicalPath());
		    }
		    catch (IOException e) 
		    {
		    	Assert.fail("No file at " + testVolTemp.getAbsolutePath());
		    }
		}
		return newFileURIJSON;
	}
    

    public void TestStartupCommand()
    {
		StartupRoutingCommand defaultStartRoutCmd = new StartupRoutingCommand(
				0, 0, 0, 0, null, Hypervisor.HypervisorType.Hyperv,
				RouterPrivateIpStrategy.HostLocal,
				new HashMap<String, VmState>());

		// Identity within the data centre is decided by CloudStack kernel,
		// and passed via ServerResource.configure()
		defaultStartRoutCmd.setDataCenter("1");
		defaultStartRoutCmd.setPod("1");
		defaultStartRoutCmd.setCluster("1");
		defaultStartRoutCmd.setGuid("1");
		defaultStartRoutCmd.setName("1");
		defaultStartRoutCmd.setPrivateIpAddress("1");
		defaultStartRoutCmd.setStorageIpAddress("1");
		defaultStartRoutCmd.setCpus(12);

		// TODO: does version need to be hard coded.
		defaultStartRoutCmd.setVersion("4.1.0");
		
		StartupCommand scmd = defaultStartRoutCmd;

		Command[] cmds = { scmd };
		String cmdsStr = s_gson.toJson(cmds);
		s_logger.debug("Commands[] toJson is " + cmdsStr);

		Command[]  result = s_gson.fromJson(cmdsStr, Command[].class);
		s_logger.debug("Commands[] fromJson is " + s_gson.toJson(result));
		s_logger.debug("Commands[] first element has type" + result[0].toString());
    }
    
    //@Test 
    public void TestJson() {
    	StartupStorageCommand sscmd = null;
    		com.cloud.agent.api.StoragePoolInfo pi = new com.cloud.agent.api.StoragePoolInfo(
                "test123", "192.168.0.1", "c:\\", "c:\\", 
                StoragePoolType.Filesystem, 100L, 50L);

        sscmd = new StartupStorageCommand();
        sscmd.setPoolInfo(pi);
        sscmd.setGuid(pi.getUuid());
        sscmd.setDataCenter("foo");
        sscmd.setResourceType(Storage.StorageResourceType.STORAGE_POOL);
		s_logger.debug("StartupStorageCommand fromJson is " + s_gson.toJson(sscmd));
    } 
    
    @Test
    public void TestInitialize() {
    	StartupCommand[] startCmds = s_hypervresource.initialize();
    	Command[] cmds = new Command[]{ startCmds[0], startCmds[1] };
        String result = s_gson.toJson(cmds);
        if (result == null ) {
        	result = "NULL";
        }
		s_logger.debug("TestInitialize returned " + result);
		s_logger.debug("TestInitialize expected " + s_SetTestJsonResultStr);
		Assert.assertEquals(s_SetTestJsonResultStr, result);
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
    
    protected String s_SetTestJsonResultStr = null;
    public void SetTestJsonResult(final Map<String, Object> params) 
    {
    	s_SetTestJsonResultStr =
 String.format("[{\"StartupRoutingCommand\":{" +
                        "\"cpus\":%s," +
                        "\"speed\":%s," +
                        "\"memory\":%s," +
                        "\"dom0MinMemory\":%s," +
                        "\"poolSync\":false," +
                        "\"vms\":{}," +
                        "\"hypervisorType\":\"Hyperv\"," +
                        "\"hostDetails\":{" +
                        "\"com.cloud.network.Networks.RouterPrivateIpStrategy\":\"HostLocal\"" +
                        "}," +
                        "\"type\":\"Routing\"," +
                        "\"dataCenter\":\"1\"," +
                        "\"pod\":\"1\"," +
                        "\"cluster\":\"1\"," +
                        "\"guid\":\"16f85622-4508-415e-b13a-49a39bb14e4d\"," +
                        "\"name\":\"hypervresource\"," +
                        "\"version\":\"4.1.0\"," +
                        "\"privateIpAddress\":%s," +
                        "\"privateMacAddress\":%s," +
                        "\"privateNetmask\":%s," +
                        "\"storageIpAddress\":%s," +
                        "\"storageNetmask\":%s," +
                        "\"storageMacAddress\":%s," +
                        "\"gatewayIpAddress\":%s," +
                        "\"contextMap\":{}," +
                        "\"wait\":0" +
                        "}}," +
                        "{\"StartupStorageCommand\":{" +
                        "\"totalSize\":0,"+
                        "\"poolInfo\":{" +
                        "\"uuid\":\"16f85622-4508-415e-b13a-49a39bb14e4d\"," +
                        "\"host\":\"localhost\"," +
                        "\"localPath\":%s," +
                        "\"hostPath\":%s," +
                        "\"poolType\":\"Filesystem\"," +
                        "\"capacityBytes\":995907072000," +
                        "\"availableBytes\":945659260928" +
                        "}," +
                        "\"resourceType\":\"STORAGE_POOL\"," +
                        "\"hostDetails\":{}," +
                        "\"type\":\"Storage\","+
                        "\"dataCenter\":\"1\"," +
                        "\"guid\":\"16f85622-4508-415e-b13a-49a39bb14e4d\"," +
                        "\"contextMap\":{}," +
                        "\"wait\":0" +
                        "}}]",
                        params.get("TestCoreCount"),
                        params.get("TestCoreMhz"),
                        params.get("TestMemoryMb"),
                        params.get("TestDom0MinMemoryMb"),
                        s_gson.toJson((String) params.get("private.ip.address")),
                        s_gson.toJson((String) params.get("private.mac.address")),
                        s_gson.toJson((String) params.get("private.ip.netmask")),
                        s_gson.toJson((String) params.get("private.ip.address")),
                        s_gson.toJson((String) params.get("private.ip.netmask")),
                        s_gson.toJson((String) params.get("private.mac.address")),
                        s_gson.toJson((String) params.get("gateway.ip.address")),
                        s_gson.toJson((String) params.get("DefaultVirtualDiskFolder")),
                        s_gson.toJson((String) params.get("DefaultVirtualDiskFolder")));
    }
}

