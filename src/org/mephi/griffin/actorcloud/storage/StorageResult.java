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

import org.mephi.griffin.actorcloud.client.messages.Message;

public class StorageResult implements Message {
	public static final int GET = 1;
	public static final int PUT = 2;
	public static final int UPDATE = 3;
	public static final int REMOVE = 4;
	
	private boolean error;
	private String message;
	private int op;
	private int requestId;
	private int count;
	private Entity[] entities;
	
	public StorageResult() {}
	
	public StorageResult(int op, int requestId, String message) {
		this.op = op;
		this.requestId = requestId;
		this.error = true;
		this.message = message;
		this.count = 0;
		this.entities = null;
	}
	
	public StorageResult(int op, int requestId, int count) {
		this.op = op;
		this.requestId = requestId;
		this.error = false;
		this.message = null;
		this.count = count;
		this.entities = null;
	}
	
	public StorageResult(int op, int requestId, Entity[] entities) {
		this.op = op;
		this.requestId = requestId;
		this.error = false;
		this.message = null;
		this.count = entities.length;
		this.entities = entities;
	}
	
	public boolean error() {
		return error;
	}
	
	public String getMessage() {
		return message;
	}
	
	public int getOp() {
		return op;
	}
	
	public int getId() {
		return requestId;
	}
	
	public int getCount() {
		return count;
	}
	
	public Entity[] getEntities() {
		return entities;
	}
	
	@Override
	public String toString() {
		String res = "Operation ";
		switch(op) {
			case GET:
				res += "GET";
				break;
			case PUT:
				res += "PUT";
				break;
			case UPDATE:
				res += "UPDATE";
				break;
			case REMOVE:
				res += "REMOVE";
				break;
		}
		res += ", request id " + requestId + ", result: ";
		if(error) res += "error: " + message;
		else {
			res += count + " entities";
			if(op == GET && entities != null) {
				res += ":\n";
				for(Entity entity : entities) res += entity + "\n";
			}
		}
		return res;
	}
}