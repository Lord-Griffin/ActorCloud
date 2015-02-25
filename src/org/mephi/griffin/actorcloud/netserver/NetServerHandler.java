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

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.mephi.griffin.actorcloud.client.Message;

/**
 *
 * @author Griffin
 */
public class NetServerHandler extends IoHandlerAdapter {
	private static final Logger logger = Logger.getLogger(NetServerHandler.class.getName());
	private final NetServer server;
	private int sessionId = 0;
	
	public NetServerHandler(NetServer server) {
		logger.entering("NetServerHandler", "Constructor");
		this.server = server;
		sessionId = 0;
		logger.exiting("NetServerHandler", "Constructor");
	}
	
	@Override
	public void sessionOpened(IoSession session) {
		logger.entering("NetServerHandler", "sessionOpened");
		logger.logp(Level.FINE, "NetServerHandler", "sessionOpened", "Connected client " + ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress() + ", session id " + sessionId);
		session.setAttribute("ID", sessionId);
		server.addSession(sessionId++, session);
		logger.exiting("NetServerHandler", "sessionOpened");
	}
	
	@Override
	public void sessionClosed(IoSession session) {
		logger.entering("NetServerHandler", "sessionClosed");
		logger.logp(Level.FINE, "NetServerHandler", "sessionClosed", "Disconnected client " + ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress() + ", session id " + session.getAttribute("ID"));
		server.removeSession((int) session.getAttribute("ID"));
		logger.exiting("NetServerHandler", "sessionClosed");
	}
	
	@Override
	public void messageReceived(IoSession session, Object message) {
		logger.entering("NetServerHandler", "messageReceived");
		if(!(message instanceof Message)) logger.logp(Level.FINER, "NetServerHandler", "messageReceived", "Discarded unexpected object " + message.getClass().getName());
		else {
			logger.logp(Level.FINE, "NetServerHandler", "messageReceived", "Message " + message.getClass().getName() + " from client " + ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress() + ", session id " + session.getAttribute("ID"));
			server.receive((int) session.getAttribute("ID"), (Message) message);
		}
		logger.exiting("NetServerHandler", "messageReceived");
	}
		
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		logger.entering("NetServerHandler", "exceptionCaught");
		logger.throwing("NetServerHandler", "exceptionCaught", cause);
		session.close(true);
		server.removeSession((int) session.getAttribute("ID"));
		logger.exiting("NetServerHandler", "exceptionCaught");
	}
}