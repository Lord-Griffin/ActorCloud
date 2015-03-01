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
import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteResult;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.InitSuccess;

public class StorageActor extends UntypedActor {
	private static final Logger logger = Logger.getLogger(StorageActor.class.getName());
	private List<InetSocketAddress> addresses;
	private MongoClient client;
	private DB db;
	
	public StorageActor(List<InetSocketAddress> addresses) {
		logger.entering("StorageActor", "Constructor");
		String log = "DB addresses: localhost:27017";
		if(addresses != null) {
			for(InetSocketAddress address : addresses)
				log += ", " + address.getAddress().getHostAddress() + ":" + address.getPort();
		}
		logger.logp(Level.FINER, "StorageActor", "Constructor", log);
		client = null;
		this.addresses = addresses;
		logger.exiting("StorageActor", "Constructor");
	}

	@Override
	public void preStart() {
		logger.entering("StorageActor", "preStart");
		String errors = "";
		try {
			client = new MongoClient();
			logger.logp(Level.FINER, "StorageActor", "preStart", "Connected to localhost:27017");
		}
		catch (MongoException | UnknownHostException e) {
			logger.throwing("StorageActor", "preStart", e);
			errors += e.getMessage() + "\n";
		}
		if(addresses != null) {
			for(InetSocketAddress address : addresses) {
				try {
					client = new MongoClient(new ServerAddress(address));
					logger.logp(Level.FINER, "StorageActor", "preStart", "Connected to " + address.getAddress().getHostAddress() + ":" + address.getPort());
					break;
				}
				catch (MongoException e) {
					logger.throwing("StorageActor", "preStart", e);
					errors += e.getMessage() + "\n";
				}
			}
		}
		if(client != null) {
			db = client.getDB("actorcloud");
			InitSuccess msg = new InitSuccess(InitSuccess.STORAGE, null);
			logger.logp(Level.FINER, "StorageActor", "preStart", "InitSuccess -> Manager: " + msg);
			getContext().parent().tell(msg, getSelf());
			logger.logp(Level.INFO, "StorageActor", "preStart", "Storage started");
		}
		else {
			InitFail msg = new InitFail(InitFail.STORAGE, null, errors);
			logger.logp(Level.FINER, "StorageActor", "preStart", "InitFail -> Manager: " + msg);
			getContext().parent().tell(msg, getSelf());
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
	
	public static void main(String[] args) {		
		try {
			MongoClient client = new MongoClient("localhost", 27017);
			DB db = client.getDB("actorcloud");
			MessageDigest md = MessageDigest.getInstance("SHA-512");
			for(int i = 30; i < 50; i++) {
				BasicDBObject obj = new BasicDBObject();
				byte[] hash = md.digest(("netsend" + i).getBytes());
				obj.append("name", "netsend" + i);
				obj.append("hash", hash);
				obj.append("messageHandler", "actorcloudnettest.StringMessageHandler");
				obj.append("childHandler", "");
				db.getCollection("clients").insert(obj);
			}
		}
		catch (UnknownHostException | NoSuchAlgorithmException ex) {
			Logger.getLogger(StorageActor.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
}
