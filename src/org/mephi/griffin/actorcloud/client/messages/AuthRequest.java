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
package org.mephi.griffin.actorcloud.client.messages;

import java.io.Serializable;
import org.mephi.griffin.actorcloud.util.Coder;

/**
 *
 * @author Griffin
 */
public class AuthRequest implements Serializable {
	private final String login;
	private final byte[] hash;
	private final String actor;
	
	public AuthRequest(String login, byte[] hash, String actor) {
		this.login = login;
		this.hash = hash;
		this.actor = actor;
	}
	
	public String getLogin() {
		return login;
	}
	
	public byte[] getHash() {
		return hash;
	}
	
	public String getActor() {
		return actor;
	}
	
	@Override
	public String toString() {
		return "Login: " + login + ", hash: " + Coder.toHexString(hash);
	}
}