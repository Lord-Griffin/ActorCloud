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
package org.mephi.griffin.actorcloud.actormanager.messages;

import akka.actor.ActorRef;
import java.io.Serializable;

/**
 *
 * @author Griffin
 */
public class RecoverClientActor implements Serializable {
	private final ActorRef deadActor;
	private final String client;
	private final String messageHandler;
	private final String childHandler;
	
	public RecoverClientActor(ActorRef deadActor, String client, String messageHandler, String childHandler) {
		this.deadActor = deadActor;
		this.client = client;
		this.messageHandler = messageHandler;
		this.childHandler = childHandler;
	}
	
	public ActorRef getActor() {
		return deadActor;
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
	
	@Override
	public String toString() {
		return "deadActor " + deadActor + ", client " + client + ", messageHandler " + messageHandler + ", childHandler " + childHandler;
	}
}
