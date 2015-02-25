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
package org.mephi.griffin.actorcloud.client;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.InitSuccess;
import org.mephi.griffin.actorcloud.enqueuer.AddSession;
import org.mephi.griffin.actorcloud.enqueuer.RemoveSession;
import org.mephi.griffin.actorcloud.manager.ActorRefMessage;
import org.mephi.griffin.actorcloud.manager.ClientFindResult;
import org.mephi.griffin.actorcloud.netserver.SessionMessage;
import org.mephi.griffin.actorcloud.storage.StorageResult;

/**
 *
 * @author Griffin
 */
public class ClientActor extends UntypedActor {
	private static final Logger logger = Logger.getLogger(ClientActor.class.getName());
	private final ClassLoader cl;
	private String name;
	private ActorRef netServer;
	private ActorRef storage;
	private List<Integer> sessions;
	private String messageHandlerName;
	private String childHandlerName;
	private MessageHandler handler;
	private ActorRef[] childs;
	
	public ClientActor(String name, ClassLoader cl, ActorRef netServer, ActorRef storage, String messageHandler, String childHandler) {
		logger.entering("ClientActor(" + name + ")", "Constructor");
		this.name = "ClientActor(" + name + ")";
		this.cl = cl;
		this.netServer = netServer;
		this.storage = storage;
		this.sessions = new ArrayList<>();
		this.messageHandlerName = messageHandler;
		this.childHandlerName = childHandler;
		logger.logp(Level.FINER, this.name, "Constructor", "NetServer: " + netServer + ", Storage: " + storage + ", messageHandler: " + messageHandlerName + ", childHanlder: " + childHandlerName);
		logger.exiting(this.name, "Constructor");
	}
	
	@Override
	public void preStart() {
		logger.entering(name, "preStart");
		logger.logp(Level.FINE, name, "preStart", name + " starts");
		String errors = "";
		try {
			logger.logp(Level.FINER, name, "preStart", "Loading message handler class: " + messageHandlerName);
			Class handlerClass = cl.loadClass(messageHandlerName);
			if(!MessageHandler.class.isAssignableFrom(handlerClass)) {
				errors += "Handler class doesn't inherit MessageHandler class\n";
			}
			logger.logp(Level.FINER, name, "preStart", "Acquiring constructor");
			Constructor con = handlerClass.getDeclaredConstructor();
			logger.logp(Level.FINER, name, "preStart", "Initializing instance");
			handler = (MessageHandler) con.newInstance();
			logger.logp(Level.FINER, name, "preStart", "Initializing config");
			handler.setActors(this, storage, getContext().parent());
		}
		catch(ClassNotFoundException cnfe) {
			logger.throwing(name, "preStart", cnfe);
			errors += "Handler class not found: " + cnfe.getMessage() + "\n";
		}
		catch(NoSuchMethodException nsme) {
			logger.throwing(name, "preStart", nsme);
			errors += "Failed to get handler constructor: " + nsme.getMessage() + "\n";
		}
		catch(InvocationTargetException | InstantiationException | ClassCastException | IllegalAccessException e) {
			logger.throwing(name, "preStart", e);
			errors += "Failed to get handler instance: " + e.getMessage() + "\n";
		}
		catch(Exception e) {
			logger.throwing(name, "preStart", e);
			errors += "Main actor initialization error: " + e.getMessage() + "\n";
		}
		if(!errors.equals("")) {
			logger.logp(Level.WARNING, name, "preStart", "There was errors loading message handler:\n" + errors);
			InitFail msg = new InitFail(InitFail.CLIENT, getSelf().path().name(), "Error initializing message handler:\n" + errors);
			logger.logp(Level.FINER, name, "preStart", "InitFail -> Manager: " + msg);
			getContext().parent().tell(msg, getSelf());
			getContext().stop(getSelf());
		}
		else {
			logger.logp(Level.INFO, name, "preStart", "Client actor for client \"" + getSelf().path().name() + "\" started");
			InitSuccess msg = new InitSuccess(InitSuccess.CLIENT, getSelf().path().name());
			logger.logp(Level.FINER, name, "preStart", "InitSuccess -> Manager: " + msg);
			getContext().parent().tell(msg, getSelf());
			handler.init();
		}
		logger.exiting(name, "preStart");
	}
	
