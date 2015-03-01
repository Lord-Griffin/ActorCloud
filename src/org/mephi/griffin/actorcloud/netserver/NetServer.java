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
package org.mephi.griffin.actorcloud.netserver;

import org.mephi.griffin.actorcloud.enqueuer.DisconnectSession;
import org.mephi.griffin.actorcloud.enqueuer.AllowAddress;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.filter.firewall.BlacklistFilter;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.SocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.mephi.griffin.actorcloud.client.ErrorMessage;
import org.mephi.griffin.actorcloud.common.AddSession;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.RegisterServer;
import org.mephi.griffin.actorcloud.common.RemoveSession;
import org.mephi.griffin.actorcloud.common.ServerInfo;
import org.mephi.griffin.actorcloud.common.UnregisterServer;
import org.mephi.griffin.actorcloud.manager.ActorRefMessage;
import scala.concurrent.duration.Duration;

/**
 *
 * @author Griffin
 */
public class NetServer extends UntypedActor {
	
	private static final Logger logger = Logger.getLogger(NetServer.class.getName());
	private ActorRef enqueuer;
	private final ActorRef manager;
	private final ClassLoader cl;
	private List<InetSocketAddress> addresses;
	private SocketAcceptor acceptor;
	private WhitelistFilter whitelist;
	private BlacklistFilter blacklist;
	private final Map<InetAddress, Long> bannedAddresses;
	private final Map<Integer, IoSession> sessions;
	private final Map<InetAddress, Integer> fails;
	private Cancellable schedule = null;
	
	public NetServer(List<InetSocketAddress> addresses, ClassLoader cl) throws UnknownHostException {
		logger.entering("NetServer", "Constructor", new Object[]{addresses, cl});
		String log = "Addresses: ";
		for(int i = 0; i < addresses.size() - 1; i++) log += addresses.get(i) + ", ";
		log += addresses.get(addresses.size() - 1);
		logger.logp(Level.FINER, "NetServer", "Constructor", log);
		this.addresses = addresses;
		sessions = new HashMap<>();
		bannedAddresses = new HashMap<>();
		fails = new HashMap<>();
		manager = getContext().parent();
		this.cl = cl;
		logger.exiting("NetServer", "Constructor");
	}
	
