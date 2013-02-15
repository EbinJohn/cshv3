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
package com.cloud.hypervisor.hyperv.discoverer;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.Listener;
import com.cloud.agent.api.AgentControlAnswer;
import com.cloud.agent.api.AgentControlCommand;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.ShutdownCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.transport.Request;
import com.cloud.alert.AlertManager;
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.DiscoveredWithErrorException;
import com.cloud.exception.DiscoveryException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.hypervisor.hyperv.resource.HypervDummyResourceBase;
import com.cloud.network.NetworkManager;
import com.cloud.network.PhysicalNetworkSetupInfo;
import com.cloud.resource.Discoverer;
import com.cloud.resource.DiscovererBase;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceStateAdapter;
import com.cloud.resource.ServerResource;
import com.cloud.resource.UnableDeleteHostException;
import com.cloud.resource.ResourceStateAdapter.DeleteHostAnswer;

@Local(value=Discoverer.class)
public class HypervServerDiscoverer extends DiscovererBase implements Discoverer, 
		Listener, ResourceStateAdapter {
    private static final Logger s_logger = Logger.getLogger(HypervServerDiscoverer.class);
    private int _waitTime = 1; /*Change to wait for 5 minutes */

    @Inject HostDao _hostDao = null;
    // TODO:  What is the difference between each table?
    @Inject ClusterDao _clusterDao;
    @Inject ClusterDetailsDao _clusterDetailsDao;
    @Inject ResourceManager _resourceMgr;
    
    // TODO:  Why doesn't KvmServerDiscoverer make use of AlertManager when error occurs?
	@Inject AgentManager _agentMgr;
    @Inject AlertManager _alertMgr;
    
	// Listener interface methods
	
	@Override
	public boolean processAnswers(long agentId, long seq, Answer[] answers) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean processCommands(long agentId, long seq, Command[] commands) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public AgentControlAnswer processControlCommand(long agentId,
			AgentControlCommand cmd) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void processConnect(HostVO host, StartupCommand cmd, boolean forRebalance) {
	}

	@Override
	public boolean processDisconnect(long agentId, Status state) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isRecurring() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getTimeout() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean processTimeout(long agentId, long seq) {
		// TODO Auto-generated method stub
		return false;
	}
	
	// End Listener implementation

    // Discoverer implementation:
    // Overrides find - find used to when agent connects.
    // Overrides postDiscovery - used to configure ServerResource after object found ?
    // Overrides matchHypervisor - indicates of supports hypervisor type in requested (why is this not in DiscoverBase?
    // Overrides getHypervisorType - returns supported hypervisor type.  (why is this not in base class?)
    // Inherits putParam
    // Inhertis reloadResource
	//
	// implements Adapter (see below)
	//
	// Discoverer.find() causes an agent capable of controlling the hypervisor to call home.
	// If a remote agent is required and not running, 'find' launches the agent using remote invocation.
	// Previously, the code below sent a StartupVMMAgentCommand to trigger a registration.
	// However, this was to make use of a custom IAgentShell to operate in the SCVMM, class VmmAgentShell
	// We no longer want to use VmmAgentShell.  It's purpose was to add a ServerResource to the process.
	// Problem is that it uses an Agent that is at present untested.
    @SuppressWarnings("static-access")
    @Override
    public Map<? extends ServerResource, Map<String, String>> find(long dcId, 
			Long podId, Long clusterId, URI uri, String username, 
			String password, List<String> hostTags) throws DiscoveryException {

		// Sanity checks
        if(podId == null ) {
            if(s_logger.isInfoEnabled()) {
                s_logger.info("No pod is assigned, skipping the discovery in Hyperv discoverer");
            }
            return null;
        }
		
        if(s_logger.isInfoEnabled()) {
            s_logger.info("Discover host. dc: " + dcId + ", pod: " + podId + ", cluster: " + clusterId + ", uri host: " + uri.getHost());
        }
		
		// ClusterVO is object from database.
        ClusterVO cluster = _clusterDao.findById(clusterId);
        if(cluster == null || cluster.getHypervisorType() != HypervisorType.Hyperv) {
            if(s_logger.isInfoEnabled()) 
                s_logger.info("invalid cluster id or cluster is not for Hyperv hypervisors");
            return null;
        }

		Map<HypervDummyResourceBase, Map<String, String>> resources = new HashMap<HypervDummyResourceBase, Map<String, String>>();
	    Map<String, String> details = new HashMap<String, String>();
        if (!uri.getScheme().equals("http")) {
            String msg = "urlString is not http so we're not taking care of the discovery for this: " + uri;
            s_logger.debug(msg);
            return null;
        }
        //String clusterName = cluster.getName();
		String agentIp = null;
        try {
        	
    		String hostname = uri.getHost();
    		InetAddress ia = InetAddress.getByName(hostname);
    		agentIp = ia.getHostAddress();
    		String uuidSeed = agentIp;
    		String guid = CalcServerResourceGuid(uuidSeed);
    		String guidWithTail = guid + "-HypervResource";/*tail added by agent.java*/
    		if (_resourceMgr.findHostByGuid(guidWithTail) != null) {
    			s_logger.debug("Skipping " + agentIp + " because " + guidWithTail + " is already in the database.");
    			return null;
    		}
        	
			// Find expects to trigger agent to connect back to mgmt server,
			// KVM uses SSH to do this, first attempt at HyperV used a custom IAgentShell
			
			// Management server expects this step to succeed if the GUI is to be used to
    		// register the HyperV server.
    		
    		s_logger.info("Creating HypervDummyResourceBase for zone/pod/cluster " +dcId + "/"+podId + "/"+ clusterId);

	        Map<String, Object> params = new HashMap<String, Object>();
	        HypervDummyResourceBase resource = new HypervDummyResourceBase(); 
	
	        details.put("url", uri.getHost());
	        details.put("username", username);
	        details.put("password", password);
	
	        params.put("zone", Long.toString(dcId));
	        params.put("pod", Long.toString(podId));
	        params.put("cluster", Long.toString(clusterId));
			params.put("guid", guid); 
			params.put("agentIp", agentIp);
            resource.configure("Hyperv agent", params);
			resources.put(resource, details);
			
			HostVO connectedHost = waitForHostConnect(dcId, podId, clusterId, guidWithTail);
			if (connectedHost == null)
				return null;
			
			details.put("guid", guidWithTail);
			
			 // place a place holder guid derived from cluster ID
			if (cluster.getGuid() == null) {
			    cluster.setGuid(UUID.nameUUIDFromBytes(String.valueOf(clusterId).getBytes()).toString());
			    _clusterDao.update(clusterId, cluster);
			}
			
			//correct zone/dc/cluster ids
			_hostDao.loadDetails(connectedHost);
			long oldClusterId = connectedHost.getClusterId();
			long oldPodId = connectedHost.getPodId();
			long oldDataCenterId = connectedHost.getDataCenterId();
			
    		s_logger.debug("Changing Host " + guidWithTail + " zone/pod/cluster of " 
			    		+ oldDataCenterId + "/"+oldPodId + "/"+ oldClusterId
			    		+ " to " 
			    		+ dcId + "/"+podId + "/"+ clusterId);

			connectedHost.setClusterId(clusterId);
			connectedHost.setDataCenterId(dcId);
			connectedHost.setPodId(podId);
			_hostDao.saveDetails(connectedHost);

            return resources;
        } catch (ConfigurationException e) {
            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + uri.getHost(), "Error is " + e.getMessage());
            s_logger.warn("Unable to instantiate " + uri.getHost(), e);
        } catch (UnknownHostException e) {
            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + uri.getHost(), "Error is " + e.getMessage());
            s_logger.warn("Unable to instantiate " + uri.getHost(), e);
        } catch (Exception e) {
			String msg = " can't setup agent, due to " + e.toString() + " - " + e.getMessage();
			s_logger.warn(msg);
        }
        return null;
    }

	public static String CalcServerResourceGuid(String uuidSeed) {
		String guid = UUID.nameUUIDFromBytes(uuidSeed.getBytes()).toString();
		return guid;
	}

	// Watches database for confirmation that agent has called in.
	private HostVO waitForHostConnect(long dcId, long podId, long clusterId, String guid) {
        for (int i = 0; i < _waitTime *2; i++) {
            List<HostVO> hosts = _resourceMgr.listAllUpAndEnabledHosts(Host.Type.Routing, clusterId, podId, dcId);
            for (HostVO host : hosts) {
                if (host.getGuid().equalsIgnoreCase(guid)) {
                    return host;
                }
            }
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                s_logger.debug("Failed to sleep: " + e.toString());
            }
        }
        s_logger.debug("Timeout, to wait for the host connecting to mgt svr, assuming it is failed");
		List<HostVO> hosts = _resourceMgr.findHostByGuid(dcId, guid);
		if (hosts.size() == 1) {
			return hosts.get(0);
		} else {
        	return null;
    	}
	}
    // Adapter implementation:  (facilitates plug in loading)
    // Required because Discoverer extends Adapter
    // Overrides Adapter.configure to always return true
    // Inherit Adapter.getName
    // Inherit Adapter.stop
    // Inherit Adapter.start
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
    	_resourceMgr.registerResourceStateAdapter(this.getClass().getSimpleName(), this);
        return true;
    }
    // end of Adapter

	@Override
	public void postDiscovery(List<HostVO> hosts, long msId)
			throws DiscoveryException {
		// TODO Auto-generated method stub
	}
	public Hypervisor.HypervisorType getHypervisorType() {
		return Hypervisor.HypervisorType.Hyperv;
	}
	
    @Override
	public boolean matchHypervisor(String hypervisor) {
    	if(hypervisor == null)
    		return true;
    	
        return Hypervisor.HypervisorType.Hyperv.toString().equalsIgnoreCase(hypervisor);
    }

    // end of Discoverer
	

	// ResourceStateAdapter

	// Two kinds of agent:  ConnectedAgent and DirectConnectAgent.  
	// Former is remote, resource-based; latter is based on mgmt server.
	@Override	
    public HostVO createHostVOForConnectedAgent(HostVO host, StartupCommand[] cmd) {
		// Sanity check
		StartupCommand firstCmd = cmd[0];
		if (!(firstCmd instanceof StartupRoutingCommand)) {
			return null;
		}
    
		StartupRoutingCommand ssCmd = ((StartupRoutingCommand) firstCmd);
		if (ssCmd.getHypervisorType() != HypervisorType.Hyperv) {
			return null;
		}
		/* TODO:  Should Hyperv force all hosts to be of the same hypervisor type? */
		_hostDao.loadDetails(host);
		
		return _resourceMgr.fillRoutingHostVO(host, ssCmd, HypervisorType.Hyperv, host.getDetails(), null);
    }
	@Override
    public HostVO createHostVOForDirectConnectAgent(HostVO host, StartupCommand[] startup, ServerResource resource, Map<String, String> details,
			List<String> hostTags) {
	    // TODO Auto-generated method stub
	    return null;
    }
	@Override
    public DeleteHostAnswer deleteHost(HostVO host, boolean isForced, boolean isForceDeleteStorage) throws UnableDeleteHostException {
        if (host.getType() != Host.Type.Routing || host.getHypervisorType() != HypervisorType.Hyperv) {
            return null;
        }
        
        _resourceMgr.deleteRoutingHost(host, isForced, isForceDeleteStorage);
        try {
            ShutdownCommand cmd = new ShutdownCommand(ShutdownCommand.DeleteHost, null);
            _agentMgr.send(host.getId(), cmd);
        } catch (AgentUnavailableException e) {
            s_logger.warn("Sending ShutdownCommand failed: ", e);
        } catch (OperationTimedoutException e) {
            s_logger.warn("Sending ShutdownCommand failed: ", e);
        }
        
        return new DeleteHostAnswer(true);
    }
	// end of ResourceStateAdapter
}


