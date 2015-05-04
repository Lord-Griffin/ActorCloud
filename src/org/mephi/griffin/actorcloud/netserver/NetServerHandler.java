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

import org.mephi.griffin.actorcloud.netserver.messages.SessionMessage;
import akka.actor.ActorRef;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.mephi.griffin.actorcloud.client.messages.CloseSession;
import org.mephi.griffin.actorcloud.client.messages.TokenMessage;
import org.mephi.griffin.actorcloud.common.AddSession;
import org.mephi.griffin.actorcloud.common.RemoveSession;

/**
 *
 * @author Griffin
 */
public class NetServerHandler extends IoHandlerAdapter {
	private static final Logger logger = Logger.getLogger(NetServerHandler.class.getName());
	private final ActorRef server;
	private int sessionId = 0;
	
	public NetServerHandler(ActorRef server) {
		logger.entering("NetServerHandler", "Constructor");
		this.server = server;
		sessionId = 0;
		logger.exiting("NetServerHandler", "Constructor");
	}
	
	@Override
	public void sessionOpened(IoSession session) {
		logger.entering("NetServerHandler session id " + sessionId, "sessionOpened");
		logger.logp(Level.FINE, "NetServerHandler session id " + sessionId, "sessionOpened", "Connected client " + ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress());
		session.setAttribute("ID", sessionId);
		AddSession msg = new AddSession(sessionId++, session);
		logger.logp(Level.FINER, "NetServerHandler session id " + sessionId, "sessionOpened", "AddSession -> NetServer: {0}", msg);
		server.tell(msg, ActorRef.noSender());
		logger.exiting("NetServerHandler session id " + session.getAttribute("ID"), "sessionOpened");
	}
	
	@Override
	public void sessionClosed(IoSession session) {
		logger.entering("NetServerHandler session id " + session.getAttribute("ID"), "sessionClosed");
		session.close(true);
		logger.logp(Level.FINE, "NetServerHandler session id " + session.getAttribute("ID"), "sessionClosed", "Disconnected client " + ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress());
		RemoveSession msg = new RemoveSession((int) session.getAttribute("ID"));
		logger.logp(Level.FINER, "NetServerHandler session id " + session.getAttribute("ID"), "sessionClosed", "RemoveSession -> NetServer: {0}", msg);
		server.tell(msg, ActorRef.noSender());
		logger.exiting("NetServerHandler session id " + session.getAttribute("ID"), "sessionClosed");
	}
	
	@Override
	public void messageReceived(IoSession session, Object message) {
		logger.entering("NetServerHandler session id " + session.getAttribute("ID"), "messageReceived");
		if(!(message instanceof TokenMessage) && !(message instanceof CloseSession) && !(message instanceof byte[])) logger.logp(Level.FINER, "NetServerHandler session id " + session.getAttribute("ID"), "messageReceived", "Discarded unexpected object " + message.getClass().getName());
		else {
			logger.logp(Level.FINE, "NetServerHandler session id " + session.getAttribute("ID"), "messageReceived", "Message " + message.getClass().getName() + " from client " + ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress());
			SessionMessage msg = new SessionMessage((int) session.getAttribute("ID"), true, message);
			logger.logp(Level.FINE, "NetServerHandler session id " + session.getAttribute("ID"), "messageReceived", "SessionMessage -> NetServer: {0}", msg);
			server.tell(msg, ActorRef.noSender());
		}
		logger.exiting("NetServerHandler session id " + session.getAttribute("ID"), "messageReceived");
	}
		
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		logger.entering("NetServerHandler session id " + session.getAttribute("ID"), "exceptionCaught");
		logger.throwing("NetServerHandler session id " + session.getAttribute("ID"), "exceptionCaught", cause);
		session.close(true);
		RemoveSession msg = new RemoveSession((int) session.getAttribute("ID"));
		logger.logp(Level.FINER, "NetServerHandler session id " + sessionId, "sessionOpened", "RemoveSession -> NetServer: {0}", msg);
		server.tell(msg, ActorRef.noSender());
		logger.exiting("NetServerHandler session id " + session.getAttribute("ID"), "exceptionCaught");
	}
}