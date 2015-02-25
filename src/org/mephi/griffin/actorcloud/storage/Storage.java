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
package org.mephi.griffin.actorcloud.storage;

import akka.actor.ActorRef;
import com.mongodb.BasicDBObject;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Storage {
	private static final Logger logger = Logger.getLogger(Storage.class.getName());
	private ActorRef storage;
	private String name;
	private ActorRef client;
	private int requestId;
	
	public Storage(ActorRef storage, ActorRef client) {
		logger.entering("Storage(" + client.path().name() + ")", "Constructor");
		logger.logp(Level.FINER, "Storage(" + client.path().name() + ")", "Constructor", "StorageActor: " + storage + ", storage client: " + client);
		this.storage = storage;
		name = "Storage(" + client.path().name() + ")";
		this.client = client;
		requestId = 1;
		logger.exiting(name, "Constructor");
	}
	
	public int get(String collection, Query query, String[] sort) {
		logger.entering(name, "get");
		try {
			BasicDBObject queryDoc = query.getDoc();
			BasicDBObject sortDoc = new BasicDBObject();

			if(sort != null) {
				for(int i = 0; i < sort.length; i++) {
					sortDoc.append(sort[i], i + 1);
				}
			}
			else sortDoc = null;
			Get msg = new Get(requestId, collection, queryDoc, sortDoc);
			logger.logp(Level.FINER, name, "get", "Get -> StorageActor: " + msg);
			storage.tell(msg, client);
		}
		catch(IllegalArgumentException iae) {
			logger.throwing(name, "get", iae);
			throw iae;
		}
		logger.exiting(name, "get");
		return requestId++;
	}
	
	public int put(String collection, Entity entity) {
		logger.entering(name, "put");
		try {
			BasicDBObject doc = entity.getDoc();
			Insert msg = new Insert(requestId, collection, doc);
			logger.logp(Level.FINER, name, "put", "Put -> StorageActor: " + msg);
			storage.tell(msg, client);
		}
		catch(IllegalArgumentException iae) {
			logger.throwing(name, "put", iae);
			throw iae;
		}
		logger.exiting(name, "put");
		return requestId++;
	}
	
	public int put(String collection, Entity[] entities) {
		logger.entering(name, "put");
		BasicDBObject[] docs = new BasicDBObject[entities.length];
		try {
			for(int i = 0; i < entities.length; i++)
				docs[i] =  entities[i].getDoc();
			Insert msg = new Insert(requestId, collection, docs);
			logger.logp(Level.FINER, name, "put", "Put -> StorageActor: " + msg);
			storage.tell(msg, client);
		}
		catch(IllegalArgumentException iae) {
			logger.throwing(name, "put", iae);
			throw iae;
		}
		logger.exiting(name, "put");
		return requestId++;
	}
	
	public int remove(String collection, Query query) {
		logger.entering(name, "remove");
		try {
			BasicDBObject queryDoc = query.getDoc();
			Remove msg = new Remove(requestId, collection, queryDoc);
			logger.logp(Level.FINER, name, "remove", "Remove -> StorageActor: " + msg);
			storage.tell(msg, client);
		}
		catch(IllegalArgumentException iae) {
			logger.throwing(name, "remove", iae);
			throw iae;
		}
		logger.exiting(name, "remove");
		return requestId++;
	}
	
	public int update(String collection, Query query, Entity fields) {
		logger.entering(name, "update");
		try {
			BasicDBObject queryDoc = query.getDoc();
			BasicDBObject fieldsDoc = fields.getDoc();
			Update msg = new Update(requestId, collection, queryDoc, fieldsDoc);
			logger.logp(Level.FINE, name, "update", "Update -> MessageActor: " + msg);
			storage.tell(msg, client);
		}
		catch(IllegalArgumentException iae) {
			logger.throwing(name, "update", iae);
			throw iae;
		}
		logger.exiting(name, "update");
		return requestId++;
	}
}