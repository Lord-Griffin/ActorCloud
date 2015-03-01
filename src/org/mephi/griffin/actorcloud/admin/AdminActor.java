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

import org.mephi.griffin.actorcloud.client.ErrorMessage;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import java.util.ArrayList;
import java.util.List;
import org.mephi.griffin.actorcloud.common.AddSession;
import org.mephi.griffin.actorcloud.common.RemoveSession;
import org.mephi.griffin.actorcloud.common.InitSuccess;
import org.mephi.griffin.actorcloud.netserver.SessionMessage;
import org.mephi.griffin.actorcloud.storage.Entity;
import org.mephi.griffin.actorcloud.storage.SimpleQuery;
import org.mephi.griffin.actorcloud.storage.Storage;
import org.mephi.griffin.actorcloud.storage.StorageResult;

/**
 *
 * @author Griffin
 */
public class AdminActor extends UntypedActor {
	
	private ActorRef netServer;
	private Storage storage;
	private List<Integer> sessions;
	private ClientInfo[] list;
	
	public AdminActor(ActorRef netServer, ActorRef storage) {
		this.netServer = netServer;
		this.storage = new Storage(storage, getSelf());
		sessions = new ArrayList<>();
	}
	
	@Override
	public void preStart() {
		list = null;
		getContext().parent().tell(new InitSuccess(InitSuccess.CLIENT, "admin"), getSelf());
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
		if(message instanceof AddSession) {
			AddSession msg = (AddSession) message;
			if(!sessions.contains(msg.getSessionId())) sessions.add(msg.getSessionId());
		}
		else if(message instanceof RemoveSession) {
			RemoveSession msg = (RemoveSession) message;
			sessions.remove(new Integer(msg.getSessionId()));
		}
		else if(message instanceof CommandMessage) {
			CommandMessage msg = (CommandMessage) message;
			if(msg.getOp() == CommandMessage.LIST && list == null) {
				SimpleQuery query = new SimpleQuery(null, SimpleQuery.ALL, null);
				String[] sort = new String[1];
				sort[0] = "name";
				storage.get("clients", query, sort);
			}
			else if(msg.getOp() == CommandMessage.ADD) {
				Entity entity = new Entity();
				entity.add("name", msg.getClientInfo().getLogin());
				entity.add("hash", msg.getClientInfo().getHash());
				entity.add("messageHandler", msg.getClientInfo().getMessageHandler());
				entity.add("childHandler", msg.getClientInfo().getChildHandler());
				storage.put("clients", entity);
			}
			else if(msg.getOp() == CommandMessage.MODIFY) {
				Entity entity = new Entity();
				entity.add("name", msg.getClientInfo().getLogin());
				entity.add("hash", msg.getClientInfo().getHash());
				entity.add("messageHandler", msg.getClientInfo().getMessageHandler());
				entity.add("childHandler", msg.getClientInfo().getChildHandler());
				SimpleQuery query = new SimpleQuery("name", SimpleQuery.EQUAL, list[msg.getIndex()].getLogin());
				storage.update("clients", query, entity);
			}
			else if(msg.getOp() == CommandMessage.REMOVE) {
				SimpleQuery query = new SimpleQuery("name", SimpleQuery.EQUAL, list[msg.getIndex()].getLogin());
				storage.remove("clients", query);
			}
		}
		else if(message instanceof StorageResult) {
			StorageResult msg = (StorageResult) message;
			if(msg.getOp() == StorageResult.GET) {
				if(msg.error()) netServer.tell(new SessionMessage(sessions, new ErrorMessage(ErrorMessage.CUSTOM, "Error getting clients list: " + msg.getMessage(), null), false), getSelf());
				else {
					Entity[] entities = msg.getEntities();
					list = new ClientInfo[msg.getCount()];
					for(int i = 0; i < entities.length; i++)
						list[i] = new ClientInfo((String) entities[i].get("name"), (byte[]) entities[i].get("hash"), (String) entities[i].get("messageHandler"), (String) entities[i].get("childHandler"));
					netServer.tell(new SessionMessage(sessions, new ListMessage(list), false), getSelf());
				}
			}
			if(msg.getOp() == StorageResult.PUT) {
				if(msg.error()) netServer.tell(new SessionMessage(sessions, new ErrorMessage(ErrorMessage.CUSTOM, "Error adding client: " + msg.getMessage(), null), false), getSelf());
				else {
					list = null;
					netServer.tell(new SessionMessage(sessions, new ResultMessage(), false), getSelf());
				}
			}
			if(msg.getOp() == StorageResult.UPDATE) {
				if(msg.error()) netServer.tell(new SessionMessage(sessions, new ErrorMessage(ErrorMessage.CUSTOM, "Error updating client: " + msg.getMessage(), null), false), getSelf());
				else {
					list = null;
					netServer.tell(new SessionMessage(sessions, new ResultMessage(), false), getSelf());
				}
			}
			if(msg.getOp() == StorageResult.REMOVE) {
				if(msg.error()) netServer.tell(new SessionMessage(sessions, new ErrorMessage(ErrorMessage.CUSTOM, "Error removing client: " + msg.getMessage(), null), false), getSelf());
				else {
					list = null;
					netServer.tell(new SessionMessage(sessions, new ResultMessage(), false), getSelf());
				}
			}
		}
		else unhandled(message);
	}
}