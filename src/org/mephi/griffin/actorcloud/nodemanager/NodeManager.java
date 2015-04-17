/*
 * Copyright 2015 Griffin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mephi.griffin.actorcloud.nodemanager;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.AddressFromURIString;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent.ClusterMetricsChanged;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.ReachabilityEvent;
import akka.cluster.ClusterEvent.ReachableMember;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.cluster.NodeMetrics;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.mephi.griffin.actorcloud.Shutdown;
import org.mephi.griffin.actorcloud.common.ActorData;
import org.mephi.griffin.actorcloud.actormanager.ActorManager;
import org.mephi.griffin.actorcloud.actormanager.messages.ActorRefMessage;
import org.mephi.griffin.actorcloud.actormanager.messages.AllowShutdown;
import org.mephi.griffin.actorcloud.actormanager.messages.CreateActorManager;
import org.mephi.griffin.actorcloud.actormanager.messages.CreateClientActor;
import org.mephi.griffin.actorcloud.actormanager.messages.GetNodes;
import org.mephi.griffin.actorcloud.actormanager.messages.RecoverClientActor;
import org.mephi.griffin.actorcloud.actormanager.messages.SyncData;
import org.mephi.griffin.actorcloud.authentication.AuthServer;
import org.mephi.griffin.actorcloud.authentication.GetManagerNode;
import org.mephi.griffin.actorcloud.backupmanager.BackupManager;
import org.mephi.griffin.actorcloud.backupmanager.messages.Backup;
import org.mephi.griffin.actorcloud.client.ClientActorWatcher;
import org.mephi.griffin.actorcloud.client.messages.HandoffClientActor;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.InitSuccess;
import org.mephi.griffin.actorcloud.common.RegisterServer;
import org.mephi.griffin.actorcloud.common.ServerInfo;
import org.mephi.griffin.actorcloud.common.UnregisterServer;
import org.mephi.griffin.actorcloud.enqueuer.Enqueuer;
import org.mephi.griffin.actorcloud.netserver.NetServer;
import org.mephi.griffin.actorcloud.nodemanager.messages.NodeOverloaded;
import org.mephi.griffin.actorcloud.nodemanager.messages.ManagerNode;
import org.mephi.griffin.actorcloud.nodemanager.messages.ActorManagerUp;
import org.mephi.griffin.actorcloud.nodemanager.messages.ActorManagersList;
import org.mephi.griffin.actorcloud.nodemanager.messages.GetBackup;
import org.mephi.griffin.actorcloud.nodemanager.messages.NetNodesList;
import org.mephi.griffin.actorcloud.nodemanager.messages.Nodes;
import org.mephi.griffin.actorcloud.nodemanager.messages.NetServerDown;
import org.mephi.griffin.actorcloud.nodemanager.messages.NetServerUp;
import org.mephi.griffin.actorcloud.nodemanager.messages.RequestShutdown;
import org.mephi.griffin.actorcloud.storage.StorageActor;
import org.mephi.griffin.actorcloud.util.JarClassLoader;
import org.mephi.griffin.actorcloud.util.MaxHeap;

/**
 *
 * @author Griffin
 */
public class NodeManager extends UntypedActor {
	private static final int WAITING = 0;
	private static final int READY = 1;
	private static final int FAILED = 2;
	
	private LoggingAdapter logger;
	private Config config;
	private Cluster cluster;
//	private MaxHeap<MyNodeMetrics> authNodes;
	private Map<Address, ServerInfo> netServers;
	private ActorRef backupManager;
	private MaxHeap<Node> netNodes;
	private MaxHeap<Node> managerNodes;
	private MaxHeap<Node> generalNodes;
	private long memoryThreshold;
	private int cpuThreshold;
	private Address joinAddress;
	private final ClassLoader cl;
	private ActorRef actorManager;
	private boolean masterManager;
	private long managerCreateTime;
	private long halfLife;
	private ServerInfo authServer;
//	private int authState;
	private ServerInfo netServer;
	private int netState;
	private ActorRef enqueuer;
	private ActorRef storage;
	private int storageState;
	private Map<ActorRef, ActorData> deadActors;
	
