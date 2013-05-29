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
import com.cloud.agent.api.StopAnswer;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.VmStatsEntry;
import com.cloud.agent.api.storage.PrimaryStorageDownloadAnswer;
import com.cloud.agent.api.storage.PrimaryStorageDownloadCommand;

import com.cloud.agent.api.storage.CreateAnswer;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.api.storage.DestroyAnswer;
import com.cloud.agent.api.storage.DestroyCommand;

import com.cloud.hypervisor.hyperv.discoverer.HypervServerDiscoverer;
import com.cloud.hypervisor.hyperv.resource.HypervResource;

import org.apache.log4j.Logger;

import com.cloud.serializer.GsonHelper;
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
public class HypervResourceTest {
    private static final Logger s_logger = Logger.getLogger(HypervResourceTest.class.getName());
    
    // TODO:  make this a config parameter
    protected static final Gson s_gson = GsonHelper.getGson();
    protected static final HypervResource s_hypervresource = new HypervResource();
    
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
    
    public HypervResourceTest() {
    }
    
    //@Before
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

        	s_hypervresource.configure("hypervresource",  params);
	        s_hypervresource.initialize();
	        
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
    
    // Alternative to JUnit is to launch as stand alone application.
    public static void main(String[] args) throws ConfigurationException {
    	
    	HypervResourceTest tester = new HypervResourceTest();
    	tester.setUp();
    	
    	// Add tests you want to run, e.g.
    	tester.TestGetHostStatsCommand();
    	return;
    }
    
    //@Test 
    public void TestGetVmStatsCommand()
    {
       	// Sample GetVmStatsCommand
    	List<String> vmNames = new ArrayList<String>();
    	vmNames.add("TestCentOS6.3");
    	GetVmStatsCommand cmd = new GetVmStatsCommand(vmNames, "1", "localhost");

    	s_hypervresource.executeRequest(cmd);
    	GetVmStatsAnswer ans = (GetVmStatsAnswer)s_hypervresource.executeRequest(cmd);
    	Assert.assertTrue(ans.getDetails(), ans.getResult());
    }
    
    //@Test 
    public void TestBadGetVmStatsCommand()
    {
       	// Sample GetVmStatsCommand
    	List<String> vmNames = new ArrayList<String>();
    	vmNames.add("FakeVM");
    	GetVmStatsCommand vmStatsCmd = new GetVmStatsCommand(vmNames, "1", "localhost");
    	GetVmStatsAnswer ans = (GetVmStatsAnswer)s_hypervresource.executeRequest(vmStatsCmd);
    	Assert.assertTrue(ans.getDetails(), ans.getResult());
    }
    
    //@Test
    public void TestCreateStoragePoolCommand()
    {
    	CreateStoragePoolCommand cmd = new CreateStoragePoolCommand();

    	Answer ans = s_hypervresource.executeRequest(cmd);
    	Assert.assertTrue(ans.getResult());
    }
    
    //@Test
    public void TestModifyStoragePoolCommand()
    {
    	// Create dummy folder
    	String folderName = "." + File.separator + "Dummy";
    	File folder = new File(folderName);
    	if (!folder.exists()) {
    		if (!folder.mkdir()) {
    			Assert.assertTrue(false);
    		}
    	}
    	
    	// Use same spec for pool
    	s_logger.info("Createing pool at : " + folderName );

        StoragePoolVO pool = new StoragePoolVO(StoragePoolType.Filesystem, 
        		"127.0.0.1", -1, folderName);

    	ModifyStoragePoolCommand cmd = new ModifyStoragePoolCommand(
    			true, pool, folderName);
    	Answer ans = s_hypervresource.executeRequest(cmd);
    	Assert.assertTrue(ans.getResult());
    	
    	DeleteStoragePoolCommand delCmd = new DeleteStoragePoolCommand(pool, folderName);
    	Answer ans2 = s_hypervresource.executeRequest(delCmd);
    	Assert.assertTrue(ans2.getResult());
    }

