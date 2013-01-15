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
package com.cloud.hypervisor.hyperv.resource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

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
import com.cloud.agent.api.storage.DestroyAnswer;
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
import com.cloud.hypervisor.hyperv.storage.HypervPhysicalDisk;
import com.cloud.hypervisor.hyperv.storage.HypervStoragePool;
import com.cloud.hypervisor.hyperv.storage.HypervStoragePoolManager;
import com.cloud.network.PhysicalNetworkSetupInfo;
import com.cloud.network.Networks.IsolationType;
import com.cloud.network.Networks.RouterPrivateIpStrategy;
import com.cloud.resource.ServerResource;
import com.cloud.serializer.GsonHelper;
import com.cloud.storage.Storage;
import com.cloud.storage.StorageLayer;
import com.cloud.storage.Volume;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.template.TemplateInfo;
import com.cloud.utils.component.ComponentLocator;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.RuntimeCloudException;
import com.cloud.utils.script.Script;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.utils.StringUtils;

import com.google.gson.Gson;

@Local(value = { ServerResource.class })
public class HypervResource implements ServerResource {
    private static final Logger s_logger = Logger.getLogger(HypervResource.class);
    protected static final Gson s_gson = GsonHelper.getGson();

    String _name;
    Host.Type _type;
    boolean _negative;
    IAgentControl _agentControl;
    private Map<String, Object> _params;
	private HypervStoragePoolManager _storagePoolMgr;
	private StorageLayer _storage;
	private String _dcId;
	private String _clusterId;
	private String _localStoragePath;
	private String _localStorageUUID;
	private String _secondaryStorageLocalPath;

    @Override
    public void disconnected() {
    }

    @Override
    public Answer executeRequest(Command cmd) {
        if (s_logger.isDebugEnabled()) {
            String cmdData = s_gson.toJson(cmd, cmd.getClass());
            s_logger.debug(cmd.getClass().getSimpleName() + " call using data:" + cmdData);
        }        
        Answer result = Answer.createUnsupportedCommandAnswer(cmd);       

    	// Commands I propose to implement:
        if (cmd instanceof CheckNetworkCommand) {
        	result =  execute((CheckNetworkCommand) cmd);
        } else if (cmd instanceof ReadyCommand) { 
        	result =  execute((ReadyCommand) cmd);
        } else if (cmd instanceof CleanupNetworkRulesCmd) {  // TODO:  provide proper implementation
        	result =  execute((CleanupNetworkRulesCmd) cmd);
		} else if (cmd instanceof GetHostStatsCommand) {
			result =  execute((GetHostStatsCommand) cmd);
		} else if (cmd instanceof GetStorageStatsCommand) {
			result =  execute((GetStorageStatsCommand) cmd);
	    } else if (cmd instanceof PrimaryStorageDownloadCommand) {
	    	result =  execute((PrimaryStorageDownloadCommand) cmd);
		} else if (cmd instanceof CreateCommand) { // volume creation
			result =  execute((CreateCommand) cmd);
		} else if (cmd instanceof StopCommand) {
			result =  execute((StopCommand) cmd);
        } else if (cmd instanceof CreateStoragePoolCommand) {
        	result =  execute((CreateStoragePoolCommand) cmd);
        } else if (cmd instanceof ModifyStoragePoolCommand) {
        	result =  execute((ModifyStoragePoolCommand) cmd);
        } else if (cmd instanceof DeleteStoragePoolCommand) {
        	result =  execute((DeleteStoragePoolCommand) cmd);
		} else if (cmd instanceof RebootCommand) {
			result =  Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof GetVmStatsCommand) {
			result =  execute((GetVmStatsCommand) cmd);
		} else if (cmd instanceof AttachVolumeCommand) {
			result =  Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof DestroyCommand) { // volume destruction
			result =  execute((DestroyCommand) cmd);
		} else if (cmd instanceof CheckVirtualMachineCommand) {
			result =  execute((CheckVirtualMachineCommand) cmd);
		} else if (cmd instanceof MaintainCommand) {
			result =  Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof StartCommand) {   // VM creation
			result =  execute((StartCommand) cmd);
		} else if (cmd instanceof PingTestCommand) {
			result =  Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof AttachIsoCommand) {
			result =  Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof CreatePrivateTemplateFromVolumeCommand) {
			result =  Answer.createUnsupportedCommandAnswer(cmd);
		} else if (cmd instanceof CopyVolumeCommand) {
			result =  Answer.createUnsupportedCommandAnswer(cmd);
		} else {
			result =  Answer.createUnsupportedCommandAnswer(cmd);
		}
        
        if (s_logger.isDebugEnabled()) {
            String cmdData = s_gson.toJson(result, result.getClass());
            s_logger.debug(result.getClass().getSimpleName() + " call result is:" + cmdData);
        }
        return result;
    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        return new PingCommand(_type, id);
    }

