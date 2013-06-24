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
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.SetupAnswer;
import com.cloud.agent.api.SetupCommand;
import com.cloud.agent.api.ShutdownCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.transport.Request;
import com.cloud.alert.AlertManager;
import com.cloud.configuration.Config;
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ConnectionException;
import com.cloud.exception.DiscoveredWithErrorException;
import com.cloud.exception.DiscoveryException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.Host;
import com.cloud.host.HostEnvironment;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.hypervisor.hyperv.resource.HypervDirectConnectResource;
import com.cloud.network.NetworkManager;
import com.cloud.network.PhysicalNetworkSetupInfo;
import com.cloud.resource.Discoverer;
import com.cloud.resource.DiscovererBase;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceStateAdapter;
import com.cloud.resource.ServerResource;
import com.cloud.resource.UnableDeleteHostException;
import com.cloud.resource.ResourceStateAdapter.DeleteHostAnswer;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.HypervisorVersionChangedException;

@Local(value=Discoverer.class)
public class HypervServerDiscoverer extends DiscovererBase implements Discoverer, 
		Listener, ResourceStateAdapter {
    private static final Logger s_logger = Logger.getLogger(HypervServerDiscoverer.class);
    private int _waitTime = 1; /*Change to wait for 5 minutes */

    @Inject HostDao _hostDao = null;
    @Inject ClusterDao _clusterDao;
    @Inject ClusterDetailsDao _clusterDetailsDao;
    @Inject ResourceManager _resourceMgr;
    @Inject HostPodDao _podDao;
    @Inject DataCenterDao _dcDao;
   
    
	@Inject AgentManager _agentMgr;	// TODO:  should we be using this mgr?
    @Inject AlertManager _alertMgr;	// TODO:  should we be using this mgr?
    
	// Listener interface methods
	
	@Override
	public boolean processAnswers(long agentId, long seq, Answer[] answers) {
		return false;
	}

	@Override
	public boolean processCommands(long agentId, long seq, Command[] commands) {
		return false;
	}

	@Override
	public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
		return null;
	}

	@Override
	public void processConnect(Host agent, StartupCommand cmd, boolean forRebalance) throws ConnectionException  {
		// Limit the commands we can process
		if (!(cmd instanceof StartupRoutingCommand )) {
            return;
        }       
		
        StartupRoutingCommand startup = (StartupRoutingCommand)cmd;

        // assert
        if (startup.getHypervisorType() != HypervisorType.Hyperv) {
            s_logger.debug("Not Hyper-V hypervisor, so moving on.");
            return;
        }
        
        long agentId = agent.getId();
        HostVO host = _hostDao.findById(agentId);

        // Our Hyper-V machines are not participating in pools, and the pool id we provide them  is not persisted.
        // This means the pool id can vary.
        ClusterVO cluster = _clusterDao.findById(host.getClusterId());
        if ( cluster.getGuid() == null) {
            cluster.setGuid(startup.getPool());
            _clusterDao.update(cluster.getId(), cluster);
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Setting up host " + agentId);
        }
        
        HostEnvironment env = new HostEnvironment();
        SetupCommand setup = new SetupCommand(env);
        if (!host.isSetup()) {
            setup.setNeedSetup(true);
        }
        
        try {
            SetupAnswer answer = (SetupAnswer)_agentMgr.send(agentId, setup);
            if (answer != null && answer.getResult()) {
                host.setSetup(true);
                host.setLastPinged((System.currentTimeMillis()>>10) - 5 * 60 );
                _hostDao.update(host.getId(), host);
                if ( answer.needReconnect() ) {
                    throw new ConnectionException(false, "Reinitialize agent after setup.");
                }
                return;
            } else {
                s_logger.warn("Unable to setup agent " + agentId + " due to " + ((answer != null)?answer.getDetails():"return null"));
            }
        // Error handling borrowed from XcpServerDiscoverer, may need to be updated.
        } catch (AgentUnavailableException e) {
            s_logger.warn("Unable to setup agent " + agentId + " because it became unavailable.", e);
        } catch (OperationTimedoutException e) {
            s_logger.warn("Unable to setup agent " + agentId + " because it timed out", e);
        }
        throw new ConnectionException(true, "Reinitialize agent after setup.");
    }

	@Override
	public boolean processDisconnect(long agentId, Status state) {
		return false;
	}

	@Override
	public boolean isRecurring() {
		return false;
	}

	@Override
	public int getTimeout() {
		return 0;
	}

	@Override
	public boolean processTimeout(long agentId, long seq) {
		return false;
	}
	
	// End Listener implementation

	// Returns server component used by server manager to operate the plugin. 
	// Server component is a ServerResource.  If a connected agent is used, the ServerResource is 
	// ignored in favour of another created in response to 
    @SuppressWarnings("static-access")
    @Override
    public Map<? extends ServerResource, Map<String, String>> find(long dcId, 
			Long podId, Long clusterId, URI uri, String username, 
			String password, List<String> hostTags) throws DiscoveryException {

        if(s_logger.isInfoEnabled()) {
            s_logger.info("Discover host. dc(zone): " + dcId + ", pod: " + podId + ", cluster: " + clusterId + ", uri host: " + uri.getHost());
        }

		// Assertions
        if(podId == null ) {
            if(s_logger.isInfoEnabled()) {
                s_logger.info("No pod is assigned, skipping the discovery in Hyperv discoverer");
            }
            return null;
        }
        ClusterVO cluster = _clusterDao.findById(clusterId); // ClusterVO exists in the database
        if(cluster == null) {
            if(s_logger.isInfoEnabled()) 
                s_logger.info("No cluster in database for cluster id " + clusterId);
            return null;
        }
        if(cluster.getHypervisorType() != HypervisorType.Hyperv) {
            if(s_logger.isInfoEnabled()) 
                s_logger.info("Cluster " + clusterId + "is not for Hyperv hypervisors");
            return null;
        }
        if (!uri.getScheme().equals("http")) {
            String msg = "urlString is not http so we're not taking care of the discovery for this: " + uri;
            s_logger.debug(msg);
            return null;
        }
        
        try {
    		String hostname = uri.getHost();
    		InetAddress ia = InetAddress.getByName(hostname);
    		String agentIp = ia.getHostAddress();
    		String uuidSeed = agentIp;
    		String guidWithTail = CalcServerResourceGuid(uuidSeed)+ "-HypervResource";;
    		
    		if (_resourceMgr.findHostByGuid(guidWithTail) != null) {
    			s_logger.debug("Skipping " + agentIp + " because " + guidWithTail + " is already in the database.");
    			return null;
    		}
        	
    		s_logger.info("Creating" +  HypervDirectConnectResource.class.getName() + " HypervDummyResourceBase for zone/pod/cluster " +dcId + "/"+podId + "/"+ clusterId);

    		
			// Some Hypervisors organise themselves in pools.
    		// The startup command tells us what pool they are using.
    		// In the meantime, we have to place a GUID corresponding to the pool in the database
    		// This GUID may change.
			if (cluster.getGuid() == null) {
			    cluster.setGuid(UUID.nameUUIDFromBytes(String.valueOf(clusterId).getBytes()).toString());
			    _clusterDao.update(clusterId, cluster);
			}
		

		    Map<String, String> details = new HashMap<String, String>();
	        details.put("url", uri.getHost());
	        details.put("username", username);
	        details.put("password", password);
			details.put("cluster.guid", cluster.getGuid());

	        // TODO: what parameters are required to satisfy the resource.configure call?
	        Map<String, Object> params = new HashMap<String, Object>();
	        params.put("zone", Long.toString(dcId));
	        params.put("pod", Long.toString(podId));
	        params.put("cluster", Long.toString(clusterId));
			params.put("guid", guidWithTail);
			params.put("ipaddress", agentIp);
			params.putAll(details);
						
			HypervDirectConnectResource resource = new HypervDirectConnectResource(); 
            resource.configure(agentIp, params);
            
            // Assert 
            // TODO:  test by using bogus URL and bogus virtual path in URL
            ReadyCommand ping = new ReadyCommand();
            Answer pingAns = resource.executeRequest(ping);
            if (pingAns == null || pingAns.getResult()==false)
            {
                String errMsg = "Agent not running, or no route to agent on at " + uri;
                s_logger.debug(errMsg);
                throw new DiscoveryException(errMsg);
            }
            
			Map<HypervDirectConnectResource, Map<String, String>> resources = new HashMap<HypervDirectConnectResource, Map<String, String>>();
			resources.put(resource, details);
			
			// TODO: does the resource have to create a connection?
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

    // Adapter implementation:  (facilitates plug in loading)
    // Required because Discoverer extends Adapter
    // Overrides Adapter.configure to always return true
    // Inherit Adapter.getName
    // Inherit Adapter.stop
    // Inherit Adapter.start
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
    	
    	// TODO: we may want to configure the timeout on HTTPRequests and pass the value through this configure call
    	// TODO: we may want to add an ISO containing the Hyper-V Integration services
        
        _agentMgr.registerForHostEvents(this, true, false, true);
        _resourceMgr.registerResourceStateAdapter(this.getClass().getSimpleName(), this);
        return true;
    }
    // end of Adapter

	@Override
	public void postDiscovery(List<HostVO> hosts, long msId) throws DiscoveryException {
	}
	public Hypervisor.HypervisorType getHypervisorType() {
		return Hypervisor.HypervisorType.Hyperv;
	}
	
	// TODO:  verify that it is okay to return true on null hypervisor
    @Override
	public boolean matchHypervisor(String hypervisor) {
    	if(hypervisor == null)
    		return true;
    	
        return Hypervisor.HypervisorType.Hyperv.toString().equalsIgnoreCase(hypervisor);
    }
    // end of Discoverer
	

	// ResourceStateAdapter
	@Override	
    public HostVO createHostVOForConnectedAgent(HostVO host, StartupCommand[] cmd) {
	    return null;
    }
	
	// TODO: test
	@Override
    public HostVO createHostVOForDirectConnectAgent(HostVO host, StartupCommand[] startup, ServerResource resource, Map<String, String> details,
			List<String> hostTags) {
		StartupCommand firstCmd = startup[0];
		if (!(firstCmd instanceof StartupRoutingCommand)) {
			return null;
		}

		StartupRoutingCommand ssCmd = ((StartupRoutingCommand) firstCmd);
		if (ssCmd.getHypervisorType() != HypervisorType.Hyperv) {
			return null;
		}

		s_logger.info("Host: " + host.getName() + " connected with hypervisor type: " + HypervisorType.Hyperv + ". Checking CIDR...");

		HostPodVO pod = _podDao.findById(host.getPodId());
		DataCenterVO dc = _dcDao.findById(host.getDataCenterId());
		// TODO: what does this do?
		_resourceMgr.checkCIDR(pod, dc, ssCmd.getPrivateIpAddress(), ssCmd.getPrivateNetmask());
		
		return _resourceMgr.fillRoutingHostVO(host, ssCmd, HypervisorType.Hyperv, details, hostTags);
    }
	
	// TODO: test
	@Override
    public DeleteHostAnswer deleteHost(HostVO host, boolean isForced, boolean isForceDeleteStorage) throws UnableDeleteHostException {
		// assert
        if (host.getType() != Host.Type.Routing || host.getHypervisorType() != HypervisorType.Hyperv) {
            return null;
        }
        _resourceMgr.deleteRoutingHost(host, isForced, isForceDeleteStorage);
        return new DeleteHostAnswer(true);
    }
	
    @Override
    public boolean stop() {
    	_resourceMgr.unregisterResourceStateAdapter(this.getClass().getSimpleName());
        return super.stop();
    }
	// end of ResourceStateAdapter

}


