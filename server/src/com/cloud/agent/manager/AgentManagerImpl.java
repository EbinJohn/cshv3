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
package com.cloud.agent.manager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import org.apache.cloudstack.config.ConfigDepot;
import org.apache.cloudstack.config.ConfigKey;
import org.apache.cloudstack.config.ConfigValue;
import org.apache.cloudstack.context.ServerContexts;
import org.apache.cloudstack.utils.identity.ManagementServerNode;

import com.cloud.agent.AgentManager;
import com.cloud.agent.Listener;
import com.cloud.agent.StartupCommandProcessor;
import com.cloud.agent.api.AgentControlAnswer;
import com.cloud.agent.api.AgentControlCommand;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CheckHealthCommand;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.PingAnswer;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.PingRoutingCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.ShutdownCommand;
import com.cloud.agent.api.StartupAnswer;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupProxyCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.StartupSecondaryStorageCommand;
import com.cloud.agent.api.StartupStorageCommand;
import com.cloud.agent.api.UnsupportedAnswer;
import com.cloud.agent.transport.Request;
import com.cloud.agent.transport.Response;
import com.cloud.alert.AlertManager;
import com.cloud.configuration.Config;
import com.cloud.configuration.dao.ConfigurationDao;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ConnectionException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.UnsupportedVersionException;
import com.cloud.ha.HighAvailabilityManager;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.Status.Event;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.hypervisor.HypervisorGuruManager;
import com.cloud.hypervisor.kvm.discoverer.KvmDummyResourceBase;
import com.cloud.resource.Discoverer;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceState;
import com.cloud.resource.ServerResource;
import com.cloud.storage.resource.DummySecondaryStorageResource;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.SearchCriteria2;
import com.cloud.utils.db.SearchCriteriaService;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.HypervisorVersionChangedException;
import com.cloud.utils.fsm.NoTransitionException;
import com.cloud.utils.fsm.StateMachine2;
import com.cloud.utils.nio.HandlerFactory;
import com.cloud.utils.nio.Link;
import com.cloud.utils.nio.NioServer;
import com.cloud.utils.nio.Task;
import com.cloud.utils.time.InaccurateClock;

/**
 * Implementation of the Agent Manager. This class controls the connection to the agents.
 **/
@Local(value = { AgentManager.class })
public class AgentManagerImpl extends ManagerBase implements AgentManager, HandlerFactory {
    protected static final Logger s_logger = Logger.getLogger(AgentManagerImpl.class);
    protected static final Logger status_logger = Logger.getLogger(Status.class);

    protected ConcurrentHashMap<Long, AgentAttache> _agents = new ConcurrentHashMap<Long, AgentAttache>(10007);
    protected List<Pair<Integer, Listener>> _hostMonitors = new ArrayList<Pair<Integer, Listener>>(17);
    protected List<Pair<Integer, Listener>> _cmdMonitors = new ArrayList<Pair<Integer, Listener>>(17);
    protected List<Pair<Integer, StartupCommandProcessor>> _creationMonitors = new ArrayList<Pair<Integer, StartupCommandProcessor>>(17);
    protected List<Long> _loadingAgents = new ArrayList<Long>();
    protected int _monitorId = 0;
    private final Lock _agentStatusLock = new ReentrantLock();


    @Inject
    protected EntityManager _entityMgr;

    protected NioServer _connection;
    @Inject
    protected HostDao _hostDao = null;
    @Inject
    protected DataCenterDao _dcDao = null;
    @Inject
    protected HostPodDao _podDao = null;
    @Inject
    protected ConfigurationDao _configDao = null;
    @Inject
    protected ClusterDao _clusterDao = null;
    protected int _port;

    @Inject
    protected HighAvailabilityManager _haMgr = null;
    @Inject
    protected AlertManager _alertMgr = null;

    @Inject
    protected HypervisorGuruManager _hvGuruMgr;

    protected int _retry = 2;

    protected ConfigValue<Integer> _wait;
    protected ConfigValue<Integer> _alertWait;
    protected long _nodeId = -1;

    protected ConfigValue<Integer> _pingInterval;
    protected ConfigValue<Float> _pingTimeout;

    protected ExecutorService _executor;
    protected ThreadPoolExecutor _connectExecutor;
    protected ScheduledExecutorService _directAgentExecutor;
    protected ScheduledExecutorService _monitorExecutor;

    protected StateMachine2<Status, Status.Event, Host> _statusStateMachine = Status.getStateMachine();
    private final Map<Long, Long> _pingMap = new ConcurrentHashMap<Long, Long>(10007);

    @Inject ResourceManager _resourceMgr;

    @Inject
    protected ConfigDepot _configDepot;

    protected final ConfigKey<Integer> Workers = new ConfigKey<Integer>(Integer.class, "workers", "Advance", AgentManager.class, "5",
            "Number of worker threads handling remote agent connections.", false, "5-Max Thread Limit");
    protected final ConfigKey<Integer> Port = new ConfigKey<Integer>(Integer.class, "port", "Advance", AgentManager.class, "8250", "Port to listen on for remote agent connections.", false, "Usable port range");
    protected final ConfigKey<Integer> PingInterval = new ConfigKey<Integer>(Integer.class, "ping.interval", "Advance", AgentManager.class, "60", "Interval to send application level pings to make sure the connection is still working", false, "Seconds");
    protected final ConfigKey<Float> PingTimeout = new ConfigKey<Float>(Float.class, "ping.timeout", "Advance", AgentManager.class, "2.5", "Multiplier to ping.interval before announcing an agent has timed out", true, null);
    protected final ConfigKey<Integer> Wait = new ConfigKey<Integer>(Integer.class, "wait", "Advance", AgentManager.class, "1800",
            "Time in seconds to wait for control commands to return", true, "Seconds");
    protected final ConfigKey<Integer> AlertWait = new ConfigKey<Integer>(Integer.class, "alert.wait", "Advance", AgentManager.class, "1800",
            "Seconds to wait before alerting on a disconnected agent", true, "Seconds");
    protected final ConfigKey<Integer> DirectAgentLoadSize = new ConfigKey<Integer>(Integer.class, "direct.agent.load.size", "Advance", AgentManager.class, "16",
            "The number of direct agents to load each time", false, null);
    protected final ConfigKey<Integer> DirectAgentPoolSize = new ConfigKey<Integer>(Integer.class, "direct.agent.pool.size", "Advance", AgentManager.class, "500",
            "Default size for DirectAgentPool", false, null);

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {

        final Map<String, String> configs = _configDao.getConfiguration("AgentManager", params);
        _port = NumbersUtil.parseInt(configs.get("port"), 8250);
        final int workers = NumbersUtil.parseInt(configs.get("workers"), 5);

        _pingInterval = _configDepot.get(PingInterval);

        _wait = _configDepot.get(Wait);
        _alertWait = _configDepot.get(AlertWait);
        _pingTimeout = _configDepot.get(PingTimeout);

        s_logger.info("Ping Timeout is " + _pingTimeout);

        ConfigValue<Integer> threads = _configDepot.get(DirectAgentLoadSize);

        _nodeId = ManagementServerNode.getManagementServerId();
        s_logger.info("Configuring AgentManagerImpl. management server node id(msid): " + _nodeId);

        long lastPing = (System.currentTimeMillis() >> 10) - (long)(_pingTimeout.value() * _pingInterval.value());
        _hostDao.markHostsAsDisconnected(_nodeId, lastPing);

        registerForHostEvents(new BehindOnPingListener(), true, true, false);

        _executor = new ThreadPoolExecutor(threads.value(), threads.value(), 60l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("AgentTaskPool"));

        _connectExecutor = new ThreadPoolExecutor(100, 500, 60l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("AgentConnectTaskPool"));
        //allow core threads to time out even when there are no items in the queue
        _connectExecutor.allowCoreThreadTimeOut(true);

        _connection = new NioServer("AgentManager", _port, workers + 10, this);
        s_logger.info("Listening on " + _port + " with " + workers + " workers");

        
        ConfigValue<Integer> size = _configDepot.get(DirectAgentPoolSize);
        _directAgentExecutor = new ScheduledThreadPoolExecutor(size.value(), new NamedThreadFactory("DirectAgent"));
        s_logger.debug("Created DirectAgentAttache pool with size: " + size.value());
        
        _monitorExecutor = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("AgentMonitor"));