	@SuppressWarnings("LeakingThisInConstructor")
	public NodeManager() {
		logger = Logging.getLogger(this);
		cluster = Cluster.get(getContext().system());
//		authNodes = new MaxHeap<>(new NodeMetricsComparator());
		netServers = new HashMap<>();
		netNodes = new MaxHeap<>(new NodeMetricsComparator());
		managerNodes = new MaxHeap<>(new NodeMetricsComparator());
		generalNodes = new MaxHeap<>(new NodeMetricsComparator());
		config = getContext().system().settings().config();
		String tmp = config.getString("actorcloud.cluster.handoff.memory-threshold");
		try {
			memoryThreshold = Long.parseLong(tmp);
		}
		catch(NumberFormatException ex) {
			if(tmp.toUpperCase().endsWith("B")) memoryThreshold = Long.parseLong(tmp.substring(0, tmp.length() - 1));
			else if(tmp.toUpperCase().endsWith("K")) memoryThreshold = Long.parseLong(tmp.substring(0, tmp.length() - 1)) * 1024;
			else if(tmp.toUpperCase().endsWith("M")) memoryThreshold = Long.parseLong(tmp.substring(0, tmp.length() - 1)) * 1048576;
			else if(tmp.toUpperCase().endsWith("G")) memoryThreshold = Long.parseLong(tmp.substring(0, tmp.length() - 1)) * 1073741824;
			else throw ex;
		}
		tmp = config.getString("actorcloud.cluster.handoff.cpu-threshold");
		try {
			cpuThreshold = Integer.parseInt(tmp);
		}
		catch(NumberFormatException ex) {
			if(tmp.endsWith("%")) cpuThreshold = Integer.parseInt(tmp.substring(0, tmp.length() - 1));
			else throw ex;
		}
		try {
			joinAddress = AddressFromURIString.parse(config.getString("actorcloud.cluster.join-to"));
		}
		catch(Exception ex) {
			joinAddress = cluster.selfAddress();
		}
		List<String> classpath = config.getStringList("actorcloud.classpath");
		List<File> dirList = new ArrayList<>();
		for(String path : classpath) {
			File dir = new File(path);
			if(dir.canRead()) dirList.add(dir);
		}
		actorManager = null;
		masterManager = false;
		managerCreateTime = 0;
		String halfLifeStr = config.getString("akka.cluster.metrics.moving-average-half-life");
		halfLife = Long.parseLong(halfLifeStr.substring(0, halfLifeStr.length() - 1));
		if(halfLifeStr.toLowerCase().endsWith("s")) halfLife *= 1000;
		else if(halfLifeStr.toLowerCase().endsWith("m")) halfLife *= 60000;
		else if(halfLifeStr.toLowerCase().endsWith("h")) halfLife *= 3600000;
		authServer = null;
		netServer = null;
		enqueuer = null;
		storage = null;
		cl = new JarClassLoader(dirList.toArray(new File[0]), ClassLoader.getSystemClassLoader());
		deadActors = new HashMap<>();
	}
	
	@Override
	public void preStart() {
		cluster.join(joinAddress);
		if(joinAddress.equals(cluster.selfAddress())) {
			actorManager = getContext().system().actorOf(Props.create(ActorManager.class, getSelf(), true), "actor-manager");
			masterManager = true;
			managerNodes.add(new Node(cluster.selfAddress()));
			cluster.subscribe(getSelf(), MemberUp.class);
		}
		storage = getContext().system().actorOf(Props.create(StorageActor.class, getSelf()), "storage");
		storageState = WAITING;
		if(cluster.getSelfRoles().contains("edge")) {
			ActorRef authServerActor = getContext().system().actorOf(Props.create(AuthServer.class, getSelf()), "auth-server");
			ActorRef netServerActor = getContext().system().actorOf(Props.create(NetServer.class, cl, getSelf()), "net-server");
//			authState = WAITING;
			netState = WAITING;
			enqueuer = getContext().system().actorOf(Props.create(Enqueuer.class), "enqueuer");
			ActorRefMessage msg = new ActorRefMessage(ActorRefMessage.ENQUEUER, enqueuer);
			logger.debug("{} -> NetServer: {}", msg.getClass().getSimpleName(), msg);
			netServerActor.tell(msg, getSelf());
			msg = new ActorRefMessage(ActorRefMessage.NET, netServerActor);
			logger.debug("{} -> Enqueuer: {}", msg.getClass().getSimpleName(), msg);
			enqueuer.tell(msg, getSelf());
		}
		cluster.subscribe(getSelf(), MemberRemoved.class, ReachabilityEvent.class, ClusterMetricsChanged.class);
	}
	
