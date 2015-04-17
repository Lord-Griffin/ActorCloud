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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.storage.Storage;

public abstract class ChildMessageHandler {
	private static final Logger logger = Logger.getLogger(ChildMessageHandler.class.getName());
	private String name;
	private ChildActor actor;
	private ActorRef storage;
	private ActorRef parent;
	
	public ChildMessageHandler() {
		logger.entering("ChildMessageHandler", "Constructor");
		actor = null;
		storage = null;
		logger.exiting("ChildMessageHandler", "Constructor");
	}
	
	public abstract void init();
	
	public abstract void execute(Message message, String source);
	
	public abstract void destroy();
	
	void setActors(ChildActor actor, ActorRef storage, ActorRef parent) {
		logger.entering("ChildMessageHandler(" + parent.path().name() + "/" + actor.getSelf().path().name() + ")", "setActors");
		logger.logp(Level.FINER, "ChildMessageHandler(" + parent.path().name() + "/" + actor.getSelf().path().name() + ")", "setActors", "Child actor: " + actor + ", storage: " + storage + ", parent: " + parent);
		this.name = "ChildMessageHandler(" + parent.path().name() + "/" + actor.getSelf().path().name() + ")";
		this.actor = actor;
		this.storage = storage;
		this.parent = parent;
		logger.exiting(name, "setActors");
	}
	
	public Storage getStorage() {
		logger.entering(name, "getStorage");
		Storage storage = new Storage(this.storage, actor.getSelf());
		logger.exiting(name, "getStorage");
		return storage;
	}
	
	public void sendSelf(Message message) {
		logger.entering(name, "sendSelf");
		logger.logp(Level.FINER, name, "sendSelf", "Message -> ChildActor(" + parent.path().name() + "/" + actor.getSelf().path().name() + "): " + message);
		actor.getSelf().tell(message, actor.getSelf());
		logger.exiting(name, "sendSelf");
	}
	
	public void sendParent(Message message) {
		logger.entering(name, "sendParent");
		logger.logp(Level.FINER, name, "sendParent", "Message -> ClientActor(" + parent.path().name() + "): " + message);
		parent.tell(message, actor.getSelf());
		logger.exiting(name, "sendParent");
	}
}