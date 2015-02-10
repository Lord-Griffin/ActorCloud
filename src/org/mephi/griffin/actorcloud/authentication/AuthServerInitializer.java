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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import java.util.logging.Logger;

/**
 *
 * @author Griffin
 */
public class AuthServerInitializer extends ChannelInitializer<SocketChannel> {
	private static final Logger logger = Logger.getLogger(AuthServerInitializer.class.getName());
	private final SslContext sslCtx;
	private final AuthServer server;
	private int channelId = 0;
	
	public AuthServerInitializer(SslContext ctx, AuthServer server) {
		logger.entering("AuthServerInitializer", "Constructor");
		sslCtx = ctx;
		this.server = server;
		logger.exiting("AuthServerInitializer", "Constructor");
	}
	
	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		logger.entering("AuthServerInitializer", "initChannel");
		ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
		//ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
		ch.pipeline().addLast(new AuthServerHandler(server, channelId));
		channelId++;
		logger.exiting("AuthServerInitializer", "initChannel");
	}
}