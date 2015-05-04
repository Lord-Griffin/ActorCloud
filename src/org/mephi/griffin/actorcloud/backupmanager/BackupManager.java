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
package org.mephi.griffin.actorcloud.backupmanager;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mephi.griffin.actorcloud.backupmanager.messages.Backup;
import org.mephi.griffin.actorcloud.client.messages.ClearBackup;
import org.mephi.griffin.actorcloud.client.messages.DeleteMessage;
import org.mephi.griffin.actorcloud.client.messages.SaveMessage;
import org.mephi.griffin.actorcloud.client.messages.Snapshot;
import org.mephi.griffin.actorcloud.nodemanager.messages.GetBackup;
import org.mephi.griffin.actorcloud.storage.CompoundQuery;
import org.mephi.griffin.actorcloud.storage.Entity;
import org.mephi.griffin.actorcloud.storage.SimpleQuery;
import org.mephi.griffin.actorcloud.storage.Storage;
import org.mephi.griffin.actorcloud.storage.messages.StorageResult;

/**
 *
 * @author Griffin
 */
public class BackupManager extends UntypedActor {
	private ActorRef nodeManager;
	private Storage storage;
	private Map<Integer, ActorRef> queue;
	
	public BackupManager(ActorRef storage) {
		this.nodeManager = getContext().parent();
		this.storage = new Storage(storage, getSelf());
		this.queue = new HashMap<>();
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if(message instanceof Snapshot) {
			Snapshot s = (Snapshot) message;
			Entity entity = new Entity();
			entity.add("name", getSender().path().name());
			entity.add("type", "snapshot");
			entity.add("snapshot-type", s.getType());
			entity.add("id", s.getId());
			entity.add("snapshot", s.getSnapshot());
			SimpleQuery sq1 = new SimpleQuery("name", SimpleQuery.EQUAL, getSender().path().name());
			SimpleQuery sq2 = new SimpleQuery("type", SimpleQuery.EQUAL, "snapshot");
			SimpleQuery sq3 = new SimpleQuery("snapshot-type", SimpleQuery.EQUAL, s.getType());
			SimpleQuery sq4 = new SimpleQuery("id", SimpleQuery.EQUAL, s.getId() - 1);
			CompoundQuery query = new CompoundQuery(CompoundQuery.AND);
			query.add(new SimpleQuery[]{sq1, sq2, sq3, sq4});
			storage.remove("snapshots", query);
			storage.put("snapshots", entity);
		}
		else if(message instanceof SaveMessage) {
			SaveMessage sm = (SaveMessage) message;
			Entity entity = new Entity();
			entity.add("name", getSender().path().name());
			entity.add("type", "message");
			entity.add("id", sm.getId());
			entity.add("message", sm.getMessage());
			storage.put("snapshots", entity);
		}
		else if(message instanceof DeleteMessage) {
			SimpleQuery sq1 = new SimpleQuery("name", SimpleQuery.EQUAL, getSender().path().name());
			SimpleQuery sq2 = new SimpleQuery("type", SimpleQuery.EQUAL, "message");
			CompoundQuery query = new CompoundQuery(CompoundQuery.AND);
			query.add(new SimpleQuery[]{sq1, sq2});
			storage.get("snapshots", query, new String[]{"id"});
		}
		else if(message instanceof StorageResult) {
			StorageResult sr = (StorageResult) message;
			ActorRef actor = queue.remove(sr.getId());
			if(actor != null) {
				byte[] watcherSnapshot = null;
				byte[] actorSnapshot = null;
				List<byte[]> messages = new ArrayList<>();
				Entity[] entities = sr.getEntities();
				for(Entity entity : entities) {
					if(entity.get("type").equals("snapshot")) {
						if(entity.get("snapshot-type").equals(Snapshot.ACTOR)) actorSnapshot = (byte[]) entity.get("snapshot");
						else if(entity.get("snapshot-type").equals(Snapshot.WATCHER)) watcherSnapshot = (byte[]) entity.get("snapshot");
					}
					else if(entity.get("type").equals("message")) {
						messages.add((byte[]) entity.get("message"));
					}
				}
				Backup msg = new Backup(actor, watcherSnapshot, messages, actorSnapshot);
				nodeManager.tell(msg, getSelf());
			}
			else {
				SimpleQuery sq1 = new SimpleQuery("name", SimpleQuery.EQUAL, (String) sr.getEntities()[0].get("name"));
				SimpleQuery sq2 = new SimpleQuery("type", SimpleQuery.EQUAL, "message");
				SimpleQuery sq3 = new SimpleQuery("id", SimpleQuery.EQUAL, (int) sr.getEntities()[0].get("id"));
				CompoundQuery query = new CompoundQuery(CompoundQuery.AND);
				query.add(new SimpleQuery[]{sq1, sq2, sq3});
				storage.remove("snapshots", query);
			}
		}
		else if(message instanceof ClearBackup) {
			SimpleQuery query = new SimpleQuery("name", SimpleQuery.EQUAL, getSender().path().name());
			storage.remove("snapshots", query);
		}
		else if(message instanceof GetBackup) {
			GetBackup gb = (GetBackup) message;
			SimpleQuery query = new SimpleQuery("name", SimpleQuery.EQUAL, gb.getActor().path().name());
			queue.put(storage.get("snapshots", query, new String[]{"id"}), gb.getActor());
		}
	}
}
