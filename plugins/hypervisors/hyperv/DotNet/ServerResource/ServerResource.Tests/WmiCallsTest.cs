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
using System;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using CloudStack.Plugin.WmiWrappers.ROOT.VIRTUALIZATION;
using System.Management;
using Newtonsoft.Json.Linq;
using Newtonsoft.Json;
using System.IO;
using log4net;
using HypervResource;
using CloudStack.Plugin.AgentShell;

namespace ServerResource.Tests
{
    [TestClass]
    public class WmiCallsTest
    {
        protected static String testLocalStoreUUID = "5fe2bad3-d785-394e-9949-89786b8a63d2";
        protected static String testLocalStorePath = Path.Combine(AgentSettings.Default.hyperv_plugin_root, "var", "test", "storagepool");
        protected static String testSecondaryStoreLocalPath = Path.Combine(AgentSettings.Default.hyperv_plugin_root, "var", "test", "secondary");

        // TODO: differentiate between NFS and HTTP template URLs.
        protected static String testSampleTemplateUUID = "TestCopiedLocalTemplate.vhdx";
        protected static String testSampleTemplateURL = testSampleTemplateUUID;

        // test volumes are both a minimal size vhdx.  Changing the extension to .vhd makes on corrupt.
        protected static String testSampleVolumeWorkingUUID = "TestVolumeLegit.vhdx";
        protected static String testSampleVolumeCorruptUUID = "TestVolumeCorrupt.vhd";
        protected static String testSampleVolumeTempUUID = "TestVolumeTemp.vhdx";
        protected static String testSampleVolumeWorkingURIJSON;
        protected static String testSampleVolumeCorruptURIJSON;
        protected static String testSampleVolumeTempURIJSON;

        protected static String testSampleTemplateURLJSON;
        protected static String testLocalStorePathJSON;

        private static ILog s_logger = LogManager.GetLogger(typeof(WmiCallsTest));

