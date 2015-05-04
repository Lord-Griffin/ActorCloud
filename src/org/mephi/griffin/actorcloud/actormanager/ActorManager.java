/*
 * Copyright 2014 Griffin.
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
package org.mephi.griffin.actorcloud.actormanager;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent.MemberRemoved;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.actormanager.messages.ActorHandedOff;
import org.mephi.griffin.actorcloud.actormanager.messages.ActorRecovered;
import org.mephi.griffin.actorcloud.actormanager.messages.ActorStopped;
import org.mephi.griffin.actorcloud.actormanager.messages.AllowConnection;
import org.mephi.griffin.actorcloud.actormanager.messages.AllowShutdown;
import org.mephi.griffin.actorcloud.actormanager.messages.AuthConfirmation;
import org.mephi.griffin.actorcloud.actormanager.messages.AuthDecline;
import org.mephi.griffin.actorcloud.actormanager.messages.CreateActorManager;
import org.mephi.griffin.actorcloud.actormanager.messages.CreateClientActor;
import org.mephi.griffin.actorcloud.actormanager.messages.GetNodes;
import org.mephi.griffin.actorcloud.actormanager.messages.Handoff;
import org.mephi.griffin.actorcloud.actormanager.messages.RecoverClientActor;
import org.mephi.griffin.actorcloud.actormanager.messages.SyncData;
import org.mephi.griffin.actorcloud.actormanager.messages.SyncTime;
import org.mephi.griffin.actorcloud.authentication.messages.ClientAuthenticated;
import org.mephi.griffin.actorcloud.client.messages.HandoffFail;
import org.mephi.griffin.actorcloud.client.messages.HandoffSuccess;
import org.mephi.griffin.actorcloud.client.messages.RecoveryFail;
import org.mephi.griffin.actorcloud.client.messages.RecoverySuccess;
import org.mephi.griffin.actorcloud.common.ActorData;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.InitSuccess;
import org.mephi.griffin.actorcloud.dispatcher.messages.AllowConfirmation;
import org.mephi.griffin.actorcloud.dispatcher.messages.ClientDisconnected;
import org.mephi.griffin.actorcloud.nodemanager.messages.NodeOverloaded;
import org.mephi.griffin.actorcloud.nodemanager.messages.Nodes;
import org.mephi.griffin.actorcloud.nodemanager.messages.RequestShutdown;


/**
 *
 * @author Griffin
 */
public class ActorManager extends UntypedActor {
	private static final int NODES_WAITING = 1;
	private static final int READY = 2;
	
	private static final Logger logger = Logger.getLogger(ActorManager.class.getName());
	private final SecureRandom random;
	private final Cluster cluster;
	private final ActorRef nodeManager;
	private boolean master;
	private int state;
	private final Map<String, ClientData> clients;
	private final Map<String, ClientData> newClients;
	private final Map<ActorRef, ActorData> deadActors;
	private final Map<Address, Map<ActorRef, ActorData>> shutdownRequests;
	
	public ActorManager(ActorRef nodeManager, boolean master) {
		logger.entering("Manager", "Constructor");
		random = new SecureRandom();
		cluster = Cluster.get(getContext().system());
		this.nodeManager = nodeManager;
		this.master = master;
		state = READY;
		clients = new HashMap<>();
		newClients = new HashMap<>();
		deadActors = new HashMap<>();
		shutdownRequests = new HashMap<>();
		logger.exiting("Manager", "Constructor");
	}
	
	@Override
	public void preStart() {
		logger.entering("Manager", "preStart");
		if(master) cluster.subscribe(getSelf(), MemberRemoved.class);
		logger.logp(Level.INFO, "Manager", "preStart", "Actor manager is started");
		logger.exiting("Manager", "preStart");
	}
	
	@Override
	public void postStop() {
		logger.entering("Manager", "postStop");
		String dump = "Data dump:\n";
		switch(state) {
			case NODES_WAITING:
				dump += "  state NODES_WAITING\n";
				break;
			case READY:
				dump += "  state READY\n";
		}
		dump += "  clients:\n";
		for(Entry<String, ClientData> entry : clients.entrySet()) {
			dump += "    " + entry.getKey() + ":\n";
			dump += entry.getValue().getDump();
		}
		dump += "  newClients:\n";
		for(Entry<String, ClientData> entry : clients.entrySet()) {
			dump += "    " + entry.getKey() + ":\n";
			dump += entry.getValue().getDump();
		}
		dump += "  deadActors:\n";
		for(Entry<ActorRef, ActorData> entry : deadActors.entrySet()) {
			dump += "    " + entry.getKey() + ":\n";
			dump += entry.getValue().getDump();
		}
		logger.logp(Level.FINEST, "Manager", "postStop", dump);
		logger.logp(Level.INFO, "Manager", "postStop", "Actor manager is stoppped");
		logger.exiting("Manager", "postStop");
	}
	
