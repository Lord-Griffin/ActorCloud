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

import akka.actor.Address;
import java.io.Serializable;
import java.util.Map;

/**
 *
 * @author Griffin
 */
public class Handoff implements Serializable {
	private final Address nodeHandoffTo;
	private final String client;
	private final int maxChilds;
	private final Map<String, String> messageHandlers;
	private final Map<String, String> childHandlers;
	
	public Handoff(Address nodeHandoffTo, String client, int maxChilds, Map<String, String> messageHandlers, Map<String, String> childHandlers) {
		this.nodeHandoffTo = nodeHandoffTo;
		this.client = client;
		this.maxChilds = maxChilds;
		this.messageHandlers = messageHandlers;
		this.childHandlers = childHandlers;
	}

	public Address getNode() {
		return nodeHandoffTo;
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
}