    // @Test
    public void TestModifyStoragePoolCommand2()
    {
    	// Should return existing pool
    	// Create dummy folder
    	String folderName = "." + File.separator + "Dummy";
    	File folder = new File(folderName);
    	if (!folder.exists()) {
    		if (!folder.mkdir()) {
    			Assert.assertTrue(false);
    		}
    	}
    	
    	// Use same spec for pool
    	s_logger.info("Createing pool at : " + folderName );

        StoragePoolVO pool = new StoragePoolVO(StoragePoolType.Filesystem, 
        		"127.0.0.1", -1, folderName);
        pool.setUuid(testLocalStoreUUID);

    	ModifyStoragePoolCommand cmd = new ModifyStoragePoolCommand(
    			true, pool, folderName);
    	Answer ans = s_hypervresource.executeRequest(cmd);
    	Assert.assertTrue(ans.getResult());
    	
    	DeleteStoragePoolCommand delCmd = new DeleteStoragePoolCommand(pool, folderName);
    	Answer ans2 = s_hypervresource.executeRequest(delCmd);
    	Assert.assertTrue(ans2.getResult());
    }
    
    //@Test
    public void TestPrimaryStorageDownloadCommandNFS()
    {
    	PrimaryStorageDownloadCommand cmd = samplePrimaryDownloadCommand();
		String tmpltFileName = cmd.getUrl().substring(cmd.getUrl().lastIndexOf("/"));
    	File tmpltFile = new File(testSecondaryStoreLocalPath + File.separator + tmpltFileName);
    	Assert.assertTrue("template disk image should exist at " + tmpltFile.getPath(), tmpltFile.exists());

    	corePrimaryStorageDownloadCommandTestCycle(cmd);
    }

    //@Test
    public void TestPrimaryStorageDownloadCommandHTTP()
    {
    	PrimaryStorageDownloadCommand cmd = samplePrimaryDownloadCommand();
    	cmd.setUrl("http://s3-eu-west-1.amazonaws.com/cshv3eu/SmallDisk.vhdx");
    	corePrimaryStorageDownloadCommandTestCycle(cmd);
    }
	private void corePrimaryStorageDownloadCommandTestCycle(
			PrimaryStorageDownloadCommand cmd) {
    	PrimaryStorageDownloadAnswer ans = (PrimaryStorageDownloadAnswer)s_hypervresource.executeRequest(cmd);
    	if ( !ans.getResult()){
    		s_logger.error(ans.getDetails());
    	}
    	else {
    		s_logger.debug(ans.getDetails());
    	}
    		
    	Assert.assertTrue(ans.getDetails(), ans.getResult());
    	// Test that returned URL works.
    	CreateCommand createCmd = CreateCommandSample();
    	CreateCommand testCreateCmd = new CreateCommand(createCmd.getDiskCharacteristics(), 
    			ans.getInstallPath(), createCmd.getPool());
    	CreateAnswer ans2 = (CreateAnswer)s_hypervresource.executeRequest(testCreateCmd);
    	Assert.assertTrue(ans2.getDetails(), ans2.getResult());
	}

	private PrimaryStorageDownloadCommand samplePrimaryDownloadCommand() {
		String cmdJson = "{\"localPath\":" +testLocalStorePathJSON + 
    			",\"poolUuid\":\"" +testLocalStoreUUID + "\",\"poolId\":201,"+ 
    			"\"secondaryStorageUrl\":\"nfs://10.70.176.36/mnt/cshv3/secondarystorage\"," +
    			"\"primaryStorageUrl\":\"nfs://10.70.176.29E:\\\\Disks\\\\Disks\"," + 
    			"\"url\":\"nfs://10.70.176.36/mnt/cshv3/secondarystorage/template/tmpl//2/204//af39aa7f-2b12-37e1-86d3-e23f2f005101.vhdx\","+
    			"\"format\":\"VHDX\",\"accountId\":2,\"name\":\"204-2-5a1db1ac-932b-3e7e-a0e8-5684c72cb862\"" +
    			",\"contextMap\":{},\"wait\":10800}";
    	PrimaryStorageDownloadCommand cmd = s_gson.fromJson(cmdJson, 
    			PrimaryStorageDownloadCommand.class);
		return cmd;
	}
    
