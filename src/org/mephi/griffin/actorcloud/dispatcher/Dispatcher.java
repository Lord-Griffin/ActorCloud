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
package org.mephi.griffin.actorcloud.dispatcher;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.actormanager.messages.ActorRefMessage;
import org.mephi.griffin.actorcloud.actormanager.messages.ActorHandedOff;
import org.mephi.griffin.actorcloud.actormanager.messages.ActorRecovered;
import org.mephi.griffin.actorcloud.actormanager.messages.AllowConnection;
import org.mephi.griffin.actorcloud.client.messages.TokenMessage;
import org.mephi.griffin.actorcloud.client.messages.TokenResponse;
import org.mephi.griffin.actorcloud.dispatcher.messages.AllowAddress;
import org.mephi.griffin.actorcloud.dispatcher.messages.AllowConfirmation;
import org.mephi.griffin.actorcloud.dispatcher.messages.CleanTokens;
import org.mephi.griffin.actorcloud.dispatcher.messages.ClientConnected;
import org.mephi.griffin.actorcloud.dispatcher.messages.ClientDisconnected;
import org.mephi.griffin.actorcloud.dispatcher.messages.DisconnectSession;
import org.mephi.griffin.actorcloud.dispatcher.messages.LastMessage;
import org.mephi.griffin.actorcloud.netserver.messages.SessionDisconnected;
import org.mephi.griffin.actorcloud.netserver.messages.SessionMessage;
import scala.concurrent.duration.Duration;

/**
 *
 * @author Griffin
 */
public class Dispatcher extends UntypedActor {
	private static final Logger logger = Logger.getLogger(Dispatcher.class.getName());
	private ActorRef netServer;
	private Map<Integer, ActorRef> sessions;
	private Map<String, TokenData> tokens;
	private Cancellable schedule = null;
	
	public Dispatcher() {
		logger.entering("Dispatcher", "Constructor");
		logger.exiting("Dispatcher", "Constructor");
	}
	
	@Override
	public void preStart() {
		logger.entering("Dispatcher", "preStart");
		sessions = new HashMap<>();
		tokens = new HashMap<>();
		logger.logp(Level.INFO, "Dispatcher", "preStart", "Dispatcher started");
		logger.exiting("Dispatcher", "preStart");
	}
	
	@Override
	public void postStop() {
		logger.entering("Dispatcher", "postStop");
		if(schedule != null) {
			logger.logp(Level.FINER, "Dispatcher", "postStop", "Stopped monitoring token list");
			schedule.cancel();
		}
		String dump = "Data dump:\n";
		dump += "  netServer" + netServer + "\n";
		dump += "  sessions:\n";
		for(Entry<Integer, ActorRef> entry : sessions.entrySet()) {
			dump += "    " + entry.getKey() + ":\n";
			dump += "    " + entry.getValue() + "\n";
		}
		dump += "  tokens:\n";
		for(Entry<String, TokenData> entry : tokens.entrySet()) {
			dump += "    " + entry.getKey() + ":\n";
			dump += entry.getValue().getDump();
		}
		logger.logp(Level.FINEST, "Dispatcher", "postStop", dump);
		logger.logp(Level.INFO, "Dispatcher", "postStop", "Dispatcher stopped");
		logger.exiting("Dispatcher", "postStop");
	}
	
