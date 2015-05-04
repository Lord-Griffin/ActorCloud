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
package org.mephi.griffin.actorcloud.authentication;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import com.typesafe.config.Config;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.SocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.mephi.griffin.actorcloud.actormanager.messages.ActorRefMessage;
import org.mephi.griffin.actorcloud.actormanager.messages.AuthDecline;
import org.mephi.griffin.actorcloud.actormanager.messages.AuthConfirmation;
import org.mephi.griffin.actorcloud.authentication.messages.ClientAuthenticated;
import org.mephi.griffin.actorcloud.authentication.messages.GetManagerNode;
import org.mephi.griffin.actorcloud.client.messages.AuthResponse;
import org.mephi.griffin.actorcloud.client.messages.ErrorMessage;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.RegisterServer;
import org.mephi.griffin.actorcloud.common.RemoveSession;
import org.mephi.griffin.actorcloud.common.ServerInfo;
import org.mephi.griffin.actorcloud.common.UnregisterServer;
import org.mephi.griffin.actorcloud.nodemanager.messages.ManagerNode;
import org.mephi.griffin.actorcloud.storage.Entity;
import org.mephi.griffin.actorcloud.storage.SimpleQuery;
import org.mephi.griffin.actorcloud.storage.Storage;
import org.mephi.griffin.actorcloud.storage.messages.StorageResult;

/**
 *
 * @author Griffin
 */
public class AuthServer extends UntypedActor {
	private static final int NODE_WAITING = 1;
	private static final int READY = 2;
			
	private static final Logger logger = Logger.getLogger(AuthServer.class.getName());
	
	private ActorRef nodeManager;
	private Storage storage;
	private List<InetSocketAddress> addresses;
	private SocketAcceptor acceptor;
	private int state;
	private final Map<Integer, IoSession> sessions;
	private final Map<Integer, AuthData> clients;
	
	public AuthServer(ActorRef nodeManager) {
		logger.entering("AuthServer", "Constructor", addresses);
		this.nodeManager = nodeManager;
		storage = null;
		state = READY;
		sessions = new HashMap<>();
		clients = new HashMap<>();
		logger.exiting("AuthServer", "Constructor");
	}
	
