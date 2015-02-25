/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mephi.griffin.actorcloud.client;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;


/**
 *
 * @author Griffin
 */
public class NetHandler extends IoHandlerAdapter {
	
	private final MessageListener messageListener;
	
	public NetHandler(MessageListener messageListener) {
		this.messageListener = messageListener;
	}
	
	@Override
	public void sessionClosed(IoSession session) {
		session.close(false);
	}
	
	@Override
	public void messageReceived(IoSession session, Object message) {
		if(message instanceof Message)
			messageListener.receive((Message) message);
	}
	
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		cause.printStackTrace(System.err);
		session.close(true);
	}
}
