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

import org.mephi.griffin.actorcloud.storage.messages.Update;
import org.mephi.griffin.actorcloud.storage.messages.Remove;
import org.mephi.griffin.actorcloud.storage.messages.Insert;
import org.mephi.griffin.actorcloud.storage.messages.Get;
import akka.actor.ActorRef;
import com.mongodb.BasicDBObject;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Storage {
	private static final Logger logger = Logger.getLogger(Storage.class.getName());
	private ActorRef storage;
	private String name;
	private ActorRef client;
	private final Object lock;
	private int requestId;
	
	public Storage(ActorRef storage, ActorRef client) {
		logger.entering("Storage(" + client.path().name() + ")", "Constructor");
		logger.logp(Level.FINER, "Storage(" + client.path().name() + ")", "Constructor", "StorageActor: " + storage + ", storage client: " + client);
		this.storage = storage;
		name = "Storage(" + client.path().name() + ")";
		this.client = client;
		lock = new Object();
		requestId = 1;
		logger.exiting(name, "Constructor");
	}
	
	public int get(String collection, Query query, String[] sort) {
		logger.entering(name, "get");
		int id = requestId;
		synchronized(lock) {
			requestId++;
		}
		try {
			BasicDBObject queryDoc = query.getDoc();
			BasicDBObject sortDoc = new BasicDBObject();

			if(sort != null) {
				for(int i = 0; i < sort.length; i++) {
					sortDoc.append(sort[i], i + 1);
				}
			}
			else sortDoc = null;
			Get msg = new Get(id, collection, queryDoc, sortDoc);
			logger.logp(Level.FINER, name, "get", "Get -> StorageActor: " + msg);
			storage.tell(msg, client);
		}
		catch(IllegalArgumentException iae) {
			logger.throwing(name, "get", iae);
			throw iae;
		}
		logger.exiting(name, "get");
		return id;
	}
	
	public int put(String collection, Entity entity) {
		logger.entering(name, "put");
		int id = requestId;
		synchronized(lock) {
			requestId++;
		}
		try {
			BasicDBObject doc = entity.getDoc();
			Insert msg = new Insert(id, collection, doc);
			logger.logp(Level.FINER, name, "put", "Put -> StorageActor: " + msg);
			storage.tell(msg, client);
		}
		catch(IllegalArgumentException iae) {
			logger.throwing(name, "put", iae);
			throw iae;
		}
		logger.exiting(name, "put");
		return id++;
	}
	
	public int put(String collection, Entity[] entities) {
		logger.entering(name, "put");
		int id = requestId;
		synchronized(lock) {
			requestId++;
		}
		BasicDBObject[] docs = new BasicDBObject[entities.length];
		try {
			for(int i = 0; i < entities.length; i++)
				docs[i] =  entities[i].getDoc();
			Insert msg = new Insert(id, collection, docs);
			logger.logp(Level.FINER, name, "put", "Put -> StorageActor: " + msg);
			storage.tell(msg, client);
		}
		catch(IllegalArgumentException iae) {
			logger.throwing(name, "put", iae);
			throw iae;
		}
		logger.exiting(name, "put");
		return id++;
	}
	
	public int remove(String collection, Query query) {
		logger.entering(name, "remove");
		int id = requestId;
		synchronized(lock) {
			requestId++;
		}
		try {
			BasicDBObject queryDoc = query.getDoc();
			Remove msg = new Remove(id, collection, queryDoc);
			logger.logp(Level.FINER, name, "remove", "Remove -> StorageActor: " + msg);
			storage.tell(msg, client);
		}
		catch(IllegalArgumentException iae) {
			logger.throwing(name, "remove", iae);
			throw iae;
		}
		logger.exiting(name, "remove");
		return id++;
	}
	
	public int update(String collection, Query query, Entity fields) {
		logger.entering(name, "update");
		int id = requestId;
		synchronized(lock) {
			requestId++;
		}
		try {
			BasicDBObject queryDoc = query.getDoc();
			BasicDBObject fieldsDoc = fields.getDoc();
			Update msg = new Update(id, collection, queryDoc, fieldsDoc);
			logger.logp(Level.FINE, name, "update", "Update -> MessageActor: " + msg);
			storage.tell(msg, client);
		}
		catch(IllegalArgumentException iae) {
			logger.throwing(name, "update", iae);
			throw iae;
		}
		logger.exiting(name, "update");
		return id++;
	}
}