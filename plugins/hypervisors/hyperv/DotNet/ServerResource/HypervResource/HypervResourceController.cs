using log4net;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using System;
using System.Collections.Generic;
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
        public long RootDeviceReservedSpace;
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

        // POST api/HypervResource/StartCommand
        [HttpPost]
        [ActionName("StartCommand")]
        public JContainer StartCommand([FromBody]dynamic cmd)
        {
            // TODO: Error handling, and return details to allow CloudStack to create the answer
            WmiCalls.DeployVirtualMachine(cmd);
            return cmd;
        }

        // POST api/HypervResource/StartCommand
        [HttpPost]
        [ActionName("StopCommand")]
        public JContainer StopCommand([FromBody]dynamic cmd)
        {
            // TODO: Error handling, and return details to allow CloudStack to create the answer
            WmiCalls.DestroyVm(cmd);
            return cmd;
        }

        // POST api/HypervResource/GetHostStatsCommand
        [HttpPost]
        [ActionName("GetHostStatsCommand")]
        public JContainer GetHostStatsCommand([FromBody]dynamic cmd)
        {
            bool result = false;
            string details = null;
            dynamic hostStats = null;

            JArray answer = new JArray();
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
            JToken answerTok = JToken.FromObject(answerObj);
            answer.Add(answerTok); 
            return answer;
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

                // NB: DriveInfo does not work for remote folders (http://stackoverflow.com/q/1799984/939250)
                System.IO.DriveInfo poolInfo = new System.IO.DriveInfo(localStoragePath);
                long capacity = poolInfo.TotalSize;
                long available = poolInfo.AvailableFreeSpace;

                // Don't allow all of the Root Device to be used for virtual disks
                if (poolInfo.RootDirectory.Name.ToLower().Equals(config.RootDeviceName))
                {
                    available -= config.RootDeviceReservedSpace;
                    available = available > 0 ? available : 0;
                    capacity -= config.RootDeviceReservedSpace;
                    capacity = capacity > 0 ? capacity : 0;
                }
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
            logger.Info("Sending updated StartupRoutingCommand " + cmdArray.ToString());
            return cmdArray;
        }
    }
}