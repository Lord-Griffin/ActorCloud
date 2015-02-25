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
package org.mephi.griffin.actorcloud.common;

/**
 *
 * @author Griffin
 */
public class InitSuccess {
	public static final int STORAGE = 1;
	public static final int CLIENT = 100;
	public static final int CHILD = 101;
	private int type;
	private String name;
	
	public InitSuccess() {}
	
	public InitSuccess(int type, String name) {
		this.type = type;
		this.name = name;
	}
	
	public int getType() {
		return type;
	}
	
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		String res = "";
		switch(type) {
			case STORAGE:
				res += "Storage";
				break;
			case CLIENT:
				res += "Client actor \"" + name + "\"";
				break;
			case CHILD:
				res += "Child actor " + name;
		}
		return res;
	}
}
