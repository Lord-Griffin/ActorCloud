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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.types.ObjectId;

public class Entity {
	HashMap<String, Object> properties;
	
	public Entity() {
		properties = new HashMap<>();
	}
	
	public Entity(BasicDBObject doc) {
		properties = new HashMap<>();
		Set<String> keys = doc.keySet();
		for(String key : keys) {
			if(doc.get(key) instanceof BasicDBObject)
				properties.put(key, new Entity((BasicDBObject) doc.get(key)));
			else if(doc.get(key) instanceof BasicDBObject[]) {
				ArrayList<Entity> entities = new ArrayList<>();
				for (BasicDBObject item : ((BasicDBObject[]) doc.get(key))) {
					entities.add(new Entity(item));
				}
				properties.put(key, entities);
			}
			else if(doc.get(key) instanceof List && ((List) doc.get(key)).size() > 0 && ((List) doc.get(key)).get(0) instanceof BasicDBObject) {
				ArrayList<Entity> entities = new ArrayList<>();
				for(BasicDBObject item : (List<BasicDBObject>) doc.get(key))
					entities.add(new Entity(item));
				properties.put(key, entities);
			}
			else {
				properties.put(key, doc.get(key));
			}
		}
	}
	
	public void add(String name, Object value) {
		properties.put(name, value);
	}
	
	public Object get(String name) {
		return properties.get(name);
	}
	
	private boolean checkClass(Object value) {
		if(value instanceof ObjectId) return true;
		if(value instanceof Integer) return true;
		if(value instanceof Short) return true;
		if(value instanceof Byte) return true;
		if(value instanceof Long) return true;
		if(value instanceof Float) return true;
		if(value instanceof Double) return true;
		if(value instanceof Boolean) return true;
		if(value instanceof String) return true;
		if(value instanceof Date) return true;
		if(value instanceof int[]) return true;
		if(value instanceof short[]) return true;
		if(value instanceof byte[]) return true;
		if(value instanceof long[]) return true;
		if(value instanceof float[]) return true;
		if(value instanceof double[]) return true;
		if(value instanceof boolean[]) return true;
		if(value instanceof Entity) return true;
		if(value instanceof Entity[]) return true;
		if(value instanceof List) return true;
		return false;
	}
	
	BasicDBObject getDoc() {
		BasicDBObject doc = new BasicDBObject();
		Set<Map.Entry<String, Object>> entries = properties.entrySet();
		for(Map.Entry<String, Object> entry : entries) {
			if(checkClass(entry.getValue())) {
				if(entry.getValue() instanceof Entity) {
					doc.append(entry.getKey(), ((Entity) entry.getValue()).getDoc());
				}
				else if(entry.getValue() instanceof Entity[]) {
					ArrayList<BasicDBObject> docs = new ArrayList<>();
					for (Entity entity : ((Entity[]) entry.getValue())) {
						docs.add(entity.getDoc());
					}
					doc.append(entry.getKey(), docs);
				}
				else if(entry.getValue() instanceof List && ((List) entry.getValue()).size() > 0 && ((List) entry.getValue()).get(0) instanceof Entity) {
					ArrayList<BasicDBObject> docs = new ArrayList<>();
					for (Entity entity : ((List<Entity>) entry.getValue())) {
						docs.add(entity.getDoc());
					}
					doc.append(entry.getKey(), docs);
				}
				else doc.append(entry.getKey(), entry.getValue());
			}
			else throw new IllegalArgumentException("Storage of class " + entry.getValue().getClass().getName() + " is not supported");
		}
		return doc;
	}
	
	@Override
	public String toString() {
		return "MongoDB doc: " + getDoc().toString();
	}
}
