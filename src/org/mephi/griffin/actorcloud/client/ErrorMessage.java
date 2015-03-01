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
package org.mephi.griffin.actorcloud.client;

/**
 *
 * @author Griffin
 */
public class ErrorMessage implements Message {
	public static final int WRONG_CRED = 1;
	public static final int SYSTEM_ERR = 2;
	public static final int STOR_ERR = 3;
	public static final int INVALID_TOKEN = 4;
	public static final int CUSTOM = 100;
	
	private int type;
	private String message;
	private Exception ex;
	
	public ErrorMessage() {}
	
	public ErrorMessage(int type, String message, Exception ex) {
		this.type = type;
		this.message = message;
		this.ex = ex;
	}
	
	public int getType() {
		return type;
	}
	
	public String getMessage() {
		return message;
	}
	
	public Exception getException() {
		return ex;
	}
	
	public void setException(Exception ex) {
		this.ex = ex;
	}
	
	@Override
	public String toString() {
		String res = "";
		switch(type) {
			case WRONG_CRED:
				res = "Wrong credentials";
				break;
			case SYSTEM_ERR:
				res = "System error";
				break;
			case STOR_ERR:
				res = "Storage error";
				break;
			case INVALID_TOKEN:
				res = "Invalid token";
				break;
			case CUSTOM:
				res = "Custom error";
				break;
		}
		if(message != null) res += ": " + message;
		if(ex != null && ex.getMessage() != null) res += ", exception: " + ex.getMessage();
		else if(ex != null) res += ", exception: " + ex.getClass().getName();
		return res;
	}
}
