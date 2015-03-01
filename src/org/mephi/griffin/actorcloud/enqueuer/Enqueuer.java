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
package org.mephi.griffin.actorcloud.enqueuer;

import org.mephi.griffin.actorcloud.common.AddSession;
import org.mephi.griffin.actorcloud.manager.ActorStarted;
import org.mephi.griffin.actorcloud.manager.AllowConnection;
import org.mephi.griffin.actorcloud.manager.ActorStopped;
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
import org.mephi.griffin.actorcloud.client.TokenMessage;
import org.mephi.griffin.actorcloud.client.TokenResponse;
import org.mephi.griffin.actorcloud.common.RemoveSession;
import org.mephi.griffin.actorcloud.manager.ActorRefMessage;
import org.mephi.griffin.actorcloud.netserver.SessionDisconnected;
import org.mephi.griffin.actorcloud.netserver.SessionMessage;
import scala.concurrent.duration.Duration;

/**
 *
 * @author Griffin
 */
public class Enqueuer extends UntypedActor {
	private static final Logger logger = Logger.getLogger(Enqueuer.class.getName());
	private ActorRef netServer;
	private ActorRef manager;
	private Map<String, ActorRef> clientActors;
	private Map<Integer, SessionData> sessions;
	private Map<String, TokenData> tokens;
	private Cancellable schedule = null;
	
	public Enqueuer() {
		logger.entering("Enqueuer", "Constructor");
		manager = getContext().parent();
		logger.exiting("Enqueuer", "Constructor");
	}
	
	@Override
	public void preStart() {
		logger.entering("Enqueuer", "preStart");
		clientActors = new HashMap<>();
		sessions = new HashMap<>();
		tokens = new HashMap<>();
		logger.logp(Level.INFO, "Enqueuer", "preStart", "Enqueuer started");
		logger.exiting("Enqueuer", "preStart");
	}
	
	@Override
	public void postStop() {
		logger.entering("Enqueuer", "postStop");
		if(schedule != null) {
			logger.logp(Level.FINER, "Enqueuer", "postStop", "Stopped monitoring token list");
			schedule.cancel();
		}
		logger.logp(Level.INFO, "Enqueuer", "postStop", "Enqueuer stopped");
		logger.exiting("Enqueuer", "postStop");
	}
	
