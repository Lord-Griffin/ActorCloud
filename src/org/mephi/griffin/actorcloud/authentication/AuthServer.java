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

import org.mephi.griffin.actorcloud.manager.AuthConfirmation;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.RegisterServer;
import org.mephi.griffin.actorcloud.common.ServerInfo;
import org.mephi.griffin.actorcloud.common.UnregisterServer;
import org.mephi.griffin.actorcloud.manager.ActorRefMessage;
import org.mephi.griffin.actorcloud.storage.Entity;
import org.mephi.griffin.actorcloud.storage.SimpleQuery;
import org.mephi.griffin.actorcloud.storage.Storage;
import org.mephi.griffin.actorcloud.storage.StorageResult;

/**
 *
 * @author Griffin
 */
public class AuthServer extends UntypedActor {
	
	private static final Logger logger = Logger.getLogger(AuthServer.class.getName());
	private ActorRef manager;
	private Storage storage;
	private List<InetAddress> addresses;
	private int port;
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private List<Channel> serverChannels;
	private final Map<Integer, Channel> channels;
	private final Map<Integer, ClientData> clients;
	
	public AuthServer(List<InetAddress> addresses, int port) {
		logger.entering("AuthServer", "Constructor");
		String log = "Addresses: ";
		if(addresses == null || addresses.isEmpty()) log += "0.0.0.0:" + port;
		else {
			for(int i = 0; i < addresses.size() - 1; i++) log += addresses.get(i).getHostAddress() + ":" + port + ",";
			log += addresses.get(addresses.size() - 1).getHostAddress() + ":" + port;
		}
		logger.logp(Level.FINER, "AuthServer", "Constructor", log);
		this.addresses = addresses;
		this.port = port;
		serverChannels = new ArrayList<>();
		channels = new HashMap<>();
		clients = new HashMap<>();
		manager = getContext().parent();
		storage = null;
		logger.exiting("AuthServer", "Constructor");
	}
	