	public CreateCommand CreateCommandSample()
	{
		String sample = "{\"volId\":17,\"pool\":{\"id\":201,\"uuid\":\""+testLocalStoreUUID+"\",\"host\":\"10.70.176.29\"" +
						",\"path\":"+testLocalStorePathJSON+",\"port\":0,\"type\":\"Filesystem\"},\"diskCharacteristics\":{\"size\":0," +
						"\"tags\":[],\"type\":\"ROOT\",\"name\":\"ROOT-15\",\"useLocalStorage\":true,\"recreatable\":true,\"diskOfferingId\":11," +
						"\"volumeId\":17,\"hyperType\":\"Hyperv\"},\"templateUrl\":"+testSampleTemplateURLJSON+",\"wait\":0}";
    	CreateCommand cmd = s_gson.fromJson(sample, CreateCommand.class);
    	return cmd;
	}
	
    //@Test
    public void TestCreateCommand()
    {
    	String sample = "{\"volId\":10,\"pool\":{\"id\":201,\"uuid\":\""+testLocalStoreUUID+"\",\"host\":\"10.70.176.29\"" +
    					",\"path\":"+testLocalStorePathJSON+",\"port\":0,\"type\":\"Filesystem\"},\"diskCharacteristics\":{\"size\":0," +
    					"\"tags\":[],\"type\":\"ROOT\",\"name\":\"ROOT-9\",\"useLocalStorage\":true,\"recreatable\":true,\"diskOfferingId\":11," +
    					"\"volumeId\":10,\"hyperType\":\"Hyperv\"},\"templateUrl\":"+testSampleTemplateURLJSON+",\"contextMap\":{},\"wait\":0}";

    	File destDir = new File(testLocalStorePath);
    	Assert.assertTrue(destDir.isDirectory());
	    File testSampleTemplateURLFile = new File(testLocalStorePath + File.separator + s_gson.fromJson(testSampleTemplateURLJSON, String.class));
    	Assert.assertTrue("The template that create should make volumes from is missing from path " + testSampleTemplateURLFile.getPath(),
    			testSampleTemplateURLFile.exists());

    	int fileCount = destDir.listFiles().length;
    	s_logger.debug(" test local store has " + fileCount + "files");
    	// Test requires there to be a template at the tempalteUrl, which is its location in the local file system.
    	CreateCommand cmd = s_gson.fromJson(sample, CreateCommand.class);
    	CreateAnswer ans =(CreateAnswer)s_hypervresource.executeRequest(cmd);
    	Assert.assertTrue(ans.getDetails(), ans.getResult());
    	Assert.assertTrue("CreateCommand should add a file to the folder", fileCount+1 == destDir.listFiles().length);
    	File newFile = new File(ans.getVolume().getPath());
    	Assert.assertTrue("The new file should have a size greater than zero", newFile.length() > 0);
    	newFile.delete();
    }

