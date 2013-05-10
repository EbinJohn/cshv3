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
            WmiCalls.GetMemoryResources(out memory_mb);

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