        /// <summary>
        /// Test WmiCalls to which incoming HTTP POST requests are dispatched.
        /// 
        /// TODO: revise beyond first approximation
        /// First approximation is a quick port of the existing Java tests for Hyper-V server resource.
        /// A second approximation would use the AgentShell settings files directly.
        /// A third approximation would look to invoke ServerResource methods via an HTTP request
        /// </summary>
        [TestInitializeAttribute]
        public void setUp()
        {
            // Used to create existing StoragePool in preparation for the ModifyStoragePool
            testLocalStoreUUID = AgentSettings.Default.local_storage_uuid.ToString();

            // Make sure secondary store is available.
            string fullPath = Path.GetFullPath(testSecondaryStoreLocalPath);
            s_logger.Info("Test secondary storage in " + fullPath);
            DirectoryInfo testSecondarStoreDir = new DirectoryInfo(fullPath);
            if (!testSecondarStoreDir.Exists)
            {
                try
                {
                    testSecondarStoreDir.Create();
                }
                catch (System.IO.IOException ex)
                {
                    Assert.Fail("Need to be able to create the folder " + testSecondarStoreDir.FullName);
                }
            }

            // Convert to secondary storage string to canonical path
            testSecondaryStoreLocalPath = testSecondarStoreDir.FullName;
            AgentSettings.Default.local_secondary_storage_path = testSecondaryStoreLocalPath;

            // Make sure local primary storage is available
            DirectoryInfo testPoolDir = new DirectoryInfo(testLocalStorePath);
            Assert.IsTrue(testPoolDir.Exists, "To simulate local file system Storage Pool, you need folder at " + testPoolDir.FullName);

            // Convert to local primary storage string to canonical path
            testLocalStorePath = testPoolDir.FullName;
            AgentSettings.Default.local_storage_path = testLocalStorePath;

            // Clean up old test files in local storage folder
            FileInfo testVolWorks = new FileInfo(Path.Combine(testLocalStorePath, testSampleVolumeWorkingUUID));
            Assert.IsTrue(testVolWorks.Exists, "Create a working virtual disk at " + testVolWorks.FullName);


            // Delete all temporary files in local folder save the testVolWorks
            foreach (var file in testPoolDir.GetFiles())
            {
                if (file.FullName == testVolWorks.FullName)
                {
                    continue;
                }
                file.Delete();
                file.Refresh();
                Assert.IsFalse(file.Exists, "removed file from previous test called " + file.FullName);
            }

            // Recreate starting point files for test, and record JSON encoded paths for each ...
            testSampleVolumeTempURIJSON = CreateTestDiskImageFromExistingImage(testVolWorks, testLocalStorePath, testSampleVolumeTempUUID);
            s_logger.Info("Created " + testSampleVolumeTempURIJSON);
            testSampleVolumeCorruptURIJSON = CreateTestDiskImageFromExistingImage(testVolWorks, testLocalStorePath, testSampleVolumeCorruptUUID);
            s_logger.Info("Created " + testSampleVolumeCorruptURIJSON);
            CreateTestDiskImageFromExistingImage(testVolWorks, testLocalStorePath, testSampleTemplateUUID);
            testSampleTemplateURLJSON = JsonConvert.SerializeObject(testSampleTemplateUUID);
            s_logger.Info("Created " + testSampleTemplateURLJSON + " in local storage.");

            // ... including a secondary storage template:
            CreateTestDiskImageFromExistingImage(testVolWorks, testSecondarStoreDir.FullName, "af39aa7f-2b12-37e1-86d3-e23f2f005101.vhdx");
            s_logger.Info("Created " + "af39aa7f-2b12-37e1-86d3-e23f2f005101.vhdx" + " in secondary (NFS) storage.");


            // Capture other JSON encoded paths
            testSampleVolumeWorkingURIJSON = Newtonsoft.Json.JsonConvert.SerializeObject(testVolWorks.FullName);
            testLocalStorePathJSON = JsonConvert.SerializeObject(testLocalStorePath);

            // TODO: may need to initialise the server resource in future.
            //    s_hypervresource.initialize();

            // Verify sample template is in place storage pool
            s_logger.Info("setUp complete, sample StoragePool at " + testLocalStorePathJSON
                      + " sample template at " + testSampleTemplateURLJSON);
        }

        private String CreateTestDiskImageFromExistingImage(FileInfo srcFile,
        String dstPath,
        String dstFileName)
        {
            var newFullname = Path.Combine(dstPath, dstFileName);
            var newFileInfo = new FileInfo(newFullname);
            if (!newFileInfo.Exists)
            {
                newFileInfo = srcFile.CopyTo(newFullname);
            }
            newFileInfo.Refresh();
            Assert.IsTrue(newFileInfo.Exists, "Attempted to create " + newFullname + " from " + newFileInfo.FullName);

            return JsonConvert.SerializeObject(newFileInfo.FullName);
        }

        [TestMethod]
        public void TestPrimaryStorageDownloadCommandHTTP()
        {
            string downloadURI = "https://s3-eu-west-1.amazonaws.com/cshv3eu/SmallDisk.vhdx";
            corePrimaryStorageDownloadCommandTestCycle(downloadURI);
        }

        private void corePrimaryStorageDownloadCommandTestCycle(string downloadURI)
        {
            // Arrange
            HypervResourceController rsrcServer = new HypervResourceController();
            dynamic jsonPSDCmd = JsonConvert.DeserializeObject(samplePrimaryDownloadCommand());
            jsonPSDCmd.url = downloadURI;

            // Act
            dynamic jsonResult = rsrcServer.PrimaryStorageDownloadCommand(jsonPSDCmd);

            // Assert
            dynamic ans = jsonResult[0].PrimaryStorageDownloadAnswer;
            Assert.IsTrue((bool)ans.result, "PrimaryStorageDownloadCommand did not succeed " + ans.details);

            // Test that URL of downloaded template works for file creation.
            dynamic jsonCreateCmd = JsonConvert.DeserializeObject(CreateCommandSample());
            jsonCreateCmd.templateUrl = ans.installPath;
            dynamic jsonAns2 = rsrcServer.CreateCommand(jsonCreateCmd);
            dynamic ans2 = jsonAns2[0].CreateAnswer;
            Assert.IsTrue((bool)ans2.result, (string)ans2.details);

            FileInfo newFile = new FileInfo((string)ans2.volume.path);
            Assert.IsTrue(newFile.Length > 0, "The new file should have a size greater than zero");
            newFile.Delete();
        }

