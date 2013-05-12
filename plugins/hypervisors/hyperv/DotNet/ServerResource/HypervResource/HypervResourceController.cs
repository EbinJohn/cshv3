using log4net;
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
            // Convert to IP address;f
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
    }

    // Supports HTTP GET and HTTP POST
    // POST takes dynamic to allow it to receive JSON without concern for what is the underlying object.
    // E.g. http://stackoverflow.com/questions/14071715/passing-dynamic-json-object-to-web-api-newtonsoft-example 
    // and http://stackoverflow.com/questions/3142495/deserialize-json-into-c-sharp-dynamic-object
    // Use ActionName attribute to allow multiple POST URLs, one for each supported command
    // E.g. http://stackoverflow.com/a/12703423/939250
    // Strictly speaking, this goes against the purpose of an ApiController, which is to provide one GET/POST/PUT/DELETE, etc.
    // However, it reduces the amount of code by removing the need for a switch according to the incoming command type.
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
        // Useful for testing!
        //
        public string Get()
        {
            return "HypervResource controller, use POST to send send JSON encoded objects";
        }


        // POST api/HypervResource/CheckVirtualMachineCommand
        [HttpPost]
        [ActionName("CheckVirtualMachineCommand")]
        public JContainer CheckVirtualMachineCommand([FromBody]dynamic cmd)
        {
            string details = null;
            JToken answerTok;
            bool result = false;
            string vmName = cmd.vmName;
            string state = null;

            // TODO: Look up the VM, convert Hyper-V state to CloudStack version.
            var sys = WmiCalls.GetComputerSystem(vmName);
            if (sys == null)
            {
                details = "CheckVirtualMachineCommand requested unknown VM " + vmName;
                logger.Error(details);
            }
            else
            {
                state = EnabledState.ToString(sys.EnabledState);
                result = true;
            }

            var answerObj = new
            {
                CheckVirtualMachineAnswer = new
                {
                    result = result,
                    details = details,
                    state = state
                }
            };
            answerTok = JToken.FromObject(answerObj);
            JArray answer = new JArray();
            answer.Add(answerTok);
            logger.Info("CheckVirtualMachineAnswer result is " + answer.ToString());
            return answer;
        }

        // POST api/HypervResource/DeleteStoragePoolCommand
        [HttpPost]
        [ActionName("DeleteStoragePoolCommand")]
        public JContainer DeleteStoragePoolCommand([FromBody]dynamic cmd)
        {
            string details = "Current implementation does not delete storage pools!";
            JToken answerTok;
            var answerObj = new
            {
                Answer = new
                {
                    result = false,
                    details = details
                }
            };
            answerTok = JToken.FromObject(answerObj);
            JArray answer = new JArray();
            answer.Add(answerTok);
            logger.Info("DeleteStoragePoolCommand result is " + answer.ToString());
            return answer;
        }

        // POST api/HypervResource/CreateStoragePoolCommand
        [HttpPost]
        [ActionName("CreateStoragePoolCommand")]
        public JContainer CreateStoragePoolCommand([FromBody]dynamic cmd)
        {
            string details = "success - NOP";
            string localPath;
            JToken answerTok;

            bool result = ValidateStoragePoolCommand(cmd, out localPath);
            if (!result)
            {
                details = "Failed to create storage pool, invalid path or FileSystem type!";
                var answerObj = new
                {
                    Answer = new
                    {
                        result = result,
                        details = details
                    }
                };
                answerTok = JToken.FromObject(answerObj);
            }
            else
            {
                var answerObj = new
                {
                    Answer = new
                    {
                        result = true,
                        details = details
                    }
                };
                answerTok = JToken.FromObject(answerObj);
            }

            JArray answer = new JArray();
            answer.Add(answerTok);
            logger.Info("CreateStoragePoolCommand result is " + answer.ToString());
            return answer;
        }

        // POST api/HypervResource/ModifyStoragePoolCommand
        [HttpPost]
        [ActionName("ModifyStoragePoolCommand")]
        public JContainer ModifyStoragePoolCommand([FromBody]dynamic cmd)
        {
            string details = null;
            string localPath;
            JToken answerTok;

            bool result = ValidateStoragePoolCommand(cmd, out localPath);
            if (!result)
            {
                details = " Failed to create storage pool";
                var answerObj = new
                {
                    Answer = new {
                        result = result,
                        details = details
                    }
                };
                answerTok = JToken.FromObject(answerObj);
            }
            else
            {
                var tInfo = new Dictionary<string, string>();
                long capacityBytes;
                long availableBytes;
                GetCapacityForLocalPath(localPath, out capacityBytes, out availableBytes);

               String uuid = null;
                var poolInfo = new {
		            uuid = uuid,
		            host = cmd.pool.host,
		            localPath = cmd.pool.host,
		            hostPath = cmd.localPath,
		            poolType =cmd.pool.type,
		            capacityBytes = capacityBytes,
                    // TODO:  double check whether you need 'available' or 'used' bytes?
		            availableBytes = availableBytes
                };


                var answerObj = new
                {
                    ModifyStoragePoolAnswer = new
                    {
                        result = result,
                        details = details,
                        templateInfo = tInfo,
                        poolInfo = poolInfo
                    }
                };
                answerTok = JToken.FromObject(answerObj);

            }

            JArray answer = new JArray();
            answer.Add(answerTok);
            logger.Info("ModifyStoragePoolCommand result is " + answer.ToString());
            return answer;
        }

        private bool ValidateStoragePoolCommand(dynamic cmd, out string localPath)
        {
            dynamic pool = cmd.pool;
            string poolTypeStr = pool.type;
            StoragePoolType poolType;
            localPath = cmd.localPath;
            if (!Enum.TryParse<StoragePoolType>(poolTypeStr, out poolType) || poolType != StoragePoolType.Filesystem)
            {
                String msg = "Request to create / modify unsupported pool type: " + poolTypeStr;
                logger.Error(msg);
                return false;
            }
            if (!Directory.Exists(localPath))
            {
                String msg = "Request to create / modify unsupported StoragePoolType.Filesystem with non-existent path:" + localPath;
                logger.Error(msg);
                return false;
            }
            return true;
        }


        // POST api/HypervResource/CleanupNetworkRulesCmd
        [HttpPost]
        [ActionName("CleanupNetworkRulesCmd")]
        public JContainer CleanupNetworkRulesCmd([FromBody]dynamic cmd)
        {
            string details = "nothing to cleanup in our current implementation";

            var answerObj = new
            {
                Answer = new
                {
                    result = false,
                    details = details
                }
            };

            JArray answer = new JArray();
            JToken answerTok = JToken.FromObject(answerObj);
            answer.Add(answerTok);
            logger.Info("CleanupNetworkRulesCmd result is " + answer.ToString());
            return answer;
        }

        // POST api/HypervResource/CheckNetworkCommand
        [HttpPost]
        [ActionName("CheckNetworkCommand")]
        public JContainer CheckNetworkCommand([FromBody]dynamic cmd)
        {
            string details = null;
            bool result = true;

            logger.Debug("CheckNetworkCommand call using data:" + cmd.ToString());

            // TODO: correctly verify network names, for now, return success.

            var answerObj = new
            {
                CheckNetworkAnswer = new
                {
                    result = result,
                    details = details
                }
            };

            JArray answer = new JArray();
            JToken answerTok = JToken.FromObject(answerObj);
            answer.Add(answerTok);
            logger.Info("CheckNetworkAnswer result is " + answer.ToString());
            return answer;
        }

        // POST api/HypervResource/ReadyCommand
        [HttpPost]
        [ActionName("ReadyCommand")]
        public JContainer ReadyCommand([FromBody]dynamic cmd)
        {
            string details = null;

            var answerObj = new
            {
                ReadyAnswer = new
                {
                    result = true,
                    details = details
                }
            };

            JArray answer = new JArray();
            JToken answerTok = JToken.FromObject(answerObj);
            answer.Add(answerTok);
            logger.Info("ReadyCommand result is " + answer.ToString());
            return answer;
        }

        // POST api/HypervResource/StartCommand
        [HttpPost]
        [ActionName("StartCommand")]
        public JContainer StartCommand([FromBody]dynamic cmd)
        {
            string details = null;
            bool result = false;

            try
            {
                WmiCalls.DeployVirtualMachine(cmd);
                result = true;
            }
            catch (WmiException wmiEx)
            {
                details = "StartCommand fail on exception" + wmiEx.Message;
                logger.Error(details, wmiEx);
            }

            var answerObj = new
            {
                StartAnswer = new
                {
                    result = result,
                    details = details,
                    vm = cmd.vm
                }
            };


            JArray answer = new JArray();
            JToken answerTok = JToken.FromObject(answerObj);
            answer.Add(answerTok);
            logger.Info("StartCommand result is " + answer.ToString());
            return answer;
        }

        // POST api/HypervResource/StartCommand
        [HttpPost]
        [ActionName("StopCommand")]
        public JContainer StopCommand([FromBody]dynamic cmd)
        {
            string details = null;
            bool result = false;

            try
            {
                WmiCalls.DestroyVm(cmd);
            }
            catch (WmiException wmiEx)
            {
                details = "StopCommand fail on exception" + wmiEx.Message;
                logger.Error(details, wmiEx);
            }

            var answerObj = new
            {
                StartAnswer = new
                {
                    result = result,
                    details = details,
                    vm = cmd.vm
                }
            };


            JArray answer = new JArray();
            JToken answerTok = JToken.FromObject(answerObj);
            answer.Add(answerTok);
            logger.Info("StopCommand result is " + answer.ToString());
            return answer;
        }


        // POST api/HypervResource/GetVmStatsCommand
        [HttpPost]
        [ActionName("GetVmStatsCommand")]
        public JContainer GetVmStatsCommand([FromBody]dynamic cmd)
        {
            bool result = false;
            string details = null;

            String[] vmNames = cmd.vmNames;
            Dictionary<string, VmStatsEntry> vmProcessorInfo = new Dictionary<string,VmStatsEntry>(vmNames.Length);


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
            System.Management.ManagementPath[] vmPaths =  vmsToInspect.ToArray();
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

            var answerObj = new
            {
                GetVmStatsAnswer = new
                {
                    vmInfos = vmProcessorInfo,
                    result = result,
                    details = details,
                }
            };

            JArray answer = new JArray();
            JToken answerTok = JToken.FromObject(answerObj);
            answer.Add(answerTok);
            logger.Info("GetVmStatsCommand result is " + answer.ToString());
            return answer;
        }

        // POST api/HypervResource/GetStorageStatsCommand
        [HttpPost]
        [ActionName("GetStorageStatsCommand")]
        public JContainer GetStorageStatsCommand([FromBody]dynamic cmd)
        {
            bool result = false;
            string details = null;
            long capacity = 0;
            long used = 0;
            try
            {
                string localPath = (string)cmd.localPath;
                long available;
                GetCapacityForLocalPath(localPath, out capacity, out available);
                used = capacity - available;

            }
            catch (FormatException exFormat)
            {
                details = "GetStorageStatsCommand fail on exception" + exFormat.Message;
                logger.Error(details, exFormat);
            }
            catch (WmiException wmiEx)
            {
                details = "GetStorageStatsCommand fail on exception" + wmiEx.Message;
                logger.Error(details, wmiEx);
            }

            var answerObj = new
            {
                GetStorageStatsAnswer = new
                {
                    result = result,
                    details = details,
                    capacity = capacity,
                    used = used
                }
            };
            JArray answer = new JArray();
            JToken answerTok = JToken.FromObject(answerObj);
            answer.Add(answerTok);
            logger.Info("GetStorageStatsCommand result is " + answer.ToString());
            return answer;
        }

        // POST api/HypervResource/GetHostStatsCommand
        [HttpPost]
        [ActionName("GetHostStatsCommand")]
        public JContainer GetHostStatsCommand([FromBody]dynamic cmd)
        {
            bool result = false;
            string details = null;
            dynamic hostStats = null;

            var entityType = "host";
            ulong totalMemoryKBs;
            ulong freeMemoryKBs;
            double networkReadKBs;
            double networkWriteKBs;
            double cpuUtilization;

            try
            {
                long hostId = cmd.hostId;
                WmiCalls.GetMemoryResources(out totalMemoryKBs, out freeMemoryKBs);
                WmiCalls.GetProcessorUsageInfo(out cpuUtilization);

                // TODO: can we assume that the host has only one adaptor?

                string tmp;
                var privateNic = GetNicInfoFromIpAddress(config.PrivateIpAddress, out tmp);
                var nicStats = privateNic.GetIPStatistics();
                networkReadKBs = nicStats.BytesReceived;
                networkWriteKBs = nicStats.BytesSent;

                // Generate GetHostStatsAnswer
                result = true;

                hostStats = new {
                    hostId = hostId,
                    entityType = entityType,
                    cpuUtilization = cpuUtilization,
                    networkReadKBs = networkReadKBs,
                    networkWriteKBs = networkWriteKBs,
                    totalMemoryKBs = (double)totalMemoryKBs,
                    freeMemoryKBs = (double)freeMemoryKBs
                };

            }
            catch (FormatException exFormat)
            {
                details = "GetHostStatsCommand fail on exception" + exFormat.Message;
                logger.Error(details, exFormat);
            }
            catch (WmiException wmiEx)
            {
                details = "GetHostStatsCommand fail on exception" + wmiEx.Message;
                logger.Error(details, wmiEx);
            }

            var answerObj = new
            {
                GetHostStatsAnswer = new
                {
                    result = result,
                    hostStats = hostStats,
                    details = details
                }
            };
            JArray answer = new JArray();
            JToken answerTok = JToken.FromObject(answerObj);
            answer.Add(answerTok);
            logger.Info("GetHostStatsCommand result is " + answer.ToString());
            return answer;
        }

        // POST api/HypervResource/StartupCommand
        [HttpPost]
        [ActionName("StartupCommand")]
        public JContainer StartupCommand([FromBody]dynamic cmdArray)
        {
            // Log agent configuration
            logger.Info("Agent StartupRoutingCommand received " + cmdArray.ToString());
            dynamic strtRouteCmd = cmdArray[0].StartupRoutingCommand;

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
            strtRouteCmd.memory = memoryKBs/1024;

            // Need 2 Gig for DOM0, see http://technet.microsoft.com/en-us/magazine/hh750394.aspx
            strtRouteCmd.dom0MinMemory = config.ParentPartitionMinMemoryMb;
            

            // Insert storage pool details.
            //
            // Read the localStoragePath for virtual disks from the Hyper-V configuration
            // See http://blogs.msdn.com/b/virtual_pc_guy/archive/2010/05/06/managing-the-default-virtual-machine-location-with-hyper-v.aspx
            // for discussion of Hyper-V file locations paths.
            string localStoragePath =WmiCalls.GetDefaultVirtualDiskFolder();
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
                var startupStorageCommand = new
                    {
                        StartupStorageCommand = new
                            {
                                poolInfo = pi,
                                guid = pi.uuid,
                                dataCenter = strtRouteCmd.dataCenter,
                                resourceType = StorageResourceType.STORAGE_POOL.ToString()
                            }
                    };
                JToken tok = JToken.FromObject(startupStorageCommand);
                cmdArray.Add(tok);
            }

            // Convert result to array for type correctness?
            logger.Info("StartupCommand result is " + cmdArray.ToString());
            return cmdArray;
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

        private static void GetCapacityForLocalPath(string localStoragePath, out long capacityBytes, out long availableBytes)
        {
            // NB: DriveInfo does not work for remote folders (http://stackoverflow.com/q/1799984/939250)
            System.IO.DriveInfo poolInfo = new System.IO.DriveInfo(localStoragePath);
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