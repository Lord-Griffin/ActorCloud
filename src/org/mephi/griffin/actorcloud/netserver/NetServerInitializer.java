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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Griffin
 */
public class NetServerInitializer extends ChannelInitializer<SocketChannel> {
	
	private static final Logger logger = Logger.getLogger(NetServerInitializer.class.getName());
	private final SslContext sslCtx;
	private final NetServer server;
	private final ClassLoader cl;
	private final List<String> whiteList;
	private final Map<String, Date> blackList;
	int channelId = 0;
	private final Timer timer;
	private final ListWatcher listWatcher;
	
	public NetServerInitializer(ClassLoader cl, SslContext ctx, NetServer server) {
		logger.entering("NetServerInitializer", "Constructor");
		this.sslCtx = ctx;
		this.server = server;
		this.cl = cl;
		whiteList = new ArrayList<>();
		blackList = new HashMap<>();
		timer = new Timer();
		listWatcher = new ListWatcher(blackList, timer);
		logger.exiting("NetServerInitializer", "Constructor");
	}
	
	@Override
	public void initChannel(SocketChannel ch) {
		logger.entering("NetServerInitializer", "initChannel");
		String address = ((InetSocketAddress) ch.remoteAddress()).getAddress().getHostAddress();
		logger.logp(Level.FINER, "NetServerInitializer", "initChannel", "Got client address: " + address);
		boolean deny;
		synchronized(blackList) {
			deny = blackList.containsKey(address);
			if(deny) logger.logp(Level.FINER, "NetServerInitializer", "initChannel", "Client with address " + address + " is present in black list");
			else logger.logp(Level.FINER, "NetServerInitializer", "initChannel", "Client with address " + address + " is not present in black lsit");
		}
		synchronized(whiteList) {
			deny = deny || !whiteList.contains(address);
			if(whiteList.contains(address)) logger.logp(Level.FINER, "NetServerInitializer", "initChannel", "Client with address " + address + " is present in white list");
			else logger.logp(Level.FINER, "NetServerInitializer", "initChannel", "Client with address " + address + " is not present in white list");
		}
		if(deny) {
			logger.logp(Level.INFO, "NetServerInitializer", "initChannel", "Client with address " + address + " is not allowed to connect");
			try {ch.close().sync();}
			catch(InterruptedException ie) {
				logger.throwing("NetServerInitializer", "initChannel", ie);
			}
			logger.exiting("NetServerInitializer", "initChannel");
			return;
		}
		synchronized(whiteList) {
			logger.logp(Level.FINER, "NetServerInitializer", "initChannel", "Removed address " + address + " from white list");
			whiteList.remove(address);
		}
		ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
		ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.softCachingConcurrentResolver(cl)));
		ch.pipeline().addLast(new ObjectEncoder());
		//ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
		ch.pipeline().addLast(new NetServerHandler(server, channelId));
		channelId++;
		logger.exiting("NetServerInitializer", "initChannel");
	}
	
	public void addToWhiteList(String address) {
		logger.entering("NetServerInitializer", "addToWhiteList");
		synchronized(whiteList) {
		logger.logp(Level.FINE, "NetServerInitializer", "addToWhiteList", "Added address " + address + " to white list");
			whiteList.add(address);
		}
		logger.exiting("NetServerInitializer", "addToWhiteList");
	}
	
	public void addToBlackList(String address) {
		logger.entering("NetServerInitializer", "addToBlackList");
		synchronized(blackList) {
			logger.logp(Level.INFO, "NetServerInitializer", "addToBlackList", "Added address " + address + " to black list");
			if(blackList.isEmpty()) {
				logger.logp(Level.FINE, "NetServerInitializer", "addToBlackList", "ListWatcher started");
				timer.scheduleAtFixedRate(listWatcher, 300000, 5000);
			}
			blackList.put(address, new Date());
		}
		logger.exiting("NetServerInitializer", "addToBlackList");
	}
}