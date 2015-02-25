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

import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class CompoundQuery extends Query {
	public static final int AND = 1;
	public static final int OR = 2;
	
	private int type;
	private HashMap<String, Object> fields;
	
	public CompoundQuery(int type) {
		if(type != AND && type != OR) throw new IllegalArgumentException("Uknown operation");
		this.type = type;
		fields = new HashMap<>();
	}
	
	int getType() {
		return type;
	}
	
	HashMap<String, Object> getFields() {
		return fields;
	}
	
	public void add(CompoundQuery query) {
		if(query.getType() == type) {
			fields.putAll(query.getFields());
		}
		else if(query.getType() != type) {
			if(type == AND) {
				fields.put("$or", query.getDoc());
			}
			else {
				fields.put("_AND_", query.getDoc());
			}
		}
	}
	
	public void add(SimpleQuery query) {
		if(query.getOp() == query.EQUAL) fields.put(query.getField(), query.getValue());
		if(query.getOp() == query.GREATER) fields.put(query.getField(), new BasicDBObject("$gt", query.getValue()));
		if(query.getOp() == query.GREATER_OR_EQUAL) fields.put(query.getField(), new BasicDBObject("$gte", query.getValue()));
		if(query.getOp() == query.LESS) fields.put(query.getField(), new BasicDBObject("$lt", query.getValue()));
		if(query.getOp() == query.LESS_OR_EQUAL) fields.put(query.getField(), new BasicDBObject("$lte", query.getValue()));
		if(query.getOp() == query.NOT_EQUAL) fields.put(query.getField(), new BasicDBObject("$ne", query.getValue()));
		if(query.getOp() == query.IN) fields.put(query.getField(), new BasicDBObject("$in", query.getValue()));
		if(query.getOp() == query.NOT_IN) fields.put(query.getField(), new BasicDBObject("$nin", query.getValue()));
	}
	
	public void add(SimpleQuery[] queries) {
		for (SimpleQuery query : queries) add(query);
	}
	
	BasicDBObject getDoc() {
		BasicDBObject result = new BasicDBObject();
		if(type == AND) {
			Set<String> keys = fields.keySet();
			for(String key : keys) result.append(key, fields.get(key));
		}
		else {
			ArrayList<BasicDBObject> docs = new ArrayList<>();
			Set<String> keys = fields.keySet();
			for(String key : keys) {
				if(key.equals("_AND_")) docs.add(((Query) fields.get(key)).getDoc());
				else docs.add(new BasicDBObject(key, fields.get(key)));
			}
			result.append("$or", docs);
		}
		return result;
	}
}
