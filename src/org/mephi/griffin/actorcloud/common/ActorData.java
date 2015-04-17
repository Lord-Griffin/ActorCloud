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

/**
 *
 * @author Griffin
 */
public class ActorData {
	private String client;
	private String messageHandler;
	private String childHandler;
	private ActorRef actorManager;

	public ActorData(String client, String messageHandler, String childHandler) {
		this.client = client;
		this.messageHandler = messageHandler;
		this.childHandler = childHandler;
		this.actorManager = null;
	}
	
	public ActorData(String client, String messageHandler, String childHandler, ActorRef actorManager) {
		this(client, messageHandler, childHandler);
		this.actorManager = actorManager;
	}
	
	public String getClient() {
		return client;
	}
	
	public String getMessageHandler() {
		return messageHandler;
	}
	
	public String getChildHandler() {
		return childHandler;
	}
	
	public ActorRef getActorManager() {
		return actorManager;
	}
	
	public String getDump() {
		String dump = "";
		dump += "    client " + client + "\n";
		dump += "    messsageHandler " + messageHandler + "\n";
		dump += "    childHandler " + childHandler + "\n";
		if(actorManager != null) dump += "    actorManager " + actorManager + "\n";
		return dump;
	}
}
