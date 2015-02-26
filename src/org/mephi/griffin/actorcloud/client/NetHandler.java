/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mephi.griffin.actorcloud.client;

import java.util.logging.Logger;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.mephi.griffin.actorcloud.netserver.NetServer;


/**
 *
 * @author Griffin
 */
public class NetHandler extends IoHandlerAdapter {
	
	private static final Logger logger = Logger.getLogger(NetHandler.class.getName());
	private final MessageListener messageListener;
	
	public NetHandler(MessageListener messageListener) {
		logger.entering("NetHandler", "Constructor", messageListener);
		this.messageListener = messageListener;
		logger.exiting("NetHandler", "Constructor");
	}
	
	@Override
	public void sessionOpened(IoSession session) {
		logger.entering("NetHandler", "sessionOpened", session);
		logger.exiting("NetHandler", "sessionOpened");
	}
	
	@Override
	public void sessionClosed(IoSession session) {
		logger.entering("NetHandler", "sessionClosed", session);
		//session.close(false);
		logger.exiting("NetHandler", "sessionClosed");
	}
	
	@Override
	public void messageReceived(IoSession session, Object message) {
		logger.entering("NetHandler", "messageReceieved", new Object[]{session, message});
		if(message instanceof Message)
			messageListener.receive((Message) message);
		logger.exiting("NetHandler", "messageReceived");
	}
	
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		logger.entering("NetHandler", "exceptionCaught", new Object[]{session, cause});
		logger.throwing("NetHandler", "exceptionCaught", cause);
		session.close(true);
		logger.exiting("NetHandler", "exceptionCaught");
	}
}
