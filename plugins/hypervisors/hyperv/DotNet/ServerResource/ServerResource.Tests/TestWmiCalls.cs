using System;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using CloudStack.Plugin.WmiWrappers.ROOT.VIRTUALIZATION;
using System.Management;
using Newtonsoft.Json.Linq;
using Newtonsoft.Json;
using System.IO;
using log4net;

namespace ServerResource.Tests
{
    [TestClass]
    public class TestWmiCalls
    {
            protected static String testLocalStoreUUID = "5fe2bad3-d785-394e-9949-89786b8a63d2";
            protected static String testLocalStorePath = Path.Combine(ServerResource.Tests.AgentShell.Default.hyperv_plugin_root, "var" ,  "test", "storagepool");
            protected static String testSecondaryStoreLocalPath = Path.Combine(ServerResource.Tests.AgentShell.Default.hyperv_plugin_root, "var" ,  "test", "secondary");
    
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

    private static ILog s_logger = LogManager.GetLogger(typeof(TestWmiCalls));


    
    /// <summary>
    /// TODO: revise beyond first approximation
    /// First approximation is a quick port of the existing Java tests for Hyper-V server resource.
    /// A second approximation would use the AgentShell settings files directly.
    /// A third approximation would look to invoke ServerResource methods via an HTTP request
    /// </summary>
    [TestInitializeAttribute]
    public void setUp()
    {
        // Used to create existing StoragePool in preparation for the ModifyStoragePool
        testLocalStoreUUID = ServerResource.Tests.AgentShell.Default.local_storage_uuid.ToString();

        // Make sure secondary store is available.
        DirectoryInfo testSecondarStoreDir = new DirectoryInfo(testSecondaryStoreLocalPath);
        if (!testSecondarStoreDir.Exists)
        {
            try {
                testSecondarStoreDir.Create();
            }
            catch (System.IO.IOException ex)
            {
                Assert.Fail("Need to be able to create the folder " + testSecondarStoreDir.FullName);
            }
        }

        // Convert to secondary storage string to canonical path
        testSecondaryStoreLocalPath = testSecondarStoreDir.FullName;
        ServerResource.Tests.AgentShell.Default.local_secondary_storage_path = testSecondaryStoreLocalPath;

        // Make sure local primary storage is available
        DirectoryInfo testPoolDir = new DirectoryInfo(testLocalStorePath);
        Assert.IsTrue(testPoolDir.Exists, "To simulate local file system Storage Pool, you need folder at " + testPoolDir.FullName);

        // Convert to local primary storage string to canonical path
        testLocalStorePath = testPoolDir.FullName;
        ServerResource.Tests.AgentShell.Default.local_storage_path = testLocalStorePath;

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
        s_logger.Info("Created " + testSampleVolumeTempURIJSON );
        testSampleVolumeCorruptURIJSON = CreateTestDiskImageFromExistingImage(testVolWorks, testLocalStorePath, testSampleVolumeCorruptUUID);
        s_logger.Info("Created " + testSampleVolumeCorruptURIJSON);
        CreateTestDiskImageFromExistingImage(testVolWorks, testLocalStorePath, testSampleTemplateUUID);
        testSampleTemplateURLJSON = testSampleTemplateUUID;
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
        public void TestDeployVm()
        {
            // Arrange
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

            // Act
            var vm = WmiCalls.DeployVirtualMachine(sample);

            // Assert
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

            // TODO: System is now running.  Compare the resources allocated for the VM with what the VM is using.

            // Clean up
            // Destory VM resources
            WmiCalls.DestroyVm(vmName);

            // Assert VM is gone!
            var finalVm = WmiCalls.GetComputerSystem(vmName);
            Assert.IsTrue(WmiCalls.GetComputerSystem(vmName) == null);
        }

        //[TestMethod]
        public void TestDeployVmConstituents()
        {
            // Arrange
            var vmName = "UnitTestVm_CreateVMCall";
            long memory_mb = 512;
            int vcpus = 1;

            // WmiCalls.DeleteSwitchPort(vmName);

            // Act
            var vm = WmiCalls.CreateVM(vmName, memory_mb, vcpus);

            // Assert
            VirtualSystemSettingData vmSettings = WmiCalls.GetVmSettings(vm);
            MemorySettingData memSettings = WmiCalls.GetMemSettings(vmSettings);
            ProcessorSettingData procSettings = WmiCalls.GetProcSettings(vmSettings);
            Assert.IsTrue((long)memSettings.VirtualQuantity == memory_mb);
            Assert.IsTrue((long)memSettings.Reservation == memory_mb);
            Assert.IsTrue((long)memSettings.Limit == memory_mb);
            Assert.IsTrue((int)procSettings.VirtualQuantity == vcpus);
            Assert.IsTrue((int)procSettings.Reservation == vcpus);
            Assert.IsTrue((int)procSettings.Limit == 100000);

            // Act
            // Add a HD and DVD to the new vm
            // TODO: Use relative that for test vhdLocation.
            var vhdLocation = @"C:\cygwin\home\Administrator\github\cshv3\plugins\hypervisors\hyperv\DotNET\ServerResource\ServerResource.Tests\TestResources\TestVolumeLegit.vhdx";
            ManagementPath hdPath = WmiCalls.AddDiskDriveToVm(vm, vhdLocation, "0", WmiCalls.IDE_HARDDISK_DRIVE);
            ManagementPath isoPath = WmiCalls.AddDiskDriveToVm(vm, null, "1", WmiCalls.IDE_ISO_DRIVE);

            // TODO:  Add an ISO as well.

            // Act
            // TODO:  Use the following invalid MAC address to trigger the error handling (check that it is indeed the wrong format)
            //            var nic = WmiCalls.CreateNICforVm(vm, "ff:01:02:03:04:06", "501");  
            SyntheticEthernetPortSettingData nicSettings = WmiCalls.CreateNICforVm(vm, "02:00:33:F8:00:09", "501"); 
            
            // Assert by looking of the nic vi the VM.
            SyntheticEthernetPortSettingData nicSettingsViaVm = WmiCalls.GetEthernetPort(vm);
            Assert.AreEqual(nicSettingsViaVm.InstanceID, nicSettings.InstanceID);
            
            // TODO:  NIC seems only to appear when VM is started, which prevents tests below.  Can we work around this problem by starting the VM?

            // Assert NIC is associated with switchport (via its LANEndPoint)
            // 
            //SwitchPort switchPort = WmiCalls.GetSwitchPort(nic);
            //Assert.AreEqual(switchPort.ElementName, nic.ElementName);

            // TODO:  verify that the switch port has the correct VLAN.  Not clear how switch port references a NIC.

            // Assert switchport has correct VLAN (via Msvm_VLANEndpoint via Msvm_VLANEndpointSettings) of the switch port
            //VirtualSwitchManagementService vmNetMgmtSvc = WmiCalls.GetVirtualSwitchManagementService();
            //VLANEndpointSettingData vlanSettings = WmiCalls.GetVlanEndpointSettings(vmNetMgmtSvc, switchPort.Path);

            // Clean up
            // Destory VM resources
            WmiCalls.DestroyVm(vmName);

            // Assert VM is gone!
            var finalVm = WmiCalls.GetComputerSystem(vmName);
            Assert.IsTrue(WmiCalls.GetComputerSystem(vmName) == null);
        }
    }
}
