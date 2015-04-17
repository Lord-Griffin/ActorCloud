/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mephi.griffin.actorcloud.client.messages;

import java.io.Serializable;

/**
 *
 * @author Griffin
 */
public class SystemMessage implements Serializable {
	
	private String message;
	
	public SystemMessage() {
		message = "";
	}
	
	public SystemMessage(String message) {
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}
	
	public String toString() {
		return "System Message: \"" + message + "\"";
	}
}
