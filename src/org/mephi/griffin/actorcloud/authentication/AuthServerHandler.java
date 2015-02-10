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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.util.Hex;

/**
 *
 * @author Griffin
 */
public class AuthServerHandler extends ChannelInboundHandlerAdapter {
	private static final Logger logger = Logger.getLogger(AuthServerHandler.class.getName());
	private final AuthServer server;
	private final int channelId;
	
	public AuthServerHandler(AuthServer server, int channelId) {
		logger.entering("AuthServerHandler", "Constructor");
		this.server = server;
		this.channelId = channelId;
		logger.logp(Level.FINER, "AuthServerHandler", "Constructor", "Initialized channel with id " + channelId);
		logger.exiting("AuthServerHandler", "Constructor");
	}
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		logger.logp(Level.FINE, "AuthServerHandler", "channelActive", "Connected client " + ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress() + ", channel id " + channelId);
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		logger.entering("AuthServerHandler", "channelInactive");
		logger.logp(Level.FINE, "AuthServerHandler", "channelInactive", "Disconnected client " + ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress() + ", channel id " + channelId);
		server.removeChannel(channelId);
		ctx.close();
		logger.exiting("AuthServerHandler", "channelInactive");
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		logger.entering("AuthServerHandler", "channelRead");
		if(!(msg instanceof ByteBuf)) logger.logp(Level.FINER, "AuthServerHandler", "channelRead", "Discarded unexpected object " + msg.getClass().getName());
		else {
			ByteBuf buff = (ByteBuf) msg;
			byte[] bytes = new byte[buff.readInt()];
			buff.readBytes(bytes);
			String login = new String(bytes);
			byte[] hash = new byte[buff.readInt()];
			buff.readBytes(hash);
			logger.logp(Level.FINER, "AuthServerHandler", "channelRead", "Received credentials: " + login + ", " + Hex.toHexString(hash));
			server.checkAuth(login, hash, channelId, ctx.channel());
		}
		logger.exiting("AuthServerHandler", "channelRead");
	}
		
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		logger.entering("AuthServerHandler", "exceptionCaught");
		logger.throwing("AuthServerHandler", "exceptionCaught", cause);
		ctx.close();
		logger.exiting("AuthServerHandler", "exceptionCaught");
	}
}