	/**
	 *
	 * @param message
	 */
	@Override
	@SuppressWarnings("null")
	public void onReceive(Object message) {
		logger.entering("Manager", "onReceive");
		if(getContext().system().settings().AddLoggingReceive())
			logger.logp(Level.FINER, "Manager", "onReceive", "Received {0} {1} from {2}", new Object[]{message.getClass().getName(), message, getSender()});
		if(message instanceof RequestShutdown) {
			RequestShutdown rs = (RequestShutdown) message;
			boolean found = false;
			for(Entry<String, ClientData> entry : newClients.entrySet()) {
				for(SessionData sd : entry.getValue().getSessions()) {
					if(sd.getActorNode().equals(rs.getNode())) {
						found = true;
						Map<ActorRef, ActorData> request = shutdownRequests.get(rs.getNode());
						if(request == null) {
							request = new HashMap<>();
							shutdownRequests.put(rs.getNode(), request);
						}
						request.put(sd.getActor(), new ActorData(entry.getKey(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler()));
					}
				}
			}
			for(Entry<String, ClientData> entry : clients.entrySet()) {
				for(SessionData sd : entry.getValue().getSessions()) {
					if(sd.getActorNode().equals(rs.getNode())) {
						found = true;
						Map<ActorRef, ActorData> request = shutdownRequests.get(rs.getNode());
						if(request == null) {
							request = new HashMap<>();
							shutdownRequests.put(rs.getNode(), request);
						}
						request.put(sd.getActor(), new ActorData(entry.getKey(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler()));
					}
				}
			}
			if(found && state == READY) {
				nodeManager.tell(new GetNodes(), getSelf());
				state = NODES_WAITING;
			}
			else if(!found) {
				getSender().tell(new AllowShutdown(), getSelf());
			}
		}
		else if(message instanceof NodeOverloaded) {
			if(master) {
				NodeOverloaded no = (NodeOverloaded) message;
				boolean handoffSuccess = false;
				for(Entry<String, ClientData> entry : newClients.entrySet()) {
					for(SessionData sd : entry.getValue().getSessions()) {
						if(sd.getActorNode().equals(no.getOverloadedNode())) {
							sd.getActor().tell(new Handoff(no.getFreeNode(), entry.getKey(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler()), getSelf());
							handoffSuccess = true;
							break;
						}
					}
				}
				if(!handoffSuccess) {
					for(Entry<String, ClientData> entry : clients.entrySet()) {
						for(SessionData sd : entry.getValue().getSessions()) {
							if(sd.getActorNode().equals(no.getOverloadedNode())) {
								sd.getActor().tell(new Handoff(no.getFreeNode(), entry.getKey(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler()), getSelf());
								handoffSuccess = true;
								break;
							}
						}
					}
				}
				if(no.getType() == NodeOverloaded.MANAGER && !handoffSuccess)
					nodeManager.tell(new CreateActorManager(), getSelf());
			}
		}
		else if(message instanceof MemberRemoved) {
			MemberRemoved mr = (MemberRemoved) message;
			Address node = mr.member().address();
			boolean found = false;
			for(Entry<String, ClientData> entry : newClients.entrySet()) {
				for(AuthData ad : entry.getValue().getAuthData()) {
					if(ad.getActorNode() != null && ad.getActorNode().equals(node)) {
						switch(ad.getState()) {
							case AuthData.ACTOR_WAITING:
								ad.setActor(null);
								ad.setActorNode(null);
								ad.setState(AuthData.NODE_WAITING);
								found = true;
								break;
							case AuthData.READY:
								deadActors.put(ad.getActor(), new ActorData(entry.getKey(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler()));
								found = true;
								break;
						}
					}
					if(ad.getNetNode() != null && ad.getNetNode().equals(node)) {
						switch(ad.getState()) {
							case AuthData.ACTOR_WAITING:
								ad.setNetNode(null);
								found = true;
								break;
							case AuthData.READY:
								entry.getValue().getAuthData().remove(ad);
								found = true;
								break;
						}
					}
				}
				for(SessionData sd : entry.getValue().getSessions()) {
					if(sd.getActorNode().equals(node)) {
						deadActors.put(sd.getActor(), new ActorData(entry.getKey(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler()));
						found = true;
					}
					if(sd.getNetNode().equals(node)) {
						sd.getActor().tell(new ClientDisconnected(), nodeManager);
					}
				}
			}
			for(Entry<String, ClientData> entry : clients.entrySet()) {
				for(AuthData ad : entry.getValue().getAuthData()) {
					if(ad.getActorNode() != null && ad.getActorNode().equals(node)) {
						switch(ad.getState()) {
							case AuthData.ACTOR_WAITING:
								ad.setActor(null);
								ad.setActorNode(null);
								ad.setState(AuthData.NODE_WAITING);
								found = true;
								break;
							case AuthData.READY:
								deadActors.put(ad.getActor(), new ActorData(entry.getKey(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler()));
								found = true;
								break;
						}
					}
					if(ad.getNetNode() != null && ad.getNetNode().equals(node)) {
						switch(ad.getState()) {
							case AuthData.ACTOR_WAITING:
								ad.setNetNode(null);
								found = true;
								break;
							case AuthData.READY:
								entry.getValue().getAuthData().remove(ad);
								found = true;
								break;
						}
					}
				}
				for(SessionData sd : entry.getValue().getSessions()) {
					if(sd.getActorNode().equals(node)) {
						deadActors.put(sd.getActor(), new ActorData(entry.getKey(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler()));
						found = true;
					}
					if(sd.getNetNode().equals(node)) {
						sd.getActor().tell(new ClientDisconnected(), nodeManager);
					}
				}
			}
			logger.logp(Level.FINEST, "Manager", "onReceive", deadActors.toString());
			if(found && state == READY) {
				nodeManager.tell(new GetNodes(), getSelf());
				state = NODES_WAITING;
			}
		}
		else if(message instanceof ClientAuthenticated) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- ClientAuthenticated: " + message);
			ClientAuthenticated ca = (ClientAuthenticated) message;
			String client = ca.getLogin();
			if(ca.getActor().equals("")) {
				if(!clients.containsKey(client)) {
					if(!newClients.containsKey(client)) {
						ClientData cd = new ClientData(ca.getMessageHandler(), ca.getChildHandler(), ca.getMaxSessions());
						cd.addAuthData(ca.getAddress(), ca.getSessionId(), getSender());
						clients.put(client, cd);
					}
					else {
						newClients.get(client).addAuthData(ca.getAddress(), ca.getSessionId(), getSender());
					}
				}
				else {
					clients.get(client).addAuthData(ca.getAddress(), ca.getSessionId(), getSender());
				}
			}
			else {
				boolean found = false;
				ClientData cd = clients.get(client);
				if(cd != null) {
					for(SessionData sd : cd.getSessions()) {
						if(sd.getActor().toString().equals(ca.getActor())) {
							found = true;
							cd.addAuthData(ca.getAddress(), ca.getSessionId(), getSender(), sd.getActor(), sd.getActorNode());
							cd.getSessions().remove(sd);
							break;
						}
					}
				}
				if(!found) {
					cd = newClients.get(client);
					if(cd != null) {
						for(SessionData sd : cd.getSessions()) {
							if(sd.getActor().toString().equals(ca.getActor())) {
								found = true;
								cd.addAuthData(ca.getAddress(), ca.getSessionId(), getSender(), sd.getActor(), sd.getActorNode());
								cd.getSessions().remove(sd);
								break;
							}
						}
					}
				}
				if(!found) {
					//error
				}
			}
			if(state == READY) {
				nodeManager.tell(new GetNodes(), getSelf());
				state = NODES_WAITING;
			}
		}
		else if(message instanceof Nodes) {
			Nodes n = (Nodes) message;
			state = READY;
			logger.finest(shutdownRequests.toString());
			logger.finest(n.getGeneralNode().toString());
			for(Entry<Address, Map<ActorRef, ActorData>> entry1 : shutdownRequests.entrySet()) {
				if(!entry1.getKey().equals(n.getGeneralNode())) {
					for(Entry<ActorRef, ActorData> entry2 : entry1.getValue().entrySet()) {
						entry2.getKey().tell(new Handoff(n.getGeneralNode(), entry2.getValue().getClient(), entry2.getValue().getMessageHandler(), entry2.getValue().getChildHandler()), getSelf());
					}
				}
				else if(state == READY) {
					nodeManager.tell(new GetNodes(), getSelf());
					state = NODES_WAITING;
				}
			}
			for(Entry<ActorRef, ActorData> entry : deadActors.entrySet()) {
				getContext().actorSelection(n.getGeneralNode() + "/user/node-manager").tell(new RecoverClientActor(entry.getKey(), entry.getValue().getClient(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler()), getSelf());
			}
			for(Entry<String, ClientData> entry : newClients.entrySet()) {
				for(AuthData ad : entry.getValue().getAuthData()) {
					if(ad.getState() == AuthData.NODE_WAITING) {
						getContext().actorSelection(n.getGeneralNode() + "/user/node-manager").tell(new CreateClientActor(entry.getKey(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler(), ad.getAuthServer(), ad.getAuthSessionId()), getSelf());
						ad.setState(AuthData.ACTOR_WAITING);
						ad.setActorNode(n.getGeneralNode());
						ad.setNetNode(n.getNetNode());
						ad.setNetServer(n.getNetServer());
					}
					else if(ad.getState() == AuthData.NET_NODE_WAITING) {
						ad.setState(AuthData.READY);
						ad.setNetNode(n.getNetNode());
						ad.setNetServer(n.getNetServer());
						String token = (new BigInteger(130, random)).toString(32);
						ad.setToken(token);
						logger.logp(Level.FINER, "Manager", "onReceive", "Generated token for session " + ad);
						AllowConnection ac = new AllowConnection(entry.getKey(), ad.getActor(), token, ad.getAddress());
						logger.logp(Level.FINER, "Manager", "onReceive", "AllowConnection -> Dispatcher: " + ac);
						getContext().actorSelection(ad.getNetNode() + "/user/dispatcher").tell(ac, getSelf());
					}
				}
			}
			for(Entry<String, ClientData> entry : clients.entrySet()) {
				for(AuthData ad : entry.getValue().getAuthData()) {
					if(ad.getState() == AuthData.NODE_WAITING) {
						getContext().actorSelection(n.getGeneralNode() + "/user/node-manager").tell(new CreateClientActor(entry.getKey(), entry.getValue().getMessageHandler(), entry.getValue().getChildHandler(), ad.getAuthServer(), ad.getAuthSessionId()), getSelf());
						ad.setState(AuthData.ACTOR_WAITING);
						ad.setActorNode(n.getGeneralNode());
						ad.setNetNode(n.getNetNode());
						ad.setNetServer(n.getNetServer());
					}
					else if(ad.getState() == AuthData.NET_NODE_WAITING) {
						ad.setState(AuthData.READY);
						ad.setNetNode(n.getNetNode());
						ad.setNetServer(n.getNetServer());
						String token = (new BigInteger(130, random)).toString(32);
						ad.setToken(token);
						logger.logp(Level.FINER, "Manager", "onReceive", "Generated token for session " + ad);
						AllowConnection ac = new AllowConnection(entry.getKey(), ad.getActor(), token, ad.getAddress());
						logger.logp(Level.FINER, "Manager", "onReceive", "AllowConnection -> Dispatcher: " + ac);
						getContext().actorSelection(ad.getNetNode() + "/user/dispatcher").tell(ac, getSelf());
					}
				}
			}
		}
		else if(message instanceof InitSuccess) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- InitSuccess: " + message);
			InitSuccess is = (InitSuccess) message;
			switch(is.getType()) {
				case InitSuccess.CLIENT:
					String client = is.getName();
					ClientData cd = newClients.get(is.getName());
					AuthData authData = null;
					if(cd != null) {
						for(AuthData ad : cd.getAuthData()) {
							if(ad.getAuthServer().equals(is.getAuthServer()) && ad.getAuthSessionId() == is.getAuthSessionId()) {
								authData = ad;
								break;
							}
						}
					}
					if(authData == null) {
						cd = clients.get(is.getName());
						if(cd != null) {
							for(AuthData ad : cd.getAuthData()) {
								if(ad.getAuthServer().equals(is.getAuthServer()) && ad.getAuthSessionId() == is.getAuthSessionId()) {
									authData = ad;
									break;
								}
							}
						}
					}
					if(authData == null) {
						logger.logp(Level.FINER, "Manager", "onReceive", "Authentication data for created actor not found");
						getContext().stop(getSender());
					}
					else {
						if(authData.getNetNode() == null) {
							authData.setState(AuthData.NET_NODE_WAITING);
							return;
						}
						else authData.setState(AuthData.READY);
						authData.setActor(getSender());
						logger.logp(Level.FINER, "Manager", "onReceive", "Client's \"" + client + "\" actor state set to ready");
						String token = (new BigInteger(130, random)).toString(32);
						authData.setToken(token);
						logger.logp(Level.FINER, "Manager", "onReceive", "Generated token for session " + authData);
						AllowConnection ac = new AllowConnection(is.getName(), authData.getActor(), token, authData.getAddress());
						logger.logp(Level.FINER, "Manager", "onReceive", "AllowConnection -> Dispatcher: " + ac);
						getContext().actorSelection(authData.getNetNode() + "/user/dispatcher").tell(ac, getSelf());
					}
					break;
			}
		}
		else if(message instanceof InitFail) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- InitFail:" + message);
			InitFail ifd = (InitFail) message;
			if(ifd.getType() == InitFail.CLIENT) {
				ClientData cd = newClients.get(ifd.getName());
				AuthData authData = null;
				if(cd != null) {
					for(AuthData ad : cd.getAuthData()) {
						if(ad.getAuthServer().equals(ifd.getAuthServer()) && ad.getAuthSessionId() == ifd.getAuthSessionId()) {
							authData = ad;
							break;
						}
					}
				}
				if(authData == null) {
					cd = clients.get(ifd.getName());
					if(cd != null) {
						for(AuthData ad : cd.getAuthData()) {
							if(ad.getAuthServer().equals(ifd.getAuthServer()) && ad.getAuthSessionId() == ifd.getAuthSessionId()) {
								authData = ad;
								break;
							}
						}
					}
				}
				if(authData == null) {
					logger.logp(Level.FINER, "Manager", "onReceive", "Authentication data for created actor not found");
					getContext().stop(getSender());
				}
				else {
					cd.getAuthData().remove(authData);
					logger.logp(Level.FINER, "Manager", "onReceive", "Processing waiting authentication session " + authData);
					AuthDecline adc = new AuthDecline(authData.getAuthSessionId(), ifd.getError());
					logger.logp(Level.FINER, "Manager", "onReceive", "AuthDecline -> AuthServer: " + adc);
					authData.getAuthServer().tell(adc, getSelf());
				}
			}
		}
		else if(message instanceof HandoffSuccess) {
			HandoffSuccess hs = (HandoffSuccess) message;
			ClientData cd = newClients.get(hs.getClient());
			for(Entry<Address, Map<ActorRef, ActorData>> entry : shutdownRequests.entrySet()) {
				if(entry.getKey().equals(hs.getActor().path().address())) {
					entry.getValue().remove(hs.getActor());
					if(entry.getValue().isEmpty()) getContext().actorSelection(entry.getKey() + "/user/node-manager").tell(new AllowShutdown(), getSelf());
				}
			}
			if(cd != null) {
				for(SessionData sd : cd.getSessions()) {
					if(sd.getActor().equals(hs.getActor())) {
						sd.setActor(getSender());
						sd.setActorNode(getSender().path().address());
						getContext().actorSelection(sd.getNetNode() + "/user/dispatcher").tell(new ActorHandedOff(hs.getActor(), getSender()), getSelf());
					}
				}
			}
			cd = clients.get(hs.getClient());
			if(cd != null) {
				Iterator<SessionData> iterator = cd.getSessions().iterator();
				while(iterator.hasNext()) {
					SessionData sd = iterator.next();
					if(sd.getActor().equals(hs.getActor())) {
						iterator.remove();
						sd.setActor(getSender());
						sd.setActorNode(getSender().path().address());
						getContext().actorSelection(sd.getNetNode() + "/user/dispatcher").tell(new ActorHandedOff(hs.getActor(), getSender()), getSelf());
						ClientData clientData = newClients.get(hs.getClient());
						if(clientData != null) clientData.getSessions().add(sd);
						else {
							newClients.put(hs.getClient(), new ClientData(cd.getMessageHandler(), cd.getChildHandler(), cd.getMaxSessions()));
							newClients.get(hs.getClient()).getSessions().add(sd);
						}
					}
				}
			}
		}
		else if(message instanceof HandoffFail) {
			HandoffFail hf = (HandoffFail) message;
			if(hf.getActor() != null) {
				getContext().system().stop(hf.getActor());
				for(Entry<Address, Map<ActorRef, ActorData>> entry : shutdownRequests.entrySet()) {
					if(entry.getKey().equals(hf.getActor().path().address())) {
						entry.getValue().remove(hf.getActor());
						if(entry.getValue().isEmpty()) getContext().actorSelection(entry.getKey() + "/user/node-manager").tell(new AllowShutdown(), getSelf());
					}
				}
			}
		}
		else if(message instanceof RecoverySuccess) {
			RecoverySuccess rs = (RecoverySuccess) message;
			ActorData ad = deadActors.remove(rs.getActor());
			ClientData cd = newClients.get(ad.getClient());
			if(cd != null) {
				for(AuthData authData : cd.getAuthData()) {
					if(authData.getActor().equals(rs.getActor())) {
						authData.setActor(getSender());
						authData.setActorNode(getSender().path().address());
						getContext().actorSelection(authData.getNetNode() + "/user/dispatcher").tell(new ActorRecovered(rs.getActor(), getSender()), getSelf());
					}
				}
				for(SessionData sd : cd.getSessions()) {
					if(sd.getActor().equals(rs.getActor())) {
						sd.setActor(getSender());
						sd.setActorNode(getSender().path().address());
						getContext().actorSelection(sd.getNetNode() + "/user/dispatcher").tell(new ActorRecovered(rs.getActor(), getSender()), getSelf());
					}
				}
			}
			cd = clients.get(ad.getClient());
			if(cd != null) {
				for(AuthData authData : cd.getAuthData()) {
					if(authData.getActor().equals(rs.getActor())) {
						authData.setActor(getSender());
						authData.setActorNode(getSender().path().address());
						getContext().actorSelection(authData.getNetNode() + "/user/dispatcher").tell(new ActorRecovered(rs.getActor(), getSender()), getSelf());
					}
				}
				Iterator<SessionData> iterator = cd.getSessions().iterator();
				while(iterator.hasNext()) {
					SessionData sd = iterator.next();
					if(sd.getActor().equals(rs.getActor())) {
						iterator.remove();
						sd.setActor(getSender());
						sd.setActorNode(getSender().path().address());
						getContext().actorSelection(sd.getNetNode() + "/user/dispatcher").tell(new ActorRecovered(rs.getActor(), getSender()), getSelf());
						ClientData clientData = newClients.get(ad.getClient());
						if(clientData != null) clientData.getSessions().add(sd);
						else {
							newClients.put(ad.getClient(), new ClientData(cd.getMessageHandler(), cd.getChildHandler(), cd.getMaxSessions()));
							newClients.get(ad.getClient()).getSessions().add(sd);
						}
					}
				}
			}
		}
		else if(message instanceof RecoveryFail) {
			RecoveryFail rf = (RecoveryFail) message;
			ActorData ad = deadActors.remove(rf.getActor());
			ClientData cd = newClients.get(ad.getClient());
			if(cd != null) {
				Iterator<AuthData> iteratorAd = cd.getAuthData().iterator();
				while(iteratorAd.hasNext()) {
					AuthData authData = iteratorAd.next();
					if(authData.getActor().equals(rf.getActor())) {
						getContext().actorSelection(authData.getNetNode() + "/user/dispatcher").tell(message, getSelf());
						iteratorAd.remove();
					}
				}
				Iterator<SessionData> iteratorSd = cd.getSessions().iterator();
				while(iteratorSd.hasNext()) {
					SessionData sd = iteratorSd.next();
					if(sd.getActor().equals(rf.getActor())) {
						getContext().actorSelection(sd.getNetNode() + "/user/dispatcher").tell(message, getSelf());
						iteratorSd.remove();
					}
				}
			}
			cd = clients.get(ad.getClient());
			if(cd != null) {
				Iterator<AuthData> iteratorAd = cd.getAuthData().iterator();
				while(iteratorAd.hasNext()) {
					AuthData authData = iteratorAd.next();
					if(authData.getActor().equals(rf.getActor())) {
						getContext().actorSelection(authData.getNetNode() + "/user/dispatcher").tell(message, getSelf());
						iteratorAd.remove();
					}
				}
				Iterator<SessionData> iteratorSd = cd.getSessions().iterator();
				while(iteratorSd.hasNext()) {
					SessionData sd = iteratorSd.next();
					if(sd.getActor().equals(rf.getActor())) {
						ClientData clientData = newClients.get(ad.getClient());
						if(clientData != null)
							clientData.closeSession(sd.getActor());
						else {
							newClients.put(ad.getClient(), new ClientData(cd.getMessageHandler(), cd.getChildHandler(), cd.getMaxSessions()));
							newClients.get(ad.getClient()).closeSession(sd.getActor());
						}
						getContext().actorSelection(sd.getNetNode() + "/user/dispatcher").tell(message, getSelf());
						iteratorSd.remove();
					}
				}
			}
		}
		else if(message instanceof AllowConfirmation) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- AllowConfirmation: " + message);
			AllowConfirmation ac = (AllowConfirmation) message;
			String client = ac.getClient();
			ClientData cd = newClients.get(client);
			AuthData authData = null;
			if(cd != null) authData = cd.getAuthData(ac.getToken());
			if(authData == null) {
				cd = clients.get(client);
				if(cd != null) authData = cd.getAuthData(ac.getToken());
			}
			if(authData != null) {
				logger.logp(Level.FINER, "Manager", "onReceive", "Got waiting authentication session for client \"" + client + "\" by token " + ac.getToken() + ": " + authData);
				AuthConfirmation msg = new AuthConfirmation(authData.getAuthSessionId(), ac.getToken(), authData.getNetServer().getAddresses());
				logger.logp(Level.FINER, "Manager", "onReceive", "AuthConfirmation -> AuthServer: " + msg);
				authData.getAuthServer().tell(msg, getSelf());
				logger.logp(Level.FINER, "Manager", "onReceive", "Removed waiting authentication session for clien \"" + client + "\"");
				cd.getAuthData().remove(authData);
				cd.addSession(authData.getActor(), authData.getActorNode(), authData.getNetNode());
			}
			else {
				logger.logp(Level.WARNING, "Manager", "onReceive", "Waiting authentication session for client \"" + client + "\" not found by token " + ac.getToken());
			}
			logger.logp(Level.FINEST, "Manager", "onReceive", cd.getDump());
		}
		else if(message instanceof ActorStopped) {
			ActorStopped as = (ActorStopped) message;
			String client = as.getClient();
			ClientData cd = newClients.get(client);
			if(cd != null) {
				Iterator<AuthData> ad = cd.getAuthData().iterator();
				while(ad.hasNext()) {
					if(ad.next().getActor().equals(getSender())) {
						ad.remove();
					}
				}
				Iterator<SessionData> sd = cd.getSessions().iterator();
				while(sd.hasNext()) {
					if(sd.next().getActor().equals(getSender())) {
						sd.remove();
					}
				}
			}
			cd = clients.get(client);
			if(cd != null) {
				Iterator<AuthData> ad = cd.getAuthData().iterator();
				while(ad.hasNext()) {
					if(ad.next().getActor().equals(getSender())) {
						ad.remove();
					}
				}
				Iterator<SessionData> sd = cd.getSessions().iterator();
				while(sd.hasNext()) {
					if(sd.next().getActor().equals(getSender())) {
						sd.remove();
					}
				}
			}
		}
		else if(message instanceof SyncTime) {
			SyncData msg = new SyncData(newClients);
			nodeManager.tell(msg, getSelf());
			Iterator<Entry<String, ClientData>> iterator = newClients.entrySet().iterator();
			while(iterator.hasNext()) {
				Entry<String, ClientData> entry = iterator.next();
				iterator.remove();
				ClientData cd = clients.get(entry.getKey());
				if(cd != null) cd.merge(entry.getValue());
				else clients.put(entry.getKey(), entry.getValue());
			}
			logger.logp(Level.FINEST, "Manager", "onReceive", newClients.size() + " " + clients.size());
		}
		else if(message instanceof SyncData) {
			SyncData sd = (SyncData) message;
			for(Entry<String, ClientData> entry : sd.getClients().entrySet()) {
				ClientData cd = clients.get(entry.getKey());
				if(cd != null) cd.merge(entry.getValue());
				else clients.put(entry.getKey(), entry.getValue());
			}
		}
		else unhandled(message);
	}
}