	@Override
	public void preStart() {
		logger.entering("AuthServer", "preStart");
		logger.logp(Level.FINE, "AuthServer", "preStart", "Authentication server starts");
		try {
			Config config = getContext().system().settings().config();
			List<String> ips = config.getStringList("actorcloud.auth.ips");
			int port = config.getInt("actorcloud.auth.port");
			addresses = new ArrayList<>();
			for(String ip : ips) addresses.add(new InetSocketAddress(ip, port));
			acceptor = new NioSocketAcceptor();
			DefaultIoFilterChainBuilder chain = acceptor.getFilterChain();
			if(config.getBoolean("actorcloud.auth.ssl")) {
				String keyStoreFile = config.getString("actorcloud.auth.keystore.file");
				String keyStorePass = config.getString("actorcloud.auth.keystore.pass");
				String privateKeyPass = config.getString("actorcloud.auth.keystore.pkpass");
				String trustStoreFile = config.getString("actorcloud.auth.truststore.file");
				String trustStorePass = config.getString("actorcloud.auth.truststore.pass");
				KeyStore keyStore = KeyStore.getInstance("jks");
				keyStore.load(new FileInputStream(keyStoreFile), keyStorePass.toCharArray());
				KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
				kmf.init(keyStore, privateKeyPass.toCharArray());
				KeyStore trustStore = KeyStore.getInstance("jks");
				trustStore.load(new FileInputStream(trustStoreFile), trustStorePass.toCharArray());
				TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
				tmf.init(trustStore);
				SSLContext sslContext = SSLContext.getInstance("SSL");
				sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
				SslFilter sslFilter = new SslFilter(sslContext);
				sslFilter.setNeedClientAuth(true);
				sslFilter.setUseClientMode(false);
				chain.addLast("sslFilter", sslFilter);
			}
			chain.addLast("serializationFilter", new ProtocolCodecFilter(new ObjectSerializationCodecFactory()));
			acceptor.setHandler(new AuthServerHandler(getSelf()));
			acceptor.bind(addresses);
			ServerInfo info;
			String log = "Authentication server is listening on: ";
			if(addresses.get(0).getAddress().getHostAddress().equals("0.0.0.0")) {
				List<InetSocketAddress> registerAddresses = new ArrayList<>();
				Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
				while(ifaces.hasMoreElements()) {
					NetworkInterface iface = ifaces.nextElement();
					List<InterfaceAddress> ifaceaddrs = iface.getInterfaceAddresses();
					for(InterfaceAddress ifaceaddr : ifaceaddrs) {
						InetAddress address = ifaceaddr.getAddress();
						if(!address.isLoopbackAddress()) {
							registerAddresses.add(new InetSocketAddress(address, addresses.get(0).getPort()));
						}
					}
				}
				for(int i = 0; i < registerAddresses.size(); i++) {
					log += registerAddresses.get(i);
					if(i != registerAddresses.size() - 1) log += ", ";
				}
				info = new ServerInfo(getSelf(), registerAddresses);
			}
			else {
				for(int i = 0; i < addresses.size(); i++) {
					log += addresses.get(i);
					if(i != addresses.size() - 1) log += ", ";
				}
				info = new ServerInfo(getSelf(), addresses);
			}
			logger.logp(Level.INFO, "AuthServer", "preStart", log);
			RegisterServer message = new RegisterServer(RegisterServer.AUTH, info);
			logger.logp(Level.FINER, "AuthServer", "preStart", "RegisterServer -> Manager: {0}", message);
			nodeManager.tell(message, getSelf());
			logger.logp(Level.INFO, "AuthServer", "preStart", "Authentication server is started");
		}
		catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyManagementException ex) {
			logger.logp(Level.SEVERE, "AuthServer", "preStart", "Failed to start authentication server");
			logger.throwing("AuthServer", "preStart", ex);
			InitFail message = new InitFail(InitFail.AUTH, "", null, 0, "");
			logger.logp(Level.FINER, "AuthServer", "preStart", "InitFail -> Manager: {0}", message);
			nodeManager.tell(message, getSelf());
			getContext().stop(getSelf());
		}
		logger.exiting("AuthServer", "preStart");
	}
	
	@Override
	public void postStop() {
		logger.entering("AuthServer", "postStop");
		if(acceptor != null) {
			acceptor.unbind();
			for(Map.Entry<Integer, IoSession> entry : sessions.entrySet()) {
				try{entry.getValue().close(false).await();}
				catch(InterruptedException ie) {
					logger.throwing("AuthServer", "postStop", ie);
				}
			}
			acceptor.dispose();
		}
		String dump = "Data dump:\n";
		dump += "  nodeManager " + nodeManager + "\n";
		switch(state) {
			case NODE_WAITING:
				dump += "  state NODE_WAITING\n";
				break;
			case READY:
				dump += "  state READY\n";
				break;
		}
		dump += "  session ids ";
		for(Integer id : sessions.keySet()) dump += id + " ";
		dump += "\n";
		dump += "  clients:\n";
		for(Entry<Integer, AuthData> entry : clients.entrySet()) {
			dump += "    " + entry.getKey() + ":\n";
			dump += entry.getValue().getDump();
		}
		logger.logp(Level.FINEST, "Authserver", "postStop", dump);
		logger.logp(Level.INFO, "AuthServer", "postStop", "Authentication server stopped");
		logger.exiting("AuthServer", "postStop");
	}
	
	/**
	 *
	 * @param message
	 */
	@Override
	public void onReceive(Object message) {
		logger.entering("AuthServer", "onReceive");
		logger.logp(Level.FINER, "AuthServer", "onReceive", "AuthServer <- {0}: {1}", new Object[] {message.getClass().getSimpleName(), message});
		if(message instanceof ActorRefMessage) {
			logger.logp(Level.FINER, "AuthServer", "onReceive", "AuthServer <- ActorRefMessage: " + message);
			ActorRefMessage arm = (ActorRefMessage) message;
			if(arm.getType() == ActorRefMessage.STORAGE) {
				if(arm.getRef() != null)
					storage = new Storage(arm.getRef(), getSelf());
				else {
					logger.logp(Level.FINE, "AuthServer", "onReceive", "Storage unavailable");
					UnregisterServer msg = new UnregisterServer(UnregisterServer.AUTH);
					logger.logp(Level.FINER, "AuthServer", "onReceive", "UnregisterServer -> Manager: " + msg);
					nodeManager.tell(msg, getSelf());
					getContext().stop(getSelf());
				}
			}
		}
		else if(message instanceof AuthData) {
			logger.logp(Level.FINER, "AuthServer", "onReceive", "AuthServer <- AuthData: " + message);
			AuthData ad = (AuthData) message;
			if(storage != null) {
				logger.logp(Level.FINER, "AuthServer", "checkAuth", "Request data for client \"" + ad.getLogin() + "\"");
				SimpleQuery query = new SimpleQuery("name", SimpleQuery.EQUAL, ad.getLogin());
				int requestId = storage.get("clients", query, null);
				AuthData data = new AuthData(ad.getLogin(), ad.getHash(), ad.getActor(), ad.getSessionId(), ad.getSession());
				logger.logp(Level.FINER, "AuthServer", "checkAuth", "Put client data to waiting queue: Storage request id " + requestId + ", " + data);
				clients.put(requestId, data);
			}
			else {
				logger.logp(Level.FINER, "AuthServer", "checkAuth", "Storage unavailable");
				decline(ad.getSession(), new ErrorMessage(ErrorMessage.STOR_ERR, "Storage unavailable", null));
			}
		}
		else if(message instanceof RemoveSession) {
			logger.logp(Level.FINER, "AuthServer", "onReceive", "AuthServer <- RemoveSession: " + message);
			sessions.remove(((RemoveSession) message).getSessionId());
		}
		else if(message instanceof StorageResult) {
			logger.logp(Level.FINER, "AuthServer", "onReceive", "AuthServer <- StorageResult: " + message);
			StorageResult sr = (StorageResult) message;
			if(sr.getOp() == StorageResult.GET) {
				AuthData clientData = clients.get(sr.getId());
				if(clientData != null) {
					logger.logp(Level.FINER, "AuthServer", "onReceive", "Got client data from queue by storage request id " + sr.getId() + ": " + clientData);
					if(sr.error()) {
						logger.logp(Level.WARNING, "AuthServer", "onReceive", "Authentication declined because of storage error: " + sr.getMessage());
						decline(clientData.getSession(), new ErrorMessage(ErrorMessage.STOR_ERR, "Storage error: " + sr.getMessage(), null));
					}
					else if(sr.getCount() == 0) {
						logger.logp(Level.INFO, "AuthServer", "onReceive", "Wrong credentials: login \"" + clientData.getLogin() + "\"");
						decline(clientData.getSession(), new ErrorMessage(ErrorMessage.WRONG_CRED, null, null));
					}
					else if(sr.getCount() > 1) {
						logger.logp(Level.WARNING, "AuthServer", "onReceive", "Authentication declined because of storage error: too much clients with same login: " + sr.getCount());
						decline(clientData.getSession(), new ErrorMessage(ErrorMessage.STOR_ERR, null, null));
					}
					else {
						try {
							MessageDigest md = MessageDigest.getInstance("SHA-512");
							Entity entity = sr.getEntities()[0];
							byte[] hash = (byte[]) entity.get("hash");
							if(Arrays.equals(clientData.getHash(), md.digest(hash))) {
								logger.logp(Level.INFO, "AuthServer", "onReceive", "Client \"" + clientData.getLogin() + "\" authenticated");
								logger.logp(Level.FINER, "AuthServer", "onReceive", "Put client session with id " + clientData.getSessionId() + " to waiting session list");
								sessions.put(clientData.getSessionId(), clientData.getSession());
								InetAddress address = null;
								try {
									address = InetAddress.getByAddress(new byte[]{0,0,0,0});
								} catch (UnknownHostException ex) {}
								if(clientData.getSession().getRemoteAddress() != null) {
									address = ((InetSocketAddress) clientData.getSession().getRemoteAddress()).getAddress();
								}
								else System.out.println("o_O");
								String messageHandler = (String) entity.get("messageHandler");
								String childHandler = (String) entity.get("childHandler");
								clientData.setAddress(address);
								clientData.setMessageHandler(messageHandler);
								clientData.setChildHandler(childHandler);
								clientData.setAuthOk(true);
								if(state == READY) {
									GetManagerNode msg = new GetManagerNode();
									logger.logp(Level.FINER, "AuthServer", "onReceive", "GetManagerNode -> NodeManager: " + msg);
									nodeManager.tell(msg, getSelf());
									state = NODE_WAITING;
								}
							}
							else {
								logger.logp(Level.INFO, "AuthServer", "onReceive", "Wrong credentials: login \"" + clientData.getLogin() + "\"");
								decline(clientData.getSession(), new ErrorMessage(ErrorMessage.WRONG_CRED, null, null));
							}
						}
						catch(NoSuchAlgorithmException nsae) {
							logger.throwing("AuthServer", "onReceive", nsae);
							decline(clientData.getSession(), new ErrorMessage(ErrorMessage.SYSTEM_ERR, null, null));
						}
					}
				}
				else {
					logger.logp(Level.WARNING, "AuthServer", "onReceive", "No client waiting for authentication for storage request id " + sr.getId());
				}
			}
		}
		else if(message instanceof ManagerNode) {
			ManagerNode mn = (ManagerNode) message;
			state = READY;
			Iterator<AuthData> iterator = clients.values().iterator();
			while(iterator.hasNext()) {
				AuthData authData = iterator.next();
				if(authData.isAuthOk()) {
					ClientAuthenticated msg = new ClientAuthenticated(authData.getLogin(), authData.getActor(), authData.getAddress(), authData.getSessionId(), authData.getMessageHandler(), authData.getChildHandler(), 1);
					logger.logp(Level.FINER, "AuthServer", "onReceive", "ClientAuthenticated -> ActorManager: " + msg);
					getContext().actorSelection(mn.getAddress() + "/user/actor-manager").tell(msg, getSelf());
					iterator.remove();
				}
			}
		}
		else if(message instanceof AuthConfirmation) {
			logger.logp(Level.FINER, "AuthServer", "onReceive", "AuthServer <- AuthConfirmation: " + message);
			AuthConfirmation ac = (AuthConfirmation) message;
			int sessionId = ac.getSessionId();
			String token = ac.getToken();
			IoSession session = sessions.remove(sessionId);
			if(session != null) {
				logger.logp(Level.FINER, "AuthServer", "onReceive", "Got client session with id " + sessionId + " from waiting session list");
				grant(session, token, ac.getAddresses());
			}
			else
				logger.logp(Level.FINER, "AuthServer", "onReceive", "Client session with id " + sessionId + " is not in waiting list");
		}
		else if(message instanceof AuthDecline) {
			logger.logp(Level.FINER, "AuthServer", "onReceive", "AuthServer <- AuthDecline: " + message);
			AuthDecline ad = (AuthDecline) message;
			int sessionId = ad.getSessionId();
			String reason = ad.getReason();
			IoSession session = sessions.remove(sessionId);
			if(session != null) {
				logger.logp(Level.FINER, "AuthServer", "onReceive", "Got client session with id " + sessionId + " from waiting session list");
				decline(session, new ErrorMessage(ErrorMessage.CUSTOM, reason, null));
			}
			else
				logger.logp(Level.FINER, "AuthServer", "onReceive", "Client session with id " + sessionId + " is not in waiting list");
		}
		else
			unhandled(message);
		logger.exiting("AuthServer", "onReceive");
	}
	
	private void decline(IoSession session, ErrorMessage message) {
		logger.entering("AuthServer", "decline");
		logger.logp(Level.FINE, "AuthServer", "decline", "Declined client authentication, address {0}, {1}", new Object[]{session.getRemoteAddress(), message});
		session.write(message);
		session.close(false);
		logger.exiting("AuthServer", "decline");
	}
	
	private void grant(IoSession session, String token, List<InetSocketAddress> addresses) {
		logger.entering("AuthServer", "grant");
		AuthResponse message = new AuthResponse(token, addresses);
		logger.logp(Level.FINE, "AuthServer", "grant", "Granted client authentication, session id {0}, address {1}: {2}", new Object[]{session.getAttribute("ID"), session.getRemoteAddress(), message});
		session.write(message);
		session.close(false);
		logger.exiting("AuthServer", "grant");
	}
}