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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.client.Message;

/**
 *
 * @author Griffin
 */
public class NetServerHandler extends SimpleChannelInboundHandler<Message> {
	private static final Logger logger = Logger.getLogger(NetServerHandler.class.getName());
	private final NetServer server;
	private final int channelId;
	
	public NetServerHandler(NetServer server, int channelId) {
		logger.entering("NetServerHandler", "Constructor");
		logger.logp(Level.FINER, "NetServerHandler", "Constructor", "Client channel with id " + channelId + " initialized");
		this.server = server;
		this.channelId = channelId;
		logger.exiting("NetServerHandler", "Constructor");
	}
	
	@Override
	public void channelActive(final ChannelHandlerContext ctx) {
		logger.entering("NetServerHandler", "channelActive");
		logger.logp(Level.FINE, "NetServerHandler", "channelActive", "Connected client " + ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress() + ", channel id " + channelId);
		server.addChannel(channelId, ctx.channel());
		logger.exiting("NetServerHandler", "channelActive");
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		logger.entering("NetServerHandler", "channelInactive");
		logger.logp(Level.FINE, "NetServerHandler", "channelInactive", "Disconnected client " + ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress() + ", channel id " + channelId);
		server.removeChannel(channelId);
		ctx.close();
		logger.exiting("NetServerHandler", "channelInactive");
	}
	
	@Override
	public void channelRead0(ChannelHandlerContext ctx, Message msg) {
		logger.entering("NetServerHandler", "channelRead0");
		logger.logp(Level.FINE, "NetServerHandler", "channelRead0", "Message " + msg.getClass().getName() + " from client " + ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress() + ", channel id " + channelId);
		server.receive(channelId, msg);
		logger.exiting("NetServerHandler", "channelRead0");
	}
		
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		logger.entering("NetServerHandler", "exceptionCaught");
		logger.throwing("NetServerHandler", "exceptionCaught", cause);
		ctx.close();
		logger.exiting("NetServerHandler", "exceptionCaught");
	}
}