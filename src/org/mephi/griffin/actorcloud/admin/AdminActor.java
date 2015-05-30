/*
 * Copyright 2015 Griffin.
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
package org.mephi.griffin.actorcloud.admin;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.mephi.griffin.actorcloud.client.messages.ErrorMessage;
import org.mephi.griffin.actorcloud.client.messages.GetSnapshot;
import org.mephi.griffin.actorcloud.client.messages.Ready;
import org.mephi.griffin.actorcloud.client.messages.RecoverySuccess;
import org.mephi.griffin.actorcloud.client.messages.Snapshot;
import org.mephi.griffin.actorcloud.common.InitSuccess;
import org.mephi.griffin.actorcloud.dispatcher.messages.ClientConnected;
import org.mephi.griffin.actorcloud.dispatcher.messages.ClientDisconnected;
import org.mephi.griffin.actorcloud.netserver.messages.SessionMessage;
import org.mephi.griffin.actorcloud.storage.Entity;
import org.mephi.griffin.actorcloud.storage.SimpleQuery;
import org.mephi.griffin.actorcloud.storage.Storage;
import org.mephi.griffin.actorcloud.storage.messages.StorageResult;

/**
 *
 * @author Griffin
 */
public class AdminActor extends UntypedActor {
	
	private Storage storage;
	private ActorRef backupManager;
	private ActorRef netServer;
	private int sessionId;
	private ActorRef watcher;
	private ActorRef deadActor;
	private int page;
	
	public AdminActor(ActorRef storage, ActorRef backupManager) {
		this.storage = new Storage(storage, getSelf());
		this.backupManager = backupManager;
		this.netServer = null;
		this.watcher = getContext().parent();
		this.deadActor = null;
	}
	
	public AdminActor(ActorRef storage, ActorRef backupManager, ActorRef deadActor, byte[] snapshot) {
		this(storage, backupManager);
		this.deadActor = deadActor;
	}
	
	@Override
	public void preStart() {
		if(deadActor != null) {
			RecoverySuccess msg = new RecoverySuccess(deadActor);
			watcher.tell(msg, getSelf());
		}
		else {
			InitSuccess msg = new InitSuccess(InitSuccess.CLIENT, null, null, 0);
			watcher.tell(msg, getSelf());
		}
		System.out.println("AdminActor " + getSelf().path().name() + " starts");
	}
	
	@Override
	public void postStop() {
		System.out.println("AdminActor " + getSelf().path().name() + " is stopped");
	}

