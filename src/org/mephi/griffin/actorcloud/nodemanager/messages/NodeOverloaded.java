/*
 * Copyright 2015 griffin.
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
package org.mephi.griffin.actorcloud.nodemanager.messages;

import akka.actor.Address;
import java.io.Serializable;

/**
 *
 * @author griffin
 */
public class NodeOverloaded implements Serializable {
	public static final int MANAGER = 1;
	public static final int NET = 2;
	public static final int GENERAL = 3;
	
	private final Address node;
	private final Address freeNode;
	private final int type;
	
	public NodeOverloaded(int type, Address node, Address freeNode) {
		this.node = node;
		this.freeNode = freeNode;
		this.type = type;
	}
	
	public int getType() {
		return type;
	}
	
	public Address getOverloadedNode() {
		return node;
	}
	
	public Address getFreeNode() {
		return freeNode;
	}
	
	@Override
	public String toString() {
		String res = "";
		switch(type) {
			case MANAGER:
				res += "Manager";
				break;
			case NET:
				res += "Network server";
				break;
			case GENERAL:
				res += "General";
				break;
		}
		res += " node overloaded: " + node;
		return res;
	}
}
