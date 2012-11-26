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
package com.cloud.agent.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ejb.Local;

import org.apache.log4j.Logger;
import org.libvirt.Connect;

import com.cloud.agent.IAgentControl;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.StartupStorageCommand;
import com.cloud.agent.api.StoragePoolInfo;
import com.cloud.agent.api.StartupRoutingCommand.VmState;
import com.cloud.agent.api.AttachIsoCommand;
import com.cloud.agent.api.AttachVolumeAnswer;
import com.cloud.agent.api.AttachVolumeCommand;
import com.cloud.agent.api.CheckNetworkAnswer;
import com.cloud.agent.api.CheckNetworkCommand;
import com.cloud.agent.api.CheckVirtualMachineAnswer;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.agent.api.CleanupNetworkRulesCmd;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.CreatePrivateTemplateFromVolumeCommand;
import com.cloud.agent.api.CreateStoragePoolCommand;
import com.cloud.agent.api.DeleteStoragePoolCommand;
import com.cloud.agent.api.FenceAnswer;
import com.cloud.agent.api.FenceCommand;
import com.cloud.agent.api.GetHostStatsAnswer;
import com.cloud.agent.api.GetHostStatsCommand;
import com.cloud.agent.api.GetStorageStatsAnswer;
import com.cloud.agent.api.GetStorageStatsCommand;
import com.cloud.agent.api.GetVmStatsAnswer;
import com.cloud.agent.api.GetVmStatsCommand;
import com.cloud.agent.api.GetVncPortAnswer;
import com.cloud.agent.api.GetVncPortCommand;
import com.cloud.agent.api.HostStatsEntry;
import com.cloud.agent.api.MaintainAnswer;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.MigrateAnswer;
import com.cloud.agent.api.MigrateCommand;
import com.cloud.agent.api.ModifyStoragePoolAnswer;
import com.cloud.agent.api.ModifyStoragePoolCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.PingRoutingCommand;
import com.cloud.agent.api.PingTestCommand;
import com.cloud.agent.api.PrepareForMigrationAnswer;
import com.cloud.agent.api.PrepareForMigrationCommand;
import com.cloud.agent.api.PrepareOCFS2NodesCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.RebootAnswer;
import com.cloud.agent.api.RebootCommand;
import com.cloud.agent.api.SecurityGroupRuleAnswer;
import com.cloud.agent.api.SecurityGroupRulesCmd;
import com.cloud.agent.api.StartAnswer;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.StopAnswer;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.VmStatsEntry;
import com.cloud.agent.api.storage.CopyVolumeAnswer;
import com.cloud.agent.api.storage.CopyVolumeCommand;
import com.cloud.agent.api.storage.CreateAnswer;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.api.storage.CreatePrivateTemplateAnswer;
import com.cloud.agent.api.storage.DestroyCommand;
import com.cloud.agent.api.storage.PrimaryStorageDownloadAnswer;
import com.cloud.agent.api.storage.PrimaryStorageDownloadCommand;
import com.cloud.agent.api.to.NicTO;
import com.cloud.agent.api.to.StorageFilerTO;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.agent.api.to.VolumeTO;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.hypervisor.kvm.resource.LibvirtConnection;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef;
import com.cloud.hypervisor.kvm.storage.KVMPhysicalDisk;
import com.cloud.hypervisor.kvm.storage.KVMStoragePool;
import com.cloud.network.PhysicalNetworkSetupInfo;
import com.cloud.network.Networks.IsolationType;
import com.cloud.network.Networks.RouterPrivateIpStrategy;
import com.cloud.resource.ServerResource;
import com.cloud.storage.Storage;
import com.cloud.storage.Volume;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;

@Local(value = { ServerResource.class })
public class HypervResource implements ServerResource {
    private static final Logger s_logger = Logger.getLogger(HypervResource.class);

    String _name;
    Host.Type _type;
    boolean _negative;
    IAgentControl _agentControl;
    private Map<String, Object> _params;

    @Override
    public void disconnected() {
    }

