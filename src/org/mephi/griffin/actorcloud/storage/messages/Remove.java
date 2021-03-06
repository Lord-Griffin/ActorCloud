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
package org.mephi.griffin.actorcloud.storage.messages;

import com.mongodb.BasicDBObject;

public class Remove {
	private int requestId;
	private String collection;
	private BasicDBObject query;
	
	public Remove() {}
	
	public Remove(int requestId, String collection, BasicDBObject query) {
		this.requestId = requestId;
		this.collection = collection;
		this.query = query;
	}
	
	public int getId() {
		return requestId;
	}
	
	public String getCollection() {
		return collection;
	}
	
	public BasicDBObject getQuery() {
		return query;
	}
	
	@Override
	public String toString() {
		String res = "Request id " + requestId + ", collection " + collection + ", query: ";
		if(query != null) res += query;
		else res += "all rows";
		return res;
	}
}
