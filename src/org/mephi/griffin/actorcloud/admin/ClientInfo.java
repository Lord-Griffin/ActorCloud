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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Griffin
 */
public class ClientInfo implements Serializable {
	
	private String oldLogin;
	private String login;
	private byte[] hash;
	private int maxSessions;
	private int maxChilds;
	private List<Handler> mainHandlers;
	private List<Handler> childHandlers;
	
	public ClientInfo() {
		oldLogin = "";
		login = "";
		hash = null;
		maxSessions = 0;
		maxChilds = 0;
		mainHandlers = new ArrayList<>();
		childHandlers = new ArrayList<>();
	}
	
	public void setOldLogin(String oldLogin) {
		this.oldLogin = oldLogin;
	}
	
	public void setLogin(String login) {
		this.login = login;
	}
	
	public void setHash(byte[] hash) {
		this.hash = hash;
	}
	
	public void setMaxSessions(int maxSessions) {
		this.maxSessions = maxSessions;
	}
	
	public void setMaxChilds(int maxChilds) {
		this.maxChilds = maxChilds;
	}
	
	public void addMainHandler(String message, String handler) {
		mainHandlers.add(new Handler(message, handler));
	}
	
	public void setMainHandler(String message, String handler, int page, int index) {
		mainHandlers.set(page * 10 + (9 + index) % 10, new Handler(message, handler));
	}
	
	public void removeMainHandler(int page, int index) {
		mainHandlers.remove(page * 10 + (9 + index) % 10);
	}
	
	public void addChildHandler(String message, String handler) {
		childHandlers.add(new Handler(message, handler));
	}
	
	public void setChildHandler(String message, String handler, int page, int index) {
		childHandlers.set(page * 10 + (9 + index) % 10, new Handler(message, handler));
	}
	
	public void removeChildHandler(int page, int index) {
		childHandlers.remove(page * 10 + (9 + index) % 10);
	}
	
	public String getOldLogin() {
		return oldLogin;
	}
	
	public String getLogin() {
		return login;
	}
	
	public byte[] getHash() {
		return hash;
	}
	
	public int getMaxSessions() {
		return maxSessions;
	}
	
	public int getMaxChilds() {
		return maxChilds;
	}
	
	public List<Handler> getMainHandlers() {
		return mainHandlers;
	}
	
	public List<Handler> getChildHandlers() {
		return childHandlers;
	}
}
