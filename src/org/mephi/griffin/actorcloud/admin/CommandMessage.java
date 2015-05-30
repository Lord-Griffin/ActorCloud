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
	public static final int GET = 2;
	public static final int ADD = 3;
	public static final int MODIFY = 4;
	public static final int REMOVE = 5;
	
	private final int op;
	private final int page;
	private final int index;
	private final String login;
	private final ClientInfo info;
	
	public CommandMessage(int op, int page) {
		this.op = op;
		this.page = page;
		this.index = 0;
		this.login = null;
		this.info = null;
	}
	
	public CommandMessage(int op, String login) {
		this.op = op;
		this.page = 0;
		this.index = 0;
		this.login = login;
		this.info = null;
	}
	
	public CommandMessage(int op, ClientInfo clientInfo) {
		this.op = op;
		this.page = 0;
		this.index = 0;
		this.login = null;
		this.info = clientInfo;
	}
	
	public CommandMessage(int op, ClientInfo clientInfo, String login) {
		this.op = op;
		this.page = 0;
		this.index = 0;
		this.login = login;
		this.info = clientInfo;
	}
	
	public int getOp() {
		return op;
	}
	
	public int getPage() {
		return page;
	}
	
	public int getIndex() {
		return index;
	}
	
	public String getLogin() {
		return login;
	}
	
	public ClientInfo getClientInfo() {
		return info;
	}
	
}
