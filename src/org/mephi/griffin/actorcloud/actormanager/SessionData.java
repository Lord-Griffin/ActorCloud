/*
 * Copyright 2015 Griffin.
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
package org.mephi.griffin.actorcloud.actormanager;

import akka.actor.ActorRef;
import akka.actor.Address;
import java.io.Serializable;

/**
 *
 * @author Griffin
 */
public class SessionData implements Serializable {
	private ActorRef actor;
	private Address actorNode;
	private Address netNode;
	
	public SessionData(ActorRef actor, Address actorNode, Address netNode) {
		this.actor = actor;
		this.actorNode = actorNode;
		this.netNode = netNode;
	}
	
	public ActorRef getActor() {
		return actor;
	}
	
	public void setActor(ActorRef actor) {
		this.actor = actor;
	}
	
	public Address getActorNode() {
		return actorNode;
	}
	
	public void setActorNode(Address node) {
		actorNode = node;
	}
	
	public Address getNetNode() {
		return netNode;
	}
	
	public String getDump() {
		String dump = "";
		dump += "      actor " + actor + "\n";
		dump += "      actorNode " + actorNode + "\n";
		dump += "      netNode " + netNode + "\n";
		return dump;
	}
}
