/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mephi.griffin.actorcloud.client;

import java.io.IOException;
import java.io.Serializable;

/**
 *
 * @author Griffin
 */
public abstract class Message implements Serializable {
	
	public Message() {}
	
	public abstract byte[] getData() throws IOException;	
}