	@Override
	public void preStart() {
		logger.entering("NetServer", "preStart");
		logger.logp(Level.FINE, "NetServer", "preStart", "Network server starts");
		try {
			KeyStore keyStore = KeyStore.getInstance("jks");
			keyStore.load(new FileInputStream("D:\\server.jks"), "abcdef".toCharArray());
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
			kmf.init(keyStore, "antharas".toCharArray());
			KeyStore trustStore = KeyStore.getInstance("jks");
			trustStore.load(new FileInputStream("D:\\ca.jks"), "abcdef".toCharArray());
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
			tmf.init(trustStore);
			SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			acceptor = new NioSocketAcceptor();
			DefaultIoFilterChainBuilder chain = acceptor.getFilterChain();
			blacklist = new BlacklistFilter();
			chain.addLast("blackList", blacklist);
			whitelist = new WhitelistFilter();
			chain.addLast("whitelist", whitelist);
			SslFilter sslFilter = new SslFilter(sslContext);
			sslFilter.setNeedClientAuth(true);
			sslFilter.setUseClientMode(false);
			chain.addLast("sslFilter", sslFilter);
			chain.addLast("serializationFilter", new ProtocolCodecFilter(new ObjectSerializationCodecFactory(cl)));
			acceptor.setHandler(new NetServerHandler(getSelf()));
			acceptor.bind(addresses);
			ServerInfo info;
			String log = "Network server is listening on: ";
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
			logger.logp(Level.INFO, "NetServer", "preStart", log);
			RegisterServer message = new RegisterServer(RegisterServer.NET, info);
			logger.logp(Level.FINER, "NetServer", "preStart", "RegisterServer -> Manager: {0}", message);
			manager.tell(message, getSelf());
			logger.logp(Level.INFO, "NetServer", "preStart", "Network server is started");
		}
		catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyManagementException ex) {
			logger.logp(Level.SEVERE, "NetServer", "preStart", "Failed to start network server");
			logger.throwing("NetServer", "preStart", ex);
			InitFail message = new InitFail(InitFail.NET, "", "");
			logger.logp(Level.FINER, "NetServer", "preStart", "InitFail -> Manager: {0}", message);
			manager.tell(message , getSelf());
			getContext().stop(getSelf());
		}
		logger.exiting("NetServer", "preStart");
	}
	
	@Override
	public void postStop() throws Exception {
		logger.entering("NetServer", "postStop");
		if(acceptor != null) {
			acceptor.unbind();
			for(Entry<Integer, IoSession> entry : sessions.entrySet()) {
				try{entry.getValue().close(false).await();}
				catch(InterruptedException ie) {
					logger.throwing("NetServer", "postStop", ie);
				}
			}
			acceptor.dispose();
		}
		logger.logp(Level.INFO, "NetServer", "postStop", "Network server stopped");
		logger.exiting("NetServer", "postStop");
	}
	
	/**
	 *
	 * @param message
	 */
	@Override
	public void onReceive(Object message) {
		logger.entering("NetServer", "onReceive");
		if(message instanceof ActorRefMessage) {
			logger.logp(Level.FINER, "NetServer", "onReceive", "NetServer <- ActorRefMessage: " + message);
			ActorRefMessage arm = (ActorRefMessage) message;
			if(arm.getType() == ActorRefMessage.ENQUEUER) {
				if(arm.getRef() != null) {
					enqueuer = arm.getRef();
				}
				else {
					logger.logp(Level.FINE, "NetServer", "onReceive", "Enqueuer unavailable");
					UnregisterServer msg = new UnregisterServer(UnregisterServer.NET);
					logger.logp(Level.FINER, "NetServer", "onReceive", "UnregisterServer -> Manager: " + msg);
					manager.tell(msg, getSelf());
					getContext().stop(getSelf());
				}
			}
		}
		else if(message instanceof AllowAddress) {
			logger.logp(Level.FINER, "NetServer", "onReceive", "NetServer <- AllowAddress: " + message);
			whitelist.allow(((AllowAddress) message).getAddress());
		}
		else if(message instanceof DisconnectSession) {
			logger.logp(Level.FINER, "NetServer", "onReceive", "NetServer <- DisconnectSession: " + message);
			DisconnectSession dc = (DisconnectSession) message;
			IoSession session = sessions.get(dc.getSessionId());
			if(session != null) {
				logger.logp(Level.FINER, "NetServer", "onReceive", "Got client session with id " + dc.getSessionId() + " from session list");
				InetAddress address = ((InetSocketAddress) session.getRemoteAddress()).getAddress();
				if(dc.getReason() == DisconnectSession.NOTOKEN) {
					logger.logp(Level.FINER, "NetServer", "onReceive", "Got client address: " + address);
					if(!fails.containsKey(address)) {
						logger.logp(Level.FINER, "NetServer", "onReceive", "There was 0 invalid tokens. Added " + address + " to fails counter list");
						fails.put(address, 1);
					}
					else {
						int count = fails.get(address);
						if(count == 5) {
							logger.logp(Level.FINER, "NetServer", "onReceive", "There was " + count + " invalid tokens. Removed " + address + " from fails counter list");
							fails.remove(address);
							logger.logp(Level.FINER, "NetServer", "onReceive", "Address {0} added to blacklist", address);
							blacklist.block(address);
							bannedAddresses.put(address, (new Date()).getTime() + 300000);
							if(schedule == null || schedule.isCancelled()) {
								logger.logp(Level.FINER, "NetServer", "onReceive", "Started to monitor blacklist");
								schedule = getContext().system().scheduler().schedule(Duration.Zero(), Duration.create(1000, TimeUnit.MILLISECONDS), getSelf(), new CleanBlacklist(), getContext().system().dispatcher(), ActorRef.noSender());
							}
						}
						else {
							logger.logp(Level.FINER, "NetServer", "onReceive", "There was " + count + " invalid tokens. Incremented fail count for " + address);
							fails.put(address, count + 1);
						}
					}
				}
				logger.logp(Level.INFO, "NetServer", "onReceive", "Disconnected client with address " + address + ", reason: Invalid token");
				session.write(new ErrorMessage(ErrorMessage.INVALID_TOKEN, null, null));
				try {session.close(false).await();}
				catch(InterruptedException ie) {
					logger.throwing("NetServer", "onReceive", ie);
				}
			}
			else {
				logger.logp(Level.WARNING, "NetServer", "onReceive", "No client session with id " + dc.getSessionId());
			}
		}
		else if(message instanceof AddSession) {
			logger.logp(Level.FINER, "NetServer", "onReceive", "NetServer <- AddSession: {0}", message);
			AddSession ad = (AddSession) message;
			logger.logp(Level.FINER, "NetServer", "onReceive", "Added session with id " + ad.getSessionId() + " to session list");
			sessions.put(ad.getSessionId(), ad.getSession());
		}
		else if(message instanceof RemoveSession) {
			logger.logp(Level.FINER, "NetServer", "onReceive", "NetServer <- RemoveSession: {0}", message);
			RemoveSession rd = (RemoveSession) message;
			logger.logp(Level.FINER, "NetServer", "onReceive", "Removed session with id " + rd.getSessionId() + " from session list");
			sessions.remove(rd.getSessionId());
			SessionDisconnected msg = new SessionDisconnected(rd.getSessionId());
			logger.logp(Level.FINER, "NetServer", "onReceive", "SessionDisconnected -> Enqueuer: " + msg);
			enqueuer.tell(msg, getSelf());
		}
		else if(message instanceof SessionMessage) {
			logger.logp(Level.FINER, "NetServer", "onReceive", "NetServer <- SessionMessage: " + message);
			SessionMessage sm = (SessionMessage) message;
			if(sm.isInbound()) {
				logger.logp(Level.FINER, "NetServer", "onReceive", "SessionMessage -> Enqueuer: " + message);
				String log = "Message: " + message.getClass().getName() + "\n";
				for(Field field : message.getClass().getDeclaredFields()) {
					try {
						field.setAccessible(true);
						Class type = field.getType();
						Object value = field.get(message);
						log += type.getName() + " " + field.getName() + " = " + value + "\n";
					}
					catch(IllegalAccessException iae) {}
				}
				logger.logp(Level.FINEST, "NetServer", "onReceive", log);
				enqueuer.tell(message, getSelf());
			}
			else {
				String log = "Message: " + sm.getMessage().getClass().getName() + "\n";
				for(Field field : sm.getMessage().getClass().getDeclaredFields()) {
					try {
						field.setAccessible(true);
						Class type = field.getType();
						Object value = field.get(sm.getMessage());
						log += type.getName() + " " + field.getName() + " = " + value + "\n";
					}
					catch(IllegalAccessException iae) {}
				}
				logger.logp(Level.FINEST, "NetServer", "onReceive", log);
				for(int sessionId : sm.getSessionIds()) {
					if(sessions.get(sessionId) != null) {
						logger.logp(Level.FINER, "NetServer", "onReceive", "Message sent to client session with id " + sessionId);
						sessions.get(sessionId).write(sm.getMessage());
					}
					else {
						logger.logp(Level.WARNING, "NetServer", "onReceive", "No client session with id " + sessionId);
					}
				}
			}
		}
		else if(message instanceof CleanBlacklist) {
			logger.logp(Level.FINER, "NetServer", "onReceive", "NetServer <- CleanBlacklist");
			long now = (new Date()).getTime();
			int count = 0;
			List<InetAddress> expiredAddresses = new ArrayList<>();
			for(Entry<InetAddress, Long> entry : bannedAddresses.entrySet()) {
				if(entry.getValue() == null) {
					logger.logp(Level.SEVERE, "Enqueuer", "onReceive", "Expiration time for address " + entry.getKey() + " not found");
					expiredAddresses.add(entry.getKey());
				}
				else if(entry.getValue() < now) {
					logger.logp(Level.FINER, "NetServer", "onReceive", "Ban for address " + entry.getKey() + " is expired");
					expiredAddresses.add(entry.getKey());
				}
				else {
					logger.logp(Level.FINEST, "NetServer", "onReceive", "Ban for address " + entry.getKey() + " is valid for " + (entry.getValue() - now) + " ms");
				}
			}
			for(InetAddress address : expiredAddresses) {
				count++;
				bannedAddresses.remove(address);
				blacklist.unblock(address);
			}
			if(count > 0) logger.logp(Level.FINE, "NetServer", "onReceive", "Cleaned " + count + " expired bans");
			if(bannedAddresses.isEmpty() && schedule.cancel())
				logger.logp(Level.FINER, "NetServer", "onReceive", "Blacklist is empty, monitoring stopped");
		}
		else
			unhandled(message);
		logger.exiting("NetServer", "onReceive");
	}
}