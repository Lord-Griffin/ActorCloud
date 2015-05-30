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

import org.mephi.griffin.actorcloud.client.messages.ErrorMessage;
import org.mephi.griffin.actorcloud.client.messages.Message;
import org.mephi.griffin.actorcloud.client.messages.TokenMessage;
import org.mephi.griffin.actorcloud.client.messages.TokenResponse;
import org.mephi.griffin.actorcloud.client.messages.AuthResponse;
import org.mephi.griffin.actorcloud.client.messages.AuthRequest;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.SocketConnector;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

/**
 *
 * @author Griffin
 */
public class Connector extends IoHandlerAdapter {
	private SocketConnector connector;
	
	@SuppressWarnings("LeakingThisInConstructor")
	public Connector(String keyStoreName, String keyStorePass, String privateKeyPass, String trustStoreName, String trustStorePass) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {
		KeyStore keyStore = KeyStore.getInstance("jks");
		keyStore.load(new FileInputStream(keyStoreName), keyStorePass.toCharArray());
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
		kmf.init(keyStore, privateKeyPass.toCharArray());
		KeyStore trustStore = KeyStore.getInstance("jks");
		trustStore.load(new FileInputStream(trustStoreName), trustStorePass.toCharArray());
		TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
		tmf.init(trustStore);
		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		connector = new NioSocketConnector();
		DefaultIoFilterChainBuilder chain = connector.getFilterChain();
		SslFilter sslFilter = new SslFilter(sslContext);
		sslFilter.setUseClientMode(true);
		chain.addLast("sslFilter", sslFilter);
		chain.addLast("serializationFilter", new ProtocolCodecFilter(new ObjectSerializationCodecFactory()));
		connector.setHandler(this);
	}
	