	@Override
	public void postStop() {
		cluster.unsubscribe(getSelf());
		cluster.leave(cluster.selfAddress());
	}
	
	@Override
	public void onReceive(Object message) {
		if(getContext().system().settings().AddLoggingReceive())
			logger.debug(" <- {}: {} from {}", message.getClass().getSimpleName(), message, getSender());
		if(message instanceof Shutdown) {
			getContext().actorSelection(managerNodes.getMax().getAddress() + "/user/actor-manager").tell(new RequestShutdown(cluster.selfAddress()), getSelf());
		}
		if(message instanceof AllowShutdown) {
			cluster.leave(cluster.selfAddress());
		}
		else if(message instanceof InitSuccess) {
			InitSuccess is = (InitSuccess) message;
			switch(is.getType()) {
				case InitSuccess.STORAGE:
					storageState = READY;
					backupManager = getContext().actorOf(Props.create(BackupManager.class, storage), "backup-manager");
					if(authServer != null) {
						ActorRefMessage msg = new ActorRefMessage(ActorRefMessage.STORAGE, storage);
						logger.debug("{} -> AuthServer: {}", msg.getClass().getName(), msg);
						authServer.getRef().tell(msg, getSelf());
					}
					break;
			}
		}
		else if(message instanceof RegisterServer) {
			RegisterServer rs = (RegisterServer) message;
			switch(rs.getType()) {
				case RegisterServer.AUTH:
					logger.debug("Registered authentication server: {}", rs.getInfo());
					authServer = rs.getInfo();
//					authState = READY;
					if(storageState != WAITING) {
						ActorRefMessage msg = new ActorRefMessage(ActorRefMessage.STORAGE, storage);
						logger.debug("{} -> AuthServer: {}", msg.getClass().getSimpleName(), msg);
						authServer.getRef().tell(msg, getSelf());
					}
					break;
				case RegisterServer.NET:
					logger.debug("Registered network server: {}", rs.getInfo());
					netServer = rs.getInfo();
					netState = READY;
					if(!managerNodes.isEmpty())
						for(Node node : managerNodes) {
							logger.debug("NetServerUp -> {}", node);
							getContext().actorSelection(node.getAddress() + "/user/node-manager").tell(new NetServerUp(cluster.selfAddress(), rs.getInfo()), getSelf());
						}
					break;
			}
		}
		else if(message instanceof UnregisterServer) {
			UnregisterServer us = (UnregisterServer) message;
			switch(us.getType()) {
				case UnregisterServer.AUTH:
					logger.debug("Unregistered authentication server: {}", authServer);
//					authState = FAILED;
					authServer = null;
					break;
				case UnregisterServer.NET:
					logger.debug("Unregistered network server: {}", netServer);
					netState = FAILED;
					netServer = null;
					if(!managerNodes.isEmpty())
						for(Node node : managerNodes)
							getContext().actorSelection(node.getAddress() + "/user/node-manager").tell(new NetServerDown(), getSelf());
					break;
			}
		}
		else if(message instanceof InitFail) {
			InitFail ifd = (InitFail) message;
			switch(ifd.getType()) {
				case InitFail.AUTH:
//					authState = FAILED;
					break;
				case InitFail.NET:
					netState = FAILED;
					break;
				case InitFail.STORAGE:
					storageState = FAILED;
					storage = null;
					if(authServer != null) {
						ActorRefMessage msg = new ActorRefMessage(ActorRefMessage.STORAGE, null);
						logger.debug("{} -> AuthServer: {}", msg.getClass().getName(), msg);
						authServer.getRef().tell(msg, getSelf());
					}
					break;
			}
		}
		else if(message instanceof ActorManagersList) {
			ActorManagersList aml = (ActorManagersList) message;
			for(Address address : aml.getList())
				managerNodes.add(new Node(address));
			if(netState == READY) {
				for(Node node : managerNodes)
					getContext().actorSelection(node.getAddress() + "/user/node-manager").tell(new NetServerUp(cluster.selfAddress(), netServer), getSelf());
			}
		}
		else if(message instanceof NetNodesList) {
			NetNodesList nnl = (NetNodesList) message;
			for(int i = 0; i < generalNodes.size(); i++) {
				if(nnl.getList().contains(generalNodes.get(i).getAddress())) {
					nnl.getList().remove(generalNodes.get(i).getAddress());
					netNodes.add(generalNodes.remove(i--));
				}
			}
			generalNodes.buildHeap();
			for(Node node : managerNodes) {
				if(nnl.getList().contains(node.getAddress())) {
					nnl.getList().remove(node.getAddress());
					netNodes.add(node.getCopy());
				}
			}
			for(Address node : nnl.getList()) {
				netNodes.add(new Node(node));
			}
		}
		else if(message instanceof ActorManagerUp) {
			Address sourceNode = getSender().path().address();
			boolean found = false;
			for(int i = 0; i < generalNodes.size(); i++) {
				if(generalNodes.get(i).getAddress().equals(sourceNode)) {
					found = true;
					managerNodes.add(generalNodes.remove(i--));
					break;
				}
			}
			if(found) generalNodes.buildHeap();
			if(!found)
				for(Node netNode : netNodes) {
					if (netNode.getAddress().equals(getSender().path().address())) {
						found = true;
						managerNodes.add(netNode.getCopy());
						break;
					}
				}
			if(!found) managerNodes.add(new Node(getSender().path().address()));
		}
		else if(message instanceof NetServerUp) {
			NetServerUp nsu = (NetServerUp) message;
			Address sourceNode = nsu.getNode();
			boolean found = false;
			for(int i = 0; i < generalNodes.size(); i++) {
				if(generalNodes.get(i).getAddress().equals(sourceNode)) {
					netNodes.add(generalNodes.remove(i--));
					found = true;
					break;
				}
			}
			if(!found) netNodes.add(new Node(sourceNode));
			else generalNodes.buildHeap();
			netServers.put(sourceNode, nsu.getInfo());
		}
		else if(message instanceof NetServerDown) {
			Address sourceNode = getSender().path().address();
			boolean found = false;
			for(Node node : managerNodes) {
				if(node.getAddress().equals(sourceNode))
					found = true;
			}
			for(int i = 0; i < netNodes.size(); i++) {
				if(netNodes.get(i).getAddress().equals(sourceNode)) {
					if(found) netNodes.remove(i);
					else generalNodes.add(netNodes.remove(i));
					break;
				}
			}
			netNodes.buildHeap();
			netServers.remove(sourceNode);
			if(netServers.isEmpty());
				//warning cluster not visible to clients
		}
//		else if(message instanceof CurrentClusterState) {
//			CurrentClusterState ccs = (CurrentClusterState) message;
//			if(leader == null || !leader.equals(ccs.getLeader())) {
//				leader = ccs.getLeader();
//				if(leader.equals(cluster.selfAddress())) {
//					cluster.subscribe(getSelf(), MemberUp.class);
//				}
//			}
//		}
		else if(message instanceof MemberUp) {
			MemberUp mu = (MemberUp) message;
			if(!mu.member().address().equals(cluster.selfAddress())) {
				generalNodes.add(new Node(mu.member().address()));
				if(masterManager) {
					getContext().actorSelection(mu.member().address() + "/user/node-manager").tell(new ActorManagersList(managerNodes), getSelf());
				}
			}
		}
		else if(message instanceof MemberRemoved) {
			MemberRemoved mr = (MemberRemoved) message;
			boolean found = false;
			for(int i = 0; i < managerNodes.size(); i++)
				if(managerNodes.get(i).getAddress().equals(mr.member().address())) {
					managerNodes.remove(i);
					found = true;
					break;
				}
			managerNodes.buildHeap();
			if(actorManager != null) {
				for(int i = 0; i < netNodes.size(); i++)
					if(netNodes.get(i).getAddress().equals(mr.member().address())) {
						Address node = netNodes.remove(i).getAddress();
						netServers.remove(node);
						found = true;
						break;
					}
				netNodes.buildHeap();
				for(int i = 0; i < generalNodes.size(); i++)
					if(generalNodes.get(i).getAddress().equals(mr.member().address())) {
						generalNodes.remove(i);
						if(found); //consistency error
						found = true;
						break;
					}
				generalNodes.buildHeap();
			}
			if(!found); //consistency error
			if(mr.previousStatus().equals(MemberStatus.down()) && managerNodes.isEmpty()) {
				//recover after network partition
			}
			if(netNodes.isEmpty()) {
				//warning cluster not visible to clients
			}
			if(mr.member().address().equals(cluster.selfAddress())) {
				getContext().system().shutdown();
			}
		}
		else if(message instanceof ReachableMember) {
			ReachableMember rm = (ReachableMember) message;
			boolean found = false;
			for(Node managerNode : managerNodes) {
				if(managerNode.getAddress().equals(rm.member().address())) {
					managerNode.setReachable(true);
					found = true;
					break;
				}
			}
			if(actorManager != null) {
				for(Node netNode : netNodes)
					if(netNode.getAddress().equals(rm.member().address())) {
						netNode.setReachable(true);
						found = true;
						break;
					}
				for(Node generalNode : generalNodes)
					if(generalNode.getAddress().equals(rm.member().address())) {
						generalNode.setReachable(true);
						if(found); //consistency error
						found = true;
						break;
					}
				if(!found); //consistency error
			}
		}
		else if(message instanceof UnreachableMember) {
			UnreachableMember um = (UnreachableMember) message;
			boolean found = false;
			for(Node managerNode : managerNodes) {
				if(managerNode.getAddress().equals(um.member().address())) {
					managerNode.setReachable(false);
					found = true;
					break;
				}
			}
			if(actorManager != null) {
				for(Node netNode : netNodes)
					if(netNode.getAddress().equals(um.member().address())) {
						netNode.setReachable(false);
						found = true;
						break;
					}
				for(Node generalNode : generalNodes)
					if(generalNode.getAddress().equals(um.member().address())) {
						generalNode.setReachable(false);
						if(found); //consistency error
						found = true;
						break;
					}
				if(!found); //consistency error
			}
		}
		else if(message instanceof ClusterMetricsChanged) {
			ClusterMetricsChanged cmc = (ClusterMetricsChanged) message;
			Map<Address, NodeMetrics> newNodeMetrics = new HashMap<>();
			for(NodeMetrics metrics : cmc.getNodeMetrics()) newNodeMetrics.put(metrics.address(), metrics);
			if(actorManager != null) {
				for(Node generalNode : generalNodes) {
					NodeMetrics newMetrics = newNodeMetrics.get(generalNode.getAddress());
					if(newMetrics != null) {
						generalNode.updateMetrics(newMetrics.getMetrics());
						if(generalNode.getMetric("heap-memory-free").getAverage() < memoryThreshold || generalNode.getMetric("system-cpu-load") != null && generalNode.getMetric("system-cpu-load").getAverage() * 100 > cpuThreshold) {
							actorManager.tell(new NodeOverloaded(NodeOverloaded.GENERAL, generalNode.getAddress(), getFreeNode(false)), getSelf());
						}
					}
				}
				generalNodes.buildHeap();
				for(Node netNode : netNodes) {
					NodeMetrics newMetrics = newNodeMetrics.get(netNode.getAddress());
					if(newMetrics != null) {
						netNode.updateMetrics(newMetrics.getMetrics());
						if(netNode.getMetric("heap-memory-free").getAverage() < memoryThreshold || netNode.getMetric("system-cpu-load") != null && netNode.getMetric("system-cpu-load").getAverage() * 100 > cpuThreshold) {
							actorManager.tell(new NodeOverloaded(NodeOverloaded.NET, netNode.getAddress(), getFreeNode(false)), getSelf());
						}
					}
				}
				netNodes.buildHeap();
			}
			for(Node managerNode : managerNodes) {
				NodeMetrics newMetrics = newNodeMetrics.get(managerNode.getAddress());
				if(newMetrics != null) {
					managerNode.updateMetrics(newMetrics.getMetrics());
					if(actorManager != null && (managerNode.getMetric("heap-memory-free").getAverage() < memoryThreshold || managerNode.getMetric("system-cpu-load") != null && managerNode.getMetric("system-cpu-load").getAverage() * 100 > cpuThreshold)) {
						actorManager.tell(new NodeOverloaded(NodeOverloaded.MANAGER, managerNode.getAddress(), getFreeNode(false)), getSelf());
					}
				}
			}
			managerNodes.buildHeap();
			if(actorManager != null) logMetrics();
		}
		else if(message instanceof CreateActorManager) {
			if(getSender().equals(actorManager)) {
				if(System.currentTimeMillis() - managerCreateTime > halfLife * 10) {
					Address freeNode = getFreeNode(true);
					if(freeNode != null) {
						getContext().actorSelection(freeNode + "/user/node-manager").tell(new CreateActorManager(), getSelf());
						getContext().actorSelection(freeNode + "/user/node-manager").tell(new NetNodesList(netNodes), getSelf());
						managerCreateTime = System.currentTimeMillis();
					}
				}
			}
			else {
				actorManager = getContext().system().actorOf(Props.create(ActorManager.class, getSelf(), false), "actor-manager");
				masterManager = false;
				managerNodes.add(new Node(cluster.selfAddress()));
				for(Member member : cluster.state().getMembers()) {
					if(!member.address().equals(cluster.selfAddress()))
						getContext().actorSelection(member.address() + "/user/node-manager").tell(new ActorManagerUp(), getSelf());
					boolean found = false;
					for(Node node : managerNodes)
						if(node.getAddress().equals(member.address())) {
							found = true;
							break;
						}
					if(!found) generalNodes.add(new Node(member.address()));
				}
				cluster.subscribe(getSelf(), MemberUp.class);
			}
		}
		else if(message instanceof GetManagerNode) {
			ManagerNode msg;
			if(managerNodes.isEmpty()) msg = new ManagerNode(null);
			else msg = new ManagerNode(managerNodes.getMax().getAddress());
			getSender().tell(msg, getSelf());
		}
		else if(message instanceof GetNodes) {
			Address netNode;
			ServerInfo netSrv;
			if(netNodes.isEmpty()) {
				netNode = null;
				netSrv = null;
			}
			else {
				netNode = netNodes.getMax().getAddress();
				netSrv = netServers.get(netNode);
			}
			Address generalNode = getFreeNode(false);
			Nodes msg = new Nodes(generalNode, netNode, netSrv);
			getSender().tell(msg, getSelf());
		}
		else if(message instanceof CreateClientActor) {
			CreateClientActor cca = (CreateClientActor) message;
			String name = cca.getClient() + "-" + cca.getAuthServer().path().address().host().get() + "-" + cca.getAuthServer().path().address().port().get() + "-" + cca.getAuthSessionId();
			getContext().system().actorOf(Props.create(ClientActorWatcher.class, cl, storage, backupManager, cca.getMessageHandler(), cca.getChildHandler(), cca.getClient(), getSender(), cca.getAuthServer(), cca.getAuthSessionId()), name);
		}
		else if(message instanceof HandoffClientActor) {
			HandoffClientActor hca = (HandoffClientActor) message;
			String name = getSender().path().name();
			getContext().system().actorOf(Props.create(ClientActorWatcher.class, cl, storage, backupManager, hca.getMessageHandler(), hca.getChildHandler(), hca.getClient(), hca.getActorManager(), getSender(), hca.getWatcherSnapshot()), name);
		}
		else if(message instanceof RecoverClientActor) {
			RecoverClientActor rca = (RecoverClientActor) message;
			backupManager.tell(new GetBackup(rca.getActor()), getSelf());
			deadActors.put(rca.getActor(), new ActorData(rca.getClient(), rca.getMessageHandler(), rca.getChildHandler(), getSender()));
		}
		else if(message instanceof Backup) {
			Backup b = (Backup) message;
			ActorData ad = deadActors.get(b.getActor());
			getContext().system().actorOf(Props.create(ClientActorWatcher.class, cl, storage, backupManager, ad.getMessageHandler(), ad.getChildHandler(), ad.getClient(), ad.getActorManager(), b.getActor(), b.getWatcherSnapshot(), b.getMessages(), b.getActorSnapshot()), b.getActor().path().name());
		}
		else if(message instanceof SyncData) {
			for(Node managerNode : managerNodes) {
				if(managerNode.isReachable())
					getContext().actorSelection(managerNode.getAddress() + "/user/actor-manager").tell(message, getSender());
			}
		}
		else unhandled(message);
	}
	
