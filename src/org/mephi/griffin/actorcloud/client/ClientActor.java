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
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.client.messages.GetSnapshot;
import org.mephi.griffin.actorcloud.client.messages.Message;
import org.mephi.griffin.actorcloud.client.messages.Ready;
import org.mephi.griffin.actorcloud.client.messages.RecoveryFail;
import org.mephi.griffin.actorcloud.client.messages.RecoverySuccess;
import org.mephi.griffin.actorcloud.client.messages.Snapshot;
import org.mephi.griffin.actorcloud.client.messages.SystemMessage;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.InitSuccess;
import org.mephi.griffin.actorcloud.enqueuer.messages.ClientConnected;
import org.mephi.griffin.actorcloud.enqueuer.messages.ClientDisconnected;
import org.mephi.griffin.actorcloud.netserver.SessionMessage;
import org.mephi.griffin.actorcloud.storage.StorageResult;
import org.mephi.griffin.actorcloud.util.MyObjectInputStream;

/**
 *
 * @author Griffin
 */
public class ClientActor extends UntypedActor {
	private static final Logger logger = Logger.getLogger(ClientActor.class.getName());
	private ClassLoader cl;
	private String name;
	private ActorRef watcher;
	private ActorRef netServer;
	private int sessionId;
	private ActorRef storage;
	private String messageHandlerName;
	private String childHandlerName;
	private MessageHandler handler;
	private ActorRef[] childs;
	private ActorRef deadActor;
	private byte[] snapshot;
	private int snapshotId;
	private ActorRef backupManager;
	
	public ClientActor(ClassLoader cl, ActorRef storage, ActorRef backupManager, String messageHandler, String childHandler, String name) {
		logger.entering("ClientActor(" + name + ")", "Constructor");
		this.cl = cl;
		this.name = name;
		this.watcher = getContext().parent();
		this.netServer = null;
		this.storage = storage;
		this.messageHandlerName = messageHandler;
		this.childHandlerName = childHandler;
		this.snapshot = null;
		this.snapshotId = 0;
		this.backupManager = backupManager;
		logger.logp(Level.FINER, this.name, "Constructor", "NetServer: " + netServer + ", Storage: " + storage + ", messageHandler: " + messageHandlerName + ", childHanlder: " + childHandlerName);
		logger.exiting(this.name, "Constructor");
	}
	
	@SuppressWarnings("LeakingThisInConstructor")
	public ClientActor(ClassLoader cl, ActorRef storage, ActorRef backupManager, String messageHandler, String childHandler, String name, ActorRef deadActor, byte[] snapshot) {
		this(cl, storage, backupManager, messageHandler, childHandler, name);
		this.deadActor = deadActor;
		this.snapshot = snapshot;
	}
	
