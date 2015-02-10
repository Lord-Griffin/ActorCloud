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
package org.mephi.griffin.actorcloud.enqueuer;

import akka.actor.ActorRef;

/**
 *
 * @author Griffin
 */
public class ChannelData {
	
	private String client;
	private ActorRef actor;
	
	public ChannelData(String client, ActorRef actor) {
		this.client = client;
		this.actor = actor;
	}
	
	public String getClient() {
		return client;
	}
	
	public ActorRef getActor() {
		return actor;
	}
	
	@Override
	public String toString() {
		return "Client " + client + ", actor " + actor;
	}
}