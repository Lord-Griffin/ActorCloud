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
package org.mephi.griffin.actorcloud.manager;

import org.mephi.griffin.actorcloud.common.InitSuccess;
import org.mephi.griffin.actorcloud.enqueuer.AllowConfirmation;
import org.mephi.griffin.actorcloud.enqueuer.ClientDisconnected;
import org.mephi.griffin.actorcloud.authentication.ClientAuthenticated;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import java.io.File;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.JarClassLoader;
import org.mephi.griffin.actorcloud.client.ClientActor;
import org.mephi.griffin.actorcloud.authentication.AuthDecline;
import org.mephi.griffin.actorcloud.authentication.AuthServer;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.RegisterServer;
import org.mephi.griffin.actorcloud.common.ServerInfo;
import org.mephi.griffin.actorcloud.common.UnregisterServer;
import org.mephi.griffin.actorcloud.enqueuer.ClientConnected;
import org.mephi.griffin.actorcloud.enqueuer.Enqueuer;
import org.mephi.griffin.actorcloud.netserver.NetServer;
import org.mephi.griffin.actorcloud.storage.StorageActor;

/**
 *
 * @author Griffin
 */
public class Manager extends UntypedActor {
	private static final int WAITING = 0;
	private static final int READY = 1;
	private static final int FAILED = 2;
	private static final Logger logger = Logger.getLogger(Manager.class.getName());
	private ServerInfo authServer;
	private ServerInfo netServer;
	private ActorRef enqueuer;
	private ActorRef storage;
	private int storageState;
	private final Map<String, ClientData> clients;
	private final SecureRandom random;
	
	private final ClassLoader cl;
	private final File dirs[];
	private final List<InetSocketAddress> authAddresses;
	private final List<InetSocketAddress> netAddresses;
	private final List<InetSocketAddress> storageAddresses;
	
	public Manager() {
		logger.entering("Manager", "Constructor");
		clients = new HashMap<>();
		random = new SecureRandom();
		dirs = new File[3];
		dirs[0] = new File("D:\\dropbox\\netbeans\\actorcloudchat\\dist");
		dirs[1] = new File("D:\\dropbox\\netbeans\\actorcloudconvolution\\dist");
		dirs[2] = new File("D:\\dropbox\\netbeans\\actorcloudnettest\\dist");
		authAddresses = new ArrayList<>();
		authAddresses.add(new InetSocketAddress("0.0.0.0", 8444));
		netAddresses = new ArrayList<>();
		netAddresses.add(new InetSocketAddress("0.0.0.0", 8446));
		storageAddresses = null;
		String log = "Authentication server listen addresses: ";
		for(int i = 0; i < authAddresses.size() - 1; i++) log += authAddresses.get(i) + ", ";
		log += authAddresses.get(authAddresses.size() - 1);
		logger.logp(Level.CONFIG, "Manager", "Constructor", log);
		log = "Network server listen addresses: ";
		for(int i = 0; i < netAddresses.size() - 1; i++) log += netAddresses.get(i) + ", ";
		log += netAddresses.get(netAddresses.size() - 1);
		logger.logp(Level.CONFIG, "Manager", "Constructor", log);
		if(storageAddresses == null) logger.logp(Level.CONFIG, "Manager", "Constructor", "Storage addresses: localhost");
		else {
			log = "Storage addresses: ";
			for(int i = 0; i < storageAddresses.size() - 1; i++) log += storageAddresses.get(i).getAddress().getHostAddress() + ":" + storageAddresses.get(i).getPort() + ", ";
			log += storageAddresses.get(storageAddresses.size() - 1).getAddress().getHostAddress() + ":" + storageAddresses.get(storageAddresses.size() - 1).getPort();
			logger.logp(Level.CONFIG, "Manager", "Constructor", log);
		}
		for(File dir : dirs)
			logger.logp(Level.CONFIG, "Manager", "Constructor", "Added directory \"" + dir.getAbsolutePath() + "\" to classpath");
		cl = new JarClassLoader(dirs, ClassLoader.getSystemClassLoader());
		logger.exiting("Manager", "Constructor");
	}
	