	public Future<Connection> getConnection(final String authHost, final int authPort, final String login, String pass, final MessageListener messageListener) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-512");
		final byte[] hash = md.digest(md.digest(pass.getBytes()));
		final AuthRequest request = new AuthRequest(login, hash, "");
		final ConnectionFuture connectionFuture = new ConnectionFuture();
		ConnectFuture cf = connector.connect(new InetSocketAddress(authHost, authPort));
		connectionFuture.setAuthFuture(cf);
		cf.addListener(new IoFutureListener<ConnectFuture>() {
			@Override
			public void operationComplete(ConnectFuture future) {
				if(!future.isConnected()) {
					connectionFuture.setException(future.getException());
					return;
				}
				final IoSession session = future.getSession();
				session.setAttribute("login", login);
				session.setAttribute("hash", hash);
				session.setAttribute("host", authHost);
				session.setAttribute("port", authPort);
				session.setAttribute("future", connectionFuture);
				session.setAttribute("listener", messageListener);
				WriteFuture wf = session.write(request);
				connectionFuture.setAuthFuture(wf);
				wf.addListener(new IoFutureListener<WriteFuture>() {
					@Override
					public void operationComplete(WriteFuture future) {
						if(!future.isWritten()) {
							connectionFuture.setException(future.getException());
							session.removeAttribute("login");
							session.removeAttribute("hash");
							session.removeAttribute("host");
							session.removeAttribute("port");
							session.removeAttribute("future");
							session.removeAttribute("listener");
							session.close(true);
						}
					}
				});
			}
		});
		return connectionFuture;
	}
	
	public void close() {
		connector.dispose();
	}
	
	@Override
	public void sessionOpened(IoSession session) {
		
	}
	
	@Override
	public void sessionClosed(IoSession session) {
		if(session.getAttribute("future") != null)
			((ConnectionFuture) session.getAttribute("future")).sessionClosed((InetSocketAddress) session.getRemoteAddress(), null);
		if(session.getAttribute("future") == null && session.getAttribute("gracefullyClosed") == null) {
			final String login = (String) session.getAttribute("login");
			final byte[] hash = (byte[]) session.getAttribute("hash");
			final String authHost = (String) session.getAttribute("host");
			final int authPort = (int) session.getAttribute("port");
			final MessageListener messageListener = (MessageListener) session.getAttribute("listener");
			final Connection connection = (Connection) session.getAttribute("connection");
			final AuthRequest request = new AuthRequest(login, hash, (String) session.getAttribute("actor"));
			final ConnectionFuture connectionFuture = new ConnectionFuture();
			ConnectFuture cf = connector.connect(new InetSocketAddress(authHost, authPort));
			connectionFuture.setAuthFuture(cf);
			cf.addListener(new IoFutureListener<ConnectFuture>() {
				@Override
				public void operationComplete(ConnectFuture future) {
					if(!future.isConnected()) {
						connectionFuture.setException(future.getException());
						return;
					}
					final IoSession session = future.getSession();
					session.setAttribute("login", login);
					session.setAttribute("hash", hash);
					session.setAttribute("host", authHost);
					session.setAttribute("port", authPort);
					session.setAttribute("future", connectionFuture);
					session.setAttribute("listener", messageListener);
					session.setAttribute("connection", connection);
					WriteFuture wf = session.write(request);
					connectionFuture.setAuthFuture(wf);
					wf.addListener(new IoFutureListener<WriteFuture>() {
						@Override
						public void operationComplete(WriteFuture future) {
							if(!future.isWritten()) {
								connectionFuture.setException(future.getException());
								session.removeAttribute("login");
								session.removeAttribute("hash");
								session.removeAttribute("host");
								session.removeAttribute("port");
								session.removeAttribute("future");
								session.removeAttribute("listener");
								session.removeAttribute("connection");
								session.close(true);
							}
						}
					});
				}
			});
		}
		session.close(true);
	}
	
	@Override
	public void messageReceived(IoSession session, Object message) {
		if(message instanceof AuthResponse) {
			final AuthResponse response = (AuthResponse) message;
			final String login = (String) session.getAttribute("login");
			final byte[] hash = (byte[]) session.getAttribute("hash");
			final String authHost = (String) session.getAttribute("host");
			final int authPort = (int) session.getAttribute("port");
			final ConnectionFuture connectionFuture = (ConnectionFuture) session.getAttribute("future");
			final MessageListener messageListener = (MessageListener) session.getAttribute("listener");
			final Connection connection = (Connection) session.getAttribute("connection");
			List<InetSocketAddress> addresses = response.getAddresses();
			connectionFuture.setAddresses(addresses);
			session.removeAttribute("login");
			session.removeAttribute("hash");
			session.removeAttribute("host");
			session.removeAttribute("port");
			session.removeAttribute("future");
			session.removeAttribute("listener");
			if(connection != null) session.removeAttribute("connection");
			session.close(true);
			for(final InetSocketAddress address : addresses) {
				ConnectFuture cf = connector.connect(address);
				connectionFuture.setNetFuture(address, cf);
				cf.addListener(new IoFutureListener<ConnectFuture>() {
					@Override
					public void operationComplete(ConnectFuture future) {
						if(!future.isConnected()) {
							connectionFuture.connectFail(address, future.getException());
							return;
						}
						final IoSession session = future.getSession();
						session.setAttribute("login", login);
						session.setAttribute("hash", hash);
						session.setAttribute("host", authHost);
						session.setAttribute("port", authPort);
						session.setAttribute("future", connectionFuture);
						session.setAttribute("listener", messageListener);
						if(connection != null) session.setAttribute("connection", connection);
						WriteFuture wf = session.write(new TokenMessage(response.getToken()));
						connectionFuture.setNetFuture(address, wf);
						wf.addListener(new IoFutureListener<WriteFuture>() {
							@Override
							public void operationComplete(WriteFuture future) {
								if(!future.isWritten()) {
									connectionFuture.connectFail(address, future.getException());
									session.removeAttribute("login");
									session.removeAttribute("hash");
									session.removeAttribute("host");
									session.removeAttribute("port");
									session.removeAttribute("future");
									session.removeAttribute("listener");
									if(session.getAttribute("connection") != null) session.removeAttribute("connection");
									session.close(true);
								}
							}
						});
					}
				});
			}
		}
		else if(message instanceof TokenResponse) {
			TokenResponse tr = (TokenResponse) message;
			Connection connection = (Connection) session.getAttribute("connection");
			if(connection != null) connection.setSession(session);
			session.setAttribute("actor", tr.getActor());
			((ConnectionFuture) session.getAttribute("future")).connectSuccess(session);
			session.removeAttribute("future");
		}
		else if(message instanceof ErrorMessage) {
			if(session.getAttribute("future") != null) {
				((ConnectionFuture) session.getAttribute("future")).connectFail((InetSocketAddress) session.getRemoteAddress(), (ErrorMessage) message);
				session.removeAttribute("login");
				session.removeAttribute("hash");
				session.removeAttribute("host");
				session.removeAttribute("port");
				session.removeAttribute("future");
				session.removeAttribute("listener");
				session.close(true);
			}
			else {
				//TODO
			}
		}
		/*else if(message instanceof LogMessage) {
			
		}*/
		else if(message instanceof byte[]) {
			byte[] bytes = (byte[]) message;
			if(session.getAttribute("listener") != null) {
				try {
					ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
					ObjectInputStream ois = new ObjectInputStream(bais);
					Object msg = ois.readObject();
					if(msg instanceof Message) {
						((MessageListener) session.getAttribute("listener")).receive((Message) msg);
					}
				} catch (IOException | ClassNotFoundException ex) {
					Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}
	
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		if(session.getAttribute("future") != null) {
			((ConnectionFuture) session.getAttribute("future")).sessionClosed((InetSocketAddress) session.getRemoteAddress(), cause);
		}
		if(session.getAttribute("future") == null && session.getAttribute("gracefullyClosed") == null) {
			final String login = (String) session.getAttribute("login");
			final byte[] hash = (byte[]) session.getAttribute("hash");
			final String authHost = (String) session.getAttribute("host");
			final int authPort = (int) session.getAttribute("port");
			final MessageListener messageListener = (MessageListener) session.getAttribute("listener");
			final Connection connection = (Connection) session.getAttribute("connection");
			final AuthRequest request = new AuthRequest(login, hash, (String) session.getAttribute("actor"));
			final ConnectionFuture connectionFuture = new ConnectionFuture();
			ConnectFuture cf = connector.connect(new InetSocketAddress(authHost, authPort));
			connectionFuture.setAuthFuture(cf);
			cf.addListener(new IoFutureListener<ConnectFuture>() {
				@Override
				public void operationComplete(ConnectFuture future) {
					if(!future.isConnected()) {
						connectionFuture.setException(future.getException());
						return;
					}
					final IoSession session = future.getSession();
					session.setAttribute("login", login);
					session.setAttribute("hash", hash);
					session.setAttribute("host", authHost);
					session.setAttribute("port", authPort);
					session.setAttribute("future", connectionFuture);
					session.setAttribute("listener", messageListener);
					session.setAttribute("connection", connection);
					WriteFuture wf = session.write(request);
					connectionFuture.setAuthFuture(wf);
					wf.addListener(new IoFutureListener<WriteFuture>() {
						@Override
						public void operationComplete(WriteFuture future) {
							if(!future.isWritten()) {
								connectionFuture.setException(future.getException());
								session.removeAttribute("login");
								session.removeAttribute("hash");
								session.removeAttribute("host");
								session.removeAttribute("port");
								session.removeAttribute("future");
								session.removeAttribute("listener");
								session.removeAttribute("connection");
								session.close(true);
							}
						}
					});
				}
			});
		}
		session.close(true);
	}
}
