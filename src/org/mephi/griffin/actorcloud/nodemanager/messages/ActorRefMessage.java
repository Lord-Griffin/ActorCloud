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
package org.mephi.griffin.actorcloud.nodemanager.messages;

import akka.actor.ActorRef;

/**
 *
 * @author Griffin
 */
public class ActorRefMessage {
	public static final int DISPATCHER = 1;
	public static final int NET = 2;
	public static final int STORAGE = 3;
	
	private int type;
	private ActorRef actor;
	
	public ActorRefMessage() {}
	
	public ActorRefMessage(int type, ActorRef actor) {
		this.type = type;
		this.actor = actor;
	}
	
	public int getType() {
		return type;
	}
	
	public ActorRef getRef() {
		return actor;
	}
	
	@Override
	public String toString() {
		String res = "";
		switch(type) {
			case DISPATCHER:
				res += "Dispatcher: ";
				break;
			case NET:
				res += "Network server: ";
				break;
			case STORAGE:
				res += "Storage: ";
				break;
		}
		res += actor;
		return res;
	}
}