	@Override
	public void preStart() {
		logger.entering("Manager", "preStart");
		logger.logp(Level.FINE, "Manager", "preStart", "Starting actors");
		ActorRef authServerActor = getContext().actorOf(Props.create(AuthServer.class, authAddresses), "auth-server");
		ActorRef netServerActor = getContext().actorOf(Props.create(NetServer.class, netAddresses, cl), "net-server");
		enqueuer = getContext().actorOf(Props.create(Enqueuer.class), "enqueuer");
		storage = getContext().actorOf(Props.create(StorageActor.class, storageAddresses), "storage");
		storageState = WAITING;
		ActorRefMessage msg = new ActorRefMessage(ActorRefMessage.ENQUEUER, enqueuer);
		logger.logp(Level.FINER, "Manager", "preStart", "ActorRefMessage -> NetServer: " + msg);
		netServerActor.tell(msg, getSelf());
		msg = new ActorRefMessage(ActorRefMessage.NET, netServerActor);
		logger.logp(Level.FINER, "Manager", "preStart", "ActorRefMessage -> Enqueuer: " + msg);
		enqueuer.tell(msg, getSelf());
		logger.logp(Level.INFO, "Manager", "preStart", "Actor manager is started");
		logger.exiting("Manager", "preStart");
	}
	
	@Override
	public void postStop() {
		logger.entering("Manager", "postStop");
		logger.logp(Level.INFO, "Manager", "postStop", "Actor manager is stoppped");
		logger.exiting("Manager", "postStop");
	}
	
