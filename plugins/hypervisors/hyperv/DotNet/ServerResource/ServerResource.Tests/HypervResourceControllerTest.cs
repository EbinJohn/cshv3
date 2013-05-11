using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Web.Http;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using HypervResource;
using Newtonsoft.Json;
using CloudStack.Plugin.AgentShell;
using Newtonsoft.Json.Linq;

namespace ServerResource.Tests.Controllers
{
    // Tests whose origin lies in the ApiController that processes
    // incoming HTTP requests.
    [TestClass]
    public class HypervResourceControllerTest
    {

        [TestInitialize]
        public void setUp()
        {
            AgentService.ConfigServerResource();

            // Test tweaks
            HypervResourceController.config.PrivateMacAddress = AgentSettings.Default.private_mac_address;
            HypervResourceController.config.PrivateNetmask = AgentSettings.Default.private_ip_netmask;
            HypervResourceController.config.StorageIpAddress = HypervResourceController.config.PrivateIpAddress;
            HypervResourceController.config.StorageMacAddress = HypervResourceController.config.PrivateMacAddress;
            HypervResourceController.config.StorageNetmask = HypervResourceController.config.PrivateNetmask;

        }

        [TestMethod]
        public void GetHostStatsCommand()
        {
            // Arrange
            long hostIdVal = 123;
            HypervResourceController controller = new HypervResourceController();
            var cmd = new { GetHostStatsCommand = new { hostId = hostIdVal } };
            JToken tok = JToken.FromObject(cmd);
 
            // Act
            dynamic jsonResult = controller.GetHostStatsCommand(tok);

            // Assert
            dynamic ans = jsonResult[0].GetHostStatsAnswer;
            Assert.IsTrue((bool)ans.result);
            Assert.IsTrue(hostIdVal == (long)ans.hostStats.hostId);
            Assert.IsTrue(0.0 < (double)ans.hostStats.totalMemoryKBs);
            Assert.IsTrue(0.0 < (double)ans.hostStats.freeMemoryKBs);
            Assert.IsTrue(0.0 <= (double)ans.hostStats.networkReadKBs);
            Assert.IsTrue(0.0 <= (double)ans.hostStats.networkWriteKBs);
            Assert.IsTrue(0.0 <= (double)ans.hostStats.cpuUtilization);
            Assert.IsTrue(100.0 >= (double)ans.hostStats.cpuUtilization);
            Assert.IsTrue("host".Equals((string)ans.hostStats.entityType));
            Assert.IsTrue(String.IsNullOrEmpty((string)ans.details));
        }

        [TestMethod]
        public void GetHostStatsCommandFail()
        {
            // Arrange
            HypervResourceController controller = new HypervResourceController();
            var cmd = new { GetHostStatsCommand = new { hostId = "badvalueType" } };
            JToken tokFail = JToken.FromObject(cmd);

            // Act
            dynamic jsonResult = controller.GetHostStatsCommand(tokFail);

            // Assert
            dynamic ans = jsonResult[0].GetHostStatsAnswer;
            Assert.IsFalse((bool)ans.result);
            Assert.IsNull((string)ans.hostStats);
            Assert.IsNotNull(ans.details);
        }
        

        [TestMethod]
        public void StartupCommand()
        {
            // Arrange
            HypervResourceController controller = new HypervResourceController();
            string sampleStartupRoutingCommand =
            #region string_literal
                    "[{\"StartupRoutingCommand\":{" +
                    "\"cpus\":0," +
                    "\"speed\":0," +
                    "\"memory\":0," +
                    "\"dom0MinMemory\":0," +
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
                    "\"name\":\"localhost\"," +
                    "\"version\":\"4.1.0\"," +
                    "\"privateIpAddress\":\"1\"," +
                    "\"storageIpAddress\":\"1\"," +
                    "\"contextMap\":{}," +
                    "\"wait\":0}}]";
            #endregion

            uint cores;
            uint mhz;
            WmiCalls.GetProcessorResources(out cores, out mhz);
            ulong memory_mb;
            ulong freememory;
            WmiCalls.GetMemoryResources(out memory_mb, out freememory);
            memory_mb = memory_mb / 1024;

            string expected =
                #region string_literal
                        String.Format("[{{\"StartupRoutingCommand\":{{" +
                        "\"cpus\":{9}," +
                        "\"speed\":{10}," +
                        "\"memory\":{11}," +
                        "\"dom0MinMemory\":{12}," +
                        "\"poolSync\":false," +
                        "\"vms\":{{}}," +
                        "\"hypervisorType\":\"Hyperv\"," +
                        "\"hostDetails\":{{" +
                        "\"com.cloud.network.Networks.RouterPrivateIpStrategy\":\"HostLocal\"" +
                        "}}," +
                        "\"type\":\"Routing\"," +
                        "\"dataCenter\":\"1\"," +
                        "\"pod\":\"1\"," +
                        "\"cluster\":\"1\"," +
                        "\"guid\":\"16f85622-4508-415e-b13a-49a39bb14e4d\"," +
                        "\"name\":\"localhost\"," +
                        "\"version\":\"4.1.0\"," +
                        "\"privateIpAddress\":{0}," +
                        "\"storageIpAddress\":{1}," +
                        "\"contextMap\":{{}}," +
                        "\"wait\":0," +
                        "\"privateNetmask\":{2}," +
                        "\"privateMacAddress\":{3}," +
                        "\"storageNetmask\":{4}," +
                        "\"storageMacAddress\":{5}," +
                        "\"gatewayIpAddress\":{6}" +
                        "}}}}," +
                        "{{\"StartupStorageCommand\":{{" +
                        "\"poolInfo\":{{" +
                        "\"uuid\":\"16f85622-4508-415e-b13a-49a39bb14e4d\"," +
                        "\"host\":\"localhost\"," +
                        "\"localPath\":{7}," +
                        "\"hostPath\":{8}," +
                        "\"poolType\":\"Filesystem\"," +
                        "\"capacityBytes\":995907072000," +
                        "\"availableBytes\":945659260928," +
                        "\"details\":null" +
                        "}}," +
                        "\"guid\":\"16f85622-4508-415e-b13a-49a39bb14e4d\"," +
                        "\"dataCenter\":\"1\"," +
                        "\"resourceType\":\"STORAGE_POOL\"" +
                        "}}}}]",
                        JsonConvert.SerializeObject(AgentSettings.Default.private_ip_address),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_ip_address),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_ip_netmask),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_mac_address),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_ip_netmask),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_mac_address),
                        JsonConvert.SerializeObject(AgentSettings.Default.gateway_ip_address),
                        JsonConvert.SerializeObject(WmiCalls.GetDefaultVirtualDiskFolder()),
                        JsonConvert.SerializeObject(WmiCalls.GetDefaultVirtualDiskFolder()),
                        JsonConvert.SerializeObject(cores),
                        JsonConvert.SerializeObject(mhz),
                        JsonConvert.SerializeObject(memory_mb),
                        JsonConvert.SerializeObject(AgentSettings.Default.dom0MinMemory)
                        );
                #endregion

            dynamic jsonArray = JsonConvert.DeserializeObject(sampleStartupRoutingCommand);

            // Act
            dynamic jsonResult = controller.StartupCommand(jsonArray);
            
            // Assert
            string actual = JsonConvert.SerializeObject(jsonResult);
            Assert.AreEqual(expected, actual, "StartupRoutingCommand not populated properly");
        }
    }
}