	/**
	 *
	 * @param message
	 */
	@Override
	public void onReceive(Object message) {
		if(message instanceof GetSnapshot) {
			byte[] currentSnapshot = new byte[1];
			getSender().tell(new Snapshot(Snapshot.ACTOR, 0, currentSnapshot), getSelf());
		}
		else if(message instanceof ClientConnected) {
			ClientConnected cc = (ClientConnected) message;
			sessionId = cc.getSessionId();
			netServer = getSender();
		}
		else if(message instanceof ClientDisconnected) {
			netServer = null;
		}
		else if(message instanceof CommandMessage) {
			CommandMessage msg = (CommandMessage) message;
			if(msg.getOp() == CommandMessage.LIST) {
				page = msg.getPage();
				SimpleQuery query = new SimpleQuery(null, SimpleQuery.ALL, null);
				String[] sort = new String[1];
				sort[0] = "name";
				storage.get("clients", query, sort);
			}
			else if(msg.getOp() == CommandMessage.GET) {
				page = -1;
				SimpleQuery query = new SimpleQuery("name", SimpleQuery.EQUAL, msg.getLogin());
				storage.get("clients", query, null);
			}
			else if(msg.getOp() == CommandMessage.ADD) {
				Entity entity = new Entity();
				entity.add("name", msg.getClientInfo().getLogin());
				entity.add("hash", msg.getClientInfo().getHash());
				entity.add("maxSessions", msg.getClientInfo().getMaxSessions());
				entity.add("maxChilds", msg.getClientInfo().getMaxChilds());
				List<Entity> handlers = new ArrayList<>();
				for(Handler handler : msg.getClientInfo().getMainHandlers()) {
					Entity e = new Entity();
					e.add("message", handler.getMessage());
					e.add("handler", handler.getHandler());
					handlers.add(e);
				}
				entity.add("messageHandlers", handlers);
				handlers = new ArrayList<>();
				for(Handler handler : msg.getClientInfo().getChildHandlers()) {
					Entity e = new Entity();
					e.add("message", handler.getMessage());
					e.add("handler", handler.getHandler());
					handlers.add(e);
				}
				entity.add("childHandlers", handlers);
				storage.put("clients", entity);
			}
			else if(msg.getOp() == CommandMessage.MODIFY) {
				Entity entity = new Entity();
				entity.add("name", msg.getClientInfo().getLogin());
				entity.add("hash", msg.getClientInfo().getHash());
				entity.add("maxSessions", msg.getClientInfo().getMaxSessions());
				entity.add("maxChilds", msg.getClientInfo().getMaxChilds());
				List<Entity> handlers = new ArrayList<>();
				for(Handler handler : msg.getClientInfo().getMainHandlers()) {
					Entity e = new Entity();
					e.add("message", handler.getMessage());
					e.add("handler", handler.getHandler());
					handlers.add(e);
				}
				entity.add("messageHandlers", handlers);
				handlers = new ArrayList<>();
				for(Handler handler : msg.getClientInfo().getChildHandlers()) {
					Entity e = new Entity();
					e.add("message", handler.getMessage());
					e.add("handler", handler.getHandler());
					handlers.add(e);
				}
				entity.add("childHandlers", handlers);
				SimpleQuery query = new SimpleQuery("name", SimpleQuery.EQUAL, msg.getLogin());
				storage.update("clients", query, entity);
			}
			else if(msg.getOp() == CommandMessage.REMOVE) {
				SimpleQuery query = new SimpleQuery("name", SimpleQuery.EQUAL, msg.getLogin());
				storage.remove("clients", query);
			}
			watcher.tell(new Ready(), getSelf());
		}
		else if(message instanceof StorageResult) {
			StorageResult msg = (StorageResult) message;
			if(msg.getOp() == StorageResult.GET) {
				if(msg.error()) netServer.tell(new SessionMessage(sessionId, false, new ErrorMessage(ErrorMessage.CUSTOM, "Error reading DB: " + msg.getMessage(), null)), getSelf());
				else if(page != -1) {
					Entity[] entities = msg.getEntities();
					ArrayList<ShortInfo> list = new ArrayList<>();
					for(int i = page * 10; i < (page + 1) * 10 && i < msg.getCount(); i++) {
						list.add(new ShortInfo((String) entities[i].get("name"), entities[i].get("_id").toString()));
					}
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try {
						ObjectOutputStream oos = new ObjectOutputStream(baos);
						oos.writeObject(new ListMessage(list, msg.getCount() % 10 == 0 ? msg.getCount() / 10 - 1 : msg.getCount() / 10));
						netServer.tell(new SessionMessage(sessionId, false, baos.toByteArray()), getSelf());
					}
					catch(IOException ex) {
						ex.printStackTrace(System.out);
					}
				}
				else {
					ClientInfo info = new ClientInfo();
					try {
						info.setOldLogin((String) msg.getEntities()[0].get("name"));
						info.setLogin((String) msg.getEntities()[0].get("name"));
					}
					catch(NullPointerException npe) {npe.printStackTrace(System.out);}
					try {
						info.setHash((byte[]) msg.getEntities()[0].get("hash"));
					}
					catch(NullPointerException npe) {npe.printStackTrace(System.out);}
					try {
						info.setMaxSessions((int) msg.getEntities()[0].get("maxSessions"));
					}
					catch(NullPointerException npe) {npe.printStackTrace(System.out);}
					try {
						info.setMaxChilds((int) msg.getEntities()[0].get("maxChilds"));
					}
					catch(NullPointerException npe) {npe.printStackTrace(System.out);}
					try {
						for(int i = 0; i < ((List) msg.getEntities()[0].get("messageHandlers")).size(); i++)
							info.addMainHandler((String) ((List<Entity>) msg.getEntities()[0].get("messageHandlers")).get(i).get("message"), (String) ((List<Entity>) msg.getEntities()[0].get("messageHandlers")).get(i).get("handler"));
					}
					catch(NullPointerException npe) {npe.printStackTrace(System.out);}
					try {
						for(int i = 0; i < ((List) msg.getEntities()[0].get("childHandlers")).size(); i++)
							info.addChildHandler((String) ((List<Entity>) msg.getEntities()[0].get("childHandlers")).get(i).get("message"), (String) ((List<Entity>) msg.getEntities()[0].get("childHandlers")).get(i).get("handler"));
					}
					catch(NullPointerException npe) {npe.printStackTrace(System.out);}
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try {
						ObjectOutputStream oos = new ObjectOutputStream(baos);
						oos.writeObject(new ClientMessage(info));
						netServer.tell(new SessionMessage(sessionId, false, baos.toByteArray()), getSelf());
					}
					catch(IOException ex) {
						ex.printStackTrace(System.out);
					}
				}
			}
			if(msg.getOp() == StorageResult.PUT) {
				if(msg.error()) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try {
						ObjectOutputStream oos = new ObjectOutputStream(baos);
						oos.writeObject(new FailMessage(FailMessage.ADD));
						netServer.tell(new SessionMessage(sessionId, false, baos.toByteArray()), getSelf());
					}
					catch(IOException ex) {
						ex.printStackTrace(System.out);
					}
					netServer.tell(new SessionMessage(sessionId, false, new ErrorMessage(ErrorMessage.CUSTOM, "Error adding client: " + msg.getMessage(), null)), getSelf());
				}
				else {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try {
						ObjectOutputStream oos = new ObjectOutputStream(baos);
						oos.writeObject(new SuccessMessage(SuccessMessage.ADD));
						netServer.tell(new SessionMessage(sessionId, false, baos.toByteArray()), getSelf());
					}
					catch(IOException ex) {
						ex.printStackTrace(System.out);
					}
				}
			}
			if(msg.getOp() == StorageResult.UPDATE) {
				if(msg.error()) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try {
						ObjectOutputStream oos = new ObjectOutputStream(baos);
						oos.writeObject(new FailMessage(FailMessage.MODIFY));
						netServer.tell(new SessionMessage(sessionId, false, baos.toByteArray()), getSelf());
					}
					catch(IOException ex) {
						ex.printStackTrace(System.out);
					}
					netServer.tell(new SessionMessage(sessionId, false, new ErrorMessage(ErrorMessage.CUSTOM, "Error updating client: " + msg.getMessage(), null)), getSelf());
				}
				else {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try {
						ObjectOutputStream oos = new ObjectOutputStream(baos);
						oos.writeObject(new SuccessMessage(SuccessMessage.MODIFY));
						netServer.tell(new SessionMessage(sessionId, false, baos.toByteArray()), getSelf());
					}
					catch(IOException ex) {
						ex.printStackTrace(System.out);
					}
				}
			}
			if(msg.getOp() == StorageResult.REMOVE) {
				if(msg.error()) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try {
						ObjectOutputStream oos = new ObjectOutputStream(baos);
						oos.writeObject(new FailMessage(FailMessage.REMOVE));
						netServer.tell(new SessionMessage(sessionId, false, baos.toByteArray()), getSelf());
					}
					catch(IOException ex) {
						ex.printStackTrace(System.out);
					}
					netServer.tell(new SessionMessage(sessionId, false, new ErrorMessage(ErrorMessage.CUSTOM, "Error removing client: " + msg.getMessage(), null)), getSelf());
				}
				else {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try {
						ObjectOutputStream oos = new ObjectOutputStream(baos);
						oos.writeObject(new SuccessMessage(SuccessMessage.REMOVE));
						netServer.tell(new SessionMessage(sessionId, false, baos.toByteArray()), getSelf());
					}
					catch(IOException ex) {
						ex.printStackTrace(System.out);
					}
				}
			}
		}
		else unhandled(message);
	}
}