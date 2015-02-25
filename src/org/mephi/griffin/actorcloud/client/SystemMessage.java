/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mephi.griffin.actorcloud.client;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 *
 * @author Griffin
 */
public class SystemMessage extends Message {
	
	private String message;
	
	public SystemMessage() {
		message = "";
	}
	
	public SystemMessage(String message) {
		this.message = message;
	}
	
	@Override
	public byte[] getData() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		dos.writeBytes(message);
		return baos.toByteArray();
	}
	
	public String getMessage() {
		return message;
	}
}