        return true;
    }

    protected long getTimeout() {
        return (long)(_pingTimeout.value() * _pingInterval.value());
    }

    @Override
    public Task create(Task.Type type, Link link, byte[] data) {
        return new AgentHandler(type, link, data);
    }

    @Override
    public int registerForHostEvents(final Listener listener, boolean connections, boolean commands, boolean priority) {
        synchronized (_hostMonitors) {
            _monitorId++;
            if (connections) {
                if (priority) {
                    _hostMonitors.add(0, new Pair<Integer, Listener>(_monitorId, listener));
                } else {
                    _hostMonitors.add(new Pair<Integer, Listener>(_monitorId, listener));
                }
            }
            if (commands) {
                if (priority) {
                    _cmdMonitors.add(0, new Pair<Integer, Listener>(_monitorId, listener));
                } else {
                    _cmdMonitors.add(new Pair<Integer, Listener>(_monitorId, listener));
                }
            }
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Registering listener " + listener.getClass().getSimpleName() + " with id " + _monitorId);
            }
            return _monitorId;
        }
    }

    @Override
    public int registerForInitialConnects(final StartupCommandProcessor creator,boolean priority) {
        synchronized (_hostMonitors) {
            _monitorId++;

            if (priority) {
                _creationMonitors.add(0, new Pair<Integer, StartupCommandProcessor>(
                        _monitorId, creator));
            } else {
                _creationMonitors.add(0, new Pair<Integer, StartupCommandProcessor>(
                        _monitorId, creator));
            }
        }

        return _monitorId;
    }

    @Override
    public void unregisterForHostEvents(final int id) {
        s_logger.debug("Deregistering " + id);
        _hostMonitors.remove(id);
    }

    private AgentControlAnswer handleControlCommand(AgentAttache attache, final AgentControlCommand cmd) {
        AgentControlAnswer answer = null;

        for (Pair<Integer, Listener> listener : _cmdMonitors) {
            answer = listener.second().processControlCommand(attache.getId(), cmd);

            if (answer != null) {
                return answer;
            }
        }

        s_logger.warn("No handling of agent control command: " + cmd + " sent from " + attache.getId());
        return new AgentControlAnswer(cmd);
    }

    public void handleCommands(AgentAttache attache, final long sequence, final Command[] cmds) {
        for (Pair<Integer, Listener> listener : _cmdMonitors) {
            boolean processed = listener.second().processCommands(attache.getId(), sequence, cmds);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("SeqA " + attache.getId() + "-" + sequence + ": " + (processed ? "processed" : "not processed") + " by " + listener.getClass());
            }
        }
    }

    public void notifyAnswersToMonitors(long agentId, long seq, Answer[] answers) {
        for (Pair<Integer, Listener> listener : _cmdMonitors) {
            listener.second().processAnswers(agentId, seq, answers);
        }
    }

    public AgentAttache findAttache(long hostId) {
        AgentAttache attache = null;
        synchronized (_agents) {
            attache = _agents.get(hostId);
        }
        return attache;
    }

    @Override
    public Answer sendTo(Long dcId, HypervisorType type, Command cmd) {
        List<ClusterVO> clusters = _clusterDao.listByDcHyType(dcId, type.toString());
        int retry = 0;
        for (ClusterVO cluster : clusters) {
            List<HostVO> hosts = _resourceMgr.listAllUpAndEnabledHosts(Host.Type.Routing, cluster.getId(), null, dcId);
            for (HostVO host : hosts) {
                retry++;
                if (retry > _retry) {
                    return null;
                }
                Answer answer = null;
                try {

                    long targetHostId = _hvGuruMgr.getGuruProcessedCommandTargetHost(host.getId(), cmd);
                    answer = easySend(targetHostId, cmd);
                } catch (Exception e) {
                }
                if (answer != null) {
                    return answer;
                }
            }
        }
        return null;
    }

    protected int getPingInterval() {
        return _pingInterval.value();
    }

    @Override
    public Answer send(Long hostId, Command cmd) throws AgentUnavailableException, OperationTimedoutException {
        Commands cmds = new Commands(OnError.Stop);
        cmds.addCommand(cmd);
        send(hostId, cmds, cmd.getWait());
        Answer[] answers = cmds.getAnswers();
        if (answers != null && !(answers[0] instanceof UnsupportedAnswer)) {
            return answers[0];
        }

        if (answers != null && (answers[0] instanceof UnsupportedAnswer)) {
            s_logger.warn("Unsupported Command: " + answers[0].getDetails());
            return answers[0];
        }

        return null;
    }

    @DB
    protected boolean noDbTxn() {
        Transaction txn = Transaction.currentTxn();
        return !txn.dbTxnStarted();
    }

    @Override
    public Answer[] send(Long hostId, Commands commands, int timeout) throws AgentUnavailableException, OperationTimedoutException {
        assert hostId != null : "Who's not checking the agent id before sending?  ... (finger wagging)";
        if (hostId == null) {
            throw new AgentUnavailableException(-1);
        }

        if (timeout <= 0) {
            timeout = _wait.value();
        }
        assert noDbTxn() : "I know, I know.  Why are we so strict as to not allow txn across an agent call?  ...  Why are we so cruel ... Why are we such a dictator .... Too bad... Sorry...but NO AGENT COMMANDS WRAPPED WITHIN DB TRANSACTIONS!";

        Command[] cmds = commands.toCommands();

        assert cmds.length > 0 : "Ask yourself this about a hundred times.  Why am I  sending zero length commands?";

        if (cmds.length == 0) {
            commands.setAnswers(new Answer[0]);
        }

        final AgentAttache agent = getAttache(hostId);
        if (agent == null || agent.isClosed()) {
            throw new AgentUnavailableException("agent not logged into this management server", hostId);
        }

        Request req = new Request(hostId, _nodeId, cmds, commands.stopOnError(), true);
        req.setSequence(agent.getNextSequence());
        Answer[] answers = agent.send(req, timeout);
        notifyAnswersToMonitors(hostId, req.getSequence(), answers);
        commands.setAnswers(answers);
        return answers;
    }

    protected Status investigate(AgentAttache agent) {
        Long hostId = agent.getId();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("checking if agent (" + hostId + ") is alive");
        }

        Answer answer = easySend(hostId, new CheckHealthCommand());
        if (answer != null && answer.getResult()) {
            Status status = Status.Up;
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("agent (" + hostId + ") responded to checkHeathCommand, reporting that agent is " + status);
            }
            return status;
        }

        return _haMgr.investigate(hostId);
    }

    protected AgentAttache getAttache(final Long hostId) throws AgentUnavailableException {
        assert (hostId != null) : "Who didn't check their id value?";
        if (hostId == null) {
            return null;
        }
        AgentAttache agent = findAttache(hostId);
        if (agent == null) {
            s_logger.debug("Unable to find agent for " + hostId);
            throw new AgentUnavailableException("Unable to find agent ", hostId);
        }

        return agent;
    }

    @Override
    public long send(Long hostId, Commands commands, Listener listener) throws AgentUnavailableException {
        final AgentAttache agent = getAttache(hostId);
        if (agent.isClosed()) {
            throw new AgentUnavailableException("Agent " + agent.getId() + " is closed", agent.getId());
        }

        Command[] cmds = commands.toCommands();

        assert cmds.length > 0 : "Why are you sending zero length commands?";
        if (cmds.length == 0) {
            throw new AgentUnavailableException("Empty command set for agent " + agent.getId(), agent.getId());
        }
        Request req = new Request(hostId, _nodeId, cmds, commands.stopOnError(), true);
        req.setSequence(agent.getNextSequence());

        agent.send(req, listener);
        return req.getSequence();
    }


    public void removeAgent(AgentAttache attache, Status nextState) {
        if (attache == null) {
            return;
        }
        long hostId = attache.getId();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Remove Agent : " + hostId);
        }
        AgentAttache removed = null;
        boolean conflict = false;
        synchronized (_agents) {
            removed = _agents.remove(hostId);
            if (removed != null && removed != attache) {
                conflict = true;
                _agents.put(hostId, removed);
                removed = attache;
            }
        }
        if (conflict) {
            s_logger.debug("Agent for host " + hostId + " is created when it is being disconnected");
        }
        if (removed != null) {
            removed.disconnect(nextState);
        }

        for (Pair<Integer, Listener> monitor : _hostMonitors) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Sending Disconnect to listener: " + monitor.second().getClass().getName());
            }
            monitor.second().processDisconnect(hostId, nextState);
        }
    }

    protected AgentAttache notifyMonitorsOfConnection(AgentAttache attache, final StartupCommand[] cmd, boolean forRebalance) throws ConnectionException {
        long hostId = attache.getId();
        HostVO host = _hostDao.findById(hostId);
        for (Pair<Integer, Listener> monitor : _hostMonitors) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Sending Connect to listener: " + monitor.second().getClass().getSimpleName());
            }
            for (int i = 0; i < cmd.length; i++) {
                try {
                    monitor.second().processConnect(host, cmd[i], forRebalance);
                } catch (Exception e) {
                    if (e instanceof ConnectionException) {
                        ConnectionException ce = (ConnectionException)e;
                        if (ce.isSetupError()) {
                            s_logger.warn("Monitor " + monitor.second().getClass().getSimpleName() + " says there is an error in the connect process for " + hostId + " due to " + e.getMessage());
                            handleDisconnectWithoutInvestigation(attache, Event.AgentDisconnected, true);
                            throw ce;
                        } else {
                            s_logger.info("Monitor " + monitor.second().getClass().getSimpleName() + " says not to continue the connect process for " + hostId + " due to " + e.getMessage());
                            handleDisconnectWithoutInvestigation(attache, Event.ShutdownRequested, true);
                            return attache;
                        }
                    } else if (e instanceof HypervisorVersionChangedException) {
                        handleDisconnectWithoutInvestigation(attache, Event.ShutdownRequested, true);
                        throw new CloudRuntimeException("Unable to connect " + attache.getId(), e);
                    } else {
                        s_logger.error("Monitor " + monitor.second().getClass().getSimpleName() + " says there is an error in the connect process for " + hostId + " due to " + e.getMessage(), e);
                        handleDisconnectWithoutInvestigation(attache, Event.AgentDisconnected, true);
                        throw new CloudRuntimeException("Unable to connect " + attache.getId(), e);
                    }
                }
            }
        }

        Long dcId = host.getDataCenterId();
        ReadyCommand ready = new ReadyCommand(dcId, host.getId());
        Answer answer = easySend(hostId, ready);
        if (answer == null || !answer.getResult()) {
            // this is tricky part for secondary storage
            // make it as disconnected, wait for secondary storage VM to be up
            // return the attache instead of null, even it is disconnectede
            handleDisconnectWithoutInvestigation(attache, Event.AgentDisconnected, true);
        }

        agentStatusTransitTo(host, Event.Ready, _nodeId);
        attache.ready();
        return attache;
    }

    protected boolean notifyCreatorsOfConnection(StartupCommand[] cmd) throws ConnectionException {
        boolean handled = false;
        for (Pair<Integer, StartupCommandProcessor> monitor : _creationMonitors) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Sending Connect to creator: "
                        + monitor.second().getClass().getSimpleName());
            }
            handled =  monitor.second().processInitialConnect(cmd);
            if (handled) {
                break;
            }
        }

        return handled;
    }

    @Override
    public boolean start() {
        startDirectlyConnectedHosts();

        if (_connection != null) {
            _connection.start();
        }

        _monitorExecutor.scheduleWithFixedDelay(new MonitorTask(), _pingInterval.value(), _pingInterval.value(), TimeUnit.SECONDS);

        return true;
    }

    public void startDirectlyConnectedHosts() {
        List<HostVO> hosts = _resourceMgr.findDirectlyConnectedHosts();
        for (HostVO host : hosts) {
            loadDirectlyConnectedHost(host, false);
        }
    }

    private ServerResource loadResourcesWithoutHypervisor(HostVO host){
        String resourceName = host.getResource();
        ServerResource resource = null;
        try {
            Class<?> clazz = Class.forName(resourceName);
            Constructor<?> constructor = clazz.getConstructor();
            resource = (ServerResource) constructor.newInstance();
        } catch (ClassNotFoundException e) {
            s_logger.warn("Unable to find class " + host.getResource(), e);
        } catch (InstantiationException e) {
            s_logger.warn("Unablet to instantiate class " + host.getResource(), e);
        } catch (IllegalAccessException e) {
            s_logger.warn("Illegal access " + host.getResource(), e);
        } catch (SecurityException e) {
            s_logger.warn("Security error on " + host.getResource(), e);
        } catch (NoSuchMethodException e) {
            s_logger.warn("NoSuchMethodException error on " + host.getResource(), e);
        } catch (IllegalArgumentException e) {
            s_logger.warn("IllegalArgumentException error on " + host.getResource(), e);
        } catch (InvocationTargetException e) {
            s_logger.warn("InvocationTargetException error on " + host.getResource(), e);
        }

        if(resource != null){
            _hostDao.loadDetails(host);

            HashMap<String, Object> params = new HashMap<String, Object>(host.getDetails().size() + 5);
            params.putAll(host.getDetails());

            params.put("guid", host.getGuid());
            params.put("zone", Long.toString(host.getDataCenterId()));
            if (host.getPodId() != null) {
                params.put("pod", Long.toString(host.getPodId()));
            }
            if (host.getClusterId() != null) {
                params.put("cluster", Long.toString(host.getClusterId()));
                String guid = null;
                ClusterVO cluster = _clusterDao.findById(host.getClusterId());
                if (cluster.getGuid() == null) {
                    guid = host.getDetail("pool");
                } else {
                    guid = cluster.getGuid();
                }
                if (guid != null && !guid.isEmpty()) {
                    params.put("pool", guid);
                }
            }

            params.put("ipaddress", host.getPrivateIpAddress());
            params.put("secondary.storage.vm", "false");
            params.put("max.template.iso.size", _configDao.getValue(Config.MaxTemplateAndIsoSize.toString()));
            params.put("migratewait", _configDao.getValue(Config.MigrateWait.toString()));

            try {
                resource.configure(host.getName(), params);
            } catch (ConfigurationException e) {
                s_logger.warn("Unable to configure resource due to " + e.getMessage());
                return null;
            }

            if (!resource.start()) {
                s_logger.warn("Unable to start the resource");
                return null;
            }
        }
        return resource;
    }


    protected boolean loadDirectlyConnectedHost(HostVO host, boolean forRebalance) {
        boolean initialized = false;
        ServerResource resource = null;
        try {
            //load the respective discoverer
            Discoverer discoverer = _resourceMgr.getMatchingDiscover(host.getHypervisorType());
            if(discoverer == null){
                s_logger.info("Could not to find a Discoverer to load the resource: "+ host.getId() +" for hypervisor type: "+host.getHypervisorType());
                resource = loadResourcesWithoutHypervisor(host);
            }else{
                resource = discoverer.reloadResource(host);
            }

            if(resource == null){
                s_logger.warn("Unable to load the resource: "+ host.getId());
                return false;
            }

            initialized = true;
        } finally {
            if(!initialized) {
                if (host != null) {
                    agentStatusTransitTo(host, Event.AgentDisconnected, _nodeId);
                }
            }
        }

        if (forRebalance) {
            Host h = _resourceMgr.createHostAndAgent(host.getId(), resource, host.getDetails(), false, null, true);
            return (h == null ? false : true);
        } else {
            _executor.execute(new SimulateStartTask(host.getId(), resource, host.getDetails()));
            return true;
        }
    }

    protected AgentAttache createAttacheForDirectConnect(HostVO host, ServerResource resource)
            throws ConnectionException {
        if (resource instanceof DummySecondaryStorageResource || resource instanceof KvmDummyResourceBase) {
            return new DummyAttache(this, host.getId(), false);
        }

        s_logger.debug("create DirectAgentAttache for " + host.getId());
        DirectAgentAttache attache = new DirectAgentAttache(this, host.getId(), resource, host.isInMaintenanceStates(), this);

        AgentAttache old = null;
        synchronized (_agents) {
            old = _agents.put(host.getId(), attache);
        }
        if (old != null) {
            old.disconnect(Status.Removed);
        }

        return attache;
    }

    @Override
    public boolean stop() {

        if (_connection != null) {
            _connection.stop();
        }

        s_logger.info("Disconnecting agents: " + _agents.size());
        synchronized (_agents) {
            for (final AgentAttache agent : _agents.values()) {
                final HostVO host = _hostDao.findById(agent.getId());
                if (host == null) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Cant not find host " + agent.getId());
                    }
                } else {
                    if (!agent.forForward()) {
                        agentStatusTransitTo(host, Event.ManagementServerDown, _nodeId);
                    }
                }
            }
        }

        _connectExecutor.shutdownNow();
        _monitorExecutor.shutdownNow();
        return true;
    }

    protected boolean handleDisconnectWithoutInvestigation(AgentAttache attache, Status.Event event, boolean transitState) {
        long hostId = attache.getId();

        s_logger.info("Host " + hostId + " is disconnecting with event " + event);
        Status nextStatus = null;
        HostVO host = _hostDao.findById(hostId);
        if (host == null) {
            s_logger.warn("Can't find host with " + hostId);
            nextStatus = Status.Removed;
        } else {
            final Status currentStatus = host.getStatus();
            if (currentStatus == Status.Down || currentStatus == Status.Alert || currentStatus == Status.Removed) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Host " + hostId + " is already " + currentStatus);
                }
                nextStatus = currentStatus;
            } else {
                try {
                    nextStatus = currentStatus.getNextStatus(event);
                } catch (NoTransitionException e) {
                    String err = "Cannot find next status for " + event + " as current status is " + currentStatus + " for agent " + hostId;
                    s_logger.debug(err);
                    throw new CloudRuntimeException(err);
                }

                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("The next status of agent " + hostId + "is " + nextStatus + ", current status is " + currentStatus);
                }
            }
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Deregistering link for " + hostId + " with state " + nextStatus);
        }

        //remove the attache
        removeAgent(attache, nextStatus);

        //update the DB
        if (host != null && transitState) {
            disconnectAgent(host, event, _nodeId);
        }

        return true;
    }

    protected boolean handleDisconnectWithInvestigation(AgentAttache attache, Status.Event event) {
        long hostId = attache.getId();
        HostVO host = _hostDao.findById(hostId);
        if (host != null) {
            Status nextStatus = null;
            try {
                nextStatus = host.getStatus().getNextStatus(event);
            } catch (NoTransitionException ne) {
                /* Agent may be currently in status of Down, Alert, Removed, namely there is no next status for some events.
                 * Why this can happen? Ask God not me. I hate there was no piece of comment for code handling race condition.
                 * God knew what race condition the code dealt with!
                 */
            }

            if (nextStatus == Status.Alert) {
                /* OK, we are going to the bad status, let's see what happened */
                s_logger.info("Investigating why host " + hostId + " has disconnected with event " + event);

                final Status determinedState = investigate(attache);
                // if state cannot be determined do nothing and bail out
                if (determinedState == null) {
                    s_logger.warn("Agent state cannot be determined, do nothing");
                    return false;
                }

                final Status currentStatus = host.getStatus();
                s_logger.info("The state determined is " + determinedState);

                if (determinedState == Status.Down) {
                    s_logger.error("Host is down: " + host.getId() + "-" + host.getName() + ".  Starting HA on the VMs");
                    event = Status.Event.HostDown;
                } else if (determinedState == Status.Up) {
                    /* Got ping response from host, bring it back*/
                    s_logger.info("Agent is determined to be up and running");
                    agentStatusTransitTo(host, Status.Event.Ping, _nodeId);
                    return false;
                } else if (determinedState == Status.Disconnected) {
                    s_logger.warn("Agent is disconnected but the host is still up: " + host.getId() + "-" + host.getName());
                    if (currentStatus == Status.Disconnected) {
                        if (((System.currentTimeMillis() >> 10) - host.getLastPinged()) > _alertWait.value()) {
                            s_logger.warn("Host " + host.getId() + " has been disconnected pass the time it should be disconnected.");
                            event = Status.Event.WaitedTooLong;
                        } else {
                            s_logger.debug("Host has been determined to be disconnected but it hasn't passed the wait time yet.");
                            return false;
                        }
                    } else if (currentStatus == Status.Up) {
                        DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                        HostPodVO podVO = _podDao.findById(host.getPodId());
                        String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName() + ", pod: " + podVO.getName();
                        if ((host.getType() != Host.Type.SecondaryStorage) && (host.getType() != Host.Type.ConsoleProxy)) {
                            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, host.getDataCenterId(), host.getPodId(), "Host disconnected, " + hostDesc, "If the agent for host [" + hostDesc
                                    + "] is not restarted within " + _alertWait + " seconds, HA will begin on the VMs");
                        }
                        event = Status.Event.AgentDisconnected;
                    }
                } else {
                    // if we end up here we are in alert state, send an alert
                    DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                    HostPodVO podVO = _podDao.findById(host.getPodId());
                    String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName() + ", pod: " + podVO.getName();
                    _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, host.getDataCenterId(), host.getPodId(), "Host in ALERT state, " + hostDesc, "In availability zone " + host.getDataCenterId()
                            + ", host is in alert state: " + host.getId() + "-" + host.getName());
                }
            } else {
                s_logger.debug("The next status of Agent " + host.getId() + " is not Alert, no need to investigate what happened");
            }
        }

        handleDisconnectWithoutInvestigation(attache, event, true);
        host = _hostDao.findById(host.getId());
        if (host.getStatus() == Status.Alert || host.getStatus() == Status.Down) {
            _haMgr.scheduleRestartForVmsOnHost(host, true);
        }

        return true;
    }

    protected class DisconnectTask implements Runnable {
        AgentAttache _attache;
        Status.Event _event;
        boolean _investigate;

        DisconnectTask(final AgentAttache attache, final Status.Event event, final boolean investigate) {
            _attache = attache;
            _event = event;
            _investigate = investigate;
        }

        @Override
        public void run() {
            try {
                if (_investigate == true) {
                    handleDisconnectWithInvestigation(_attache, _event);
                } else {
                    handleDisconnectWithoutInvestigation(_attache, _event, true);
                }
            } catch (final Exception e) {
                s_logger.error("Exception caught while handling disconnect: ", e);
            }
        }
    }

    @Override
    public Answer easySend(final Long hostId, final Command cmd) {
        try {
            Host h = _hostDao.findById(hostId);
            if (h == null || h.getRemoved() != null) {
                s_logger.debug("Host with id " + hostId + " doesn't exist");
                return null;
            }
            Status status = h.getStatus();
            if (!status.equals(Status.Up) && !status.equals(Status.Connecting)) {
                s_logger.debug("Can not send command " + cmd + " due to Host " + hostId + " is not up");
                return null;
            }
            final Answer answer = send(hostId, cmd);
            if (answer == null) {
                s_logger.warn("send returns null answer");
                return null;
            }

            if (s_logger.isDebugEnabled() && answer.getDetails() != null) {
                s_logger.debug("Details from executing " + cmd.getClass() + ": " + answer.getDetails());
            }

            return answer;

        } catch (final AgentUnavailableException e) {
            s_logger.warn(e.getMessage());
            return null;
        } catch (final OperationTimedoutException e) {
            s_logger.warn("Operation timed out: " + e.getMessage());
            return null;
        } catch (final Exception e) {
            s_logger.warn("Exception while sending", e);
            return null;
        }
    }

    @Override
    public Answer[] send(final Long hostId, Commands cmds) throws AgentUnavailableException, OperationTimedoutException {
        int wait = 0;
        for( Command cmd : cmds ) {
            if ( cmd.getWait() > wait ) {
                wait = cmd.getWait();
            }
        }
        return send(hostId, cmds, wait);
    }

    @Override
    public boolean reconnect(final long hostId) {
        HostVO host;

        host = _hostDao.findById(hostId);
        if (host == null || host.getRemoved() != null) {
            s_logger.warn("Unable to find host " + hostId);
            return false;
        }

        if (host.getStatus() == Status.Disconnected) {
            s_logger.info("Host is already disconnected, no work to be done");
            return true;
        }

        if (host.getStatus() != Status.Up && host.getStatus() != Status.Alert && host.getStatus() != Status.Rebalancing) {
            s_logger.info("Unable to disconnect host because it is not in the correct state: host=" + hostId + "; Status=" + host.getStatus());
            return false;
        }

        AgentAttache attache = findAttache(hostId);
        if (attache == null) {
            s_logger.info("Unable to disconnect host because it is not connected to this server: " + hostId);
            return false;
        }

        disconnectWithoutInvestigation(attache, Event.ShutdownRequested);
        return true;
    }

    public boolean executeUserRequest(long hostId, Event event) throws AgentUnavailableException {
        if (event == Event.AgentDisconnected) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Received agent disconnect event for host " + hostId);
            }
            AgentAttache attache = null;
            attache = findAttache(hostId);
            if (attache != null) {
                handleDisconnectWithoutInvestigation(attache, Event.AgentDisconnected, true);
            }
            return true;
        } else if (event == Event.ShutdownRequested) {
            return reconnect(hostId);
        }
        return false;
    }

    @Override
    public boolean isAgentAttached(long hostId) {
        return findAttache(hostId) != null;
    }

    protected AgentAttache createAttacheForConnect(HostVO host, Link link) throws ConnectionException {
        s_logger.debug("create ConnectedAgentAttache for " + host.getId());
        AgentAttache attache = new ConnectedAgentAttache(this, host.getId(), link, host.isInMaintenanceStates());
        link.attach(attache);

        AgentAttache old = null;
        synchronized (_agents) {
            old = _agents.put(host.getId(), attache);
        }
        if (old != null) {
            old.disconnect(Status.Removed);
        }

        return attache;
    }

    private AgentAttache handleConnectedAgent(final Link link, final StartupCommand[] startup, Request request) {
        AgentAttache attache = null;
        ReadyCommand ready = null;
        try {
            HostVO host = _resourceMgr.createHostVOForConnectedAgent(startup);
            if (host != null) {
                ready = new ReadyCommand(host.getDataCenterId(), host.getId());
                attache = createAttacheForConnect(host, link);
                attache = notifyMonitorsOfConnection(attache, startup, false);
            }
        } catch (Exception e) {
            s_logger.debug("Failed to handle host connection: " + e.toString());
            ready = new ReadyCommand(null);
            ready.setDetails(e.toString());
        } finally {
            if (ready == null) {
                ready = new ReadyCommand(null);
            }
        }

        try {
            if (attache == null) {
                final Request readyRequest = new Request(-1, -1, ready, false);
                link.send(readyRequest.getBytes());
            } else {
                easySend(attache.getId(), ready);
            }
        } catch (Exception e) {
            s_logger.debug("Failed to send ready command:" + e.toString());
        }
        return attache;
    }

    protected class SimulateStartTask implements Runnable {
        ServerResource resource;
        Map<String, String> details;
        long id;

        public SimulateStartTask(long id, ServerResource resource, Map<String, String> details) {
            this.id = id;
            this.resource = resource;
            this.details = details;
        }

        @Override
        public void run() {
            ServerContexts.registerSystemContext();
            try {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Simulating start for resource " + resource.getName() + " id " + id);
                }

                _resourceMgr.createHostAndAgent(id, resource, details, false, null, false);
            } catch (Exception e) {
                s_logger.warn("Unable to simulate start on resource " + id + " name " + resource.getName(), e);
            } finally {
                ServerContexts.unregisterSystemContext();
            }
        }
    }

    protected class HandleAgentConnectTask implements Runnable {
        Link _link;
        Command[] _cmds;
        Request _request;

        HandleAgentConnectTask(Link link, final Command[] cmds, final Request request) {
            _link = link;
            _cmds = cmds;
            _request = request;
        }

        @Override
        public void run() {
            ServerContexts.registerSystemContext();
            try {
                _request.logD("Processing the first command ");
                StartupCommand[] startups = new StartupCommand[_cmds.length];
                for (int i = 0; i < _cmds.length; i++) {
                    startups[i] = (StartupCommand)_cmds[i];
                }

                AgentAttache attache = handleConnectedAgent(_link, startups, _request);
                if (attache == null) {
                    s_logger.warn("Unable to create attache for agent: " + _request);
                }
            } finally {
                ServerContexts.unregisterSystemContext();
            }
        }
    }

    protected void connectAgent(Link link, final Command[] cmds, final Request request) {
        //send startupanswer to agent in the very beginning, so agent can move on without waiting for the answer for an undetermined time, if we put this logic into another thread pool.
        StartupAnswer[] answers = new StartupAnswer[cmds.length];
        Command cmd;
        for (int i = 0; i < cmds.length; i++) {
            cmd = cmds[i];
            if ((cmd instanceof StartupRoutingCommand) || (cmd instanceof StartupProxyCommand) || (cmd instanceof StartupSecondaryStorageCommand) || (cmd instanceof StartupStorageCommand)) {
                answers[i] = new StartupAnswer((StartupCommand)cmds[i], 0, getPingInterval());
                break;
            }
        }
        Response response = null;
        response = new Response(request, answers[0], _nodeId, -1);
        try {
            link.send(response.toBytes());
        } catch (ClosedChannelException e) {
            s_logger.debug("Failed to send startupanswer: " + e.toString());
        }
        _connectExecutor.execute(new HandleAgentConnectTask(link, cmds, request));
    }

    public class AgentHandler extends Task {
        public AgentHandler(Task.Type type, Link link, byte[] data) {
            super(type, link, data);
        }

        protected void processRequest(final Link link, final Request request) {
            AgentAttache attache = (AgentAttache) link.attachment();
            final Command[] cmds = request.getCommands();
            Command cmd = cmds[0];
            boolean logD = true;

            if (attache == null) {
                if (!(cmd instanceof StartupCommand)) {
                    s_logger.warn("Throwing away a request because it came through as the first command on a connect: " + request);
                } else {
                    //submit the task for execution
                    request.logD("Scheduling the first command ");
                    connectAgent(link, cmds, request);
                }
                return;
            }

            final long hostId = attache.getId();

            if (s_logger.isDebugEnabled()) {
                if (cmd instanceof PingRoutingCommand) {
                    final PingRoutingCommand ping = (PingRoutingCommand) cmd;
                    if (ping.getNewStates().size() > 0) {
                        s_logger.debug("SeqA " + hostId + "-" + request.getSequence() + ": Processing " + request);
                    } else {
                        logD = false;
                        s_logger.debug("Ping from " + hostId);
                        s_logger.trace("SeqA " + hostId + "-" + request.getSequence() + ": Processing " + request);
                    }
                } else if (cmd instanceof PingCommand) {
                    logD = false;
                    s_logger.debug("Ping from " + hostId);
                    s_logger.trace("SeqA " + attache.getId() + "-" + request.getSequence() + ": Processing " + request);
                } else {
                    s_logger.debug("SeqA " + attache.getId() + "-" + request.getSequence() + ": Processing " + request);
                }
            }

            final Answer[] answers = new Answer[cmds.length];
            for (int i = 0; i < cmds.length; i++) {
                cmd = cmds[i];
                Answer answer = null;
                try {
                    if (cmd instanceof StartupRoutingCommand) {
                        final StartupRoutingCommand startup = (StartupRoutingCommand) cmd;
                        answer = new StartupAnswer(startup, attache.getId(), getPingInterval());
                    } else if (cmd instanceof StartupProxyCommand) {
                        final StartupProxyCommand startup = (StartupProxyCommand) cmd;
                        answer = new StartupAnswer(startup, attache.getId(), getPingInterval());
                    } else if (cmd instanceof StartupSecondaryStorageCommand) {
                        final StartupSecondaryStorageCommand startup = (StartupSecondaryStorageCommand) cmd;
                        answer = new StartupAnswer(startup, attache.getId(), getPingInterval());
                    } else if (cmd instanceof StartupStorageCommand) {
                        final StartupStorageCommand startup = (StartupStorageCommand) cmd;
                        answer = new StartupAnswer(startup, attache.getId(), getPingInterval());
                    } else if (cmd instanceof ShutdownCommand) {
                        final ShutdownCommand shutdown = (ShutdownCommand) cmd;
                        final String reason = shutdown.getReason();
                        s_logger.info("Host " + attache.getId() + " has informed us that it is shutting down with reason " + reason + " and detail " + shutdown.getDetail());
                        if (reason.equals(ShutdownCommand.Update)) {
                            //disconnectWithoutInvestigation(attache, Event.UpdateNeeded);
                            throw new CloudRuntimeException("Agent update not implemented");
                        } else if (reason.equals(ShutdownCommand.Requested)) {
                            disconnectWithoutInvestigation(attache, Event.ShutdownRequested);
                        }
                        return;
                    } else if (cmd instanceof AgentControlCommand) {
                        answer = handleControlCommand(attache, (AgentControlCommand) cmd);
                    } else {
                        handleCommands(attache, request.getSequence(), new Command[] { cmd });
                        if (cmd instanceof PingCommand) {
                            long cmdHostId = ((PingCommand) cmd).getHostId();

                            // if the router is sending a ping, verify the
                            // gateway was pingable
                            if (cmd instanceof PingRoutingCommand) {
                                boolean gatewayAccessible = ((PingRoutingCommand) cmd).isGatewayAccessible();
                                HostVO host = _hostDao.findById(Long.valueOf(cmdHostId));

                                if (host != null) {
                                    if (!gatewayAccessible) {
                                        // alert that host lost connection to
                                        // gateway (cannot ping the default route)
                                        DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                                        HostPodVO podVO = _podDao.findById(host.getPodId());
                                        String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName() + ", pod: " + podVO.getName();

                                        _alertMgr.sendAlert(AlertManager.ALERT_TYPE_ROUTING, host.getDataCenterId(), host.getPodId(), "Host lost connection to gateway, " + hostDesc, "Host [" + hostDesc
                                                + "] lost connection to gateway (default route) and is possibly having network connection issues.");
                                    } else {
                                        _alertMgr.clearAlert(AlertManager.ALERT_TYPE_ROUTING, host.getDataCenterId(), host.getPodId());
                                    }
                                } else {
                                    s_logger.debug("Not processing " + PingRoutingCommand.class.getSimpleName() +
                                            " for agent id=" + cmdHostId + "; can't find the host in the DB");
                                }
                            }
                            answer = new PingAnswer((PingCommand) cmd);
                        } else if (cmd instanceof ReadyAnswer) {
                            HostVO host = _hostDao.findById(attache.getId());
                            if (host == null) {
                                if (s_logger.isDebugEnabled()) {
                                    s_logger.debug("Cant not find host " + attache.getId());
                                }
                            }
                            answer = new Answer(cmd);
                        } else {
                            answer = new Answer(cmd);
                        }
                    }
                } catch (final Throwable th) {
                    s_logger.warn("Caught: ", th);
                    answer = new Answer(cmd, false, th.getMessage());
                }
                answers[i] = answer;
            }

            Response response = new Response(request, answers, _nodeId, attache.getId());
            if (s_logger.isDebugEnabled()) {
                if (logD) {
                    s_logger.debug("SeqA " + attache.getId() + "-" + response.getSequence() + ": Sending " + response);
                } else {
                    s_logger.trace("SeqA " + attache.getId() + "-" + response.getSequence() + ": Sending " + response);
                }
            }
            try {
                link.send(response.toBytes());
            } catch (final ClosedChannelException e) {
                s_logger.warn("Unable to send response because connection is closed: " + response);
            }
        }

        protected void processResponse(final Link link, final Response response) {
            final AgentAttache attache = (AgentAttache) link.attachment();
            if (attache == null) {
                s_logger.warn("Unable to process: " + response);
            }

            if (!attache.processAnswers(response.getSequence(), response)) {
                s_logger.info("Host " + attache.getId() + " - Seq " + response.getSequence() + ": Response is not processed: " + response);
            }
        }

        @Override
        protected void doTask(final Task task) throws Exception {
            Transaction txn = Transaction.open(Transaction.CLOUD_DB);
            try {
                final Type type = task.getType();
                if (type == Task.Type.DATA) {
                    final byte[] data = task.getData();
                    try {
                        final Request event = Request.parse(data);
                        if (event instanceof Response) {
                            processResponse(task.getLink(), (Response) event);
                        } else {
                            processRequest(task.getLink(), event);
                        }
                    } catch (final UnsupportedVersionException e) {
                        s_logger.warn(e.getMessage());
                        // upgradeAgent(task.getLink(), data, e.getReason());
                    }
                } else if (type == Task.Type.CONNECT) {
                } else if (type == Task.Type.DISCONNECT) {
                    final Link link = task.getLink();
                    final AgentAttache attache = (AgentAttache) link.attachment();
                    if (attache != null) {
                        disconnectWithInvestigation(attache, Event.AgentDisconnected);
                    } else {
                        s_logger.info("Connection from " + link.getIpAddress() + " closed but no cleanup was done.");
                        link.close();
                        link.terminated();
                    }
                }
            } finally {
                txn.close();
            }
        }
    }

    protected AgentManagerImpl() {
    }

    @Override
    public boolean tapLoadingAgents(Long hostId, TapAgentsAction action) {
        synchronized (_loadingAgents) {
            if (action == TapAgentsAction.Add) {
                _loadingAgents.add(hostId);
            } else if (action == TapAgentsAction.Del) {
                _loadingAgents.remove(hostId);
            } else if (action == TapAgentsAction.Contains) {
                return _loadingAgents.contains(hostId);
            } else {
                throw new CloudRuntimeException("Unkonwn TapAgentsAction " + action);
            }
        }
        return true;
    }

    @Override
    public boolean agentStatusTransitTo(HostVO host, Status.Event e, long msId) {
        try {
            _agentStatusLock.lock();
            if (status_logger.isDebugEnabled()) {
                ResourceState state = host.getResourceState();
                StringBuilder msg = new StringBuilder("Transition:");
                msg.append("[Resource state = ").append(state);
                msg.append(", Agent event = ").append(e.toString());
                msg.append(", Host id = ").append(host.getId()).append(", name = " + host.getName()).append("]");
                status_logger.debug(msg);
            }

            host.setManagementServerId(msId);
            try {
                return _statusStateMachine.transitTo(host, e, host.getId(), _hostDao);
            } catch (NoTransitionException e1) {
                status_logger.debug("Cannot transit agent status with event " + e + " for host " + host.getId() + ", name=" + host.getName()
                        + ", mangement server id is " + msId);
                throw new CloudRuntimeException("Cannot transit agent status with event " + e + " for host " + host.getId() + ", mangement server id is "
                        + msId + "," + e1.getMessage());
            }
        } finally {
            _agentStatusLock.unlock();
        }
    }

    public boolean disconnectAgent(HostVO host, Status.Event e, long msId) {
        host.setDisconnectedOn(new Date());
        if (e.equals(Status.Event.Remove)) {
            host.setGuid(null);
            host.setClusterId(null);
        }

        return agentStatusTransitTo(host, e, msId);
    }

    protected void disconnectWithoutInvestigation(AgentAttache attache, final Status.Event event) {
        _executor.submit(new DisconnectTask(attache, event, false));
    }

    public void disconnectWithInvestigation(AgentAttache attache, final Status.Event event) {
        _executor.submit(new DisconnectTask(attache, event, true));
    }

    private void disconnectInternal(final long hostId, final Status.Event event, boolean invstigate) {
        AgentAttache attache = findAttache(hostId);

        if (attache != null) {
            if (!invstigate) {
                disconnectWithoutInvestigation(attache, event);
            } else {
                disconnectWithInvestigation(attache, event);
            }
        } else {
            /* Agent is still in connecting process, don't allow to disconnect right away */
            if (tapLoadingAgents(hostId, TapAgentsAction.Contains)) {
                s_logger.info("Host " + hostId + " is being loaded so no disconnects needed.");
                return;
            }

            HostVO host = _hostDao.findById(hostId);
            if (host != null && host.getRemoved() == null) {
                disconnectAgent(host, event, _nodeId);
            }
        }
    }

    public void disconnectWithInvestigation(final long hostId, final Status.Event event) {
        disconnectInternal(hostId, event, true);
    }

    @Override
    public void disconnectWithoutInvestigation(final long hostId, final Status.Event event) {
        disconnectInternal(hostId, event, false);
    }

    @Override
    public AgentAttache handleDirectConnectAgent(HostVO host, StartupCommand[] cmds, ServerResource resource, boolean forRebalance) throws ConnectionException {
        AgentAttache attache;

        attache = createAttacheForDirectConnect(host, resource);
        StartupAnswer[] answers = new StartupAnswer[cmds.length];
        for (int i = 0; i < answers.length; i++) {
            answers[i] = new StartupAnswer(cmds[i], attache.getId(), _pingInterval.value());
        }
        attache.process(answers);
        attache = notifyMonitorsOfConnection(attache, cmds, forRebalance);

        return attache;
    }

    @Override
    public void pullAgentToMaintenance(long hostId) {
        AgentAttache attache = findAttache(hostId);
        if (attache != null) {
            attache.setMaintenanceMode(true);
            // Now cancel all of the commands except for the active one.
            attache.cancelAllCommands(Status.Disconnected, false);
        }
    }

    @Override
    public void pullAgentOutMaintenance(long hostId) {
        AgentAttache attache = findAttache(hostId);
        if (attache != null) {
            attache.setMaintenanceMode(false);
        }
    }

    public ScheduledExecutorService getDirectAgentPool() {
        return _directAgentExecutor;
    }

    public Long getAgentPingTime(long agentId) {
        return _pingMap.get(agentId);
    }

    public void pingBy(long agentId) {
        _pingMap.put(agentId, InaccurateClock.getTimeInSeconds());
    }

    protected class MonitorTask implements Runnable {
        @Override
        public void run() {
            s_logger.trace("Agent Monitor is started.");

            try {
                List<Long> behindAgents = findAgentsBehindOnPing();
                for (Long agentId : behindAgents) {
                    SearchCriteriaService<HostVO, HostVO> sc = SearchCriteria2.create(HostVO.class);
                    sc.addAnd(sc.getEntity().getId(), Op.EQ, agentId);
                    HostVO h = sc.find();
                    if (h != null) {
                        ResourceState resourceState = h.getResourceState();
                        if (resourceState == ResourceState.Disabled || resourceState == ResourceState.Maintenance
                                || resourceState == ResourceState.ErrorInMaintenance) {
                            /*
                             * Host is in non-operation state, so no
                             * investigation and direct put agent to
                             * Disconnected
                             */
                            status_logger.debug("Ping timeout but host " + agentId + " is in resource state of "
                                    + resourceState + ", so no investigation");
                            disconnectWithoutInvestigation(agentId, Event.ShutdownRequested);
                        } else {
                            status_logger.debug("Ping timeout for host " + agentId + ", do invstigation");
                            disconnectWithInvestigation(agentId, Event.PingTimeout);
                        }
                    }
                }

                SearchCriteriaService<HostVO, HostVO> sc = SearchCriteria2.create(HostVO.class);
                sc.addAnd(sc.getEntity().getResourceState(), Op.IN, ResourceState.PrepareForMaintenance, ResourceState.ErrorInMaintenance);
                List<HostVO> hosts = sc.list();

                for (HostVO host : hosts) {
                    long hostId = host.getId();
                    DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                    HostPodVO podVO = _podDao.findById(host.getPodId());
                    String hostDesc = "name: " + host.getName() + " (id:" + hostId + "), availability zone: " + dcVO.getName() + ", pod: " + podVO.getName();

                    if (host.getType() != Host.Type.Storage) {
//                        List<VMInstanceVO> vos = _vmDao.listByHostId(hostId);
//                        List<VMInstanceVO> vosMigrating = _vmDao.listVmsMigratingFromHost(hostId);
//                        if (vos.isEmpty() && vosMigrating.isEmpty()) {
//                            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, host.getDataCenterId(), host.getPodId(), "Migration Complete for host " + hostDesc, "Host ["
//                                    + hostDesc
//                                    + "] is ready for maintenance");
//                            _resourceMgr.resourceStateTransitTo(host, ResourceState.Event.InternalEnterMaintenance, _msId);
//                        }
                    }
                }
            } catch (Throwable th) {
                s_logger.error("Caught the following exception: ", th);
            }

            s_logger.trace("Agent Monitor is leaving the building!");
        }

        protected List<Long> findAgentsBehindOnPing() {
            List<Long> agentsBehind = new ArrayList<Long>();
            long cutoffTime = InaccurateClock.getTimeInSeconds() - getTimeout();
            for (Map.Entry<Long, Long> entry : _pingMap.entrySet()) {
                if (entry.getValue() < cutoffTime) {
                    agentsBehind.add(entry.getKey());
                }
            }

            if (agentsBehind.size() > 0) {
                s_logger.info("Found the following agents behind on ping: " + agentsBehind);
            }

            return agentsBehind;
        }
    }

    protected class BehindOnPingListener implements Listener {
        @Override
        public boolean isRecurring() {
            return true;
        }

        @Override
        public boolean processAnswers(long agentId, long seq, Answer[] answers) {
            return false;
        }

        @Override
        public boolean processCommands(long agentId, long seq, Command[] commands) {
            boolean processed = false;
            for (Command cmd : commands) {
                if (cmd instanceof PingCommand) {
                    pingBy(agentId);
                }
            }
            return processed;
        }

        @Override
        public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
            return null;
        }

        @Override
        public void processConnect(Host host, StartupCommand cmd, boolean forRebalance) {
            if (host.getType().equals(Host.Type.TrafficMonitor) ||
                    host.getType().equals(Host.Type.SecondaryStorage)) {
                return;
            }

            // NOTE: We don't use pingBy here because we're initiating.
            _pingMap.put(host.getId(), InaccurateClock.getTimeInSeconds());
        }

        @Override
        public boolean processDisconnect(long agentId, Status state) {
            _pingMap.remove(agentId);
            return true;
        }

        @Override
        public boolean processTimeout(long agentId, long seq) {
            return true;
        }

        @Override
        public int getTimeout() {
            return -1;
        }

    }

}