    //@Test
    public void TestStartCommandCorruptDiskImage()
    {
    	String sampleStart =  "{\"vm\":{\"id\":16,\"name\":\"i-3-17-VM\",\"type\":\"User\",\"cpus\":1,\"speed\":500," +
              	"\"minRam\":536870912,\"maxRam\":536870912,\"arch\":\"x86_64\"," +
              	"\"os\":\"CentOS 6.0 (64-bit)\",\"bootArgs\":\"\",\"rebootOnCrash\":false," +
              	"\"enableHA\":false,\"limitCpuUse\":false,\"vncPassword\":\"31f82f29aff646eb\"," +
              	"\"params\":{},\"uuid\":\"8b030b6a-0243-440a-8cc5-45d08815ca11\"" +
              	",\"disks\":[" +
                  	"{\"id\":18,\"name\":\"" + testSampleVolumeCorruptUUID + "\"," +
                  		"\"mountPoint\":" + testSampleVolumeCorruptURIJSON + "," +
                  		"\"path\":" + testSampleVolumeCorruptURIJSON + ",\"size\":0,"+
                  		"\"type\":\"ROOT\",\"storagePoolType\":\"Filesystem\",\"storagePoolUuid\":\""+testLocalStoreUUID+"\"" +
                  		",\"deviceId\":0}," + 
                  	"{\"id\":16,\"name\":\"Hyper-V Sample2\",\"size\":0,\"type\":\"ISO\",\"storagePoolType\":\"ISO\",\"deviceId\":3}]," + 
              	"\"nics\":[" +
                  	"{\"deviceId\":0,\"networkRateMbps\":100,\"defaultNic\":true,\"uuid\":\"99cb4813-23af-428c-a87a-2d1899be4f4b\"," + 
                  	"\"ip\":\"10.1.1.67\",\"netmask\":\"255.255.255.0\",\"gateway\":\"10.1.1.1\"," + 
                  	"\"mac\":\"02:00:51:2c:00:0e\",\"dns1\":\"4.4.4.4\",\"broadcastType\":\"Vlan\",\"type\":\"Guest\"," + 
                  	"\"broadcastUri\":\"vlan://261\",\"isolationUri\":\"vlan://261\",\"isSecurityGroupEnabled\":false}" +
                  "]},\"contextMap\":{},\"wait\":0}";

    	{
	       	StartCommand cmd = s_gson.fromJson(sampleStart, StartCommand.class);
	       	StartAnswer ans = (StartAnswer)s_hypervresource.executeRequest(cmd);
	    	Assert.assertFalse(ans.getDetails(), ans.getResult());
    	}
    }

    //@Test
    public void TestStartStopCommand()
    {
       	String sample =  "{\"vm\":{\"id\":17,\"name\":\"i-2-17-VM\",\"type\":\"User\",\"cpus\":1,\"speed\":500," +
              	"\"minRam\":536870912,\"maxRam\":536870912,\"arch\":\"x86_64\"," +
              	"\"os\":\"CentOS 6.0 (64-bit)\",\"bootArgs\":\"\",\"rebootOnCrash\":false," +
              	"\"enableHA\":false,\"limitCpuUse\":false,\"vncPassword\":\"31f82f29aff646eb\"," +
              	"\"params\":{},\"uuid\":\"8b030b6a-0243-440a-8cc5-45d08815ca11\"" +
              	",\"disks\":[" +
                  	"{\"id\":18,\"name\":\"" + testSampleVolumeWorkingUUID + "\"," +
                  		"\"mountPoint\":" + testSampleVolumeWorkingURIJSON + "," +
                  		"\"path\":" + testSampleVolumeWorkingURIJSON + ",\"size\":0,"+
                  		"\"type\":\"ROOT\",\"storagePoolType\":\"Filesystem\",\"storagePoolUuid\":\""+testLocalStoreUUID+"\"" +
                  		",\"deviceId\":0}," + 
                  	"{\"id\":16,\"name\":\"Hyper-V Sample2\",\"size\":0,\"type\":\"ISO\",\"storagePoolType\":\"ISO\",\"deviceId\":3}]," + 
              	"\"nics\":[" +
                  	"{\"deviceId\":0,\"networkRateMbps\":100,\"defaultNic\":true,\"uuid\":\"99cb4813-23af-428c-a87a-2d1899be4f4b\"," + 
                  	"\"ip\":\"10.1.1.67\",\"netmask\":\"255.255.255.0\",\"gateway\":\"10.1.1.1\"," + 
                  	"\"mac\":\"02:00:51:2c:00:0e\",\"dns1\":\"4.4.4.4\",\"broadcastType\":\"Vlan\",\"type\":\"Guest\"," + 
                  	"\"broadcastUri\":\"vlan://261\",\"isolationUri\":\"vlan://261\",\"isSecurityGroupEnabled\":false}" +
                  "]},\"contextMap\":{},\"wait\":0}";
       	{
	       	StartCommand cmd = s_gson.fromJson(sample, StartCommand.class);
	       	StartAnswer ans = (StartAnswer)s_hypervresource.executeRequest(cmd);
	    	Assert.assertTrue(ans.getDetails(), ans.getResult());
       	}
    	{
	    	String sampleStop =  "{\"isProxy\":false,\"vmName\":\"i-2-17-VM\",\"contextMap\":{},\"wait\":0}";
	       	StopCommand cmd = s_gson.fromJson(sampleStop, StopCommand.class);
	       	StopAnswer ans = (StopAnswer)s_hypervresource.executeRequest(cmd);
	    	Assert.assertTrue(ans.getDetails(), ans.getResult());
    	}
    }
   
