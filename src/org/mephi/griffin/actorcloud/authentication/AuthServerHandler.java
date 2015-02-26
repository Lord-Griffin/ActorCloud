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

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.mephi.griffin.actorcloud.util.Coder;

/**
 *
 * @author Griffin
 */
public class AuthServerHandler extends IoHandlerAdapter {
	private static final Logger logger = Logger.getLogger(AuthServerHandler.class.getName());
	private final AuthServer server;
	private int sessionId;
	
	public AuthServerHandler(AuthServer server) {
		logger.entering("AuthServerHandler", "Constructor");
		this.server = server;
		sessionId = 0;
		logger.exiting("AuthServerHandler", "Constructor");
	}
	
	@Override
	public void sessionOpened(IoSession session) {
		logger.entering("AuthServerHandler", "sessionOpened");
		logger.logp(Level.FINE, "AuthServerHandler", "sessionOpened", "Connected client " + ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress() + ", session id " + sessionId);
		session.setAttribute("ID", sessionId++);
		logger.exiting("AuthServerHandler", "sessionOpened");
	}
	
	@Override
	public void sessionClosed(IoSession session) {
		logger.entering("AuthServerHandler", "sessionClosed");
		logger.logp(Level.FINE, "AuthServerHandler", "sessionClosed", "Disconnected client " + ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress() + ", session id " + session.getAttribute("ID"));
		server.removeSession((int) session.getAttribute("ID"));
		logger.exiting("AuthServerHandler", "sessionClosed");
	}
	
	@Override
	public void messageReceived(IoSession session, Object message) {
		logger.entering("AuthServerHandler", "messageReceived");
		if(!(message instanceof IoBuffer)) logger.logp(Level.FINER, "AuthServerHandler", "messageReceived", "Discarded unexpected object " + message.getClass().getName());
		else {
			IoBuffer buf = (IoBuffer) message;
			byte[] bytes = new byte[buf.getInt()];
			buf.get(bytes);
			String login = new String(bytes);
			byte[] hash = new byte[buf.getInt()];
			buf.get(hash);
			logger.logp(Level.FINER, "AuthServerHandler", "messageReceived", "Received credentials: " + login + ", " + Coder.toHexString(hash));
			server.checkAuth(login, hash, (int) session.getAttribute("ID"), session);
		}
		logger.exiting("AuthServerHandler", "messageReceived");
	}
	
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		logger.entering("AuthServerHandler", "exceptionCaught");
		logger.throwing("AuthServerHandler", "exceptionCaught", cause);
		session.close(true);
		server.removeSession((int) session.getAttribute("ID"));
		logger.exiting("AuthServerHandler", "exceptionCaught");
	}
}