﻿using Amazon;
using Amazon.S3;
using Amazon.S3.Model;
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

using CloudStack.Plugin.WmiWrappers.ROOT.VIRTUALIZATION;
using log4net;
using Microsoft.CSharp.RuntimeBinder;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Web.Http;

namespace HypervResource
{

    public struct HypervResourceControllerConfig
    {
        private string privateIpAddress;
        private static ILog logger = LogManager.GetLogger(typeof(HypervResourceControllerConfig));

        public string PrivateIpAddress
        {
            get
            {
                return privateIpAddress;
            }
            set
            {
                ValidateIpAddress(value);
                privateIpAddress = value;
                var nic = HypervResourceController.GetNicInfoFromIpAddress(privateIpAddress, out PrivateNetmask);
                PrivateMacAddress = nic.GetPhysicalAddress().ToString();
            }
        }

        private static void ValidateIpAddress(string value)
        {
            // Convert to IP address
            IPAddress ipAddress;
            if (!IPAddress.TryParse(value, out ipAddress))
            {
                String errMsg = "Invalid PrivateIpAddress: " + value;
                logger.Error(errMsg);
                throw new ArgumentException(errMsg);
            }
        }
        public string GatewayIpAddress;
        public string PrivateMacAddress;
        public string PrivateNetmask;
        public string StorageNetmask;
        public string StorageMacAddress;
        public string StorageIpAddress;
        public long RootDeviceReservedSpaceBytes;
        public string RootDeviceName;
        public ulong ParentPartitionMinMemoryMb;
        public string LocalSecondaryStoragePath;
    }

    /// <summary>
    /// Supports one HTTP GET and multiple HTTP POST URIs
    /// </summary>
    /// <remarks>
    /// <para>
    /// POST takes dynamic to allow it to receive JSON without concern for what is the underlying object.
    /// E.g. http://stackoverflow.com/questions/14071715/passing-dynamic-json-object-to-web-api-newtonsoft-example 
    /// and http://stackoverflow.com/questions/3142495/deserialize-json-into-c-sharp-dynamic-object
    /// Use ActionName attribute to allow multiple POST URLs, one for each supported command
    /// E.g. http://stackoverflow.com/a/12703423/939250
    /// Strictly speaking, this goes against the purpose of an ApiController, which is to provide one GET/POST/PUT/DELETE, etc.
    /// However, it reduces the amount of code by removing the need for a switch according to the incoming command type.
    /// http://weblogs.asp.net/fredriknormen/archive/2012/06/11/asp-net-web-api-exception-handling.aspx
    /// </para>
    /// <para>
    /// Exceptions handled on command by command basis rather than globally to allow details of the command
    /// to be reflected in the response.  Default error handling is in the catch for Exception, but
    /// other exception types may be caught where the feedback would be different.
    /// NB: global alternatives discussed at 
    /// http://weblogs.asp.net/fredriknormen/archive/2012/06/11/asp-net-web-api-exception-handling.aspx
    /// </para>
    /// </remarks>
    public class HypervResourceController : ApiController
    {
        public static void Configure(HypervResourceControllerConfig config)
        {
            HypervResourceController.config = config;
        }

        public static HypervResourceControllerConfig config = new HypervResourceControllerConfig();

        private static ILog logger = LogManager.GetLogger(typeof(WmiCalls));

        public static void Initialize()
        {
        }

        // GET api/HypervResource
        public string Get()
        {
            using(log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                return "HypervResource controller running, use POST to send JSON encoded RPCs"; ;
            }
        }

        /// <summary>
        /// NOP - placeholder for future setup, e.g. delete existing VMs or Network ports 
        /// POST api/HypervResource/SetupCommand
        /// </summary>
        /// <param name="cmd"></param>
        /// <returns></returns>
        /// TODO: produce test
        [HttpPost]
        [ActionName(CloudStackTypes.SetupCommand)]
        public JContainer SetupCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());

