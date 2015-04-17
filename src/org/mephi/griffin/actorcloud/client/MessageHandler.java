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

import org.mephi.griffin.actorcloud.client.messages.Message;
import akka.actor.ActorRef;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.storage.Storage;

public abstract class MessageHandler implements Serializable {
	private static final Logger logger = Logger.getLogger(MessageHandler.class.getName());
	private String name;
	private transient ClientActor actor;
	private transient Storage storage;
	private transient ActorRef manager;
	
	public MessageHandler() {
		logger.entering("MessageHandler", "Constructor");
		name = null;
		actor = null;
		storage = null;
		manager = null;
		logger.exiting("MessageHandler", "Constructor");
	}
	
	public abstract void init();
	
	public abstract void execute(Message message, String source);
	
	public abstract void destroy();
	
	void setActors(ClientActor actor, ActorRef storage, ActorRef manager) {
		logger.entering("MessageHandler(" + actor.getSelf().path().name() + ")", "setActors");
		logger.logp(Level.FINER, "MessageHandler", "setActors", "Client actor: " + actor + ", storage: " + storage + ", manager: " + manager);
		this.name = "MessageHandler(" + actor.getSelf().path().name() + ")";
		this.actor = actor;
		this.storage = new Storage(storage, actor.getSelf());
		this.manager = manager;
		logger.exiting(name, "setActors");
	}
	
	public Storage getStorage() {
		logger.entering(name, "getStorage");
		logger.exiting(name, "getStorage");
		return storage;
	}
	
	public void sendClient(Message message) {
		actor.sendClient(message);
	}
	
	public void sendSelf(Message message) {
		logger.entering(name, "sendSelf");
		logger.logp(Level.FINER, name, "sendSelf", "Message -> ClientActor(" + actor.getSelf().path().name() + "): " + message);
		actor.getSelf().tell(message, actor.getSelf());
		logger.exiting(name, "sendSelf");
	}
	
	public void sendClientActor(String client, Message message) {
		actor.sendClientActor(client, message);
	}
	
	public String getName() {
		logger.entering(name, "getName");
		logger.exiting(name, "getName");
		return actor.getSelf().path().name();
	}
	
	public void findClientActor(String client) {
		logger.entering(name, "findClientActor");
		logger.logp(Level.FINER, name, "findClientActor", "String (ClientSearch) -> Manager: " + client);
		manager.tell(client, actor.getSelf());
		logger.exiting(name, "findClientActor");
	}
	
	public void createChilds(int count) {
		actor.createChilds(count);
	}
	
	public void sendChild(int num, Message message) {
		actor.sendChild(num, message);
	}
	
	public void saveSnapshot() {
		actor.saveSnapshot();
	}
}