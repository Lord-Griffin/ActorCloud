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
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.mephi.griffin.actorcloud.client.messages.AuthRequest;
import org.mephi.griffin.actorcloud.common.RemoveSession;

/**
 *
 * @author Griffin
 */
public class AuthServerHandler extends IoHandlerAdapter {
	private static final Logger logger = Logger.getLogger(AuthServerHandler.class.getName());
	private final ActorRef server;
	private int sessionId;
	
	public AuthServerHandler(ActorRef server) {
		logger.entering("AuthServerHandler", "Constructor");
		this.server = server;
		sessionId = 0;
		logger.exiting("AuthServerHandler", "Constructor");
	}
	
	@Override
	public void sessionOpened(IoSession session) {
		logger.entering("AuthServerHandler session id " + sessionId, "sessionOpened");
		logger.logp(Level.FINE, "AuthServerHandler session id " + sessionId, "sessionOpened", "Connected client " + ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress() + ", session id " + sessionId);
		session.setAttribute("ID", sessionId++);
		logger.exiting("AuthServerHandler session id " + session.getAttribute("ID"), "sessionOpened");
	}
	
	@Override
	public void sessionClosed(IoSession session) {
		logger.entering("AuthServerHandler session id " + session.getAttribute("ID"), "sessionClosed");
		session.close(true);
		logger.logp(Level.FINE, "AuthServerHandler session id " + session.getAttribute("ID"), "sessionClosed", "Disconnected client " + ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress() + ", session id " + session.getAttribute("ID"));
		RemoveSession msg = new RemoveSession((int) session.getAttribute("ID"));
		logger.logp(Level.FINER, "AuthServerHandler session id " + session.getAttribute("ID"), "sessionClosed", "RemoveSession -> AuthServer: {0}", msg);
		server.tell(msg, null);
		logger.exiting("AuthServerHandler session id " + session.getAttribute("ID"), "sessionClosed");
	}
	
	@Override
	public void messageReceived(IoSession session, Object message) {
		logger.entering("AuthServerHandler session id " + session.getAttribute("ID"), "messageReceived");
		if(!(message instanceof AuthRequest)) logger.logp(Level.FINER, "AuthServerHandler", "messageReceived", "Discarded unexpected object " + message.getClass().getName());
		else {
			AuthRequest ar = (AuthRequest) message;
			logger.logp(Level.FINER, "AuthServerHandler session id " + session.getAttribute("ID"), "messageReceived", "Received authentication request {0}", message);
			AuthData msg = new AuthData(ar.getLogin(), ar.getHash(), ar.getActor(), (int) session.getAttribute("ID"), session);
			logger.logp(Level.FINER, "AuthServerHandler session id " + session.getAttribute("ID"), "messageReceived", "AuthData -> AuthServer: {0}", msg);
			server.tell(msg, null);
		}
		logger.exiting("AuthServerHandler session id " + session.getAttribute("ID"), "messageReceived");
	}
	
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		logger.entering("AuthServerHandler session id " + session.getAttribute("ID"), "exceptionCaught");
		logger.throwing("AuthServerHandler session id " + session.getAttribute("ID"), "exceptionCaught", cause);
		session.close(true);
		RemoveSession msg = new RemoveSession((int) session.getAttribute("ID"));
		logger.logp(Level.FINER, "AuthServerHandler session id " + session.getAttribute("ID"), "exceptionCaught", "RemoveSession -> AuthServer: {0}", msg);
		server.tell(msg, null);
		logger.exiting("AuthServerHandler session id " + session.getAttribute("ID"), "exceptionCaught");
	}
}