    @Override
    public Answer executeRequest(Command cmd) {
        System.out.println("Received Command: " + cmd.toString());

    	// Commands I propose to implement:
        if (cmd instanceof CheckNetworkCommand) {
        	return execute((CheckNetworkCommand) cmd);
        } else if (cmd instanceof ReadyCommand) { 
			return execute((ReadyCommand) cmd);
        } else if (cmd instanceof CleanupNetworkRulesCmd) {  // TODO:  provide proper implementation
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof GetHostStatsCommand) {
			return execute((GetHostStatsCommand) cmd);
		} else if (cmd instanceof GetStorageStatsCommand) {
			return execute((GetStorageStatsCommand) cmd);
	    } else if (cmd instanceof PrimaryStorageDownloadCommand) {
			return execute((PrimaryStorageDownloadCommand) cmd);
		} else if (cmd instanceof CreateCommand) { // volume creation
			return execute((CreateCommand) cmd);
		} else if (cmd instanceof StopCommand) {
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof RebootCommand) {
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof GetVmStatsCommand) {
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof AttachVolumeCommand) {
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof DestroyCommand) { // volume destruction
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof CheckVirtualMachineCommand) {
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof MaintainCommand) {
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof StartCommand) {   // VM creation
			return execute((StartCommand) cmd);
		} else if (cmd instanceof PingTestCommand) {
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof AttachIsoCommand) {
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof CreatePrivateTemplateFromVolumeCommand) {
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof CopyVolumeCommand) {
			return Answer.createUnsupportedCommandAnswer(cmd);
		} else {
			return Answer.createUnsupportedCommandAnswer(cmd);
		}
    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        return new PingCommand(_type, id);
    }

    @Override
    public Type getType() {
        return _type;
    }
   
    // TODO:  identify startup steps that should be triggered by a ReadyCommand
    protected Answer execute(ReadyCommand cmd) {
        return new ReadyAnswer(cmd);
    }

