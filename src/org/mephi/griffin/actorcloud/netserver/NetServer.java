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

import org.mephi.griffin.actorcloud.enqueuer.DisconnectChannel;
import org.mephi.griffin.actorcloud.enqueuer.AllowAddress;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
import org.mephi.griffin.actorcloud.client.Message;
import org.mephi.griffin.actorcloud.client.SystemMessage;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.RegisterServer;
import org.mephi.griffin.actorcloud.common.ServerInfo;
import org.mephi.griffin.actorcloud.common.UnregisterServer;
import org.mephi.griffin.actorcloud.manager.ActorRefMessage;

/**
 *
 * @author Griffin
 */
public class NetServer extends UntypedActor {
	
	private static final Logger logger = Logger.getLogger(NetServer.class.getName());
	private ActorRef enqueuer;
	private final ActorRef manager;
	private final ClassLoader cl;
	private List<InetAddress> addresses;
	private int port;
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private NetServerInitializer initializer;
	private List<Channel> serverChannels;
	private final Map<Integer, Channel> channels;
	private final Map<String, Integer> fails;
	
	public NetServer(List<InetAddress> addresses, int port, ClassLoader cl) throws UnknownHostException {
		logger.entering("NetServer", "Constructor");
		String log = "Addresses: ";
		if(addresses == null || addresses.isEmpty()) log += "0.0.0.0:" + port;
		else {
			for(int i = 0; i < addresses.size() - 1; i++) log += addresses.get(i).getHostAddress() + ":" + port + ",";
			log += addresses.get(addresses.size() - 1).getHostAddress() + ":" + port;
		}
		logger.logp(Level.FINER, "NetServer", "Constructor", log);
		this.addresses = addresses;
		this.port = port;
		serverChannels = new ArrayList<>();
		channels = new HashMap<>();
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
			SelfSignedCertificate ssc = new SelfSignedCertificate();
			SslContext sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());
			bossGroup = new NioEventLoopGroup();
			workerGroup = new NioEventLoopGroup();
			ServerBootstrap bootstrap = new ServerBootstrap();
			bootstrap.group(bossGroup, workerGroup);
			bootstrap.channel(NioServerSocketChannel.class);
            //b.handler(new LoggingHandler(LogLevel.INFO));
			initializer = new NetServerInitializer(cl, sslCtx, this);
			bootstrap.childHandler(initializer);
			if(addresses == null || addresses.isEmpty()) {
				addresses = new ArrayList<>();
				ChannelFuture future = bootstrap.bind(port);
				while(!future.isDone()) future = future.sync();
				if(future.isSuccess()) {
					serverChannels.add(future.channel());
					String log = "Network server is listening on ";
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
						logger.logp(Level.INFO, "NetServer", "preStart", log.substring(0, log.length() - 2));
					}
					catch(SocketException se) {
						logger.throwing("NetServer", "preStart", se);
					}
				}
				else logger.throwing("NetServer", "preStart", future.cause());
			}
			else {
				for(InetAddress address : addresses) {
					ChannelFuture future = bootstrap.bind(address, port);
					while(!future.isDone()) future = future.sync();
					if(future.isSuccess()) {
						serverChannels.add(future.channel());
						logger.logp(Level.INFO, "NetServer", "preStart", "Network server is listening on " + address.getHostAddress() + ":" + port);
					}
					else {
						addresses.remove(address);
						logger.throwing("NetServer", "preStart", future.cause());
					}
				}
			}
			if(serverChannels.isEmpty()) {
				logger.logp(Level.SEVERE, "NetServer", "preStart", "Failed to start network server");
				InitFail message = new InitFail(InitFail.NET, "", "");
				logger.logp(Level.FINER, "NetServer", "preStart", "InitFail -> Manager: " + message);
				manager.tell(message , getSelf());
				getContext().stop(getSelf());
			}
		}
		catch (InterruptedException ie) {
			logger.throwing("NetServer", "postStop", ie);
		}
		catch (CertificateException | SSLException ex) {
			logger.logp(Level.SEVERE, "NetServer", "preStart", "Failed to start network server");
			logger.throwing("NetServer", "preStart", ex);
			InitFail message = new InitFail(InitFail.NET, "", "");
			logger.logp(Level.FINER, "NetServer", "preStart", "InitFail -> Manager: " + message);
			manager.tell(message , getSelf());
			getContext().stop(getSelf());
		}
		if(addresses == null) addresses = new ArrayList<>();
		RegisterServer message = new RegisterServer(RegisterServer.NET, new ServerInfo(getSelf(), addresses, port));
		logger.logp(Level.FINER, "NetServer", "preStart", "RegisterServer -> Manager: " + message);
		manager.tell(message, getSelf());
		logger.logp(Level.INFO, "NetServer", "preStart", "Network server is started");
		logger.exiting("NetServer", "preStart");
	}
	
	@Override
	public void postStop() throws Exception {
		logger.entering("NetServer", "postStop");
		for(Channel channel : serverChannels) {
			try {channel.close().sync();}
			catch(InterruptedException ie) {
				logger.throwing("NetServer", "postStop", ie);
			}
		}
		for(Entry<Integer, Channel> entry : channels.entrySet()) {
			try{entry.getValue().close().sync();}
			catch(InterruptedException ie) {
				logger.throwing("NetServer", "postStop", ie);
			}
		}
		workerGroup.shutdownGracefully();
		bossGroup.shutdownGracefully();
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
			initializer.addToWhiteList(((AllowAddress) message).getAddress());
		}
		else if(message instanceof DisconnectChannel) {
			logger.logp(Level.FINER, "NetServer", "onReceive", "NetServer <- DisconnectChannel: " + message);
			DisconnectChannel dc = (DisconnectChannel) message;
			Channel channel = channels.get(dc.getChannelId());
			if(channel != null) {
				logger.logp(Level.FINER, "NetServer", "onReceive", "Got client channel with id " + dc.getChannelId() + " from channel list");
				String address = ((InetSocketAddress) channel.remoteAddress()).getAddress().getHostAddress();
				if(dc.getReason() == DisconnectChannel.NOTOKEN) {
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
							initializer.addToBlackList(address);
						}
						else {
							logger.logp(Level.FINER, "NetServer", "onReceive", "There was " + count + " invalid tokens. Incremented count for " + address);
							fails.put(address, count + 1);
						}
					}
				}
				logger.logp(Level.INFO, "NetServer", "onReceive", "Disconnected client with address " + address + ", reason: Invalid token");
				channel.writeAndFlush(new SystemMessage("Invalid token"));
				try {channel.close().sync();}
				catch(InterruptedException ie) {
					logger.throwing("NetServer", "onReceive", ie);
				}
			}
			else {
				logger.logp(Level.WARNING, "NetServer", "onReceive", "No client channel with id " + dc.getChannelId());
			}
		}
		else if(message instanceof ChannelMessage) {
			logger.logp(Level.FINER, "NetServer", "onReceive", "NetServer <- ChannelMessage: " + message);
			ChannelMessage cm = (ChannelMessage) message;
			String log = "Message: " + cm.getMessage().getClass().getName() + "\n";
			for(Field field : cm.getMessage().getClass().getDeclaredFields()) {
				try {
					field.setAccessible(true);
					Class type = field.getType();
					Object value = field.get(cm.getMessage());
					log += type.getName() + " " + field.getName() + " = " + value + "\n";
				}
				catch(IllegalAccessException iae) {}
			}
			logger.logp(Level.FINEST, "NetServer", "onReceive", log);
			for(int channelId : cm.getChannelIds()) {
				if(channels.get(channelId) != null) {
					logger.logp(Level.FINER, "NetServer", "onReceive", "Message sent to client channel with id " + channelId);
					channels.get(channelId).writeAndFlush(cm.getMessage());
				}
				else {
					logger.logp(Level.WARNING, "NetServer", "onReceive", "No client channel with id " + channelId);
				}
			}
		}
		else
			unhandled(message);
		logger.exiting("NetServer", "onReceive");
	}
	
	public void addChannel(int channelId, Channel channel) {
		logger.entering("NetServer", "addChannel");
		logger.logp(Level.FINER, "NetServer", "addChannel", "Added channel with id " + channelId + " to channel list");
		channels.put(channelId, channel);
		logger.exiting("NetServer", "addChannel");
	}
	
	public void removeChannel(int channelId) {
		logger.entering("NetServer", "removeChannel");
		logger.logp(Level.FINER, "NetServer", "removeChannel", "Removed channel with id " + channelId + " from channel list");
		channels.remove(channelId);
		ChannelDisconnected message = new ChannelDisconnected(channelId);
		logger.logp(Level.FINER, "NetServer", "removeChannel", "ChannelDisconnected -> Enqueuer: " + message);
		enqueuer.tell(message, getSelf());
		logger.exiting("NetServer", "removeChannel");
	}
	
	public void receive(int channelId, Message message) {
		logger.entering("NetServer", "receive");
		ChannelMessage msg = new ChannelMessage(channelId, message);
		logger.logp(Level.FINER, "NetServer", "receive", "Message -> Enqueuer: " + msg);
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
		logger.logp(Level.FINEST, "NetServer", "receive", log);
		enqueuer.tell(msg, getSelf());
		logger.exiting("NetServer", "receive");
	}
}