	@Override
	public void preStart() {
		logger.entering("AuthServer", "preStart");
		logger.logp(Level.FINE, "AuthServer", "preStart", "Authentication server starts");
		try {
			SelfSignedCertificate ssc = new SelfSignedCertificate();
			SslContext sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());
			bossGroup = new NioEventLoopGroup(1);
			workerGroup = new NioEventLoopGroup();
			ServerBootstrap bootstrap = new ServerBootstrap();
			bootstrap.group(bossGroup, workerGroup);
			bootstrap.channel(NioServerSocketChannel.class);
            //b.handler(new LoggingHandler(LogLevel.INFO));
			bootstrap.childHandler(new AuthServerInitializer(sslCtx, this));
			if(addresses == null || addresses.isEmpty()) {
				addresses = new ArrayList<>();
				ChannelFuture future = bootstrap.bind(port);
				while(!future.isDone()) future = future.sync();
				if(future.isSuccess()) {
					serverChannels.add(future.channel());
					String log = "Authentication server is listening on ";
					try {
						Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
						while(ifaces.hasMoreElements()) {
							NetworkInterface iface = ifaces.nextElement();
							List<InterfaceAddress> ifaceaddrs = iface.getInterfaceAddresses();
							for(InterfaceAddress ifaceaddr : ifaceaddrs) {
								InetAddress address = ifaceaddr.getAddress();
								if(!address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
									addresses.add(address);
									log += address.getHostAddress() + ":" + port + ", ";
								}
							}
						}
						logger.logp(Level.INFO, "AuthServer", "preStart", log.substring(0, log.length() - 2));
					}
					catch(SocketException se) {
						logger.throwing("AuthServer", "preStart", se);
					}
				}
				else logger.throwing("AuthServer", "preStart", future.cause());
			}
			else {
				for(InetAddress address : addresses) {
					ChannelFuture future = bootstrap.bind(address, port);
					while(!future.isDone()) future = future.sync();
					if(future.isSuccess()) {
						serverChannels.add(future.channel());
						logger.logp(Level.INFO, "AuthServer", "preStart", "Authentication server is listening on " + address.getHostAddress() + ":" + port);
					}
					else {
						addresses.remove(address);
						logger.throwing("AuthServer", "preStart", future.cause());
					}
				}
			}
			if(serverChannels.isEmpty()) {
				logger.logp(Level.SEVERE, "AuthServer", "preStart", "Failed to start authentication server");
				InitFail message = new InitFail(InitFail.AUTH, "", "");
				logger.logp(Level.FINER, "AuthServer", "preStart", "InitFail -> Manager: " + message);
				manager.tell(message , getSelf());
				getContext().stop(getSelf());
			}
		}
		catch (InterruptedException ie) {
			logger.throwing("AuthServer", "postStop", ie);
		}
		catch (CertificateException | SSLException ex) {
			logger.logp(Level.SEVERE, "AuthServer", "preStart", "Failed to start authentication server");
			logger.throwing("AuthServer", "preStart", ex);
			InitFail message = new InitFail(InitFail.AUTH, "", "");
			logger.logp(Level.FINER, "AuthServer", "preStart", "InitFail -> Manager: " + message);
			manager.tell(message , getSelf());
			getContext().stop(getSelf());
		}
		if(addresses == null) addresses = new ArrayList<>();
		RegisterServer message = new RegisterServer(RegisterServer.AUTH, new ServerInfo(getSelf(), addresses, port));
		logger.logp(Level.FINER, "AuthServer", "preStart", "RegisterServer -> Manager: " + message);
		manager.tell(message, getSelf());
		logger.logp(Level.INFO, "AuthServer", "preStart", "Authentication server is started");
		logger.exiting("AuthServer", "preStart");
	}
	
	@Override
	public void postStop() {
		logger.entering("AuthServer", "postStop");
		for(Channel channel : serverChannels) {
			try {channel.close().sync();}
			catch(InterruptedException ie) {
				logger.throwing("AuthServer", "postStop", ie);
			}
		}
		for(Map.Entry<Integer, Channel> entry : channels.entrySet()) {
			try{entry.getValue().close().sync();}
			catch(InterruptedException ie) {
				logger.throwing("AuthServer", "postStop", ie);
			}
		}
		workerGroup.shutdownGracefully();
		bossGroup.shutdownGracefully();
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
					manager.tell(msg, getSelf());
					getContext().stop(getSelf());
				}
			}
		}
		else if(message instanceof StorageResult) {
			logger.logp(Level.FINER, "AuthServer", "onReceive", "AuthServer <- StorageResult: " + message);
			StorageResult sr = (StorageResult) message;
			if(sr.getOp() == StorageResult.GET) {
				ClientData clientData = clients.get(sr.getId());
				if(clientData != null) {
					logger.logp(Level.FINER, "AuthServer", "onReceive", "Got client data from queue by storage request id " + sr.getId() + ": " + clientData);
					if(sr.error()) {
						logger.logp(Level.WARNING, "AuthServer", "onReceive", "Authentication declined because of storage error: " + sr.getMessage());
						decline(clientData.getChannel(), "Storage error: " + sr.getMessage());
					}
					else if(sr.getCount() == 0) {
						logger.logp(Level.INFO, "AuthServer", "onReceive", "Wrong credentials: login \"" + clientData.getLogin() + "\"");
						decline(clientData.getChannel(), "Wrong login or password");
						logger.logp(Level.FINER, "AuthServer", "onReceive", "Removed client data from queue by id " + sr.getId());
						clients.remove(sr.getId());
					}
					else if(sr.getCount() > 1) {
						logger.logp(Level.WARNING, "AuthServer", "onReceive", "Authentication declined because of storage error: too much clients with same login: " + sr.getCount());
						decline(clientData.getChannel(), "Storage error");
					}
					else {
						try {
							MessageDigest md = MessageDigest.getInstance("SHA-512");
							Entity entity = sr.getEntities()[0];
							byte[] hash = (byte[]) entity.get("hash");
							if(Arrays.equals(clientData.getHash(), md.digest(hash))) {
								logger.logp(Level.INFO, "AuthServer", "onReceive", "Client \"" + clientData.getLogin() + "\" authenticated");
								logger.logp(Level.FINER, "AuthServer", "onReceive", "Put client channel with id " + clientData.getChannelId() + " to waiting channel list");
								channels.put(clientData.getChannelId(), clientData.getChannel());
								String address = ((InetSocketAddress) clientData.getChannel().remoteAddress()).getAddress().getHostAddress();
								String messageHandler = (String) entity.get("messageHandler");
								String childHandler = (String) entity.get("childHandler");
								ClientAuthenticated msg = new ClientAuthenticated(clientData.getLogin(), address, clientData.getChannelId(), messageHandler, childHandler);
								logger.logp(Level.FINER, "AuthServer", "onReceive", "ClientAuthenticated -> Manager: " + msg);
								manager.tell(msg, getSelf());
							}
							else {
								logger.logp(Level.INFO, "AuthServer", "onReceive", "Wrong credentials: login \"" + clientData.getLogin() + "\"");
								decline(clientData.getChannel(), "Wrong login or password");
							}
						}
						catch(NoSuchAlgorithmException nsae) {
							logger.throwing("AuthServer", "onReceive", nsae);
							decline(clientData.getChannel(), "System error");
						}
					}
				}
				else {
					logger.logp(Level.WARNING, "AuthServer", "onReceive", "No client waiting for authentication for storage request id " + sr.getId());
				}
			}
		}
		else if(message instanceof AuthConfirmation) {
			logger.logp(Level.FINER, "AuthServer", "onReceive", "AuthServer <- AuthConfirmation: " + message);
			AuthConfirmation ac = (AuthConfirmation) message;
			int channelId = ac.getChannelId();
			String token = ac.getToken();
			InetAddress host = ac.getAddresses().get(0);
			int port = ac.getPort();
			if(channels.containsKey(channelId)) {
				logger.logp(Level.FINER, "AuthServer", "onReceive", "Got client channel with id " + channelId + " from waiting channel list");
				grant(channels.get(channelId), token, host, port);
				logger.logp(Level.FINER, "AuthServer", "onReceive", "Removed client channel with id " + channelId + " from waiting channel list");
				channels.remove(channelId);
			}
			else
				logger.logp(Level.FINER, "AuthServer", "onReceive", "Client channel with id " + channelId + " is not in waiting list");
		}
		if(message instanceof AuthDecline) {
			logger.logp(Level.FINER, "AuthServer", "onReceive", "AuthServer <- AuthDecline: " + message);
			AuthDecline ad = (AuthDecline) message;
			int channelId = ad.getChannelId();
			String reason = ad.getReason();
			if(channels.containsKey(channelId)) {
				logger.logp(Level.FINER, "AuthServer", "onReceive", "Got client channel with id " + channelId + " from waiting channel list");
				decline(channels.get(channelId), reason);
				logger.logp(Level.FINER, "AuthServer", "onReceive", "Removed client channel with id " + channelId + " from waiting channel list");
				channels.remove(channelId);
			}
			else
				logger.logp(Level.FINER, "AuthServer", "onReceive", "Client channel with id " + channelId + " is not in waiting list");
		}
		else
			unhandled(message);
		logger.exiting("AuthServer", "onReceive");
	}
	
	public void checkAuth(String login, byte[] hash, int channelId, Channel channel) {
		logger.entering("AuthServer", "checkAuth");
		if(storage != null) {
			logger.logp(Level.FINER, "AuthServer", "checkAuth", "Request data for client \"" + login + "\"");
			SimpleQuery query = new SimpleQuery("name", SimpleQuery.EQUAL, login);
			int requestId = storage.get("clients", query, null);
			ClientData data = new ClientData(login, hash, channelId, channel);
			logger.logp(Level.FINER, "AuthServer", "checkAuth", "Put client data to waiting queue: Storage request id " + requestId + ", " + data);
			clients.put(requestId, data);
		}
		else {
			logger.logp(Level.FINER, "AuthServer", "checkAuth", "Storage unavailable");
			decline(channel, "Storage unavailable");
		}
		logger.exiting("AuthServer", "checkAuth");
	}
	
	public void removeChannel(int channelId) {
		logger.entering("AuthServer", "removeChannel");
		channels.remove(channelId);
		logger.exiting("AuthServer", "removeChannel");
	}
	
	private void decline(Channel channel, String message) {
		logger.entering("AuthServer", "decline");
		ByteBuf buf = channel.alloc().buffer();
		buf.writeByte(1);
		buf.writeBytes(message.getBytes());
		try {
			logger.logp(Level.FINE, "AuthServer", "decline", "Declined client authentication, address " + channel.remoteAddress() + ", reason \"" + message + "\"");
			channel.writeAndFlush(buf).sync();
			channel.close().sync();
		}
		catch (InterruptedException ie) {
			logger.throwing("AuthServer", "postStop", ie);
		}
		logger.exiting("AuthServer", "decline");
	}
	
	private void grant(Channel channel, String token, InetAddress host, int port) {
		logger.entering("AuthServer", "grant");
		ByteBuf buff = channel.alloc().buffer(12 + token.getBytes().length);
		buff.writeByte(0);
		buff.writeInt(token.getBytes().length);
		buff.writeBytes(token.getBytes());
		buff.writeBytes(host.getAddress());
		buff.writeInt(port);
		try {
			logger.logp(Level.FINE, "AuthServer", "grant", "Granted client authentication, address " + channel.remoteAddress() + ", token " + token + ", netServer " + host + ":" + port);
			channel.writeAndFlush(buff).sync();
			channel.close().sync();
		}
		catch (InterruptedException ie) {
			logger.throwing("AuthServer", "postStop", ie);
		}
		logger.exiting("AuthServer", "grant");
	}
}