	@Override
	public void postStop() {
		logger.entering(name, "postStop");
		if(handler != null) handler.destroy();
		logger.logp(Level.INFO, name, "postStop", "Client actor for client \"" + getSelf().path().name() + "\" stopped");
		logger.exiting(name, "postStop");
	}

	/**
	 *
	 * @param message
	 */
	@Override
	public void onReceive(Object message) {
		logger.entering(name, "onReceive");
		if(message instanceof AddSession) {
			logger.logp(Level.FINER, name, "onReceive", name + " <- AddSession: " + message);
			AddSession ac = (AddSession) message;
			if(sessions.contains(ac.getId())) {
				logger.logp(Level.SEVERE, name, "onReceive", "Client session id " + ac.getId() + " already present in list");
			}
			else {
				logger.logp(Level.FINER, name, "onReceive", "Added client session id " + ac.getId() + " to list");
				sessions.add(ac.getId());
			}
		}
		else if(message instanceof RemoveSession) {
			logger.logp(Level.FINER, name, "onReceive", name + " <- RemoveSession: " + message);
			RemoveSession rc = (RemoveSession) message;
			if(!sessions.contains(rc.getId())) {
				logger.logp(Level.SEVERE, name, "onReceive", "Client session id " + rc.getId() + " not found in list");
			}
			else {
				logger.logp(Level.FINER, name, "onReceive", "Removed client session id " + rc.getId() + " from list");
				sessions.remove(new Integer(rc.getId()));
			}
		}
		else if(message instanceof StorageResult) {
			logger.logp(Level.FINER, name, "onReceive", name + " <- StorageResult: " + message);
			logger.logp(Level.FINER, name, "onReceive", "Handling storage result to client message handler");
			try {
				handler.execute((StorageResult) message, "storage");
			}
			catch(Exception e) {
				logger.throwing(name, "onReceive", e);
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				SessionMessage msg = new SessionMessage(sessions, new SystemMessage("Exception during message processing: " + sw.toString()));
				logger.logp(Level.FINER, name, "onReceive", "SessionMessage -> NetServer: " + msg);
				netServer.tell(msg, getSelf());
			}
		}
		else if(message instanceof InitFail) {
			logger.logp(Level.FINER, name, "onReceive", name + " <- InitFail: " + message);
			SessionMessage msg = new SessionMessage(sessions, new SystemMessage(((InitFail) message).getError()));
			logger.logp(Level.FINER, name, "onReceive", "SessionMessage -> NetServer: " + msg);
			netServer.tell(msg, getSelf());
		}
		else if(message instanceof ClientFindResult) {
			logger.logp(Level.FINER, name, "onReceive", name + " <- ClientFindResult: " + message);
			ClientFindResult cfr = (ClientFindResult) message;
			logger.logp(Level.FINER, name, "onReceive", "Handling client search result to client message handler");
			try {
				handler.execute(new ClientResult(cfr.getRef() != null, cfr.getClient()), "manager");
			}
			catch(Exception e) {
				logger.throwing(name, "onReceive", e);
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				SessionMessage msg = new SessionMessage(sessions, new SystemMessage("Exception during message processing: " + sw.toString()));
				logger.logp(Level.FINER, name, "onReceive", "SessionMessage -> NetServer: " + msg);
				netServer.tell(msg, getSelf());
			}
		}
		else if(message instanceof Message) {
			logger.logp(Level.FINE, name, "onReceive", name + " <- Message: " + message.getClass().getName());
			String log = "Message: " + message.getClass().getName() + "\n";
			for(Field field : message.getClass().getDeclaredFields()) {
				try {
					field.setAccessible(true);
					Class type = field.getType();
					Object value = field.get(message);
					log += type.getName() + " " + field.getName() + " = " + value + "\n";
				}
				catch(IllegalAccessException iae) {}
			}
			logger.logp(Level.FINEST, name, "onReceive", log);
			try {
				if(getSender().path().name().equals("enqueuer")) {
					logger.logp(Level.FINER, name, "onReceive", "Message from client");
					handler.execute((Message) message, "client");
				}
				else {
					logger.logp(Level.FINER, name, "onReceive", "Message from other client actor: \"" + getSender().path().name() + "\"");
					handler.execute((Message) message, getSender().path().name());
				}
			}
			catch(Exception e) {
				logger.throwing(name, "onReceive", e);
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				SessionMessage msg = new SessionMessage(sessions, new SystemMessage("Exception during message processing: " + sw.toString()));
				logger.logp(Level.FINER, name, "onReceive", "SessionMessage -> NetServer: " + msg);
				netServer.tell(msg, getSelf());
			}
		}
		else if(message instanceof String) {
			logger.logp(Level.FINER, name, "onReceive", name + " <- String: " + message);
			SessionMessage msg = new SessionMessage(sessions, new SystemMessage((String) message));
			logger.logp(Level.FINER, name, "onReceive", "SessionMessage -> NetServer: " + msg);
			netServer.tell(msg, getSelf());
		}
		else unhandled(message);
		logger.exiting(name, "onReceive");
	}
	
