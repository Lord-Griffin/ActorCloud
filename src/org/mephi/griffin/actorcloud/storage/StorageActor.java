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
import akka.actor.UntypedActor;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteResult;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException.Missing;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.InitSuccess;
import org.mephi.griffin.actorcloud.storage.messages.Get;
import org.mephi.griffin.actorcloud.storage.messages.Insert;
import org.mephi.griffin.actorcloud.storage.messages.Remove;
import org.mephi.griffin.actorcloud.storage.messages.StorageResult;
import org.mephi.griffin.actorcloud.storage.messages.Update;

public class StorageActor extends UntypedActor {
	private static final Logger logger = Logger.getLogger(StorageActor.class.getName());
	private ActorRef nodeManager;
	private MongoClient client;
	private String dbName;
	private DB db;
	
	public StorageActor(ActorRef nodeManager) {
		logger.entering("StorageActor", "Constructor");
		this.nodeManager = nodeManager;
		client = null;
		logger.exiting("StorageActor", "Constructor");
	}

	@Override
	public void preStart() {
		logger.entering("StorageActor", "preStart");
		String errors = "";
		Config config = getContext().system().settings().config();
		try {
			String host;
			int port;
			String login = null;
			String pass = null;
			try {host = config.getString("actorcloud.storage.host");}
			catch(Missing ex) {host = "localhost";}
			try {port = config.getInt("actorcloud.storage.port");}
			catch(Missing ex) {port = 27017;}
			try {
				login = config.getString("actorcloud.storage.login");
				pass = config.getString("actorcloud.storage.pass");
			}
			catch(Missing ex) {
				if(login != null) throw ex;
				login = "";
				pass = "";
			}
			try {dbName = config.getString("actorcloud.storage.db");}
			catch(Missing ex) {dbName = "actorcloud";}
			if(!login.equals("")) client = new MongoClient(new ServerAddress(host, port), Arrays.asList(MongoCredential.createPlainCredential(login, "actorcloud", pass.toCharArray())));
			else client = new MongoClient(new ServerAddress(host, port));
			logger.logp(Level.FINER, "StorageActor", "preStart", "Connected to " + host + ":" + port);
			if(client != null) {
				db = client.getDB(dbName);
				DBCursor cursor = db.getCollection("clients").find();
				boolean adminFound = false;
				if(cursor.count() != 0) {
					while(cursor.hasNext()) {
						DBObject doc = cursor.next();
						if(doc.get("name") != null && doc.get("name").equals("admin")) {
							adminFound = true;
							break;
						}
					}
				}
				if(!adminFound) {
					MessageDigest md = MessageDigest.getInstance("SHA-512");
					BasicDBObject doc = new BasicDBObject();
					byte[] hash = md.digest(("admin").getBytes());
					doc.append("name", "admin");
					doc.append("hash", hash);
					doc.append("maxSessions", 1);
					doc.append("maxChilds", 0);
					doc.append("messageHandlers", new ArrayList<>());
					doc.append("childHandlers", new ArrayList<>());
					db.getCollection("clients").insert(doc);
				}
				InitSuccess msg = new InitSuccess(InitSuccess.STORAGE, null, null, 0);
				logger.logp(Level.FINER, "StorageActor", "preStart", "InitSuccess -> Manager: " + msg);
				nodeManager.tell(msg, getSelf());
				logger.logp(Level.INFO, "StorageActor", "preStart", "Storage started");
			}
			else {
				InitFail msg = new InitFail(InitFail.STORAGE, null, null, 0, errors);
				logger.logp(Level.FINER, "StorageActor", "preStart", "InitFail -> Manager: " + msg);
				nodeManager.tell(msg, getSelf());
				logger.logp(Level.WARNING, "StorageActor", "preStart", "Failed to connect to DB:\n" + errors);
				getContext().stop(getSelf());
			}
		}
		catch (MongoException | UnknownHostException | NoSuchAlgorithmException e) {
			logger.throwing("StorageActor", "preStart", e);
			errors += e.getMessage() + "\n";
			InitFail msg = new InitFail(InitFail.STORAGE, null, null, 0, errors);
			logger.logp(Level.FINER, "StorageActor", "preStart", "InitFail -> Manager: " + msg);
			nodeManager.tell(msg, getSelf());
			logger.logp(Level.WARNING, "StorageActor", "preStart", "Failed to connect to DB:\n" + errors);
			getContext().stop(getSelf());
		}
		logger.exiting("StorageActor", "preStart");
	}
	