    @Override
    public Type getType() {
        return _type;
    }   
    
    private Answer execute(CleanupNetworkRulesCmd cmd) {
        return new Answer(cmd, false, "can't bridge firewall, so notthing to clean up");
    }
    
    protected Answer execute(CheckVirtualMachineCommand cmd) {
        if (s_logger.isDebugEnabled()) {
            String cmdData = s_gson.toJson(cmd, cmd.getClass());
            s_logger.debug("CheckVirtualMachineCommand call using data:" + cmdData);
        }
        return new CheckVirtualMachineAnswer(cmd, State.Running, null);
    }
    
    protected GetVmStatsAnswer execute(GetVmStatsCommand cmd) {
    	// TODO:  add infrastructure to propagate failures.
    	GetVmStatsAnswer pythonResult = PythonUtils.callHypervPythonModule(cmd, GetVmStatsAnswer.class);

    	// TODO:  why is there a ctor that takes the original command?
        return new GetVmStatsAnswer(cmd, pythonResult.getVmStatsMap());
    }

    // TODO: create unit test
    protected Answer execute(DestroyCommand cmd) {
    	DestroyAnswer pythonResult = PythonUtils.callHypervPythonModule(cmd, DestroyAnswer.class);
    	
        return new DestroyAnswer(cmd, pythonResult.getResult(), pythonResult.getDetails());
	}
    
    protected Answer execute(StopCommand cmd) {
    	StopAnswer pythonResult = PythonUtils.callHypervPythonModule(cmd, StopAnswer.class);
   	
        return new StopAnswer(cmd, pythonResult.getDetails(), pythonResult.getResult());
    }
    
    // TODO:  identify startup steps that should be triggered by a ReadyCommand
    protected Answer execute(ReadyCommand cmd) {
        return new ReadyAnswer(cmd);
    }