	public void sendClient(Message message) {
		logger.entering(name, "sendClient");
		SessionMessage msg = new SessionMessage(sessions, message);
		logger.logp(Level.FINE, name, "sendClient", "SessionMessage -> NetServer: " + msg);
		String log = "Message: " + message.getClass().getName() + "\n";
		for(Field field : message.getClass().getDeclaredFields()) {
			try {
				field.setAccessible(true);
				Class type = field.getType();
				Object value = field.get(message);
				log += type.getName() + " " + field.getName() + " = " + value + "\n";
			}
			catch(IllegalAccessException iae) {}
		}
		logger.logp(Level.FINEST, name, "sendClient", log);
		netServer.tell(msg, getSelf());
		logger.exiting(name, "sendClient");
	}
	
	public void sendClientActor(final String client, Message message) {
		logger.entering(name, "sendClientActor");
		ActorSelection as = getContext().actorSelection("/user/actor-manager/" + client);
		logger.logp(Level.FINE, name, "sendClientActor", "Message -> " + as + ": " + message.getClass().getName());
		String log = "Message: " + message.getClass().getName() + "\n";
		for(Field field : message.getClass().getDeclaredFields()) {
			try {
				field.setAccessible(true);
				Class type = field.getType();
				Object value = field.get(message);
				log += type.getName() + " " + field.getName() + " = " + value + "\n";
			}
			catch(IllegalAccessException iae) {}
		}
		logger.logp(Level.FINEST, name, "sendClientActor", log);
		as.tell(message, getSelf());
		logger.exiting(name, "sendClientActor");
	}
	
	public void createChilds(int count) {
		logger.entering(name, "createChilds");
		childs = new ActorRef[count];
		logger.logp(Level.FINE, name, "createChilds", "Creating " + count + " childs");
		for(int i = 0; i < count; i++) {
			childs[i] = getContext().actorOf(Props.create(ChildActor.class, cl), "" + i);
			ActorRefMessage msg = new ActorRefMessage(ActorRefMessage.STORAGE, storage);
			logger.logp(Level.FINER, name, "createChilds", "ActorRefMessage -> " + childs[i] + ": " + msg);
			childs[i].tell(msg, getSelf());
			logger.logp(Level.FINER, name, "createChilds", "Child handler name -> " + childs[i] + ": " + childHandlerName);
			childs[i].tell(childHandlerName, getSelf());
		}
		logger.exiting(name, "createChilds");
	}
	
	public void sendChild(int num, Message message) {
		logger.entering(name, "sendChild");
		logger.logp(Level.FINE, name, "sendChild", "Message -> " + childs[num] + ": " + message.getClass().getName());
		String log = "Message: " + message.getClass().getName() + "\n";
		for(Field field : message.getClass().getDeclaredFields()) {
			try {
				field.setAccessible(true);
				Class type = field.getType();
				Object value = field.get(message);
				log += type.getName() + " " + field.getName() + " = " + value + "\n";
			}
			catch(IllegalAccessException iae) {}
		}
		logger.logp(Level.FINEST, name, "sendChild", log);
		childs[num].tell(message, getSelf());
		logger.exiting(name, "sendChild");
	}
}