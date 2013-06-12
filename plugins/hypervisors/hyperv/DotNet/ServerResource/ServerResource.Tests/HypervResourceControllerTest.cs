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
using System.IO;

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
        public void TestModifyStoragePoolCommand()
        {
            // Create dummy folder
            String folderName = Path.Combine(".", "Dummy");
            if (!Directory.Exists(folderName))
            {
                Directory.CreateDirectory(folderName);
            }

            var pool = new
            {  // From java class StorageFilerTO
                type = Enum.GetName(typeof(StoragePoolType), StoragePoolType.Filesystem),
                host = "127.0.0.1",
                port = -1,
                path = folderName,
                uuid = Guid.NewGuid().ToString(),
                userInfo = string.Empty // Used in future to hold credential
            };

            var cmd = new
            {
                add = true,
                pool = pool,
                localPath = folderName
            };
            JToken tok = JToken.FromObject(cmd);
            HypervResourceController controller = new HypervResourceController();

            // Act
            dynamic jsonResult = controller.ModifyStoragePoolCommand(tok);

            // Assert
            dynamic ans = jsonResult[0].ModifyStoragePoolAnswer;
            Assert.IsTrue((bool)ans.result, (string)ans.details);  // always succeeds

            // Clean up
            var cmd2 = new
            {
                pool = pool,
                localPath = folderName
            };
            JToken tok2 = JToken.FromObject(cmd);

            // Act
            dynamic jsonResult2 = controller.DeleteStoragePoolCommand(tok2);

            // Assert
            dynamic ans2 = jsonResult2[0].Answer;
            Assert.IsTrue((bool)ans2.result, (string)ans2.details);  // always succeeds
        }

        [TestMethod]
        public void CreateStoragePoolCommand()
        {
            var cmd = new { localPath = "NULL" };
            JToken tok = JToken.FromObject(cmd);
            HypervResourceController controller = new HypervResourceController();

            // Act
            dynamic jsonResult = controller.CreateStoragePoolCommand(tok);

            // Assert
            dynamic ans = jsonResult[0].Answer;
            Assert.IsTrue((bool)ans.result, (string)ans.details);  // always succeeds
        }

        [TestMethod]
        public void SetupCommand()
        {
            // Omit HostEnvironment object, as this is a series of settings currently not used.
            var cmd = new { multipath = false, needSetup = true };
            JToken tok = JToken.FromObject(cmd);
            HypervResourceController controller = new HypervResourceController();

            // Act
            dynamic jsonResult = controller.SetupCommand(tok);

            // Assert
            dynamic ans = jsonResult[0].SetupAnswer;
            Assert.IsTrue((bool)ans.result, (string)ans.details);  // always succeeds
        }

        [TestMethod]
        public void GetVmStatsCommandFail()
        {
            // Use WMI to find existing VMs
            List<String> vmNames = new List<String>();
            vmNames.Add("FakeVM");

            var cmd = new
            {
                hostGuid = "FAKEguid",
                hostName = AgentSettings.Default.host,
                vmNames = vmNames
            };
            JToken tok = JToken.FromObject(cmd);
            HypervResourceController controller = new HypervResourceController();

            // Act
            dynamic jsonResult = controller.GetVmStatsCommand(tok);

            // Assert
            dynamic ans = jsonResult[0].GetVmStatsAnswer;
            Assert.IsTrue((bool)ans.result, (string)ans.details);  // always succeeds, fake VM means no answer for the named VM
        }

        [TestMethod]
        public void GetVmStatsCommand()
        {
            // Use WMI to find existing VMs
            List<String> vmNames = WmiCalls.GetVmElementNames();

            var cmd = new
            {
                hostGuid = "FAKEguid",
                hostName = AgentSettings.Default.host,
                vmNames = vmNames
            };
            JToken tok = JToken.FromObject(cmd);
            HypervResourceController controller = new HypervResourceController();

            // Act
            dynamic jsonResult = controller.GetVmStatsCommand(tok);

            // Assert
            dynamic ans = jsonResult[0].GetVmStatsAnswer;
            Assert.IsTrue((bool)ans.result, (string)ans.details);
        }

        [TestMethod]
        public void GetStorageStatsCommand()
        {
    	    // TODO:  Update sample data to unsure it is using correct info.
    	    String sample = String.Format(
            #region string_literal
                "{{\"" +
                "id\":{0},"+
                "\"localPath\":{1}," +
    			"\"pooltype\":\"Filesystem\","+
                "\"contextMap\":{{}},"+
                "\"wait\":0}}",
                JsonConvert.SerializeObject(AgentSettings.Default.testLocalStoreUUID),
                JsonConvert.SerializeObject(AgentSettings.Default.testLocalStorePath)
                );
            #endregion
            var cmd = JsonConvert.DeserializeObject(sample);
            JToken tok = JToken.FromObject(cmd);
            HypervResourceController controller = new HypervResourceController();

            // Act
            dynamic jsonResult = controller.GetStorageStatsCommand(tok);

            // Assert
            dynamic ans = jsonResult[0].GetStorageStatsAnswer;
            Assert.IsTrue((bool)ans.result, (string)ans.details);
            Assert.IsTrue((long)ans.used <= (long)ans.capacity);  // TODO: verify that capacity is indeed capacity and not used.
        }

        [TestMethod]
        public void GetHostStatsCommand()
        {
            // Arrange
            long hostIdVal = 5;
            HypervResourceController controller = new HypervResourceController();
            string sample = string.Format(
            #region string_literal
                    "{{" + 
                    "\"hostGuid\":\"B4AE5970-FCBF-4780-9F8A-2D2E04FECC34-HypervResource\"," +
                    "\"hostName\":\"CC-SVR11\"," +
                    "\"hostId\":{0}," +
                    "\"contextMap\":{{}}," +
                    "\"wait\":0}}",
                    JsonConvert.SerializeObject(hostIdVal));
            #endregion
            var cmd = JsonConvert.DeserializeObject(sample);
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
            long capacityBytes;
            long availableBytes;
            HypervResourceController.GetCapacityForLocalPath(WmiCalls.GetDefaultVirtualDiskFolder(),
                    out capacityBytes, out availableBytes);

            string expected =
                #region string_literal
                        String.Format("[{{\"StartupRoutingCommand\":{{" +
                        "\"cpus\":{0}," +
                        "\"speed\":{11}," +
                        "\"memory\":{12}," +
                        "\"dom0MinMemory\":{13}," +
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
                        "\"privateIpAddress\":{9}," +
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
                        "\"host\":{9}," +
                        "\"localPath\":{7}," +
                        "\"hostPath\":{8}," +
                        "\"poolType\":\"Filesystem\"," +
                        "\"capacityBytes\":{14}," +
                        "\"availableBytes\":{15}," +
                        "\"details\":null" +
                        "}}," +
                        "\"guid\":\"16f85622-4508-415e-b13a-49a39bb14e4d\"," +
                        "\"dataCenter\":\"1\"," +
                        "\"resourceType\":\"STORAGE_POOL\"" +
                        "}}}}]",
                        JsonConvert.SerializeObject(cores),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_ip_address),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_ip_netmask),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_mac_address),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_ip_netmask),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_mac_address),
                        JsonConvert.SerializeObject(AgentSettings.Default.gateway_ip_address),
                        JsonConvert.SerializeObject(WmiCalls.GetDefaultVirtualDiskFolder()),
                        JsonConvert.SerializeObject(WmiCalls.GetDefaultVirtualDiskFolder()),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_ip_address),
                        JsonConvert.SerializeObject(AgentSettings.Default.private_ip_address),
                        JsonConvert.SerializeObject(mhz),
                        JsonConvert.SerializeObject(memory_mb),
                        JsonConvert.SerializeObject(AgentSettings.Default.dom0MinMemory),
                        JsonConvert.SerializeObject(capacityBytes),
                        JsonConvert.SerializeObject(availableBytes)
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