	private Address getFreeNode(boolean forManager) {
		Address freeNode = getFreeNode(generalNodes);
		if(freeNode != null) return freeNode;
		freeNode = getFreeNode(netNodes);
		if(freeNode != null) return freeNode;
		if(forManager) return null;
		return getFreeNode(managerNodes);
	}
	
	private Address getFreeNode(MaxHeap<Node> nodes) {
		if(nodes.isEmpty()) return null;
		Node mostFreeNode = nodes.getMax();
		if(mostFreeNode.getMetrics().isEmpty()) return null;
		if(mostFreeNode.getMetric("heap-memory-free").getAverage() < memoryThreshold || mostFreeNode.getMetric("system-cpu-load") != null && mostFreeNode.getMetric("system-cpu-load").getAverage() * 100 > cpuThreshold)
			return null;
		else return mostFreeNode.getAddress();
	}
	
	private void logMetrics() {
		String str = "Manager nodes:\n";
		Iterator<Node> iterator1 = managerNodes.iterator();
		while(iterator1.hasNext()) {
			Node metrics = iterator1.next();
			str += metrics.getAddress() + ": ";
			Iterator<Entry<String, MyMetric>> iterator2 = metrics.getMetrics().entrySet().iterator();
			while(iterator2.hasNext()) {
				Entry<String, MyMetric> metric = iterator2.next();
				str += metric.getKey() + ": ";
				switch (metric.getKey()) {
					case "processors":
						str += String.format("%1$2.0f", metric.getValue().getAverage());
						break;
					case "system-cpu-load":
						str += String.format("%1$3.0f%%", metric.getValue().getAverage() * 100);
						break;
					default:
						if(metric.getValue().getAverage() >= 1073741824) str += String.format("%1$6.2f GB", metric.getValue().getAverage() / 1073741824);
						else if(metric.getValue().getAverage() >= 1048576) str += String.format("%1$6.2f MB", metric.getValue().getAverage() / 1048576);
						else if(metric.getValue().getAverage() >= 1024) str += String.format("%1$6.2f KB", metric.getValue().getAverage() / 1024);
						else str += String.format("%1$6.0f B", metric.getValue().getAverage());
						break;
				}
				if(iterator2.hasNext()) str += ", ";
			}
			str += "\n";
		}
		if(actorManager != null) {
			str += "Network server nodes:\n";
			iterator1 = netNodes.iterator();
			while(iterator1.hasNext()) {
				Node metrics = iterator1.next();
				str += metrics.getAddress() + ": ";
				Iterator<Entry<String, MyMetric>> iterator2 = metrics.getMetrics().entrySet().iterator();
				while(iterator2.hasNext()) {
					Entry<String, MyMetric> metric = iterator2.next();
					str += metric.getKey() + ": ";
					switch (metric.getKey()) {
						case "processors":
							str += String.format("%1$2.0f", metric.getValue().getAverage());
							break;
						case "system-cpu-load":
							str += String.format("%1$3.0f%%", metric.getValue().getAverage() * 100);
							break;
						default:
							if(metric.getValue().getAverage() >= 1073741824) str += String.format("%1$6.2f GB", metric.getValue().getAverage() / 1073741824);
							else if(metric.getValue().getAverage() >= 1048576) str += String.format("%1$6.2f MB", metric.getValue().getAverage() / 1048576);
							else if(metric.getValue().getAverage() >= 1024) str += String.format("%1$6.2f KB", metric.getValue().getAverage() / 1024);
							else str += String.format("%1$6.0f B", metric.getValue().getAverage());
							break;
					}
					if(iterator2.hasNext()) str += ", ";
				}
				str += "\n";
			}
			str += "General nodes:\n";
			iterator1 = generalNodes.iterator();
			while(iterator1.hasNext()) {
				Node metrics = iterator1.next();
				str += metrics.getAddress() + ": ";
				Iterator<Entry<String, MyMetric>> iterator2 = metrics.getMetrics().entrySet().iterator();
				while(iterator2.hasNext()) {
					Entry<String, MyMetric> metric = iterator2.next();
					str += metric.getKey() + ": ";
					switch (metric.getKey()) {
						case "processors":
							str += String.format("%1$2.0f", metric.getValue().getAverage());
							break;
						case "system-cpu-load":
							str += String.format("%1$3.0f%%", metric.getValue().getAverage() * 100);
							break;
						default:
							if(metric.getValue().getAverage() >= 1073741824) str += String.format("%1$6.2f GB", metric.getValue().getAverage() / 1073741824);
							else if(metric.getValue().getAverage() >= 1048576) str += String.format("%1$6.2f MB", metric.getValue().getAverage() / 1048576);
							else if(metric.getValue().getAverage() >= 1024) str += String.format("%1$6.2f KB", metric.getValue().getAverage() / 1024);
							else str += String.format("%1$6.0f B", metric.getValue().getAverage());
							break;
					}
					if(iterator2.hasNext()) str += ", ";
				}
				str += "\n";
			}
		}
		logger.info("Cluster metrics:\n{}", str);
	}
}