	/**
	 *
	 * @param message
	 */
	@Override
	public void onReceive(Object message) {
		logger.entering("Dispatcher", "onReceive");
		if(message instanceof ActorRefMessage) {
			logger.logp(Level.FINER, "Dispatcher", "onReceive", "Dispatcher <- ActorRefMessage: " + message);
			ActorRefMessage arm = (ActorRefMessage) message;
			if(arm.getType() == ActorRefMessage.NET) {
				if(arm.getRef() != null) {
					netServer = arm.getRef();
				}
				else {
					logger.logp(Level.FINE, "Dispatcher", "onReceive", "Network server unavailable");
					getContext().stop(getSelf());
				}
			}
		}
		else if(message instanceof AllowConnection) {
			logger.logp(Level.FINER, "Dispatcher", "onReceive", "Dispatcher <- AllowConnection: " + message);
			AllowConnection ac = (AllowConnection) message;
			AllowAddress aa = new AllowAddress(ac.getAddress());
			logger.logp(Level.FINER, "Dispatcher", "onReceive", "AllowAddress -> NetServer: " + aa);
			netServer.tell(aa, getSelf());
			Date timestamp = new Date();
			logger.logp(Level.FINE, "Dispatcher", "onReceive", "Added token " + ac.getToken() + " with timestamp " + timestamp.getTime());
			tokens.put(ac.getToken(), new TokenData(/*ac.getClient(), */ac.getActor(), timestamp));
			if(schedule == null || schedule.isCancelled()) {
				logger.logp(Level.FINER, "Dispatcher", "onReceive", "Started to monitor token list");
				schedule = getContext().system().scheduler().schedule(Duration.Zero(), Duration.create(1000, TimeUnit.MILLISECONDS), getSelf(), new CleanTokens(), getContext().system().dispatcher(), ActorRef.noSender());
			}
			AllowConfirmation msg = new AllowConfirmation(ac.getClient(), ac.getToken());
			logger.logp(Level.FINER, "Dispatcher", "onReceive", "AllowConfirmation -> Manager: " + msg);
			getSender().tell(msg, getSelf());
		}
		else if(message instanceof ActorHandedOff) {
			ActorHandedOff aho = (ActorHandedOff) message;
			for(Entry<Integer, ActorRef> entry : sessions.entrySet()) {
				if(entry.getValue().equals(aho.getOldActor()))
					entry.setValue(aho.getNewActor());
			}
			aho.getOldActor().tell(new LastMessage(), getSelf());
		}
		else if(message instanceof ActorRecovered) {
			ActorRecovered ar = (ActorRecovered) message;
			for(TokenData td : tokens.values()) {
				if(td.getActor().equals(ar.getDeadActor()))
					td.setActor(ar.getRecoveredActor());
			}
			for(Entry<Integer, ActorRef> entry : sessions.entrySet()) {
				if(entry.getValue().equals(ar.getDeadActor()))
					entry.setValue(ar.getRecoveredActor());
			}
		}
		else if(message instanceof SessionMessage) {
			logger.logp(Level.FINER, "Dispatcher", "onReceive", "Dispatcher <- SessionMessage: " + message);
			SessionMessage sm = (SessionMessage) message;
			String log = "Message: " + sm.getMessage().getClass().getName() + "\n";
			for(Field field : sm.getMessage().getClass().getDeclaredFields()) {
				try {
					field.setAccessible(true);
					Class type = field.getType();
					Object value = field.get(sm.getMessage());
					log += type.getName() + " " + field.getName() + " = " + value + "\n";
				}
				catch(IllegalAccessException iae) {}
			}
			logger.logp(Level.FINEST, "Dispatcher", "onReceive", log);
			if(sm.getMessage() instanceof TokenMessage) {
				String token = ((TokenMessage) sm.getMessage()).getToken();
				logger.logp(Level.FINER, "Dispatcher", "onReceive", "Got client token " + token);
				TokenData data = tokens.remove(token);
				if(data != null) {
					logger.logp(Level.FINE, "Dispatcher", "onReceive", "Got token data for token " + token + ": " + data);
//					logger.logp(Level.INFO, "Dispatcher", "onReceive", "Client \"" + data.getClient() + "\" connected");
					ActorRef clientActor = data.getActor();
					if(clientActor != null) {
//						logger.logp(Level.FINER, "Dispatcher", "onReceive", "Got client actor for client " + data.getClient());
//						logger.logp(Level.FINER, "Dispatcher", "onReceive", "Added client session with id " + sm.getSessionId() + ", client " + data.getClient() + ", actor " + clientActor);
//						sessions.put(sm.getSessionId(), new SessionData(data.getClient(), clientActor));
						sessions.put(sm.getSessionId(), clientActor);//new SessionData(clientActor));
						ClientConnected msg = new ClientConnected(sm.getSessionId());
						clientActor.tell(msg, netServer);
//						AddSession msg = new AddSession(sm.getSessionId(), null);
//						logger.logp(Level.FINER, "Dispatcher", "onReceive", "AddSession -> client actor: " + msg);
//						clientActor.tell(msg, getSelf());
//						ClientConnected cc = new ClientConnected(data.getClient(), data.getActor());
//						logger.logp(Level.FINER, "Dispatcher", "onReceive", "ClientConnected -> Manager: " + cc);
//						nodeManager.tell(cc, getSelf());
						SessionMessage response = new SessionMessage(sm.getSessionId(), false, new TokenResponse(clientActor.toString()));
						logger.logp(Level.FINER, "Dispatcher", "onReceive", "TokenResponse -> NetServer: {0}", response);
						netServer.tell(response, getSelf());
					}
					else {
//						logger.logp(Level.SEVERE, "Dispatcher", "onReceive", "Client actor for client " + data.getClient() + " not found");
						DisconnectSession msg = new DisconnectSession(sm.getSessionId(), DisconnectSession.NOTOKEN);
						logger.logp(Level.FINER, "Dispatcher", "onReceive", "DisconnectSession -> NetServer: " + msg);
						netServer.tell(msg, getSelf());
					}
				}
				else {
					logger.logp(Level.INFO, "Dispatcher", "onReceive", "Token " + token + " is invalid");
					DisconnectSession msg = new DisconnectSession(sm.getSessionId(), DisconnectSession.NOTOKEN);
					logger.logp(Level.FINER, "Dispatcher", "onReceive", "DisconnectSession -> NetServer: " + msg);
					netServer.tell(msg, getSelf());
				}
			}
			else {
//				SessionData data = sessions.get(sm.getSessionId());
//				if(data != null) {
//					logger.logp(Level.FINER, "Dispatcher", "onReceive", "Got session data for session with id " + sm.getSessionId() + ": " + data);
					ActorRef clientActor = sessions.get(sm.getSessionId());//data.getActor();
					if(clientActor != null) {
						logger.logp(Level.FINER, "Dispatcher", "onReceive", "Got client actor for session with id " + sm.getSessionId() + ": " + clientActor);
						logger.logp(Level.FINER, "Dispatcher", "onReceive", "Message " + sm.getMessage().getClass().getName() + " -> client actor");
						clientActor.tell(sm.getMessage(), getSelf());
					}
					else {
						logger.logp(Level.SEVERE, "Dispatcher", "onReceive", "Client actor for session with id " + sm.getSessionId() + " not found");
					}
//				}
//				else {
//					logger.logp(Level.SEVERE, "Dispatcher", "onReceive", "Session data for session with id " + sm.getSessionId() + " not found");
//				}
			}
		}
		else if(message instanceof SessionDisconnected) {
			logger.logp(Level.FINER, "Dispatcher", "onReceive", "Dispatcher <- SessionDisconnected: " + message);
			SessionDisconnected cd = (SessionDisconnected) message;
//			SessionData data = sessions.remove(cd.getSessionId());
//			if(data != null) {
//				logger.logp(Level.FINER, "Dispatcher", "onReceive", "Got session data for session with id " + cd.getSessionId() + ": " + data);
//				logger.logp(Level.INFO, "Dispatcher", "onReceive", "Client \"" + data.getClient() + "\" disconnected");
				ActorRef clientActor = sessions.remove(cd.getSessionId());//data.getActor();
				if(clientActor != null) {
					logger.logp(Level.FINER, "Dispatcher", "onReceive", "Got client actor for session with id " + cd.getSessionId() + ": " + clientActor);
					ClientDisconnected msg = new ClientDisconnected();
					clientActor.tell(msg, netServer);
//					RemoveSession rc = new RemoveSession(cd.getSessionId());
//					logger.logp(Level.FINER, "Dispatcher", "onReceive", "RemoveSession -> client actor " + clientActor + ": " + rc);
//					clientActor.tell(rc, getSelf());
//					ClientDisconnected msg = new ClientDisconnected(data.getClient(), data.getActor());
//					logger.logp(Level.FINER, "Dispatcher", "onReceive", "ClientDisconnected -> Manager: " + msg);
//					nodeManager.tell(msg, getSelf());
				}
				else {
					logger.logp(Level.SEVERE, "Dispatcher", "onReceive", "Client actor for session with id " + cd.getSessionId() + " not found");
				}
//			}
//			else {
//				logger.logp(Level.SEVERE, "Dispatcher", "onReceive", "Session data for session with id " + cd.getSessionId() + " not found");
//			}
		}
		else if(message instanceof CleanTokens) {
			logger.logp(Level.FINER, "Dispatcher", "onReceive", "Dispatcher <- CheckTokens");
			Date now = new Date();
			int count = 0;
			List<String> expiredTokens = new ArrayList<>();
			for(Entry<String, TokenData> entry : tokens.entrySet()) {
				if(entry.getValue() == null) {
					logger.logp(Level.SEVERE, "Dispatcher", "onReceive", "Token data for token " + entry.getKey() + " not found");
				}
				else if(entry.getValue().getTimestamp().getTime() < now.getTime() - 1000) {
					logger.logp(Level.FINER, "Dispatcher", "onReceive", "Token " + entry.getKey() + " is expired");
					logger.logp(Level.FINER, "Dispatcher", "onReceive", "Got token data for token " + entry.getKey() + ": " + entry.getValue());
					expiredTokens.add(entry.getKey());
				}
				else {
					logger.logp(Level.FINEST, "Dispatcher", "onReceive", "Token " + entry.getKey() + " is valid for " + (entry.getValue().getTimestamp().getTime() + 1000 - now.getTime()) + " ms");
				}
			}
			for(String token : expiredTokens) {
				count++;
				tokens.remove(token);
			}
			if(count > 0) logger.logp(Level.FINE, "Dispatcher", "onReceive", "Cleaned " + count + " expired tokens");
			if(tokens.isEmpty() && schedule.cancel())
				logger.logp(Level.FINER, "Dispatcher", "onReceive", "Token list is empty, monitoring stopped");
		}
		else {
			unhandled(message);
		}
		logger.exiting("Dispatcher", "onReceive");
	}
}