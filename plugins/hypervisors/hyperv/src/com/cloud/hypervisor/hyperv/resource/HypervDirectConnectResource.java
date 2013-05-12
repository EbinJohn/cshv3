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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.junit.Assert;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.CreateStoragePoolCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.StartupAnswer;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.StartupStorageCommand;
import com.cloud.agent.api.StoragePoolInfo;
import com.cloud.agent.api.UnsupportedAnswer;
import com.cloud.agent.api.StartupRoutingCommand.VmState;
import com.cloud.agent.api.storage.PrimaryStorageDownloadCommand;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.network.Networks.RouterPrivateIpStrategy;
import com.cloud.resource.ServerResource;
import com.cloud.resource.ServerResourceBase;
import com.cloud.serializer.GsonHelper;
import com.cloud.storage.JavaStorageLayer;
import com.cloud.storage.Storage;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.gson.Gson;

/**
 * Implementation of dummy resource to be returned from discoverer
 **/

public class HypervDirectConnectResource extends ServerResourceBase implements
		ServerResource {
	private static final Logger s_logger = Logger
			.getLogger(HypervDirectConnectResource.class.getName());

	// TODO: make this a config parameter
	protected static final Gson s_gson = GsonHelper.getGson();
	private String _zoneId;
	private String _podId;
	private String _clusterId;
	private String _guid;
	private String _agentIp;

	@Override
	public Type getType() {
		// TODO Auto-generated method stub
		return null;
	}

	//@Override
	public StartupCommand[] initialize() {
		// Create default StartupRoutingCommand, then customise
		StartupRoutingCommand defaultStartRoutCmd = new StartupRoutingCommand(
				0, 0, 0, 0, null, Hypervisor.HypervisorType.Hyperv,
				RouterPrivateIpStrategy.HostLocal,
				new HashMap<String, VmState>());

		// Identity within the data centre is decided by CloudStack kernel,
		// and passed via ServerResource.configure()
		defaultStartRoutCmd.setDataCenter(_zoneId);
		defaultStartRoutCmd.setPod(_podId);
		defaultStartRoutCmd.setCluster(_clusterId);
		defaultStartRoutCmd.setGuid(_guid);
		defaultStartRoutCmd.setName(_name);
		defaultStartRoutCmd.setPrivateIpAddress(_agentIp);
		defaultStartRoutCmd.setStorageIpAddress(_agentIp);

		// TODO: does version need to be hard coded.
		defaultStartRoutCmd.setVersion("4.1.0");

		// Specifics of the host's resource capacity and network configuration
		// comes from the host itself. CloudStack sanity checks network
		// configuration
		// and uses capacity info for resource allocation.
		Command[] startCmds = requestStartupCommand(new Command[] { defaultStartRoutCmd }); 

		// TODO: error handling when startCmds fails?
		StartupRoutingCommand startCmd = (StartupRoutingCommand)startCmds[0];

		// Assert that host identity is consistent with existing values.
		if (startCmd == null) {
			String errMsg = String.format(
					"Host %s (IP %s) did not return a StartupRoutingCommand",
					this._name, this._agentIp);
			s_logger.error(errMsg);
			// TODO: valid to return null, or should we throw?
			return null;
		}
		if (!startCmd.getDataCenter().equals(
				defaultStartRoutCmd.getDataCenter())) {
			String errMsg = String.format(
					"Host %s (IP %s) changed zone/data center.  Was "
							+ defaultStartRoutCmd.getDataCenter() + " NOW its "
							+ startCmd.getDataCenter(), this._name,
					this._agentIp);
			s_logger.error(errMsg);
			// TODO: valid to return null, or should we throw?
			return null;
		}
		if (!startCmd.getPod().equals(defaultStartRoutCmd.getPod())) {
			String errMsg = String.format(
					"Host %s (IP %s) changed pod.  Was "
							+ defaultStartRoutCmd.getPod() + " NOW its "
							+ startCmd.getPod(), this._name,
					this._agentIp);
			s_logger.error(errMsg);
			// TODO: valid to return null, or should we throw?
			return null;
		}
		if (!startCmd.getCluster().equals(defaultStartRoutCmd.getCluster())) {
			String errMsg = String.format(
					"Host %s (IP %s) changed cluster.  Was "
							+ defaultStartRoutCmd.getCluster() + " NOW its "
							+ startCmd.getCluster(), this._name,
					this._agentIp);
			s_logger.error(errMsg);
			// TODO: valid to return null, or should we throw?
			return null;
		}
		if (!startCmd.getGuid().equals(defaultStartRoutCmd.getGuid())) {
			String errMsg = String.format(
					"Host %s (IP %s) changed guid.  Was "
							+ defaultStartRoutCmd.getGuid() + " NOW its "
							+ startCmd.getGuid(), this._name,
					this._agentIp);
			s_logger.error(errMsg);
			// TODO: valid to return null, or should we throw?
			return null;
		}
		if (!startCmd.getPrivateIpAddress().equals(
				defaultStartRoutCmd.getPrivateIpAddress())) {
			String errMsg = String.format(
					"Host %s (IP %s) IP address.  Was "
							+ defaultStartRoutCmd.getPrivateIpAddress() + " NOW its "
							+ startCmd.getPrivateIpAddress(), this._name,
					this._agentIp);
			s_logger.error(errMsg);
			// TODO: valid to return null, or should we throw?
			return null;
		}
		if (!startCmd.getName().equals(defaultStartRoutCmd.getName())) {
			String errMsg = String.format(
					"Host %s (IP %s) name.  Was "
							+ startCmd.getName() + " NOW its "
							+ defaultStartRoutCmd.getName(), this._name,
					this._agentIp);
			s_logger.error(errMsg);
			// TODO: valid to return null, or should we throw?
			return null;
		}

		// Host will also supply details of an existing StoragePool if it has
		// been configured with one.
		//
		// NB:  if the host was configured
		// with a local storage pool, CloudStack may not be able to use it unless
		// it is has service offerings configured to recognise this storage type.
		StartupStorageCommand storePoolCmd = null;
		if (startCmds.length > 1)
		{
			storePoolCmd = (StartupStorageCommand)startCmds[1];
			// TODO: is this assertion required?
			if (storePoolCmd == null) {
				String errMsg = String
						.format("Host %s (IP %s) sent incorrect Command, second parameter should be a StartupStorageCommand",
								this._name, this._agentIp);
				s_logger.error(errMsg);
				// TODO: valid to return null, or should we throw?
				return null;
			}
			s_logger.info("Host " + this._name + " (IP "+ this._agentIp + 
					") already configured with a storeage pool, details " + 
					s_gson.toJson(startCmds[1]));
		}
		else
		{
			s_logger.info("Host " + this._name + " (IP "+ this._agentIp + 
					") already configured with a storeage pool, details ");
		}
		return new StartupCommand[] { startCmd, storePoolCmd };
	}

	@Override
	public PingCommand getCurrentStatus(long id) {
		// TODO Auto-generated method stub
		return null;
	}

	// TODO: Is it valid to return NULL, or should we throw on error?
	// Returns StartupCommand with fields revised with values known only to the host
	public Command[] requestStartupCommand(Command[] cmd) {
		// Set HTTP POST destination URI
		// Using java.net.URI, see
		// http://docs.oracle.com/javase/1.5.0/docs/api/java/net/URI.html
		URI agentUri = null;
		try {
			String cmdName = "StartupCommand";
			agentUri = new URI("http", null, this._agentIp, 8250,
					"/api/HypervResource/" + cmdName, null, null);
		} catch (URISyntaxException e) {
			// TODO add proper logging
			String errMsg = "Could not generate URI for Hyper-V agent";
			s_logger.error(errMsg, e);
			return null;
		}
		String incomingCmd = PostHttpRequest(s_gson.toJson(cmd), agentUri);

		if (incomingCmd == null){
			return null;
		}
		Command[] result = null;
		try {
			result = s_gson.fromJson(incomingCmd, Command[].class);
		}
		catch (Exception ex)
		{
			String errMsg = "Failed to deserialize Command[] "+incomingCmd;
			s_logger.error(errMsg, ex);
		}
		s_logger.debug("requestStartupCommand received response " + s_gson.toJson(result));
		if (result.length > 0)
		{
			return result;
		}
		return null;
	}

	@Override
	// TODO: Is it valid to return NULL, or should we throw on error?
	public Answer executeRequest(Command cmd) {
		// Set HTTP POST destination URI
		// Using java.net.URI, see
		// http://docs.oracle.com/javase/1.5.0/docs/api/java/net/URI.html
		URI agentUri = null;
		try {
			String cmdName = cmd.getClass().getSimpleName();
			agentUri = new URI("http", null, this._agentIp, 8250,
					"/api/HypervResource/" + cmdName, null, null);
		} catch (URISyntaxException e) {
			// TODO add proper logging
			String errMsg = "Could not generate URI for Hyper-V agent";
			s_logger.error(errMsg, e);
			return null;
		}
		String ansStr = PostHttpRequest(s_gson.toJson(cmd), agentUri);
		
		if (ansStr == null){
			return null;
		}
		// Only Answer instances are returned by remote agents.
		// E.g. see Response.getAnswers()
		Answer[] result = s_gson.fromJson(ansStr, Answer[].class);
		s_logger.debug("executeRequest received response " + s_gson.toJson(result));
		if (result.length > 0)
		{
			return result[0];
		}
		return null;
	}

	public static String PostHttpRequest(String jsonCmd, URI agentUri) {
		// Using Apache's HttpClient for HTTP POST
		// Java-only approach discussed at on StackOverflow concludes with
		// comment to use Apache HttpClient
		// http://stackoverflow.com/a/2793153/939250, but final comment is to
		// use Apache.
		s_logger.debug("POST request to"  +  agentUri.toString() + " with contents" + jsonCmd);

		// Create request
		HttpClient httpClient = new DefaultHttpClient();
		String result = null;
		
		// TODO: are there timeout settings and worker thread settings to tweak?
		try {
			HttpPost request = new HttpPost(agentUri);

			// JSON encode command
			// Assumes command sits comfortably in a string, i.e. not used for
			// large data transfers
			StringEntity cmdJson = new StringEntity(jsonCmd);
			request.addHeader("content-type", "application/json");
			request.setEntity(cmdJson);
			s_logger.debug("Sending cmd to " + agentUri.toString()
					+ " cmd data:" + jsonCmd);
			HttpResponse response = httpClient.execute(request);

			// Unsupported commands will not route.
			if (response.getStatusLine().getStatusCode() == 405) {
				String errMsg = "Failed to send : HTTP error code : "
						+ response.getStatusLine().getStatusCode();
				s_logger.error(errMsg);
				Answer ans =  new UnsupportedAnswer(null, "Unsupported command " + agentUri.getPath() + ".  Are you sure you got the right type of server?");
				s_logger.error(ans);
				result = s_gson.toJson(new Answer[] {ans});
			}
			// Look for status errors
			else if (response.getStatusLine().getStatusCode() != 200) {
				String errMsg = "Failed to send : HTTP error code : "
						+ response.getStatusLine().getStatusCode();
				s_logger.error(errMsg);
				return null;
			}
			else {
				result = EntityUtils.toString(response.getEntity());
				s_logger.debug("POST response is"  +  result);
			}
		} catch (ClientProtocolException protocolEx) {
			// Problem with HTTP message exchange
			s_logger.error(protocolEx);
		} catch (IOException connEx) {
			// Problem with underlying communications
			s_logger.error(connEx);
		} finally {
			httpClient.getConnectionManager().shutdown();
		}
		return result;
	}

	@Override
	protected String getDefaultScriptsDir() {
		// TODO Auto-generated method stub
		return null;
	}

	// configure, and not initialize, is called after a ServerResource is created
	@Override
	public boolean configure(final String name, final Map<String, Object> params)
			throws ConfigurationException {
		/* todo: update, make consistent with the xen server equivalent. */
		this._zoneId = (String) params.get("zone");
		this._podId = (String) params.get("pod");
		this._clusterId = (String) params.get("cluster");
		this._guid = (String) params.get("guid");
		this._agentIp = (String) params.get("agentIp");
		this._name = name;	
		return true;
	}

	@Override
	public void setName(String name) {
		// TODO Auto-generated method stub
	}

	@Override
	public void setConfigParams(Map<String, Object> params) {
		// TODO Auto-generated method stub
	}

	@Override
	public Map<String, Object> getConfigParams() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getRunLevel() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setRunLevel(int level) {
		// TODO Auto-generated method stub
	}

}
