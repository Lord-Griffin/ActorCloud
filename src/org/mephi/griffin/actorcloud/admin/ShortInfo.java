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

/**
 *
 * @author Griffin
 */
public class ShortInfo implements Serializable {
	private final String login;
	private final String id;
	
	public ShortInfo(String login, String id) {
		this.login = login;
		this.id = id;
	}
	
	public String getLogin() {
		return login;
	}
	
	public String getId() {
		return id;
	}
}
