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
public class SuccessMessage implements Message {
	public static final int ADD = 1;
	public static final int MODIFY = 2;
	public static final int REMOVE = 3;

	private final int type;
	
	public SuccessMessage(int type) {
		this.type = type;
	}
	
	public int getType() {
		return type;
	}
}
