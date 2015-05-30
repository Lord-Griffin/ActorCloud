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
package org.mephi.griffin.actorcloud.common;

import akka.actor.ActorRef;
import java.util.Map;

/**
 *
 * @author Griffin
 */
public class ActorData {
	private String client;
	private int maxChilds;
	private Map<String, String> messageHandlers;
	private Map<String, String> childHandlers;
	private ActorRef actorManager;

	public ActorData(String client, int maxChilds, Map<String, String> messageHandlers, Map<String, String> childHandlers) {
		this.client = client;
		this.maxChilds = maxChilds;
		this.messageHandlers = messageHandlers;
		this.childHandlers = childHandlers;
		this.actorManager = null;
	}
	
	public ActorData(String client, int maxChilds, Map<String, String> messageHandlers, Map<String, String> childHandlers, ActorRef actorManager) {
		this(client, maxChilds, messageHandlers, childHandlers);
		this.actorManager = actorManager;
	}
	
	public String getClient() {
		return client;
	}
	
	public int getMaxChilds() {
		return maxChilds;
	}
	
	public Map<String, String> getMessageHandlers() {
		return messageHandlers;
	}
	
	public Map<String, String> getChildHandlers() {
		return childHandlers;
	}
	
	public ActorRef getActorManager() {
		return actorManager;
	}
	
	public String getDump() {
		String dump = "";
		dump += "    client " + client + "\n";
		dump += "    messsageHandler " + messageHandlers + "\n";
		dump += "    childHandler " + childHandlers + "\n";
		if(actorManager != null) dump += "    actorManager " + actorManager + "\n";
		return dump;
	}
}