                object ansContent = new
                {
                    result = true,
                    details = "success - NOP",
                    _reconnect = false
                };

                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.SetupAnswer);
            }
        }


        // POST api/HypervResource/DestroyCommand
        [HttpPost]
        [ActionName(CloudStackTypes.DestroyCommand)]
        public JContainer DestroyCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());

                string details = null;
                bool result = false;

                try
                {
                    string path = cmd.volume.path;
                    string vmName = cmd.vmName;

                    // TODO: detach volume
                    var imgmgr = WmiCalls.GetImageManagementService();
                    if (!string.IsNullOrEmpty(vmName))
                    {
                        var returncode = imgmgr.Unmount(path);
                        if (returncode != ReturnCode.Completed)
                        {
                            details = "Could not detach driver from vm " + vmName + " for drive " + path;
                            logger.Error(details);
                        }
                        File.Delete(path);
                        result = true;
                    }
                    else
                    {
                        File.Delete(path);
                        result = true;
                    }
                }
                catch (Exception sysEx)
                {
                    details = CloudStackTypes.DestroyCommand + " failed due to " + sysEx.Message;
                    logger.Error(details, sysEx);
                }

                object ansContent = new
                    {
                        result = result,
                        details = details
                    };

                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.Answer);
            }
        }

        private static JArray ReturnCloudStackTypedJArray(object ansContent, string ansType)
        {
            JObject ansObj = Utils.CreateCloudStackObject(ansType, ansContent);
            JArray answer = new JArray(ansObj);
            logger.Info(ansObj.ToString());
            return answer;
        }

        // POST api/HypervResource/CreateCommand
        [HttpPost]
        [ActionName(CloudStackTypes.CreateCommand)]
        public JContainer CreateCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());

                string details = null;
                bool result = false;
                VolumeInfo volume = new VolumeInfo();

                try
                {
                    string diskType = cmd.diskCharacteristics.type;
                    ulong disksize = cmd.diskCharacteristics.size;
                    string templateUri = cmd.templateUrl;

                    // assert: valid storagepool?
                    string poolTypeStr = cmd.pool.type;
                    string poolLocalPath = cmd.pool.path;
                    string poolUuid = cmd.pool.uuid;
                    string newVolPath = null;
                    long volId = cmd.volId;
                    string newVolName = null;

                    if (ValidStoragePool(poolTypeStr, poolLocalPath, poolUuid, ref details))
                    {
                        // No template URI?  Its a blank disk.
                        if (string.IsNullOrEmpty(templateUri))
                        {
                            // assert
                            VolumeType volType;
                            if (!Enum.TryParse<VolumeType>(diskType, out volType) && volType != VolumeType.DATADISK)
                            {
                                details = "Cannot create volumes of type " + (string.IsNullOrEmpty(diskType) ? "NULL" : diskType);
                            }
                            else
                            {
                                newVolName = cmd.diskCharacteristics.name;
                                newVolPath = Path.Combine(poolLocalPath, newVolName, diskType.ToLower());
                                // TODO: how do you specify format as VHD or VHDX?
                                WmiCalls.CreateDynamicVirtualHardDisk(disksize, newVolPath);
                                if (File.Exists(newVolPath))
                                {
                                    result = true;
                                }
                                else
                                {
                                    details = "Failed to create DATADISK with name " + newVolName;
                                }
                            }
                        }
                        else
                        {
                            // TODO:  Does this always work, or do I need to download template at times?
                            if (templateUri.Contains("/") || templateUri.Contains("\\"))
                            {
                                details = "Problem with templateURL " + templateUri +
                                                " the URL should be volume UUID in primary storage created by previous PrimaryStorageDownloadCommand";
                                logger.Error(details);
                            }
                            else
                            {
                                logger.Debug("Template's name in primary store should be " + templateUri);
                                //            HypervPhysicalDisk BaseVol = primaryPool.getPhysicalDisk(tmplturl);
                                FileInfo srcFileInfo = new FileInfo(templateUri);
                                newVolName = Guid.NewGuid() + srcFileInfo.Extension;
                                newVolPath = Path.Combine(poolLocalPath, newVolName);
                                logger.Debug("New volume will be at " + newVolPath);
                                string oldVolPath = Path.Combine(poolLocalPath, templateUri);
                                File.Copy(oldVolPath, newVolPath);
                                if (File.Exists(newVolPath))
                                {
                                    result = true;
                                }
                                else
                                {
                                    details = "Failed to create DATADISK with name " + newVolName;
                                }
                            }
                            volume = new VolumeInfo(
                                      volId, diskType,
                                    poolTypeStr, poolUuid, newVolName,
                                    newVolPath, newVolPath, (long)disksize, null);
                        }
                    }
                }
                catch (Exception sysEx)
                {
                    // TODO: consider this as model for error processing in all commands
                    details = CloudStackTypes.CreateCommand + " failed due to " + sysEx.Message;
                    logger.Error(details, sysEx);
                }

                object ansContent = new
                {
                    result = result,
                    details = details,
                    volume = volume
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.CreateAnswer);
            }
        }

        // POST api/HypervResource/PrimaryStorageDownloadCommand
        [HttpPost]
        [ActionName(CloudStackTypes.PrimaryStorageDownloadCommand)]
        public JContainer PrimaryStorageDownloadCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                string details = null;
                bool result = false;
                long size = 0;
                string newCopyFileName = null;

                string poolLocalPath = cmd.localPath;
                string poolUuid = cmd.poolUuid;
                if (!Directory.Exists(poolLocalPath))
                {
                    details = "None existent local path " + poolLocalPath;
                }
                else
                {
                    // Compose name for downloaded file.
                    string sourceUrl = cmd.url;
                    if (sourceUrl.ToLower().EndsWith(".vhd"))
                    {
                        newCopyFileName = Guid.NewGuid() + ".vhd";
                    }
                    if (sourceUrl.ToLower().EndsWith(".vhdx"))
                    {
                        newCopyFileName = Guid.NewGuid() + ".vhdx";
                    }

                    // assert
                    if (newCopyFileName == null)
                    {
                        details = CloudStackTypes.PrimaryStorageDownloadCommand + " Invalid file extension for hypervisor type in source URL " + sourceUrl;
                        logger.Error(details);
                    }
                    else
                    {
                        try
                        {
                            FileInfo newFile;
                            if (CopyURI(sourceUrl, newCopyFileName, poolLocalPath, out newFile, ref details))
                            {
                                size = newFile.Length;
                                result = true;
                            }
                        }
                        catch (System.Exception ex)
                        {
                            details = CloudStackTypes.PrimaryStorageDownloadCommand + " Cannot download source URL " + sourceUrl + " due to " + ex.Message;
                            logger.Error(details, ex);
                        }
                    }
                }

                object ansContent = new
                {
                    result = result,
                    details = details,
                    templateSize = size,
                    installPath = newCopyFileName
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.PrimaryStorageDownloadAnswer);
            }
        }

        private static bool ValidStoragePool(string poolTypeStr, string poolLocalPath, string poolUuid, ref string details)
        {
            StoragePoolType poolType;
            if (!Enum.TryParse<StoragePoolType>(poolTypeStr, out poolType) || poolType != StoragePoolType.Filesystem)
            {
                details = "Primary storage pool " + poolUuid + " type " + poolType + " local path " + poolLocalPath + " has invalid StoragePoolType";
                logger.Error(details);
                return false;
            }
            else if (!Directory.Exists(poolLocalPath))
            {
                details = "Primary storage pool " + poolUuid + " type " + poolType + " local path " + poolLocalPath + " has invalid local path";
                logger.Error(details);
                return false;
            }
            return true;
        }

        /// <summary>
        /// Exceptions to watch out for:
        /// Exceptions related to URI creation
        /// System.SystemException
        /// +-System.ArgumentNullException
        /// +-System.FormatException
        ///   +-System.UriFormatException
        ///
        /// Exceptions related to NFS URIs
        /// System.SystemException
        /// +-System.NotSupportedException
        /// +-System.ArgumentException
        /// +-System.ArgumentNullException
        ///   +-System.Security.SecurityException;
        /// +-System.UnauthorizedAccessException
        /// +-System.IO.IOException
        ///   +-System.IO.PathTooLongException
        ///   
        /// Exceptions related to HTTP URIs
        /// System.SystemException
        /// +-System.InvalidOperationException
        ///    +-System.Net.WebException
        /// +-System.NotSupportedException
        /// +-System.ArgumentNullException
        /// </summary>
        /// <param name="sourceUri"></param>
        /// <param name="newCopyFileName"></param>
        /// <param name="poolLocalPath"></param>
        /// <returns></returns>
        private bool CopyURI(string sourceUri, string newCopyFileName, string poolLocalPath, out FileInfo newFile, ref string details)
        {
            Uri source = new Uri(sourceUri);
            String destFilePath = Path.Combine(poolLocalPath, newCopyFileName);
            string[] pathSegments = source.Segments;
            String templateUUIDandExtension = pathSegments[pathSegments.Length-1];
            newFile = new FileInfo(destFilePath);

            // NFS URI assumed to already be mounted locally.  Mount location given by settings.
        	if (source.Scheme.ToLower().Equals("nfs"))
        	{
            	String srcDiskPath = Path.Combine(HypervResourceController.config.LocalSecondaryStoragePath, templateUUIDandExtension);
            	String taskMsg = "Copy NFS url in " + sourceUri + " at " + srcDiskPath + " to pool " + poolLocalPath;
                logger.Debug(taskMsg);
                File.Copy(srcDiskPath, destFilePath);
        	}
            else if (source.Scheme.ToLower().Equals("http") || source.Scheme.ToLower().Equals("https"))
            {
                System.Net.WebClient webclient = new WebClient();
                webclient.DownloadFile(source, destFilePath);
            }
            else
            {
                details = "Unsupported URI scheme " + source.Scheme.ToLower() + " in source URI " + sourceUri;
                logger.Error(details);
                return false;
            }

            if (!File.Exists(destFilePath))
            {
                details = "Filed to copy " + sourceUri + " to primary pool destination " + destFilePath;
                logger.Error(details);
                return false;
            }
            return true;
        }

        // POST api/HypervResource/CheckHealthCommand
        // TODO: create test
        [HttpPost]
        [ActionName(CloudStackTypes.CheckHealthCommand)]
        public JContainer CheckHealthCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                object ansContent = new
                {
                    result = true,
                    details = "resource is alive"
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.CheckHealthAnswer);
            }
        }

        // POST api/HypervResource/CheckVirtualMachineCommand
        [HttpPost]
        [ActionName(CloudStackTypes.CheckVirtualMachineCommand)]
        public JContainer CheckVirtualMachineCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                string details = null;
                bool result = false;
                string vmName = cmd.vmName;
                string state = null;

                // TODO: Look up the VM, convert Hyper-V state to CloudStack version.
                var sys = WmiCalls.GetComputerSystem(vmName);
                if (sys == null)
                {
                    details = CloudStackTypes.CheckVirtualMachineCommand + " requested unknown VM " + vmName;
                    logger.Error(details);
                }
                else
                {
                    state = EnabledState.ToString(sys.EnabledState);
                    result = true;
                }

                object ansContent = new
                {
                    result = result,
                    details = details,
                    state = state
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.CheckVirtualMachineAnswer);
            }
        }

        // POST api/HypervResource/DeleteStoragePoolCommand
        [HttpPost]
        [ActionName(CloudStackTypes.DeleteStoragePoolCommand)]
        public JContainer DeleteStoragePoolCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                object ansContent = new
                {
                    result = true,
                    details = "Current implementation does not delete local path corresponding to storage pool!"
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.Answer);
            }
        }

        /// <summary>
        /// NOP - legacy command -
        /// POST api/HypervResource/CreateStoragePoolCommand
        /// </summary>
        /// <param name="cmd"></param>
        /// <returns></returns>
        [HttpPost]
        [ActionName(CloudStackTypes.CreateStoragePoolCommand)]
        public JContainer CreateStoragePoolCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                object ansContent = new
                {
                    result = true,
                    details = "success - NOP"
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.Answer);
            }
        }

        // POST api/HypervResource/ModifyStoragePoolCommand
        [HttpPost]
        [ActionName(CloudStackTypes.ModifyStoragePoolCommand)]
        public JContainer ModifyStoragePoolCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                string details = null;
                string localPath;
                object ansContent;

                bool result = ValidateStoragePoolCommand(cmd, out localPath, ref details);
                if (!result)
                {
                    ansContent = new
                        {
                            result = result,
                            details = details
                        };
                    return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.Answer);
                }

                var tInfo = new Dictionary<string, string>();
                long capacityBytes;
                long availableBytes;
                GetCapacityForLocalPath(localPath, out capacityBytes, out availableBytes);

                String uuid = null;
                var poolInfo = new
                {
                    uuid = uuid,
                    host = cmd.pool.host,
                    localPath = cmd.pool.host,
                    hostPath = cmd.localPath,
                    poolType = cmd.pool.type,
                    capacityBytes = capacityBytes,
                    // TODO:  double check whether you need 'available' or 'used' bytes?
                    availableBytes = availableBytes
                };

                ansContent = new
                    {
                        result = result,
                        details = details,
                        templateInfo = tInfo,
                        poolInfo = poolInfo
                    };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.ModifyStoragePoolAnswer);
            }
        }

        private bool ValidateStoragePoolCommand(dynamic cmd, out string localPath, ref string details)
        {
            dynamic pool = cmd.pool;
            string poolTypeStr = pool.type;
            StoragePoolType poolType;
            localPath = cmd.localPath;
            if (!Enum.TryParse<StoragePoolType>(poolTypeStr, out poolType) || poolType != StoragePoolType.Filesystem)
            {
                details = "Request to create / modify unsupported pool type: " + (poolTypeStr == null ? "NULL" : poolTypeStr) + "in cmd " + JsonConvert.SerializeObject(cmd);
                logger.Error(details);
                return false;
            }
            if (!Directory.Exists(localPath))
            {
                details = "Request to create / modify unsupported StoragePoolType.Filesystem with non-existent path:" + (localPath == null ? "NULL" : localPath) + "in cmd " + JsonConvert.SerializeObject(cmd);
                logger.Error(details);
                return false;
            }
            return true;
        }


        // POST api/HypervResource/CleanupNetworkRulesCmd
        [HttpPost]
        [ActionName(CloudStackTypes.CleanupNetworkRulesCmd)]
        public JContainer CleanupNetworkRulesCmd([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                object ansContent = new
                 {
                     result = false,
                     details = "nothing to cleanup in our current implementation"
                 };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.Answer);
            }
        }

        // POST api/HypervResource/CheckNetworkCommand
        [HttpPost]
        [ActionName(CloudStackTypes.CheckNetworkCommand)]
        public JContainer CheckNetworkCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                object ansContent = new
                {
                    result = true,
                    details = (string)null
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.CheckNetworkAnswer);
            }
        }

        // POST api/HypervResource/ReadyCommand
        [HttpPost]
        [ActionName(CloudStackTypes.ReadyCommand)]
        public JContainer ReadyCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                object ansContent = new
                {
                    result = true,
                    details = (string)null
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.ReadyAnswer);
            }

        }
        
        // POST api/HypervResource/StartCommand
        [HttpPost]
        [ActionName(CloudStackTypes.StartCommand)]
        public JContainer StartCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                string details = null;
                bool result = false;

                try
                {
                    WmiCalls.DeployVirtualMachine(cmd);
                    result = true;
                }
                catch (Exception wmiEx)
                {
                    details = CloudStackTypes.StartCommand + " fail on exception" + wmiEx.Message;
                    logger.Error(details, wmiEx);
                }

                object ansContent = new
                {
                    result = result,
                    details = details,
                    vm = cmd.vm
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.StartAnswer);
            }
        }

        // POST api/HypervResource/StartCommand
        [HttpPost]
        [ActionName(CloudStackTypes.StopCommand)]
        public JContainer StopCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                string details = null;
                bool result = false;

                try
                {
                    WmiCalls.DestroyVm(cmd);
                    result = true;
                }
                catch (Exception wmiEx)
                {
                    details = CloudStackTypes.StopCommand + " fail on exception" + wmiEx.Message;
                    logger.Error(details, wmiEx);
                }

                object ansContent = new
                {
                    result = result,
                    details = details,
                    vm = cmd.vm
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.StopAnswer);
            }
        }

        // POST api/HypervResource/GetVmStatsCommand
        [HttpPost]
        [ActionName(CloudStackTypes.GetVmStatsCommand)]
        public JContainer GetVmStatsCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                bool result = false;
                string details = null;
                JArray vmNamesJson = cmd.vmNames;
                string[] vmNames = vmNamesJson.ToObject<string[]>();
                Dictionary<string, VmStatsEntry> vmProcessorInfo = new Dictionary<string, VmStatsEntry>(vmNames.Length);

                var vmsToInspect = new List<System.Management.ManagementPath>();
                foreach (var vmName in vmNames)
                {
                    var sys = WmiCalls.GetComputerSystem(vmName);
                    if (sys == null)
                    {
                        logger.InfoFormat("GetVmStatsCommand requested unknown VM {0}", vmNames);
                        continue;
                    }
                    var sysInfo = WmiCalls.GetVmSettings(sys);
                    vmsToInspect.Add(sysInfo.Path);
                }

                // Process info available from WMI, 
                // See http://msdn.microsoft.com/en-us/library/cc160706%28v=vs.85%29.aspx
                uint[] requestedInfo = new uint[] {
                    0, // Name
                    1, // ElementName
                    4, // Number of processes
                    101 // ProcessorLoad
                };

                System.Management.ManagementBaseObject[] sysSummary;
                var vmsvc = WmiCalls.GetVirtualisationSystemManagementService();
                System.Management.ManagementPath[] vmPaths = vmsToInspect.ToArray();
                vmsvc.GetSummaryInformation(requestedInfo, vmPaths, out sysSummary);

                foreach (var summary in sysSummary)
                {
                    var summaryInfo = new CloudStack.Plugin.AgentShell.ROOT.VIRTUALIZATION.SummaryInformation(summary);

                    logger.Debug("VM " + summaryInfo.Name + "(elementName " + summaryInfo.ElementName + ") has " +
                                    summaryInfo.NumberOfProcessors + " CPUs, and load of " + summaryInfo.ProcessorLoad);
                    var vmInfo = new VmStatsEntry
                    {
                        cpuUtilization = summaryInfo.ProcessorLoad,
                        numCPUs = summaryInfo.NumberOfProcessors,
                        networkReadKBs = 1,
                        networkWriteKBs = 1,
                        entityType = "vm"
                    };
                    vmProcessorInfo.Add(summaryInfo.ElementName, vmInfo);
                }

                // TODO: Network usage comes from Performance Counter API; however it is only available in kb/s, and not in total terms.
                // Curious about these?  Use perfmon to inspect them, e.g. http://msdn.microsoft.com/en-us/library/xhcx5a20%28v=vs.100%29.aspx
                // Recent post on these counter at http://blogs.technet.com/b/cedward/archive/2011/07/19/hyper-v-networking-optimizations-part-6-of-6-monitoring-hyper-v-network-consumption.aspx
                result = true;

                object ansContent = new
                {
                    vmInfos = vmProcessorInfo,
                    result = result,
                    details = details,
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.GetVmStatsAnswer);
            }
        }

        // POST api/HypervResource/StartupCommand
        [HttpPost]
        [ActionName(CloudStackTypes.CopyCommand)]
        public JContainer CopyCommand(dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                bool result = false;
                string details = null;
                object newData = null;

                try
                {
                    dynamic timeout = cmd.wait;  // Useful?

                    TemplateObjectTO srcTemplateObjectTO = TemplateObjectTO.ParseJson(cmd.srcTO);
                    TemplateObjectTO destTemplateObjectTO = TemplateObjectTO.ParseJson(cmd.destTO);
                    VolumeObjectTO destVolumeObjectTO = VolumeObjectTO.ParseJson(cmd.destTO);

                    logger.Info(cmd.ToString()); // Log command *after* we've removed security details from the command.

                    // Create local copy of a template?
                    if (srcTemplateObjectTO != null && destTemplateObjectTO != null)
                    {
                        // S3 download to primary storage?
                        if (srcTemplateObjectTO.s3DataStoreTO != null && destTemplateObjectTO.primaryDataStore != null)
                        {
                            string destFile = destTemplateObjectTO.FullFileName;
                            if (!File.Exists(destFile))
                            {
                                // Download from S3 to destination data storage
                                DownloadS3ObjectToFile(srcTemplateObjectTO.path, srcTemplateObjectTO.s3DataStoreTO, destFile);

                                newData = cmd.destTO;
                                result = true;
                            }
                            else
                            {
                                details = "File already exists at " + destFile;
                            }
                        }
                        else
                        {
                            details = "Data store combination not supported";
                        }
                    }
                    // Create volume from a template?
                    else if (srcTemplateObjectTO != null && destVolumeObjectTO != null)
                    {
                        PrimaryDataStoreTO srcPrimaryDataStore = PrimaryDataStoreTO.ParseJson(srcTemplateObjectTO.imageDataStore);
                        PrimaryDataStoreTO destPrimaryDataStore = PrimaryDataStoreTO.ParseJson(destVolumeObjectTO.dataStore);
                        string destFile = Path.Combine(destPrimaryDataStore.path, destVolumeObjectTO.FileName);
                        string srcFile = srcTemplateObjectTO.FullFileName;

                        if (File.Exists(destFile))
                        {
                            details = "File already exists at " + destFile;
                        }
                        else if (!File.Exists(srcFile))
                        {
                            details = "Local template file missing from " + srcFile;
                        }
                        else
                        {
                            // TODO: thin provision instead of copying the full file.
                            File.Copy(srcFile, destFile);
                            newData = cmd.destTO;
                            result = true;
                        }
                    }
                    else
                    {
                        details = "Data store combination not supported";
                    }
                }
                catch (Exception ex)
                {
                    // Test by providing wrong key
                    details = CloudStackTypes.CopyCommand + " failed on exception, " + ex.Message;
                    logger.Error(details, ex);
                }

                object ansContent = new
                {
                    result = result,
                    details = details,
                    newData = newData
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.CopyCmdAnswer);
            }
        }

        private static void DownloadS3ObjectToFile(string srcObjectKey, S3TO srcS3TO, string destFile)
        {
            AmazonS3Config S3Config = new AmazonS3Config
            {
                ServiceURL = srcS3TO.endpoint,
                CommunicationProtocol = Amazon.S3.Model.Protocol.HTTP
            };

            if (srcS3TO.httpsFlag)
            {
                S3Config.CommunicationProtocol = Protocol.HTTPS;
            }

            try
            {
                using (AmazonS3 client = Amazon.AWSClientFactory.CreateAmazonS3Client(srcS3TO.accessKey, srcS3TO.secretKey, S3Config))
                {
                    GetObjectRequest getObjectRequest = new GetObjectRequest().WithBucketName(srcS3TO.bucketName).WithKey(srcObjectKey);

                    using (S3Response getObjectResponse = client.GetObject(getObjectRequest))
                    {
                        using (Stream s = getObjectResponse.ResponseStream)
                        {
                            using (FileStream fs = new FileStream(destFile, FileMode.Create, FileAccess.Write))
                            {
                                byte[] data = new byte[524288];
                                int bytesRead = 0;
                                do
                                {
                                    bytesRead = s.Read(data, 0, data.Length);
                                    fs.Write(data, 0, bytesRead);
                                }
                                while (bytesRead > 0);
                                fs.Flush();
                            }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                string errMsg = "Download from S3 url" + srcS3TO.endpoint + " said: " + ex.Message;
                logger.Error(errMsg, ex);
                throw new Exception(errMsg, ex);
            }
        }


        // POST api/HypervResource/GetStorageStatsCommand
        [HttpPost]
        [ActionName(CloudStackTypes.GetStorageStatsCommand)]
        public JContainer GetStorageStatsCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                bool result = false;
                string details = null;
                long capacity = 0;
                long available = 0;
                long used = 0;
                try
                {
                    string localPath = (string)cmd.localPath;
                    GetCapacityForLocalPath(localPath, out capacity, out available);
                    used = capacity - available;
                    result = true;
                }
                catch (Exception ex)
                {
                    details = CloudStackTypes.GetStorageStatsCommand + " failed on exception" + ex.Message;
                    logger.Error(details, ex);
                }

                object ansContent = new
                {
                    result = result,
                    details = details,
                    capacity = capacity,
                    used = used
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.GetStorageStatsAnswer);
            }
        }

        // POST api/HypervResource/GetHostStatsCommand
        [HttpPost]
        [ActionName(CloudStackTypes.GetHostStatsCommand)]
        public JContainer GetHostStatsCommand([FromBody]dynamic cmd)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmd.ToString());
                bool result = false;
                string details = null;
                object hostStats = null;

                var entityType = "host";
                ulong totalMemoryKBs;
                ulong freeMemoryKBs;
                double networkReadKBs;
                double networkWriteKBs;
                double cpuUtilization;

                try
                {
                    long hostId = (long)cmd.hostId;
                    WmiCalls.GetMemoryResources(out totalMemoryKBs, out freeMemoryKBs);
                    WmiCalls.GetProcessorUsageInfo(out cpuUtilization);

                    // TODO: can we assume that the host has only one adaptor?
                    string tmp;
                    var privateNic = GetNicInfoFromIpAddress(config.PrivateIpAddress, out tmp);
                    var nicStats = privateNic.GetIPStatistics();
                    networkReadKBs = nicStats.BytesReceived;
                    networkWriteKBs = nicStats.BytesSent;

                    // Generate GetHostStatsAnswer
                    hostStats = new
                    {
                        hostId = hostId,
                        entityType = entityType,
                        cpuUtilization = cpuUtilization,
                        networkReadKBs = networkReadKBs,
                        networkWriteKBs = networkWriteKBs,
                        totalMemoryKBs = (double)totalMemoryKBs,
                        freeMemoryKBs = (double)freeMemoryKBs
                    };
                    result = true;
                }
                catch (Exception ex)
                {
                    details = CloudStackTypes.GetHostStatsCommand + " failed on exception" + ex.Message;
                    logger.Error(details, ex);
                }

                object ansContent = new
                {
                    result = result,
                    hostStats = hostStats,
                    details = details
                };
                return ReturnCloudStackTypedJArray(ansContent, CloudStackTypes.GetHostStatsAnswer);
            }
        }

        // POST api/HypervResource/StartupCommand
        [HttpPost]
        [ActionName(CloudStackTypes.StartupCommand)]
        public JContainer StartupCommand([FromBody]dynamic cmdArray)
        {
            using (log4net.NDC.Push(Guid.NewGuid().ToString()))
            {
                logger.Info(cmdArray.ToString());
                // Log agent configuration
                logger.Info("Agent StartupRoutingCommand received " + cmdArray.ToString());
                dynamic strtRouteCmd = cmdArray[0][CloudStackTypes.StartupRoutingCommand];

                // Insert networking details
                strtRouteCmd.privateIpAddress = config.PrivateIpAddress;
                strtRouteCmd.privateNetmask = config.PrivateNetmask;
                strtRouteCmd.privateMacAddress = config.PrivateMacAddress;
                strtRouteCmd.storageIpAddress = config.PrivateIpAddress;
                strtRouteCmd.storageNetmask = config.PrivateNetmask;
                strtRouteCmd.storageMacAddress = config.PrivateMacAddress;
                strtRouteCmd.gatewayIpAddress = config.GatewayIpAddress;

                // Detect CPUs, speed, memory
                uint cores;
                uint mhz;
                WmiCalls.GetProcessorResources(out cores, out mhz);
                strtRouteCmd.cpus = cores;
                strtRouteCmd.speed = mhz;
                ulong memoryKBs;
                ulong freeMemoryKBs;
                WmiCalls.GetMemoryResources(out memoryKBs, out freeMemoryKBs);
                strtRouteCmd.memory = memoryKBs * 1024;   // Convert to bytes

                // Need 2 Gig for DOM0, see http://technet.microsoft.com/en-us/magazine/hh750394.aspx
                strtRouteCmd.dom0MinMemory = config.ParentPartitionMinMemoryMb * 1024 * 1024;  // Convert to bytes

                // Insert storage pool details.
                //
                // Read the localStoragePath for virtual disks from the Hyper-V configuration
                // See http://blogs.msdn.com/b/virtual_pc_guy/archive/2010/05/06/managing-the-default-virtual-machine-location-with-hyper-v.aspx
                // for discussion of Hyper-V file locations paths.
                string localStoragePath = WmiCalls.GetDefaultVirtualDiskFolder();
                if (localStoragePath != null)
                {
                    // GUID arbitrary.  Host agents deals with storage pool in terms of localStoragePath.
                    // We use HOST guid.
                    string poolGuid = strtRouteCmd.guid;

                    if (poolGuid == null)
                    {
                        poolGuid = Guid.NewGuid().ToString();
                        logger.InfoFormat("Setting Startup StoragePool GUID to " + poolGuid);
                    }
                    else
                    {
                        logger.InfoFormat("Setting Startup StoragePool GUID same as HOST, i.e. " + poolGuid);
                    }

                    long capacity;
                    long available;
                    GetCapacityForLocalPath(localStoragePath, out capacity, out available);

                    string ipAddr = strtRouteCmd.privateIpAddress;
                    StoragePoolInfo pi = new StoragePoolInfo(
                        poolGuid.ToString(),
                        ipAddr,
                        localStoragePath,
                        localStoragePath,
                        StoragePoolType.Filesystem.ToString(),
                        capacity,
                        available);

                    // Build StorageStartCommand using an anonymous type
                    // See http://stackoverflow.com/a/6029228/939250
                    object ansContent = new
                    {
                        poolInfo = pi,
                        guid = pi.uuid,
                        dataCenter = strtRouteCmd.dataCenter,
                        resourceType = StorageResourceType.STORAGE_POOL.ToString()  // TODO: check encoding
                    };
                    JObject ansObj = Utils.CreateCloudStackObject(CloudStackTypes.StartupStorageCommand, ansContent);
                    cmdArray.Add(ansObj);
                }

                // Convert result to array for type correctness?
                logger.Info(CloudStackTypes.StartupCommand + " result is " + cmdArray.ToString());
                return cmdArray;
            }
        }

        public static System.Net.NetworkInformation.NetworkInterface GetNicInfoFromIpAddress(string ipAddress, out string subnet)
        {
            System.Net.NetworkInformation.NetworkInterface[] nics = System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces();
            foreach (var nic in nics)
            {
                subnet = null;
                // TODO: use to remove NETMASK and MAC from the config file, and to validate the IPAddress.
                var nicProps = nic.GetIPProperties();
                bool found = false;
                foreach (var addr in nicProps.UnicastAddresses)
                {
                    if (addr.Address.Equals(IPAddress.Parse(ipAddress)))
                    {
                        subnet = addr.IPv4Mask.ToString();
                        found = true;
                    }
                }
                if (!found)
                {
                    continue;
                }
                return nic;
            }
            throw new ArgumentException("No NIC for ipAddress " + ipAddress);
        }

        public static void GetCapacityForLocalPath(string localStoragePath, out long capacityBytes, out long availableBytes)
        {
            // NB: DriveInfo does not work for remote folders (http://stackoverflow.com/q/1799984/939250)
            // DriveInfo requires a driver letter...
            string fullPath = Path.GetFullPath(localStoragePath);
            System.IO.DriveInfo poolInfo = new System.IO.DriveInfo(fullPath);
            capacityBytes = poolInfo.TotalSize;
            availableBytes = poolInfo.AvailableFreeSpace;

            // Don't allow all of the Root Device to be used for virtual disks
            if (poolInfo.RootDirectory.Name.ToLower().Equals(config.RootDeviceName))
            {
                availableBytes -= config.RootDeviceReservedSpaceBytes;
                availableBytes = availableBytes > 0 ? availableBytes : 0;
                capacityBytes -= config.RootDeviceReservedSpaceBytes;
                capacityBytes = capacityBytes > 0 ? capacityBytes : 0;
            }
        }
    }
}