	/**
	 *
	 * @param message
	 */
	@Override
	public void onReceive(Object message) {
		logger.entering("Manager", "onReceive");
		if(message instanceof InitSuccess) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- InitSuccess: " + message);
			InitSuccess is = (InitSuccess) message;
			switch(is.getType()) {
				case InitSuccess.STORAGE:
					storageState = READY;
					if(authServer != null) {
						ActorRefMessage msg = new ActorRefMessage(ActorRefMessage.STORAGE, storage);
						logger.logp(Level.FINER, "Manager", "onReceive", "ActorRefMessage -> AuthServer: " + msg);
						authServer.getRef().tell(msg, getSelf());
					}
					break;
				case InitSuccess.CLIENT:
					String client = is.getName();
					ClientData cd = clients.get(is.getName());
					if(cd != null) {
						logger.logp(Level.FINER, "Manager", "onReceive", "Got client data for client \"" + client + "\"");
						ActorStarted as = new ActorStarted(is.getName(), getSender());
						logger.logp(Level.FINER, "Manager", "onReceive", "ActorStarted -> Enqueuer: " + as);
						enqueuer.tell(as, getSelf());
						logger.logp(Level.FINER, "Manager", "onReceive", "Client's \"" + client + "\" actor state set to ready");
						cd.setActorState(true);
						List<AuthData> ad = cd.getAuthData();
						if(ad != null) {
							logger.logp(Level.FINER, "Manager", "onReceive", "Got waiting authentication session list for client \"" + client + "\"");
							for(AuthData authData : cd.getAuthData()) {
								logger.logp(Level.FINER, "Manager", "onReceive", "Processing waiting authentication session " + authData);
								if(authData.getToken() != null) {
									logger.logp(Level.WARNING, "Manager", "onReceive", "Found token generated when actor was not ready: client \"" + client + "\", " + authData);
									AllowConnection ac = new AllowConnection(client, authData.getToken(), authData.getAddress());
									logger.logp(Level.FINER, "Manager", "onReceive", "AllowConnection -> Enqueuer: " + ac);
									enqueuer.tell(ac, getSelf());
								}
								else {
									String token = (new BigInteger(130, random)).toString(32);
									authData.setToken(token);
									logger.logp(Level.FINER, "Manager", "onReceive", "Generated token for session " + authData);
									AllowConnection ac = new AllowConnection(client, token, authData.getAddress());
									logger.logp(Level.FINER, "Manager", "onReceive", "AllowConnection -> Enqueuer: " + ac);
									enqueuer.tell(ac, getSelf());
								}
							}
						}
						else {
							logger.logp(Level.SEVERE, "Manager", "onReceive", "Waiting authentication session list for client \"" + client + "\" not found");
						}
					}
					else {
						logger.logp(Level.SEVERE, "Manager", "onReceive", "Client data for client \"" + client + "\" not found");
						getContext().stop(getSender());
					}
					break;
			}
		}
		else if(message instanceof InitFail) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- InitFail:" + message);
			InitFail ifd = (InitFail) message;
			switch(ifd.getType()) {
				case InitFail.AUTH:
					getContext().stop(getSelf());
					break;
				case InitFail.NET:
					getContext().stop(getSelf());
					break;
				case InitFail.STORAGE:
					storageState = FAILED;
					storage = null;
					if(authServer != null) {
						ActorRefMessage msg = new ActorRefMessage(ActorRefMessage.STORAGE, null);
						logger.logp(Level.FINER, "Manager", "onReceive", "ActorRefMessage -> AuthServer: " + msg);
						authServer.getRef().tell(msg, getSelf());
					}
					break;
				case InitFail.CLIENT:
					String client = ifd.getName();
					ClientData cd = clients.get(ifd.getName());
					if(cd != null) {
						logger.logp(Level.FINER, "Manager", "onReceive", "Got client data for client \"" + client + "\": " + cd);
						List<AuthData> ad = cd.getAuthData();
						if(ad != null) {
							logger.logp(Level.FINER, "Manager", "onReceive", "Got waiting authentication session list for client \"" + client + "\"");
							for(AuthData authData : cd.getAuthData()) {
								logger.logp(Level.FINER, "Manager", "onReceive", "Processing waiting authentication session " + authData);
								if(authData.getToken() != null) {
									logger.logp(Level.WARNING, "Manager", "onReceive", "Found token generated when actor was not ready: client \"" + client + "\", " + authData);
								}
								else {
									AuthDecline adc = new AuthDecline(authData.getAuthSessionId(), ifd.getError());
									logger.logp(Level.FINER, "Manager", "onReceive", "AuthDecline -> AuthServer: " + adc);
									authServer.getRef().tell(adc, getSelf());
								}
							}
						}
						else {
							logger.logp(Level.SEVERE, "Manager", "onReceive", "Waiting authentication session list for client \"" + client + "\" not found");
						}
						logger.logp(Level.FINER, "Manager", "onReceive", "Removed client \"" + client + "\" from client list");
						clients.remove(ifd.getName());
						getContext().stop(getSender());
					}
					else {
						logger.logp(Level.SEVERE, "Manager", "onReceive", "Client data for client \"" + client + "\" not found");
						getContext().stop(getSender());
					}
					break;
			}
		}
		else if(message instanceof RegisterServer) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- RegisterServer: " + message);
			RegisterServer rs = (RegisterServer) message;
			switch(rs.getType()) {
				case RegisterServer.AUTH:
					logger.logp(Level.FINE, "Manager", "onReceive", "Registered authentication server: " + rs.getInfo());
					authServer = rs.getInfo();
					if(storageState != WAITING) {
						ActorRefMessage msg = new ActorRefMessage(ActorRefMessage.STORAGE, storage);
						logger.logp(Level.FINER, "Manager", "onReceive", "ActorRefMessage -> AuthServer: " + msg);
						authServer.getRef().tell(msg, getSelf());
					}
					break;
				case RegisterServer.NET:
					logger.logp(Level.FINE, "Manager", "onReceive", "Registered network server: " + rs.getInfo());
					netServer = rs.getInfo();
					break;
			}
		}
		else if(message instanceof UnregisterServer) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- UnregisterServer: " + message);
			UnregisterServer us = (UnregisterServer) message;
			switch(us.getType()) {
				case UnregisterServer.AUTH:
					logger.logp(Level.FINE, "Manager", "onReceive", "Unregistered authentication server: " + authServer);
					getContext().stop(getSelf());
					authServer = null;
					break;
				case UnregisterServer.NET:
					logger.logp(Level.FINE, "Manager", "onReceive", "Unregistered network server: " + netServer);
					getContext().stop(getSelf());
					netServer = null;
					break;
			}
		}
		else if(message instanceof ClientAuthenticated) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- ClientAuthenticated: " + message);
			ClientAuthenticated ca = (ClientAuthenticated) message;
			String client = ca.getLogin();
			ActorRef clientActor;
			if(!clients.containsKey(client)) {
				logger.logp(Level.FINE, "Manager", "onReceive", "Client's \"" + client + "\" first connection");
				if(ca.getLogin().equals("admin")) {
					clientActor = getContext().actorOf(Props.create(org.mephi.griffin.actorcloud.admin.AdminActor.class, netServer.getRef(), storage), client);
				}
				else
					clientActor = getContext().actorOf(Props.create(ClientActor.class, ca.getLogin(), cl, netServer.getRef(), storage, ca.getMessageHandler(), ca.getChildHandler()), client);
				logger.logp(Level.FINE, "Manager", "onReceive", "Created client actor " + clientActor);
				ClientData cd = new ClientData(ca.getAddress(), ca.getSessionId(), clientActor);
				logger.logp(Level.FINER, "Manager", "onReceive", "Added client \"" + client + "\" to client list: " + cd);
				clients.put(client, cd);
				//ActorRefMessage msg = new ActorRefMessage(ActorRefMessage.ENQUEUER, enqueuer);
				//logger.logp(Level.FINER, "Manager", "onReceive", "ActorRefMessage -> client \"" + client + "\" actor: " + msg);
				//clientActor.tell(msg, getSelf());
				//msg = new ActorRefMessage(ActorRefMessage.NET, netServer.getRef());
				//logger.logp(Level.FINER, "Manager", "onReceive", "ActorRefMessage -> client \"" + client + "\" actor: " + msg);
				//clientActor.tell(msg, getSelf());
				//msg = new ActorRefMessage(ActorRefMessage.STORAGE, storage);
				//logger.logp(Level.FINER, "Manager", "onReceive", "ActorRefMessage -> client \"" + client + "\" actor: " + msg);
				//clientActor.tell(msg, getSelf());
				//HandlerNames hn = new HandlerNames(ca.getMessageHandler(), ca.getChildHandler());
				//logger.logp(Level.FINER, "Manager", "onReceive", "HandlerNames -> client \"" + client + "\" actor: " + hn);
				//clientActor.tell(hn, getSelf());
			}
			else if (!clients.get(client).isActorReady()) {
				logger.logp(Level.FINE, "Manager", "onReceive", "Client's \"" + client + "\" not first connection. Actor is not ready");
				clients.get(client).addAuthData(ca.getAddress(), ca.getSessionId());
				logger.logp(Level.FINER, "Manager", "onReceive", "Added client's session to waiting list: " + clients.get(client));
			}
			else {
				logger.logp(Level.FINE, "Manager", "onReceive", "Client's \"" + client + "\" not first connection. Actor is ready");
				String token = (new BigInteger(130, random)).toString(32);
				logger.logp(Level.FINER, "Manager", "onReceive", "Generated token " + token);
				clients.get(client).addAuthData(token, ca.getAddress(), ca.getSessionId());
				logger.logp(Level.FINER, "Manager", "onReceive", "Added client's session to waiting list: " + clients.get(client));
				AllowConnection msg = new AllowConnection(client, token, ca.getAddress());
				logger.logp(Level.FINER, "Manager", "onReceive", "AllowConnection -> Enqueuer: " + msg);
				enqueuer.tell(msg, getSelf());
			}
		}
		else if(message instanceof AllowConfirmation) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- AllowConfirmation: " + message);
			AllowConfirmation ac = (AllowConfirmation) message;
			String client = ac.getClient();
			ClientData cd = clients.get(client);
			if(cd != null) {
				logger.logp(Level.FINER, "Manager", "onReceive", "Got client data for client \"" + client + "\": " + cd);
				AuthData authData = cd.getAuthData(ac.getToken());
				if(authData != null) {
					logger.logp(Level.FINER, "Manager", "onReceive", "Got waiting authentication session for client \"" + client + "\" by token " + ac.getToken() + ": " + authData);
					AuthConfirmation msg = new AuthConfirmation(authData.getAuthSessionId(), ac.getToken(), netServer.getAddresses());
					logger.logp(Level.FINER, "Manager", "onReceive", "AuthConfirmation -> AuthServer: " + msg);
					authServer.getRef().tell(msg, getSelf());
					logger.logp(Level.FINER, "Manager", "onReceive", "Removed waiting authentication session for clien \"" + client + "\"");
					cd.getAuthData().remove(authData);
				}
				else {
					logger.logp(Level.WARNING, "Manager", "onReceive", "Waiting authentication session for client \"" + client + "\" not found by token " + ac.getToken());
				}
			}
			else {
				logger.logp(Level.SEVERE, "Manager", "onReceive", "Client data for client \"" + client + "\" not found");
				ActorRef clientActor = getContext().getChild(client);
				if(clientActor != null) getContext().stop(clientActor);
				else logger.logp(Level.SEVERE, "Manager", "onReceive", "Client actor for client \"" + client + "\" is stopped");
			}
		}
		else if(message instanceof ClientConnected) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- ClientConnected: " + message);
			ClientConnected cc = (ClientConnected) message;
			String client = cc.getClient();
			ClientData cd = clients.get(client);
			if(cd != null) {
				logger.logp(Level.FINER, "Manager", "onReceive", "Got client data for client \"" + client + "\": " + cd);
				cd.addConnection();
				logger.logp(Level.FINER, "Manager", "onReceive", "Incremented connection count for client \"" + cc.getClient() + "\": " + cd);
			}
			else {
				logger.logp(Level.SEVERE, "Manager", "onReceive", "Client data for client \"" + client + "\" not found");
				ActorRef clientActor = getContext().getChild(client);
				if(clientActor != null) getContext().stop(clientActor);
				else logger.logp(Level.SEVERE, "Manager", "onReceive", "Client actor for client \"" + client + "\" is stopped");
			}
		}
		else if(message instanceof ClientDisconnected) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- ClientDisconnected: " + message);
			ClientDisconnected cd = (ClientDisconnected) message;
			String client = cd.getClient();
			ClientData cdt = clients.get(client);
			if(cdt != null) {
				logger.logp(Level.FINER, "Manager", "onReceive", "Got client data for client \"" + client + "\": " + cd);
				if(cdt.getConnections() == 1 && cdt.getAuthData().isEmpty()) {
					logger.logp(Level.FINER, "Manager", "onReceive", "Client's \"" + client + "\" last connection");
					ActorStopped as = new ActorStopped(client);
					logger.logp(Level.FINER, "Manager", "onReceive", "ActorStopped -> Enqueuer: " + as);
					enqueuer.tell(as, getSelf());
					logger.logp(Level.FINER, "Manager", "onReceive", "Removed client \"" + client + "\" from list");
					clients.remove(client);
					getContext().stop(cdt.getActor());
				}
				else if(cdt.getConnections() <= 0) {
					logger.logp(Level.WARNING, "Manager", "onReceive", "Connection count for client \"" + client + "\" is " + cdt.getConnections() + ", but it is still registered in system");
					ActorStopped as = new ActorStopped(client);
					logger.logp(Level.FINER, "Manager", "onReceive", "ActorStopped -> Enqueuer: " + as);
					enqueuer.tell(as, getSelf());
					logger.logp(Level.FINER, "Manager", "onReceive", "Removed client \"" + client + "\" from list");
					clients.remove(client);
					getContext().stop(cdt.getActor());
				}
				else {
					logger.logp(Level.FINER, "Manager", "onReceive", "Not last client's \"" + client + "\" connection");
					cdt.removeConnection();
					logger.logp(Level.FINER, "Manager", "onReceive", "Decremented connection count for client \"" + client + "\": " + cdt);
				}
			}
			else {
				logger.logp(Level.SEVERE, "Manager", "onReceive", "Client data for client \"" + client + "\" not found");
				ActorRef clientActor = getContext().getChild(client);
				if(clientActor != null) getContext().stop(clientActor);
				else logger.logp(Level.SEVERE, "Manager", "onReceive", "Client actor for client \"" + client + "\" is stopped");
			}
			
		}
		else if(message instanceof String) {
			logger.logp(Level.FINER, "Manager", "onReceive", "Manager <- String (UserSearch): " + message);
			String client = (String) message;
			if(clients.containsKey(client)) {
				getSender().tell(new ClientFindResult(client, clients.get(client).getActor()), getSelf());
			}
			else {
				getSender().tell(new ClientFindResult(client, null), getSelf());
			}
		}
		else unhandled(message);
	}
}