	@Override
	public void postStop() {
		logger.entering("StorageActor", "postStop");
		if(client != null) {
			client.close();
			logger.logp(Level.FINE, "StorageActor", "postStop", "Connection to DB closed");
		}
		logger.logp(Level.INFO, "StorageActor", "postStop", "Storage stopped");
		logger.exiting("StorageActor", "postStop");
	}
	
	@Override
	public void onReceive(Object message) {
		logger.entering("StorageActor", "onReceive");
		if(message instanceof Get) {
			logger.logp(Level.FINER, "StorageActor", "onReceive", "StorageActor <- Get from " + getSender().path().name() + ": " + message);
			Get request = (Get) message;
			DBCursor cursor;
			StorageResult msg;
			try {
				if(request.getQuery() != null)
					cursor = db.getCollection(request.getCollection()).find(request.getQuery());
				else
					cursor = db.getCollection(request.getCollection()).find();
				if(request.getSort() != null) cursor = cursor.sort(request.getSort());
				Entity[] result = new Entity[cursor.size()];
				int i = 0;
				while(cursor.hasNext()) {
					BasicDBObject tmp = (BasicDBObject) cursor.next();
					result[i++] = new Entity(tmp);
				}
				msg = new StorageResult(StorageResult.GET, request.getId(), result);
			}
			catch(MongoException me) {
				logger.throwing("StorageActor", "onReceive", me);
				msg = new StorageResult(StorageResult.GET, request.getId(), me.getMessage());
			}
			logger.logp(Level.FINER, "StorageActor", "onReceive", "StorageResult -> " + getSender().path().name() + ": " + msg);
			getSender().tell(msg, getSelf());
		}
		else if(message instanceof Insert) {
			logger.logp(Level.FINER, "StorageActor", "onReceive", "StorageActor <- Insert from " + getSender().path().name() + ": " + message);
			Insert request = (Insert) message;
			StorageResult msg;
			try {
				WriteResult result = db.getCollection(request.getCollection()).insert(request.getDocs());
				msg = new StorageResult(StorageResult.PUT, request.getId(), result.getN());
			}
			catch(MongoException me) {
				logger.throwing("StorageActor", "onReceive", me);
				msg = new StorageResult(StorageResult.PUT, request.getId(), me.getMessage());
			}
			logger.logp(Level.FINER, "StorageActor", "onReceive", "StorageResult -> " + getSender().path().name() + ": " + msg);
			getSender().tell(msg, getSelf());
		}
		else if(message instanceof Update) {
			logger.logp(Level.FINER, "StorageActor", "onReceive", "StorageActor <- Update from " + getSender().path().name() + ": " + message);
			Update request = (Update) message;
			StorageResult msg;
			try {
				WriteResult result = db.getCollection(request.getCollection()).update(request.getQuery(), new BasicDBObject("$set", request.getFields()));
				msg = new StorageResult(StorageResult.UPDATE, request.getId(), result.getN());
			}
			catch(MongoException me) {
				logger.throwing("StorageActor", "onReceive", me);
				msg = new StorageResult(StorageResult.UPDATE, request.getId(), me.getMessage());
			}
			logger.logp(Level.FINER, "StorageActor", "onReceive", "StorageResult -> " + getSender().path().name() + ": " + msg);
			getSender().tell(msg, getSelf());
		}
		else if(message instanceof Remove) {
			logger.logp(Level.FINER, "StorageActor", "onReceive", "StorageActor <- Remove from " + getSender().path().name() + ": " + message);
			Remove request = (Remove) message;
			StorageResult msg;
			try {
				WriteResult result = db.getCollection(request.getCollection()).remove(request.getQuery());
				msg = new StorageResult(StorageResult.REMOVE, request.getId(), result.getN());
			}
			catch(MongoException me) {
				logger.throwing("StorageActor", "onReceive", me);
				msg = new StorageResult(StorageResult.REMOVE, request.getId(), me.getMessage());
			}
			logger.logp(Level.FINER, "StorageActor", "onReceive", "StorageResult -> " + getSender().path().name() + ": " + msg);
			getSender().tell(msg, getSelf());
		}
		else unhandled(message);
		logger.exiting("StorageActor", "onReceive");
	}
}
