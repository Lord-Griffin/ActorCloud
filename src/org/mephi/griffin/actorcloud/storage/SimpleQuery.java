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
import java.util.Date;
import java.util.List;

public class SimpleQuery extends Query {
	public static final int ALL = 0;
	public static final int EQUAL = 1;
	public static final int GREATER = 2;
	public static final int GREATER_OR_EQUAL = 3;
	public static final int LESS = 4;
	public static final int LESS_OR_EQUAL = 5;
	public static final int NOT_EQUAL = 6;
	public static final int IN = 7;
	public static final int NOT_IN = 8;
	
	private int op;
	private String field;
	private Object value;
	
	public SimpleQuery(String field, int op, Object value) {
		if(field != null && !checkClass(value)) throw new IllegalArgumentException("Storage of class " + value.getClass().getName() + " is not supported");
		this.op = op;
		this.field = field;
		this.value = value;
	}
	
	int getOp() {
		return op;
	}
	
	String getField() {
		return field;
	}
	
	Object getValue() {
		return value;
	}
	
	private boolean checkClass(Object value) {
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
		if(op == ALL) return null;
		if(op == EQUAL) return new BasicDBObject(field, value);
		if(op == GREATER) return new BasicDBObject(field, new BasicDBObject("$gt", value));
		if(op == GREATER_OR_EQUAL) return new BasicDBObject(field, new BasicDBObject("$gte", value));
		if(op == LESS) return new BasicDBObject(field, new BasicDBObject("$lt", value));
		if(op == LESS_OR_EQUAL) return new BasicDBObject(field, new BasicDBObject("$lte", value));
		if(op == NOT_EQUAL) return new BasicDBObject(field, new BasicDBObject("$ne", value));
		if(op == IN) return new BasicDBObject(field, new BasicDBObject("$in", value));
		if(op == NOT_IN) return new BasicDBObject(field, new BasicDBObject("$nin", value));
		throw new IllegalArgumentException("Unknown operation");
	}
}