    //@Test
    public void TestStartStartCommand()
    {
       	String sample =  "{\"vm\":{\"id\":17,\"name\":\"i-2-17-VM\",\"type\":\"User\",\"cpus\":1,\"speed\":500," +
              	"\"minRam\":536870912,\"maxRam\":536870912,\"arch\":\"x86_64\"," +
              	"\"os\":\"CentOS 6.0 (64-bit)\",\"bootArgs\":\"\",\"rebootOnCrash\":false," +
              	"\"enableHA\":false,\"limitCpuUse\":false,\"vncPassword\":\"31f82f29aff646eb\"," +
              	"\"params\":{},\"uuid\":\"8b030b6a-0243-440a-8cc5-45d08815ca11\"" +
              	",\"disks\":[" +
                  	"{\"id\":18,\"name\":\"" + testSampleVolumeWorkingUUID + "\"," +
                  		"\"mountPoint\":" + testSampleVolumeWorkingURIJSON + "," +
                  		"\"path\":" + testSampleVolumeWorkingURIJSON + ",\"size\":0,"+
                  		"\"type\":\"ROOT\",\"storagePoolType\":\"Filesystem\",\"storagePoolUuid\":\""+testLocalStoreUUID+"\"" +
                  		",\"deviceId\":0}," + 
                  	"{\"id\":16,\"name\":\"Hyper-V Sample2\",\"size\":0,\"type\":\"ISO\",\"storagePoolType\":\"ISO\",\"deviceId\":3}]," + 
              	"\"nics\":[" +
                  	"{\"deviceId\":0,\"networkRateMbps\":100,\"defaultNic\":true,\"uuid\":\"99cb4813-23af-428c-a87a-2d1899be4f4b\"," + 
                  	"\"ip\":\"10.1.1.67\",\"netmask\":\"255.255.255.0\",\"gateway\":\"10.1.1.1\"," + 
                  	"\"mac\":\"02:00:51:2c:00:0e\",\"dns1\":\"4.4.4.4\",\"broadcastType\":\"Vlan\",\"type\":\"Guest\"," + 
                  	"\"broadcastUri\":\"vlan://261\",\"isolationUri\":\"vlan://261\",\"isSecurityGroupEnabled\":false}" +
                  "]},\"contextMap\":{},\"wait\":0}";
       	{
	       	StartCommand cmd = s_gson.fromJson(sample, StartCommand.class);
	       	StartAnswer ans = (StartAnswer)s_hypervresource.executeRequest(cmd);
	    	Assert.assertTrue(ans.getDetails(), ans.getResult());
       	}
       	{
	       	StartCommand cmd = s_gson.fromJson(sample, StartCommand.class);
	       	StartAnswer ans = (StartAnswer)s_hypervresource.executeRequest(cmd);
	    	Assert.assertFalse(ans.getDetails(), ans.getResult());
       	}
    	{
	    	String sampleStop =  "{\"isProxy\":false,\"vmName\":\"i-2-17-VM\",\"contextMap\":{},\"wait\":0}";
	       	StopCommand cmd = s_gson.fromJson(sampleStop, StopCommand.class);
	       	StopAnswer ans = (StopAnswer)s_hypervresource.executeRequest(cmd);
	    	Assert.assertTrue(ans.getDetails(), ans.getResult());
    	}
    }