	/**
	 *
	 * @param message
	 */
	@Override
	public void onReceive(Object message) {
		logger.entering("Enqueuer", "onReceive");
		if(message instanceof ActorRefMessage) {
			logger.logp(Level.FINER, "Enqueuer", "onReceive", "Enqueuer <- ActorRefMessage: " + message);
			ActorRefMessage arm = (ActorRefMessage) message;
			if(arm.getType() == ActorRefMessage.NET) {
				if(arm.getRef() != null) {
					netServer = arm.getRef();
				}
				else {
					logger.logp(Level.FINE, "Enqueuer", "onReceive", "Network server unavailable");
					getContext().stop(getSelf());
				}
			}
		}
		else if(message instanceof ActorStarted) {
			logger.logp(Level.FINER, "Enqueuer", "onReceive", "Enqueuer <- ActorStarted: " + message);
			ActorStarted as = (ActorStarted) message;
			logger.logp(Level.FINE, "Enqueuer", "onReceive", "Added client actor " + as.getRef());
			clientActors.put(as.getClient(), as.getRef());
		}
		else if(message instanceof ActorStopped) {
			logger.logp(Level.FINER, "Enqueuer", "onReceive", "Enqueuer <- ActorStopped: " + message);
			ActorStopped as = (ActorStopped) message;
			ActorRef ref = clientActors.remove(as.getClient());
			logger.logp(Level.FINE, "Enqueuer", "onReceive", "Removed client actor " + ref);
		}
		else if(message instanceof AllowConnection) {
			logger.logp(Level.FINER, "Enqueuer", "onReceive", "Enqueuer <- AllowConnection: " + message);
			AllowConnection ac = (AllowConnection) message;
			AllowAddress aa = new AllowAddress(ac.getAddress());
			logger.logp(Level.FINER, "Enqueuer", "onReceive", "AllowAddress -> NetServer: " + aa);
			netServer.tell(aa, getSelf());
			Date timestamp = new Date();
			logger.logp(Level.FINE, "Enqueuer", "onReceive", "Added token " + ac.getToken() + " with timestamp " + timestamp.getTime());
			tokens.put(ac.getToken(), new TokenData(ac.getClient(), timestamp));
			if(schedule == null || schedule.isCancelled()) {
				logger.logp(Level.FINER, "Enqueuer", "onReceive", "Started to monitor token list");
				schedule = getContext().system().scheduler().schedule(Duration.Zero(), Duration.create(1000, TimeUnit.MILLISECONDS), getSelf(), new CleanTokens(), getContext().system().dispatcher(), ActorRef.noSender());
			}
			AllowConfirmation msg = new AllowConfirmation(ac.getClient(), ac.getToken());
			logger.logp(Level.FINER, "Enqueuer", "onReceive", "AllowConfirmation -> Manager: " + msg);
			getSender().tell(msg, getSelf());
		}
		else if(message instanceof SessionMessage) {
			logger.logp(Level.FINER, "Enqueuer", "onReceive", "Enqueuer <- SessionMessage: " + message);
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
			logger.logp(Level.FINEST, "Enqueuer", "onReceive", log);
			if(sm.getMessage() instanceof TokenMessage) {
				String token = ((TokenMessage) sm.getMessage()).getToken();
				logger.logp(Level.FINER, "Enqueuer", "onReceive", "Got client token " + token);
				TokenData data = tokens.get(token);
				if(data != null) {
					logger.logp(Level.FINE, "Enqueuer", "onReceive", "Got token data for token " + token + ": " + data);
					logger.logp(Level.INFO, "Enqueuer", "onReceive", "Client \"" + data.getClient() + "\" connected");
					logger.logp(Level.FINER, "Enqueuer", "onReceive", "Removed token " + token);
					tokens.remove(token);
					ActorRef clientActor = clientActors.get(data.getClient());
					if(clientActor != null) {
						logger.logp(Level.FINER, "Enqueuer", "onReceive", "Got client actor for client " + data.getClient());
						logger.logp(Level.FINER, "Enqueuer", "onReceive", "Added client session with id " + sm.getSessionIds().get(0) + ", client " + data.getClient() + ", actor " + clientActors.get(data.getClient()));
						sessions.put(sm.getSessionIds().get(0), new SessionData(data.getClient(), clientActor));
						AddSession msg = new AddSession(sm.getSessionIds().get(0), null);
						logger.logp(Level.FINER, "Enqueuer", "onReceive", "AddSession -> client actor: " + msg);
						clientActors.get(data.getClient()).tell(msg, getSelf());
						ClientConnected cc = new ClientConnected(data.getClient());
						logger.logp(Level.FINER, "Enqueuer", "onReceive", "ClientConnected -> Manager: " + cc);
						manager.tell(cc, getSelf());
						SessionMessage response = new SessionMessage(sm.getSessionIds().get(0), new TokenResponse(), false);
						logger.logp(Level.FINER, "Enqueuer", "onReceive", "TokenResponse -> NetServer: {0}", response);
						netServer.tell(response, getSelf());
					}
					else {
						logger.logp(Level.SEVERE, "Enqueuer", "onReceive", "Client actor for client " + data.getClient() + " not found");
						DisconnectSession msg = new DisconnectSession(sm.getSessionIds().get(0), DisconnectSession.NOTOKEN);
						logger.logp(Level.FINER, "Enqueuer", "onReceive", "DisconnectSession -> NetServer: " + msg);
						netServer.tell(msg, getSelf());
					}
				}
				else {
					logger.logp(Level.INFO, "Enqueuer", "onReceive", "Token " + token + " is invalid");
					DisconnectSession msg = new DisconnectSession(sm.getSessionIds().get(0), DisconnectSession.NOTOKEN);
					logger.logp(Level.FINER, "Enqueuer", "onReceive", "DisconnectSession -> NetServer: " + msg);
					netServer.tell(msg, getSelf());
				}
			}
			else {
				SessionData data = sessions.get(sm.getSessionIds().get(0));
				if(data != null) {
					logger.logp(Level.FINER, "Enqueuer", "onReceive", "Got session data for session with id " + sm.getSessionIds().get(0) + ": " + data);
					ActorRef clientActor = data.getActor();
					if(clientActor != null) {
						logger.logp(Level.FINER, "Enqueuer", "onReceive", "Got client actor for session with id " + sm.getSessionIds().get(0) + ": " + clientActor);
						logger.logp(Level.FINER, "Enqueuer", "onReceive", "Message " + sm.getMessage().getClass().getName() + " -> client actor");
						clientActor.tell(sm.getMessage(), getSelf());
					}
					else {
						logger.logp(Level.SEVERE, "Enqueuer", "onReceive", "Client actor for session with id " + sm.getSessionIds().get(0) + " not found");
					}
				}
				else {
					logger.logp(Level.SEVERE, "Enqueuer", "onReceive", "Session data for session with id " + sm.getSessionIds().get(0) + " not found");
				}
			}
		}
		else if(message instanceof SessionDisconnected) {
			logger.logp(Level.FINER, "Enqueuer", "onReceive", "Enqueuer <- SessionDisconnected: " + message);
			SessionDisconnected cd = (SessionDisconnected) message;
			SessionData data = sessions.remove(cd.getSessionId());
			if(data != null) {
				logger.logp(Level.FINER, "Enqueuer", "onReceive", "Got session data for session with id " + cd.getSessionId() + ": " + data);
				logger.logp(Level.INFO, "Enqueuer", "onReceive", "Client \"" + data.getClient() + "\" disconnected");
				ActorRef clientActor = data.getActor();
				if(clientActor != null) {
					logger.logp(Level.FINER, "Enqueuer", "onReceive", "Got client actor for session with id " + cd.getSessionId() + ": " + clientActor);
					RemoveSession rc = new RemoveSession(cd.getSessionId());
					logger.logp(Level.FINER, "Enqueuer", "onReceive", "RemoveSession -> client actor: " + rc);
					clientActor.tell(rc, getSelf());
					ClientDisconnected msg = new ClientDisconnected(data.getClient());
					logger.logp(Level.FINER, "Enqueuer", "onReceive", "ClientDisconnected -> Manager: " + msg);
					manager.tell(msg, getSelf());
				}
				else {
					logger.logp(Level.SEVERE, "Enqueuer", "onReceive", "Client actor for session with id " + cd.getSessionId() + " not found");
				}
			}
			else {
				logger.logp(Level.SEVERE, "Enqueuer", "onReceive", "Session data for session with id " + cd.getSessionId() + " not found");
			}
		}
		else if(message instanceof CleanTokens) {
			logger.logp(Level.FINER, "Enqueuer", "onReceive", "Enqueuer <- CheckTokens");
			Date now = new Date();
			int count = 0;
			List<String> expiredTokens = new ArrayList<>();
			for(Entry<String, TokenData> entry : tokens.entrySet()) {
				if(entry.getValue() == null) {
					logger.logp(Level.SEVERE, "Enqueuer", "onReceive", "Token data for token " + entry.getKey() + " not found");
				}
				else if(entry.getValue().getTimestamp().getTime() < now.getTime() - 1000) {
					logger.logp(Level.FINER, "Enqueuer", "onReceive", "Token " + entry.getKey() + " is expired");
					logger.logp(Level.FINER, "Enqueuer", "onReceive", "Got token data for token " + entry.getKey() + ": " + entry.getValue());
					expiredTokens.add(entry.getKey());
				}
				else {
					logger.logp(Level.FINEST, "Enqueuer", "onReceive", "Token " + entry.getKey() + " is valid for " + (entry.getValue().getTimestamp().getTime() + 1000 - now.getTime()) + " ms");
				}
			}
			for(String token : expiredTokens) {
				count++;
				tokens.remove(token);
			}
			if(count > 0) logger.logp(Level.FINE, "Enqueuer", "onReceive", "Cleaned " + count + " expired tokens");
			if(tokens.isEmpty() && schedule.cancel())
				logger.logp(Level.FINER, "Enqueuer", "onReceive", "Token list is empty, monitoring stopped");
		}
		else {
			unhandled(message);
		}
		logger.exiting("Enqueuer", "onReceive");
	}
}