	    private string samplePrimaryDownloadCommand() {
		    String cmdJson = "{\"localPath\":" +testLocalStorePathJSON +
                    ",\"poolUuid\":\"" + testLocalStoreUUID + "\",\"poolId\":201," + 
    			    "\"secondaryStorageUrl\":\"nfs://10.70.176.36/mnt/cshv3/secondarystorage\"," +
    			    "\"primaryStorageUrl\":\"nfs://10.70.176.29E:\\\\Disks\\\\Disks\"," + 
    			    "\"url\":\"nfs://10.70.176.36/mnt/cshv3/secondarystorage/template/tmpl//2/204//af39aa7f-2b12-37e1-86d3-e23f2f005101.vhdx\","+
    			    "\"format\":\"VHDX\",\"accountId\":2,\"name\":\"204-2-5a1db1ac-932b-3e7e-a0e8-5684c72cb862\"" +
    			    ",\"contextMap\":{},\"wait\":10800}";
            return cmdJson;
	    }
    
	    public string CreateCommandSample()
	    {
		    String sample = "{\"volId\":17,\"pool\":{\"id\":201,\"uuid\":\""+testLocalStoreUUID+"\",\"host\":\"10.70.176.29\"" +
						    ",\"path\":"+testLocalStorePathJSON+",\"port\":0,\"type\":\"Filesystem\"},\"diskCharacteristics\":{\"size\":0," +
						    "\"tags\":[],\"type\":\"ROOT\",\"name\":\"ROOT-15\",\"useLocalStorage\":true,\"recreatable\":true,\"diskOfferingId\":11," +
						    "\"volumeId\":17,\"hyperType\":\"Hyperv\"},\"templateUrl\":"+ testSampleTemplateURLJSON +",\"wait\":0}";
            return sample;
	    }

        [TestMethod]
        public void TestDestroyCommand()
        {
            // Arrange
            String destoryCmd = "{\"volume\":{\"name\":\"" + testSampleVolumeWorkingUUID + 
        		    "\",\"storagePoolType\":\"Filesystem\",\"mountPoint\":"+testLocalStorePathJSON+
        		    ",\"path\":" + testSampleVolumeTempURIJSON +
        		    ",\"storagePoolUuid\":\""+testLocalStoreUUID+"\"," + 
        		    "\"type\":\"ROOT\",\"id\":9,\"size\":0}}";
            HypervResourceController rsrcServer = new HypervResourceController();
            dynamic jsonDestoryCmd = JsonConvert.DeserializeObject(destoryCmd);

            // Act
            dynamic destoryAns = rsrcServer.DestroyCommand(jsonDestoryCmd);

            // Assert
            dynamic ans = destoryAns[0].DestroyAnswer;
            Assert.IsTrue((bool)ans.result, "DestroyCommand did not succeed " + ans.details);
        }

