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

import java.io.IOException;
import org.mephi.griffin.actorcloud.client.Message;

/**
 *
 * @author Griffin
 */
public class ClientInfo extends Message {
	
	private String login;
	private byte[] hash;
	private String messageHandler;
	private String childHandler;
	
	public ClientInfo() {}
	
	public ClientInfo(String login, byte[] hash, String messageHandler, String childHandler) {
		this.login = login;
		this.hash = hash;
		this.messageHandler = messageHandler;
		this.childHandler = childHandler;
	}
	
	public void setLogin(String login) {
		this.login = login;
	}
	
	public void setHash(byte[] hash) {
		this.hash = hash;
	}
	
	public void setMessageHandler(String messageHandler) {
		this.messageHandler = messageHandler;
	}
	
	public void setChildHandler(String childHandler) {
		this.childHandler = childHandler;
	}
	
	public String getLogin() {
		return login;
	}
	
	public byte[] getHash() {
		return hash;
	}
	
	public String getMessageHandler() {
		return messageHandler;
	}
	
	public String getChildHandler() {
		return childHandler;
	}

	@Override
	public byte[] getData() throws IOException {
		return new byte[1];
	}
}