    //@Test
    public void TestDestroyCommand()
    {
    	// TODO:  how does the command vary when we are only deleting a vm versus deleting a volume?
    	//String sample = "{\"vmName\":\"i-2-6-VM\",\"volume\":{\"id\":9,\"name\":\"ROOT-8\",\"mountPoint\":"+testLocalStorePathJSON+"," +
    	//				"\"path\":\"FakeVolume\",\"size\":0,\"type\":\"ROOT\",\"storagePoolType\":\"Filesystem\"," +
    	//				"\"storagePoolUuid\":\""+testLocalStoreUUID+"\",\"deviceId\":0},\"contextMap\":{},\"wait\":0}";

        String sample2 = "{\"volume\":{\"name\":\"" + testSampleVolumeWorkingUUID + 
        		"\",\"storagePoolType\":\"Filesystem\",\"mountPoint\":"+testLocalStorePathJSON+
        		",\"path\":" + testSampleVolumeTempURIJSON +
        		",\"storagePoolUuid\":\""+testLocalStoreUUID+"\"," + 
        		"\"type\":\"ROOT\",\"id\":9,\"size\":0}}";
    	DestroyCommand cmd = s_gson.fromJson(sample2, DestroyCommand.class);
    	DestroyAnswer ans = (DestroyAnswer)s_hypervresource.executeRequest(cmd);
    	Assert.assertTrue(ans.getDetails(), ans.getResult());
    }

    //@Test
    public void TestGetStorageStatsCommand()
    {
    	// TODO:  Update sample data to unsure it is using correct info.
    	String sample = "{\"id\":\""+testLocalStoreUUID+"\",\"localPath\":"+testLocalStorePathJSON+"," +
    					"\"pooltype\":\"Filesystem\",\"contextMap\":{},\"wait\":0}";
    	       
    	s_logger.info("Sample JSON: " + sample );

    	GetStorageStatsCommand cmd = s_gson.fromJson(sample, GetStorageStatsCommand.class);
    	s_hypervresource.executeRequest(cmd);
    	GetStorageStatsAnswer ans = (GetStorageStatsAnswer)s_hypervresource.executeRequest(cmd);
    	Assert.assertTrue(ans.getDetails(), ans.getResult());
    	Assert.assertTrue(ans.getByteUsed() != ans.getCapacityBytes());
    }
    
    //@Test 
    public void TestAgentGuidCreation()
    {
    	AgentShell shell = new AgentShell();
        try {
			shell.init(new String[0]);
	        String guidConf = shell.getProperty(null, "guid");
	        if (guidConf.equals("generate_from_private.ip.address")) {
		        String privateIp = shell.getProperty(null, "private.ip.address");
		        String expectedGuid = HypervServerDiscoverer.CalcServerResourceGuid(privateIp);
		        Assert.assertTrue("Expected GUID is " + expectedGuid + " but we got " + shell.getGuid(), shell.getGuid().equals(expectedGuid));
	        }
		} catch (ConfigurationException e) {
			Assert.fail(e.toString());
		}
    }
    
    
    //@Test
    public void TestGetHostStatsCommand()
    {
    	String sample = "{\"hostGuid\":\"B4AE5970-FCBF-4780-9F8A-2D2E04FECC34-HypervResource\",\"hostName\":\"CC-SVR11\",\"hostId\":5,\"contextMap\":{},\"wait\":0}";
    
    	s_logger.info("Sample JSON: " + sample );

    	GetHostStatsCommand cmd = s_gson.fromJson(sample, GetHostStatsCommand.class);
    	GetHostStatsAnswer ans = (GetHostStatsAnswer)s_hypervresource.executeRequest(cmd);
    	Assert.assertTrue(ans.getDetails(), ans.getResult());
    }

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