        [TestMethod]
        public void TestCreateCommand()
        {
            // Arrange
            String createCmd = "{\"volId\":10,\"pool\":{\"id\":201,\"uuid\":\"" + testLocalStoreUUID + "\",\"host\":\"10.70.176.29\"" +
    					    ",\"path\":"+testLocalStorePathJSON+",\"port\":0,\"type\":\"Filesystem\"},\"diskCharacteristics\":{\"size\":0," +
    					    "\"tags\":[],\"type\":\"ROOT\",\"name\":\"ROOT-9\",\"useLocalStorage\":true,\"recreatable\":true,\"diskOfferingId\":11," +
    					    "\"volumeId\":10,\"hyperType\":\"Hyperv\"},\"templateUrl\":"+testSampleTemplateURLJSON+",\"contextMap\":{},\"wait\":0}";
            dynamic jsonCreateCmd = JsonConvert.DeserializeObject(createCmd);
            HypervResourceController rsrcServer = new HypervResourceController();

        	Assert.IsTrue(Directory.Exists(testLocalStorePath));
            string filePath = Path.Combine(testLocalStorePath, (string)JsonConvert.DeserializeObject(testSampleTemplateURLJSON));
        	Assert.IsTrue(File.Exists(filePath), "The template we make volumes from is missing from path " + filePath);
            int fileCount = Directory.GetFiles(testLocalStorePath).Length;
    	    s_logger.Debug(" test local store has " + fileCount + "files");

            // Act
            // Test requires there to be a template at the tempalteUrl, which is its location in the local file system.
            dynamic jsonResult = rsrcServer.CreateCommand(jsonCreateCmd);

            dynamic ans = jsonResult[0].CreateAnswer;
            Assert.IsNotNull(ans, "Should be an answer object of type CreateAnswer");
    	    Assert.IsTrue((bool)ans.result, "Failed to CreateCommand due to "  + (string)ans.result);
            Assert.AreEqual(Directory.GetFiles(testLocalStorePath).Length, fileCount + 1);
            FileInfo newFile = new FileInfo((string)ans.volume.path);
            Assert.IsTrue(newFile.Length > 0, "The new file should have a size greater than zero");
            newFile.Delete();
        }

        /// <summary>
        /// Possible additional tests:  place an ISO in the drive
        /// </summary>
        [TestMethod]
        public void TestStartStopCommand()
        {
            string vmName = TestStartCommand();
            TestStopCommand(vmName);
        }


