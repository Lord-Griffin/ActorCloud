/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mephi.griffin.actorcloud.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

/**
 *
 * @author Griffin
 */
public class AuthHandler extends IoHandlerAdapter {
	
	private static final Logger logger = Logger.getLogger(AuthHandler.class.getName());
	Connection conn;
	
	public AuthHandler(Connection conn) {
		logger.entering("AuthHandler", "Constructor", conn);
		this.conn = conn;
		logger.exiting("AuthHandler", "Constructor");
	}
	
	@Override
	public void sessionOpened(IoSession session) {
		logger.entering("AuthHandler", "sessionOpened", session);
		logger.exiting("AuthHandler", "sessionOpened");
	}
	
	@Override
	public void messageReceived(IoSession session, Object message) {
		logger.entering("AuthHandler", "messageReceived", new Object[]{session, message});
		if(message instanceof IoBuffer) {
			IoBuffer buf = (IoBuffer) message;
			int error = buf.getInt();
			if(error == 1) {
				byte[] str = new byte[buf.remaining()];
				buf.get(str);
				String reason = new String(str);
				conn.authError(reason);
			}
			else if(error == 0) {
				byte[] bytes = new byte[buf.getInt()];
				buf.get(bytes);
				String token = new String(bytes);
				int size = buf.getInt();
				List<InetSocketAddress> addresses = new ArrayList<>();
				for(int i = 0; i < size; i++) {
					bytes = new byte[buf.getInt()];
					buf.get(bytes);
					int port = buf.getInt();
					try {
						addresses.add(new InetSocketAddress(InetAddress.getByAddress(bytes), port));
					}
					catch(UnknownHostException ex) {}
				}
				conn.setToken(token, addresses);
			}
		}
		logger.exiting("AuthHandler", "messageReceived");
	}
	
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		logger.entering("AuthHandler", "exceptionCaught", new Object[]{session, cause});
		logger.throwing("AuthHandler", "excpetionCaught", cause);
		session.close(true);
		logger.exiting("AuthHandler", "exceptionCaught");
	}
}