	@Override
	public void preStart() {
		logger.entering(name, "preStart");
		logger.logp(Level.FINE, name, "preStart", name + " starts");
		if(snapshot != null) {
			String errors = "";
			Serialization serialization = SerializationExtension.get(getContext().system());
			Object obj = serialization.findSerializerFor(List.class).fromBinary(snapshot);
			if(obj instanceof List) {
				List list = (List) obj;
				netServer = (ActorRef) list.get(0);
				sessionId = (int) list.get(1);
				childs = (ActorRef[]) list.get(2);
				byte[] handlerSnapshot = (byte[]) list.get(3);
				try {
					ByteArrayInputStream bais = new ByteArrayInputStream(handlerSnapshot);
					MyObjectInputStream ois = new MyObjectInputStream(bais, cl);
					Object handlerObj = ois.readObject();
					if(handlerObj instanceof MessageHandler) {
						handler = (MessageHandler) handlerObj;
						handler.setActors(this, storage, getContext().parent());
						logger.logp(Level.INFO, name, "preStart", "Client actor for client \"" + name + "\" started");
						RecoverySuccess msg = new RecoverySuccess(deadActor);
						watcher.tell(msg, getSelf());
						logger.logp(Level.FINER, name, "preStart", "InitSuccess -> Manager: " + msg);
					}
					else {
						errors += "Failed to recover message handler: found class " + handlerObj.getClass().getName() + " that is not extending MessageHandler class";
					}
				}
				catch(ClassNotFoundException | IOException ex) {
					errors += ex.getMessage();
				}
			}
			else {
				errors += "Unknown snapshot content";
			}
			if(!errors.equals("")) {
				logger.logp(Level.WARNING, name, "preStart", "There was errors recovering client actor :\n" + errors);
				if(deadActor != null) {
					RecoveryFail msg = new RecoveryFail(deadActor, "Error recovering client actor:\n" + errors);
					logger.logp(Level.FINER, name, "preStart", "InitFail -> Manager: " + msg);
					watcher.tell(msg, getSelf());
				}
			}
		}
		else {
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
				logger.logp(Level.INFO, name, "preStart", "Client actor for client \"" + name + "\" started");
				InitSuccess msg = new InitSuccess(InitSuccess.CLIENT, null, null, 0);
				logger.logp(Level.FINER, name, "preStart", "InitSuccess -> Manager: " + msg);
				watcher.tell(msg, getSelf());
				handler.init();
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
				InitFail msg = new InitFail(InitFail.CLIENT, null, null, 0, "Error initializing message handler:\n" + errors);
				logger.logp(Level.FINER, name, "preStart", "InitFail -> Manager: " + msg);
				watcher.tell(msg, getSelf());
			}
		}
		logger.exiting(name, "preStart");
	}
	
	@Override
	public void postStop() {
		logger.entering(name, "postStop");
		if(handler != null) handler.destroy();
		logger.logp(Level.INFO, name, "postStop", "Client actor for client \"" + name + "\" stopped");
		logger.exiting(name, "postStop");
	}

	/**
	 *
	 * @param message
	 */
	@Override
	public void onReceive(Object message) {
		logger.entering(name, "onReceive");
		if(message instanceof GetSnapshot) {
			try {
				Serialization serialization = SerializationExtension.get(getContext().system());
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(baos);
				oos.writeObject(handler);
				List list = new ArrayList<>();
				list.add(netServer);
				list.add(sessionId);
				list.add(childs);
				list.add(baos.toByteArray());
				byte[] currentSnapshot = serialization.findSerializerFor(list).toBinary(list);
				getSender().tell(new Snapshot(Snapshot.ACTOR, 0, currentSnapshot), getSelf());
			}
			catch(IOException ex) {
				logger.throwing(name, "onReceive", ex);
			}
		}
		else if(message instanceof ClientConnected) {
			logger.logp(Level.FINER, name, "onReceive", name + " <- ClientConnected: " + message);
			ClientConnected cc = (ClientConnected) message;
			sessionId = cc.getSessionId();
			netServer = getSender();
		}
		else if(message instanceof ClientDisconnected) {
			logger.logp(Level.FINER, name, "onReceive", name + " <- ClientDisconnected: " + message);
			netServer = null;
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
				SessionMessage msg = new SessionMessage(sessionId, false, new SystemMessage("Exception during message processing: " + sw.toString()));
				logger.logp(Level.FINER, name, "onReceive", "SessionMessage -> NetServer: " + msg);
				netServer.tell(msg, getSelf());
			}
			try {
				Serialization serialization = SerializationExtension.get(getContext().system());
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(baos);
				oos.writeObject(handler);
				List list = new ArrayList<>();
				list.add(netServer);
				list.add(sessionId);
				list.add(childs);
				list.add(baos.toByteArray());
				byte[] currentSnapshot = serialization.findSerializerFor(list).toBinary(list);
				snapshotId++;
				backupManager.tell(new Snapshot(Snapshot.ACTOR, snapshotId, currentSnapshot), getSelf());
			}
			catch(IOException ex) {
				logger.throwing(name, "onReceive", ex);
			}
		}
		else if(message instanceof InitFail) {
			logger.logp(Level.FINER, name, "onReceive", name + " <- InitFail: " + message);
			SessionMessage msg = new SessionMessage(sessionId, false, new SystemMessage(((InitFail) message).getError()));
			logger.logp(Level.FINER, name, "onReceive", "SessionMessage -> NetServer: " + msg);
			netServer.tell(msg, getSelf());
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
				if(getSender().equals(watcher)) {
					logger.logp(Level.FINER, name, "onReceive", "Message from client");
					handler.execute((Message) message, "client");
					watcher.tell(new Ready(), getSelf());
				}
				else {
					logger.logp(Level.FINER, name, "onReceive", "Message from other client actor: \"" + getSender().path().name() + "\"");
					handler.execute((Message) message, getSender().path().name());
				}
				try {
					Serialization serialization = SerializationExtension.get(getContext().system());
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ObjectOutputStream oos = new ObjectOutputStream(baos);
					oos.writeObject(handler);
					List list = new ArrayList<>();
					list.add(netServer);
					list.add(sessionId);
					list.add(childs);
					list.add(baos.toByteArray());
					byte[] currentSnapshot = serialization.findSerializerFor(list).toBinary(list);
					snapshotId++;
					backupManager.tell(new Snapshot(Snapshot.ACTOR, snapshotId, currentSnapshot), getSelf());
				}
				catch(IOException ex) {
					logger.throwing(name, "onReceive", ex);
				}
			}
			catch(Exception e) {
				logger.throwing(name, "onReceive", e);
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				SessionMessage msg = new SessionMessage(sessionId, false, new SystemMessage("Exception during message processing: " + sw.toString()));
				logger.logp(Level.FINER, name, "onReceive", "SessionMessage -> NetServer: " + msg);
				netServer.tell(msg, getSelf());
			}
		}
		else if(message instanceof String) {
			logger.logp(Level.FINER, name, "onReceive", name + " <- String: " + message);
			SessionMessage msg = new SessionMessage(sessionId, false, new SystemMessage((String) message));
			logger.logp(Level.FINER, name, "onReceive", "SessionMessage -> NetServer: " + msg);
			netServer.tell(msg, getSelf());
		}
		else unhandled(message);
		logger.exiting(name, "onReceive");
	}
	
	public void sendClient(Message message) {
		logger.entering(name, "sendClient");
		if(netServer != null) {
			SessionMessage msg = new SessionMessage(sessionId, false, message);
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
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				ObjectOutputStream oos = new ObjectOutputStream(baos);
				oos.writeObject(message);
				netServer.tell(new SessionMessage(sessionId, false, baos.toByteArray()), getSelf());
			}
			catch(IOException ex) {
				logger.throwing(name, "sendClient", ex);
			}
		}
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
			childs[i] = getContext().actorOf(Props.create(ChildActor.class, cl, storage, childHandlerName), "" + i);
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
	
	public void saveSnapshot() {
		try {
			Serialization serialization = SerializationExtension.get(getContext().system());
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(handler);
			List list = new ArrayList<>();
			list.add(netServer);
			list.add(sessionId);
			list.add(childs);
			list.add(baos.toByteArray());
			byte[] currentSnapshot = serialization.findSerializerFor(list).toBinary(list);
			snapshotId++;
			backupManager.tell(new Snapshot(Snapshot.ACTOR, snapshotId, currentSnapshot), getSelf());
		}
		catch(IOException ex) {
			logger.throwing(name, "onReceive", ex);
		}
	}
}