        private static string TestStartCommand()
        {
            // Arrange
            HypervResourceController rsrcServer = new HypervResourceController();
            String sample = "{\"vm\":{\"id\":17,\"name\":\"i-2-17-VM\",\"type\":\"User\",\"cpus\":1,\"speed\":500," +
                "\"minRam\":536870912,\"maxRam\":536870912,\"arch\":\"x86_64\"," +
                "\"os\":\"CentOS 6.0 (64-bit)\",\"bootArgs\":\"\",\"rebootOnCrash\":false," +
                "\"enableHA\":false,\"limitCpuUse\":false,\"vncPassword\":\"31f82f29aff646eb\"," +
                "\"params\":{},\"uuid\":\"8b030b6a-0243-440a-8cc5-45d08815ca11\"" +
                ",\"disks\":[" +
                    "{\"id\":18,\"name\":\"" + testSampleVolumeWorkingUUID + "\"," +
                        "\"mountPoint\":" + testSampleVolumeWorkingURIJSON + "," +
                        "\"path\":" + testSampleVolumeWorkingURIJSON + ",\"size\":0," +
                        "\"type\":\"ROOT\",\"storagePoolType\":\"Filesystem\",\"storagePoolUuid\":\"" + testLocalStoreUUID + "\"" +
                        ",\"deviceId\":0}," +
                    "{\"id\":16,\"name\":\"Hyper-V Sample2\",\"size\":0,\"type\":\"ISO\",\"storagePoolType\":\"ISO\",\"deviceId\":3}]," +
                "\"nics\":[" +
                    "{\"deviceId\":0,\"networkRateMbps\":100,\"defaultNic\":true,\"uuid\":\"99cb4813-23af-428c-a87a-2d1899be4f4b\"," +
                    "\"ip\":\"10.1.1.67\",\"netmask\":\"255.255.255.0\",\"gateway\":\"10.1.1.1\"," +
                    "\"mac\":\"02:00:51:2c:00:0e\",\"dns1\":\"4.4.4.4\",\"broadcastType\":\"Vlan\",\"type\":\"Guest\"," +
                    "\"broadcastUri\":\"vlan://261\",\"isolationUri\":\"vlan://261\",\"isSecurityGroupEnabled\":false}" +
                                      "]},\"contextMap\":{},\"wait\":0}";
            dynamic jsonStartCmd = JsonConvert.DeserializeObject(sample);


            // Act
            dynamic startAns = rsrcServer.StartCommand(jsonStartCmd);

            // Assert
            Assert.IsTrue((bool)startAns[0].StartAnswer.result, "StartCommand did not succeed " +  startAns[0].StartAnswer.details);
            Assert.IsNotNull(startAns[0].StartAnswer, "StartCommand should return a StartAnswer in all cases");
            string vmCmdName = jsonStartCmd.vm.name.Value;
            var vm = WmiCalls.GetComputerSystem(vmCmdName);
            VirtualSystemSettingData vmSettings = WmiCalls.GetVmSettings(vm);
            MemorySettingData memSettings = WmiCalls.GetMemSettings(vmSettings);
            ProcessorSettingData procSettings = WmiCalls.GetProcSettings(vmSettings);
            dynamic jsonObj = JsonConvert.DeserializeObject(sample);
            var vmInfo = jsonObj.vm;
            string vmName = vmInfo.name;
            var nicInfo = vmInfo.nics;
            int vcpus = vmInfo.cpus;
            int memSize = vmInfo.maxRam / 1048576;
            Assert.IsTrue((long)memSettings.VirtualQuantity == memSize);
            Assert.IsTrue((long)memSettings.Reservation == memSize);
            Assert.IsTrue((long)memSettings.Limit == memSize);
            Assert.IsTrue((int)procSettings.VirtualQuantity == vcpus);
            Assert.IsTrue((int)procSettings.Reservation == vcpus);
            Assert.IsTrue((int)procSettings.Limit == 100000);

            // examine NIC
            SyntheticEthernetPortSettingData[] nicSettingsViaVm = WmiCalls.GetEthernetPorts(vm);
            Assert.IsTrue(nicSettingsViaVm.Length > 0, "Should be at least one ethernet port on VM");
            string expectedMac = (string)jsonStartCmd.vm.nics[0].mac;
            string strippedExpectedMac = expectedMac.Replace(":", string.Empty);
            Assert.AreEqual(nicSettingsViaVm[0].Address.ToLower(), strippedExpectedMac.ToLower());

            // Assert switchport has correct VLAN 
            SwitchPort[] switchPorts = WmiCalls.GetSwitchPorts(vm);
            VirtualSwitchManagementService vmNetMgmtSvc = WmiCalls.GetVirtualSwitchManagementService();
            VLANEndpointSettingData vlanSettings = WmiCalls.GetVlanEndpointSettings(vmNetMgmtSvc, switchPorts[0].Path);
            string isolationUri = (string)jsonStartCmd.vm.nics[0].isolationUri;
            string vlan = isolationUri.Replace("vlan://", string.Empty);
            Assert.AreEqual(vlanSettings.AccessVLAN.ToString(), vlan);

            return vmName;
        }

        private static void TestStopCommand(string vmName)
        {
            // Arrange
            HypervResourceController rsrcServer = new HypervResourceController();
            String sampleStop = "{\"isProxy\":false,\"vmName\":\"i-2-17-VM\",\"contextMap\":{},\"wait\":0}";
            dynamic jsonStopCmd = JsonConvert.DeserializeObject(sampleStop);

            // Act
            dynamic stopAns = rsrcServer.StopCommand(jsonStopCmd);

            // Assert VM is gone!
            Assert.IsNotNull(stopAns[0].StopAnswer, "StopCommand should return a StopAnswer in all cases");
            Assert.IsTrue((bool)stopAns[0].StopAnswer.result, "StopCommand did not succeed " + stopAns[0].StopAnswer.details);
            var finalVm = WmiCalls.GetComputerSystem(vmName);
            Assert.IsTrue(WmiCalls.GetComputerSystem(vmName) == null);
        }
    }
}