    protected PrimaryStorageDownloadAnswer execute(final PrimaryStorageDownloadCommand cmd) {
        String tmplturl = cmd.getUrl();
        int index = tmplturl.lastIndexOf("/");
        if (index < 0) {
            index = tmplturl.lastIndexOf("\\");
        }
        String mountpoint = tmplturl.substring(0, index);
        String tmpltname = null;
        if (index < tmplturl.length() - 1) {
            tmpltname = tmplturl.substring(index + 1);
        	s_logger.debug("Choose tmpltname " + tmpltname);
        }

        HypervPhysicalDisk tmplVol = null;
        HypervStoragePool secondaryPool = null;
        try {
        	// create transient storage pool.
            secondaryPool = _storagePoolMgr.getStoragePoolByURI(mountpoint);

            /* Get template vol */
            if (tmpltname == null) {

                List<HypervPhysicalDisk> disks = secondaryPool.listPhysicalDisks();
                if (disks == null || disks.isEmpty()) {
                	String errMsg = "Failed to get volumes from pool: "
                            + secondaryPool.getUuid();
                	s_logger.debug(errMsg);
                    return new PrimaryStorageDownloadAnswer(errMsg);
                }
                for (HypervPhysicalDisk disk : disks) {
                    if (disk.getName().toLowerCase().endsWith(secondaryPool.getDefaultFormat().toString().toLowerCase())) {
                        tmplVol = disk;
                        break;
                    }
                }
                if (tmplVol == null) {
                    return new PrimaryStorageDownloadAnswer(
                            "Failed to get template from pool: "
                                    + secondaryPool.getUuid());
                }
            } else {
                tmplVol = secondaryPool.getPhysicalDisk(tmpltname);
            }
           
	        /* Copy volume to primary storage */
	        HypervStoragePool primaryPool = _storagePoolMgr.getStoragePool(cmd
	                .getPoolUuid());
	        
	        if ( primaryPool == null)
	        {
	        	String errMsg = "Could not find primary storage pool " + cmd.getPoolUuid() + 
                        "at " + cmd.getLocalPath();
	        	s_logger.error(errMsg);
                return new PrimaryStorageDownloadAnswer(errMsg);
            }
	
	        HypervPhysicalDisk primaryVol = _storagePoolMgr.copyPhysicalDisk(
	                tmplVol, UUID.randomUUID().toString() + "." + 
	                tmplVol.getFormat().toString(), primaryPool);
            s_logger.debug("Created volume from secondary storage named " + 
            		primaryVol.getName() + " at " + primaryVol.getPath());

            // NB:  location of downloaded template is used as template URL
            // in subsequent CreateCommand calls.
	        return new PrimaryStorageDownloadAnswer(primaryVol.getName(),
	                primaryVol.getSize());
        } catch (RuntimeCloudException e) {
            return new PrimaryStorageDownloadAnswer(e.toString());
        } finally {
            if (secondaryPool != null) {
                secondaryPool.delete();
            }
        }
    }

    /*
     * Create VM.
     */
    protected synchronized StartAnswer execute(StartCommand cmd) {
        StartAnswer pythonResult = PythonUtils.callHypervPythonModule(cmd, StartAnswer.class);
    	
        if (s_logger.isDebugEnabled()) {
            String ansData = s_gson.toJson(pythonResult, pythonResult.getClass());
            s_logger.debug("StartCommand call result was " +  ansData);
        }
    	if (pythonResult.getResult())
    		return new StartAnswer(cmd);
    	else
    		return new StartAnswer(cmd, pythonResult.getDetails());
    }

    /*
     * Create volume based on KVM implementation.
     */
    protected Answer execute(CreateCommand cmd) {
        StorageFilerTO pool = cmd.getPool();
        DiskProfile dskch = cmd.getDiskCharacteristics();
        HypervStoragePool primaryPool = null;
        HypervPhysicalDisk vol = null;
        long disksize;
        try {
            primaryPool = _storagePoolMgr.getStoragePool(pool.getUuid());
            disksize = dskch.getSize();

            // Distinguish between disk based on existing image or 
            // empty one created from scratch.
            if (cmd.getTemplateUrl() != null) {
            	
                String tmplturl = cmd.getTemplateUrl();
                if (tmplturl.lastIndexOf("/") >= 0 ||  tmplturl.lastIndexOf("\\") >= 0) {
                	String errMsg = "Problem with templateURL " + tmplturl + 
                			" the URL should be volume UUID in primary storage created by previous PrimaryStorageDownloadCommand";
                	s_logger.error(errMsg);
                	throw new RuntimeCloudException(errMsg);
                }
            	s_logger.debug("Template's name in primary store should be " + tmplturl);
                // TODO:  Does this always work, or do I need to download template at times?
                HypervPhysicalDisk BaseVol = primaryPool.getPhysicalDisk(tmplturl);
                String newVolumeName = UUID.randomUUID().toString() + "." 
                						+ BaseVol.getFormat().toString();
            	s_logger.debug("New volume will be named " + newVolumeName);
                vol = _storagePoolMgr.createDiskFromTemplate(BaseVol, newVolumeName, primaryPool);

                if (vol == null) {
                    return new Answer(cmd, false,
                            " Can't create storage volume on storage pool");
                }
            } else {
            	// TODO:  if an empty disk is created, where is its format specified?
                vol = primaryPool.createPhysicalDisk(UUID.randomUUID()
                        .toString() + primaryPool.getDefaultFormat().toString(), dskch.getSize());
            }            
            VolumeTO volume = new VolumeTO(cmd.getVolumeId(), dskch.getType(),
                    pool.getType(), pool.getUuid(), vol.getName(),
                    vol.getPath(), vol.getPath(), disksize, null);
            return new CreateAnswer(cmd, volume);
        } catch (RuntimeCloudException e) {
            s_logger.debug("Failed to create volume: " + e.toString());
            return new CreateAnswer(cmd, e);
        }
    }
    
