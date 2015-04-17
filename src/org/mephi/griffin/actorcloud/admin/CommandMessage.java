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
package org.mephi.griffin.actorcloud.admin;

import org.mephi.griffin.actorcloud.client.messages.Message;

/**
 *
 * @author Griffin
 */
public class CommandMessage implements Message {
	
	public static final int LIST = 1;
	public static final int ADD = 3;
	public static final int MODIFY = 4;
	public static final int REMOVE = 5;
	
	private int op;
	private int index;
	private ClientInfo clientInfo;
	
	public CommandMessage() {}
	
	public CommandMessage(int op) {
		this.op = op;
	}
	
	public CommandMessage(int op, int index) {
		this.op = op;
		this.index = index;
	}
	
	public CommandMessage(int op, ClientInfo clientInfo) {
		this.op = op;
		this.clientInfo = clientInfo;
	}
	
	public CommandMessage(int op, int index, ClientInfo clientInfo) {
		this.op = op;
		this.index = index;
		this.clientInfo = clientInfo;
	}
	
	public int getOp() {
		return op;
	}
	
	public int getIndex() {
		return index;
	}
	
	public ClientInfo getClientInfo() {
		return clientInfo;
	}
	
}