    /*
     * Sample:
     * contextMap	LinkedHashMap<K,V>  (id=111)	
     * format	Storage$ImageFormat  (id=114)	
     * localPath	"E:\\Disks\\Disks" (id=119)	
     * name	"routing-9" (id=124)	
     * poolId	201	
     * poolUuid	"5fe2bad3-d785-394e-9949-89786b8a63d2" (id=125)	
     * primaryStorageUrl	"nfs://10.70.176.29E:\\Disks\\Disks" (id=126)	
     * secondaryStorageUrl	"nfs://10.70.176.4/CSHV3" (id=127)	
     * secUrl	null	
     * url	"nfs://10.70.176.4/CSHV3/template/tmpl/1/9/" (id=128)	
     * wait	10800	
     * 
     */
    protected PrimaryStorageDownloadAnswer execute(final PrimaryStorageDownloadCommand cmd) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Processing PrimaryStorageDownloadCommand request");
        }
        
        // Get template volume
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Template to download is in " + cmd.getUrl() );
        }
        
        // Decide destination
        String installPath = cmd.getLocalPath();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Template's destination is " + cmd.getPrimaryStorageUrl());
        }
       
        // Copy
        installPath = (String) getConfiguredProperty("prototype.vm.template.pathfilename", "e:\\Disks\\Disks\\SampleHyperVCentOS63VM.vhdx");
        String templateSizeStr = (String) getConfiguredProperty("prototype.vm.template.size", "2285895680");
        long templateSize = Long.parseLong(templateSizeStr);

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Prototype uses config settings for filename of " + installPath + " and size of " + templateSize);
        }
       
        return new PrimaryStorageDownloadAnswer(installPath, templateSize);
    }

    /*
     * Create VM.
     * 
     * KVM manages local list of VMs.
     */
    protected synchronized StartAnswer execute(StartCommand cmd) {
        VirtualMachineTO vmSpec = cmd.getVirtualMachine();
        
        // Setup volumes
        
        // Setup NICs
        
        return new StartAnswer(cmd);
    }
    
    /*
     * Creates a volume
     */
    protected Answer execute(CreateCommand cmd) {
    	// Create root volume from passed template or from scratch
    	// 
        StorageFilerTO pool = cmd.getPool();
        DiskProfile dskch = cmd.getDiskCharacteristics();
        long disksize = dskch.getSize();

        if (cmd.getTemplateUrl() != null) {
            // Create volume from template
            } else {
                // Create volume from scratch
        }
        VolumeTO volume = new VolumeTO(cmd.getVolumeId(), dskch.getType(),
                    pool.getType(), pool.getUuid(), pool.getPath(),
                    "FakeVolume", "FakeVolume", disksize, null);
            return new CreateAnswer(cmd, volume);
    }



    protected GetStorageStatsAnswer execute(final GetStorageStatsCommand cmd) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Processing GetHostStatsCommand request");
        }

        String capacityStr = (String) getConfiguredProperty(
                "local.storage.capacity", "10000000000");
        String availableStr = (String) getConfiguredProperty(
                "local.storage.avail", "5000000000");

        long capacity = Long.parseLong(capacityStr);
        long used = Long.parseLong(availableStr);
        return new GetStorageStatsAnswer(cmd, capacity, used);
    }
    
    /**
     * This is the method called for getting the HOST stats
     * 
     * @param cmd
     * @return
     */
    protected GetHostStatsAnswer execute(GetHostStatsCommand cmd) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Processing GetHostStatsCommand request");
        }
        try {
            HostStatsEntry hostStats = new HostStatsEntry(cmd.getHostId(), 0, 0, 0, "host", 0, 0, 0, 0);
            // TODO:  Use WMI to query necessary usage stats.
            hostStats.setNetworkReadKBs(0);
            hostStats.setNetworkWriteKBs(0);
            hostStats.setTotalMemoryKBs(0);
            hostStats.setFreeMemoryKBs(0);
            hostStats.setCpuUtilization(0);
            return new GetHostStatsAnswer(cmd, hostStats);
        } catch (Exception e) {
            String msg = "Unable to get Host stats" + e.toString();
            s_logger.warn(msg, e);
            return new GetHostStatsAnswer(cmd, null);
        }
    }
    
    protected CheckNetworkAnswer execute(CheckNetworkCommand cmd) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Checking if network name setup is done on the resource");
        }
        List<PhysicalNetworkSetupInfo> phyNics = cmd.getPhysicalNetworkInfoList();
        String errMsg = null;
        for (PhysicalNetworkSetupInfo nic : phyNics) {
            if (!checkNetwork(nic.getGuestNetworkName())) {
                errMsg = "Cannot find Guest network: " + nic.getGuestNetworkName();
                break;
            } else if (!checkNetwork(nic.getPrivateNetworkName())) {
                errMsg = "Cannot find Private network: " + nic.getPrivateNetworkName();
                break;
            } else if (!checkNetwork(nic.getPublicNetworkName())) {
                errMsg = "Cannot find Public network: " + nic.getPublicNetworkName();
                break;
            }
        }

        if (errMsg != null) {
            return new CheckNetworkAnswer(cmd, false, errMsg);
        } else {
            return new CheckNetworkAnswer(cmd, true, null);
        }
    }

    protected String getConfiguredProperty(String key, String defaultValue) {
        String val = (String) _params.get(key);
        return val == null ? defaultValue : val;
    }

    protected Long getConfiguredProperty(String key, Long defaultValue) {
        String val = (String) _params.get(key);

        if (val != null) {
            Long result = Long.parseLong(val);
            return result;
        }
        return defaultValue;
    }

    protected List<Object> getHostInfo() {
        final ArrayList<Object> info = new ArrayList<Object>();
        long speed = getConfiguredProperty("cpuspeed", 4000L);
        long cpus = getConfiguredProperty("cpus", 4L);
        long ram = getConfiguredProperty("memory", 16000L * 1024L * 1024L);
        long dom0ram = Math.min(ram / 10, 768 * 1024 * 1024L);

        String cap = getConfiguredProperty("capabilities", "hvm");
        info.add((int) cpus);
        info.add(speed);
        info.add(ram);
        info.add(cap);
        info.add(dom0ram);
        return info;
    }

    protected void fillNetworkInformation(final StartupCommand cmd) {

        cmd.setPrivateIpAddress((String) getConfiguredProperty(
                "private.ip.address", "127.0.0.1"));
        cmd.setPrivateMacAddress((String) getConfiguredProperty(
                "private.mac.address", "8A:D2:54:3F:7C:C3"));
        cmd.setPrivateNetmask((String) getConfiguredProperty(
                "private.ip.netmask", "255.255.255.0"));
        cmd.setStorageIpAddress((String) getConfiguredProperty(
                "private.ip.address", "127.0.0.1"));
        cmd.setStorageMacAddress((String) getConfiguredProperty(
                "private.mac.address", "8A:D2:54:3F:7C:C3"));
        cmd.setStorageNetmask((String) getConfiguredProperty(
                "private.ip.netmask", "255.255.255.0"));
        cmd.setGatewayIpAddress((String) getConfiguredProperty(
                "gateway.ip.address", "127.0.0.1"));

    }

    private Map<String, String> getVersionStrings() {
        Map<String, String> result = new HashMap<String, String>();
        String hostOs = (String) _params.get("Host.OS");
        String hostOsVer = (String) _params.get("Host.OS.Version");
        String hostOsKernVer = (String) _params.get("Host.OS.Kernel.Version");
        result.put("Host.OS", hostOs == null ? "Fedora" : hostOs);
        result.put("Host.OS.Version", hostOsVer == null ? "14" : hostOsVer);
        result.put("Host.OS.Kernel.Version",
                hostOsKernVer == null ? "2.6.35.6-45.fc14.x86_64"
                        : hostOsKernVer);
        return result;
    }

    protected StoragePoolInfo initializeLocalStorage() {
        String hostIp = (String) getConfiguredProperty("private.ip.address",
                "127.0.0.1");
        String localStoragePath = (String) getConfiguredProperty(
                "local.storage.path", "E:\\Disks\\Disks");
        String lh = hostIp + localStoragePath;
        String uuid = UUID.nameUUIDFromBytes(lh.getBytes()).toString();

        String capacity = (String) getConfiguredProperty(
                "local.storage.capacity", "1000000000");
        String available = (String) getConfiguredProperty(
                "local.storage.avail", "10000000");

        return new StoragePoolInfo(uuid, hostIp, localStoragePath,
                localStoragePath, StoragePoolType.Filesystem,
                Long.parseLong(capacity), Long.parseLong(available));
    }

    // TODO: Implement proper check for the network configuration correctly.
    private boolean checkNetwork(String networkName) {
            return true;
    }

    // Draws on values in conf/agent.properties
    @Override
    public StartupCommand[] initialize() {
        Map<String, VmState> changes = null;

        final List<Object> info = getHostInfo();

        final StartupRoutingCommand cmd = new StartupRoutingCommand(
                (Integer) info.get(0), (Long) info.get(1), (Long) info.get(2),
                (Long) info.get(4), (String) info.get(3), HypervisorType.Hyperv,
                RouterPrivateIpStrategy.HostLocal, changes);
        fillNetworkInformation(cmd);
        cmd.getHostDetails().putAll(getVersionStrings());
        cmd.setCluster(getConfiguredProperty("cluster", "1"));

        StoragePoolInfo pi = initializeLocalStorage();
        StartupStorageCommand sscmd = new StartupStorageCommand();
        sscmd.setPoolInfo(pi);
        sscmd.setGuid(pi.getUuid());
        sscmd.setDataCenter((String) _params.get("zone"));
        sscmd.setResourceType(Storage.StorageResourceType.STORAGE_POOL);

        return new StartupCommand[] { cmd, sscmd };
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) {
        _name = name;

        String value = (String) params.get("type");
        _type = Host.Type.valueOf(value);

        value = (String) params.get("negative.reply");
        _negative = Boolean.parseBoolean(value);
        setParams(params);
        return true;
    }

    public void setParams(Map<String, Object> _params) {
        this._params = _params;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public IAgentControl getAgentControl() {
        return _agentControl;
    }

    @Override
    public void setAgentControl(IAgentControl agentControl) {
        _agentControl = agentControl;
    }
}