    protected GetStorageStatsAnswer execute(final GetStorageStatsCommand cmd) {
        try {
            HypervStoragePool sp = _storagePoolMgr.getStoragePool(cmd
                    .getStorageId());
            // TODO:  may want to ask storage pool to refresh itself, as cap / used are static.
            return new GetStorageStatsAnswer(cmd, sp.getCapacity(),
                    sp.getUsed());
        } catch (RuntimeCloudException e) {
            return new GetStorageStatsAnswer(cmd, e.toString());
        }
    }
    
    @SuppressWarnings("restriction")
    protected GetHostStatsAnswer execute(GetHostStatsCommand cmd) {
        try {
            HostStatsEntry hostStats = new HostStatsEntry(cmd.getHostId(), 0, 0, 0, "host", 0, 0, 0, 0);
            // TODO:  Use WMI to query necessary usage stats.
            hostStats.setNetworkReadKBs(0);
            hostStats.setNetworkWriteKBs(0);
            
            OperatingSystemMXBean mxb = ManagementFactory.getOperatingSystemMXBean();
            hostStats.setTotalMemoryKBs(Runtime.getRuntime().totalMemory());
            hostStats.setFreeMemoryKBs(Runtime.getRuntime().freeMemory());
            
            // CPU utilisation difficult to access direct from Java
            // See http://stackoverflow.com/questions/25552/using-java-to-get-os-level-system-information
            hostStats.setCpuUtilization(0);
            try {
            	com.sun.management.OperatingSystemMXBean mxb2 = (com.sun.management.OperatingSystemMXBean)mxb;
            	hostStats.setCpuUtilization(mxb2.getSystemCpuLoad());
            }
            catch (Exception e)
            {
                String msg = "CPU utilisation only available with Oracle JVM" + e.toString();
                s_logger.warn(msg, e);
            }
            return new GetHostStatsAnswer(cmd, hostStats);
        } catch (Exception e) {
            String msg = "Unable to get Host stats" + e.toString();
            s_logger.warn(msg, e);
            return new GetHostStatsAnswer(cmd, null);
        }
    }
    
