using System;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using CloudStack.Plugin.WmiWrappers.ROOT.VIRTUALIZATION;
using System.Management;

namespace ServerResource.Tests
{
    [TestClass]
    public class TestWmiCalls
    {
        [TestMethod]
        public void TestCreateVM()
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