    protected CheckNetworkAnswer execute(CheckNetworkCommand cmd) {
        if (s_logger.isDebugEnabled()) {
            String cmdData = s_gson.toJson(cmd, cmd.getClass());
            s_logger.debug("CheckNetworkCommand call using data:" + cmdData);
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

    // TODO: Implement proper check for the network configuration correctly.
    private boolean checkNetwork(String networkName) {
            return true;
    }

    private Answer ValidateStoragePoolCommand(ModifyStoragePoolCommand cmd) {
        StorageFilerTO pool = cmd.getPool();
        if (pool.getType() != StoragePoolType.Filesystem) {
        	String msg = "Unsupported pool type: " + pool.getType();
        	s_logger.error(msg);
        	return new Answer(cmd, false, msg);
        }
        
        return null;
    }
    
    protected Answer execute(CreateStoragePoolCommand cmd) {
        return new Answer(cmd, true, "success");
    }

    protected Answer execute(ModifyStoragePoolCommand cmd) {
        Answer result = ValidateStoragePoolCommand(cmd);
        if (null != result) {
        	return result;
        }
        
    	// TODO:  drop existing storage pool, create new one. 
        HypervStoragePool storagepool = _storagePoolMgr.createStoragePool(cmd
                .getPool().getUuid(), cmd.getPool().getHost(), cmd.getPool().getPort(),
                cmd.getPool().getPath(), cmd.getPool().getUserInfo(), cmd.getPool().getType());
        if (storagepool == null) {
            return new Answer(cmd, false, " Failed to create storage pool");
        }

        Map<String, TemplateInfo> tInfo = new HashMap<String, TemplateInfo>();
        ModifyStoragePoolAnswer answer = new ModifyStoragePoolAnswer(cmd,
                storagepool.getCapacity(), storagepool.getUsed(), tInfo);

        return answer;
    }

    protected Answer execute(DeleteStoragePoolCommand cmd) {
        try {
            _storagePoolMgr.deleteStoragePool(cmd.getPool().getUuid());
            return new Answer(cmd);
        } catch (CloudRuntimeException e) {
            return new Answer(cmd, false, e.toString());
        }
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
        cmd.setCluster(_clusterId);

        StartupStorageCommand sscmd = null;
        try {
            HypervStoragePool localStoragePool = _storagePoolMgr
                    .createStoragePool(_localStorageUUID, "localhost", -1,
                            _localStoragePath, "", StoragePoolType.Filesystem);
            StoragePoolInfo pi = new com.cloud.agent.api.StoragePoolInfo(
                    localStoragePool.getUuid(), cmd.getPrivateIpAddress(),
                    _localStoragePath, _localStoragePath,
                    StoragePoolType.Filesystem, localStoragePool.getCapacity(),
                    localStoragePool.getUsed());

            sscmd = new StartupStorageCommand();
            sscmd.setPoolInfo(pi);
            sscmd.setGuid(pi.getUuid());
            sscmd.setDataCenter(_dcId);
            sscmd.setResourceType(Storage.StorageResourceType.STORAGE_POOL);
        } catch (RuntimeCloudException e) {
        	String errMsg = "Problem setting up storage pool object model" + e.toString();
            s_logger.debug(errMsg);
            throw e;
        }
        
        return new StartupCommand[] { cmd, sscmd };
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) 
            throws ConfigurationException {
        _name = name;

        String value = (String) params.get("type");
        _type = Host.Type.valueOf(value);

        try {
            Class<?> clazz = Class
                    .forName("com.cloud.storage.JavaStorageLayer");
            _storage = (StorageLayer) ComponentLocator.inject(clazz);
            _storage.configure("StorageLayer", params);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException("Unable to find class "
                    + "com.cloud.storage.JavaStorageLayer");
        }
        
        value = (String) params.get("negative.reply");
        _negative = Boolean.parseBoolean(value);
        setParams(params);
        
        _localStoragePath = (String) getConfiguredProperty("local.storage.path", "E:\\Disks\\Disks");
        _localStorageUUID = (String) params.get("local.storage.uuid");
        if (_localStorageUUID == null) {
        	throw new ConfigurationException("local.storage.uuid is not set! Please set this to a valid UUID");
        }
        
        _clusterId = getConfiguredProperty("cluster", "1");
        
        _dcId = getConfiguredProperty("zone", "1");

        _secondaryStorageLocalPath = (String) getConfiguredProperty("local.secondary.storage.path", "C:\\Secondary");
        File secondaryStorageLocalPathFile = new File(_secondaryStorageLocalPath);
        if ( !secondaryStorageLocalPathFile.exists())
        {
        	String errMsg = "local.secondary.storage.path is invalid, value is " + _secondaryStorageLocalPath;
        	ConfigurationException e = new ConfigurationException(errMsg);
        	s_logger.error(errMsg);
        	throw e;
        }
    	_storagePoolMgr = new HypervStoragePoolManager(_storage, _secondaryStorageLocalPath);
        
        return true;
    }

    public void setParams(Map<String, Object> _params) {
        this._params = _params;
        PythonUtils.s_scriptPathAndName = (String)_params.get("hyperv.python.module.dir");
        PythonUtils.s_scriptPathAndName = PythonUtils.s_scriptPathAndName + 
        		(String)_params.get("hyperv.python.module.script");
        PythonUtils.s_pythonExec = (String)_params.get("python.executable");
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
