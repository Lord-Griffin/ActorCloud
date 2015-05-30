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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.client.Connection;
import org.mephi.griffin.actorcloud.client.Connector;
import org.mephi.griffin.actorcloud.client.MessageListener;
import org.mephi.griffin.actorcloud.client.messages.ErrorMessage;
import org.mephi.griffin.actorcloud.client.messages.Message;
import org.mephi.griffin.actorcloud.client.messages.SystemMessage;
import org.mephi.griffin.actorcloud.util.Coder;

/**
 *
 * @author Griffin
 */
public class Client implements MessageListener {
	static Connector connector;
	static Connection conn;
	private static final Object lock = new Object();
	private static int state = 0;
	private static List<ShortInfo> list = null;
	private static ClientInfo info = null;
	private static int page = 0, maxPage = 0, mainHandlersPage = 0, maxMainHandlersPage = 0, childHandlersPage = 0, maxChildHandlersPage = 0;
	
	public static void main(String args[]) {
		Logger.getLogger("").setLevel(Level.ALL);
		final Client client = new Client();
		conn = null;
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		try {
			String user = in.readLine();
			System.out.println(user);
			String pass = in.readLine();
			System.out.println(pass);
			connector = new Connector(args[2], args[3], args[4], args[5], args[6]);
			System.out.println(connector);
			conn = connector.getConnection(args[0], Integer.parseInt(args[1]), user, pass, client).get();
			System.out.println(conn);
		}
		catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyManagementException | InterruptedException | ExecutionException ex) {
			Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
		}
		printMainMenu();
		try {
			while(true) {
				String chr = in.readLine();
				switch(state) {
					case 0:
						switch(chr) {
							case "0":
								conn.close(false);
								connector.close();
								return;
							case "1":
								state = 1;
								conn.send(new CommandMessage(CommandMessage.LIST, page));
								break;
							case "2":
								state = 2;
								info = new ClientInfo();
								printClientMenu();
								break;
							case "3":
								state = 3;
								conn.send(new CommandMessage(CommandMessage.LIST, page));
								break;
							case "4":
								state = 4;
								conn.send(new CommandMessage(CommandMessage.LIST, page));
								break;
						}
						break;
					case 1:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								state = 5;
								conn.send(new CommandMessage(CommandMessage.GET, list.get((9 + Integer.parseInt(chr)) % 10).getLogin()));
							}
						}
						catch (NumberFormatException nfe) {
							switch(chr) {
								case "h":
									page = 0;
									conn.send(new CommandMessage(CommandMessage.LIST, page));
									break;
								case "p":
									if(page > 0) {
										page--;
										conn.send(new CommandMessage(CommandMessage.LIST, page));
									}
									break;
								case "n":
									if(page < maxPage) {
										page++;
										conn.send(new CommandMessage(CommandMessage.LIST, page));
									}
									break;
								case "e":
									page = maxPage;
									conn.send(new CommandMessage(CommandMessage.LIST, page));
									break;
								case "m":
									list = null;
									page = 0;
									maxPage = 0;
									state = 0;
									printMainMenu();
							}
						}
						break;
					case 2:
						switch(chr) {
							case "1":
								System.out.print("Enter login: ");
								String login = in.readLine();
								info.setLogin(login);
								printClientMenu();
								break;
							case "2":
								System.out.print("Enter password: ");
								String pass = in.readLine();
								System.out.print("Confirm password: ");
								String confirm = in.readLine();
								if(!pass.equals(confirm)) {
									System.out.println("Passwords don't match. Press enter");
									in.readLine();
								}
								else {
									MessageDigest md = MessageDigest.getInstance("SHA-512");
									info.setHash(md.digest(pass.getBytes()));
								}
								printClientMenu();
								break;
							case "3":
								System.out.println("Enter max allowed concurrent sessions: ");
								try {
									int maxSessions = Integer.parseInt(in.readLine());
									info.setMaxSessions(maxSessions);
								}
								catch(NumberFormatException nfe) {
									System.out.println("Wrong value. Press Enter");
									in.readLine();
								}
								printClientMenu();
								break;
							case "4":
								System.out.println("Enter max allowed child actors: ");
								try {
									int maxChilds = Integer.parseInt(in.readLine());
									info.setMaxChilds(maxChilds);
								}
								catch(NumberFormatException nfe) {
									System.out.println("Wrong value. Press Enter");
									in.readLine();
								}
								printClientMenu();
								break;
							case "5":
								state = 6;
								printMainHandlersMenu();
								break;
							case "6":
								state = 7;
								printChildHandlersMenu();
								break;
							case "s":
								state = 8;
								conn.send(new CommandMessage(CommandMessage.ADD, info));
								break;
							case "c":
								state = 0;
								info = null;
								printMainMenu();
								break;
						}
						break;
					case 3:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								state = 15;
								conn.send(new CommandMessage(CommandMessage.GET, list.get((9 + Integer.parseInt(chr)) % 10).getLogin()));
							}
						}
						catch (NumberFormatException nfe) {
							switch(chr) {
								case "h":
									page = 0;
									conn.send(new CommandMessage(CommandMessage.LIST, page));
									break;
								case "p":
									if(page > 0) {
										page--;
										conn.send(new CommandMessage(CommandMessage.LIST, page));
									}
									break;
								case "n":
									if(page < maxPage) {
										page++;
										conn.send(new CommandMessage(CommandMessage.LIST, page));
									}
									break;
								case "e":
									page = maxPage;
									conn.send(new CommandMessage(CommandMessage.LIST, page));
									break;
								case "m":
									list = null;
									page = 0;
									maxPage = 0;
									state = 0;
									printMainMenu();
							}
						}
						break;
					case 4:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								state = 25;
								conn.send(new CommandMessage(CommandMessage.REMOVE, list.get((9 + Integer.parseInt(chr)) % 10).getLogin()));
							}
						}
						catch (NumberFormatException nfe) {
							switch(chr) {
								case "h":
									page = 0;
									conn.send(new CommandMessage(CommandMessage.LIST, page));
									break;
								case "p":
									if(page > 0) {
										page--;
										conn.send(new CommandMessage(CommandMessage.LIST, page));
									}
									break;
								case "n":
									if(page < maxPage) {
										page++;
										conn.send(new CommandMessage(CommandMessage.LIST, page));
									}
									break;
								case "e":
									page = maxPage;
									conn.send(new CommandMessage(CommandMessage.LIST, page));
									break;
								case "m":
									list = null;
									page = 0;
									maxPage = 0;
									state = 0;
									printMainMenu();
							}
						}
						break;
					case 5:
						switch(chr) {
							case "b":
								state = 1;
								info = null;
								conn.send(new CommandMessage(CommandMessage.LIST, page));
								break;
							case "m":
								list = null;
								info = null;
								state = 0;
								page = 0;
								maxPage = 0;
								printMainMenu();
								break;
						}
						break;
					case 6:
						switch(chr) {
							case "1":
								state = 9;
								printMainHandlers();
								break;
							case "2":
								System.out.println("Enter message full qualified class name: ");
								String message = in.readLine();
								System.out.println("Enter handler full qualified class name: ");
								String handler = in.readLine();
								info.addMainHandler(message, handler);
								printMainHandlersMenu();
								break;
							case "3":
								state = 10;
								printMainHandlers();
								break;
							case "4":
								state = 11;
								printMainHandlers();
								break;
							case "b":
								state = 15;
								printClientMenu();
								break;
						}
						break;
					case 7:
						switch(chr) {
							case "1":
								state = 12;
								printChildHandlers();
								break;
							case "2":
								System.out.println("Enter message full qualified class name: ");
								String message = in.readLine();
								System.out.println("Enter handler full qualified class name: ");
								String handler = in.readLine();
								info.addChildHandler(message, handler);
								printChildHandlersMenu();
								break;
							case "3":
								state = 13;
								printChildHandlers();
								break;
							case "4":
								state = 14;
								printChildHandlers();
								break;
							case "b":
								state = 15;
								printClientMenu();
								break;
						}
						break;
					case 8:
						if(chr.equals("m")) {
							state = 0;
							info = null;
							printMainMenu();
						}
						break;
					case 9:
						switch(chr) {
							case "h":
								mainHandlersPage = 0;
								printMainHandlers();
								break;
							case "p":
								if(mainHandlersPage > 0) {
									mainHandlersPage--;
									printMainHandlers();
								}
								break;
							case "n":
								if(mainHandlersPage < maxMainHandlersPage) {
									mainHandlersPage++;
									printMainHandlers();
								}
								break;
							case "e":
								mainHandlersPage = maxMainHandlersPage;
								printMainHandlers();
								break;
							case "b":
								state = 6;
								mainHandlersPage = 0;
								maxMainHandlersPage = 0;
								printMainHandlersMenu();
								break;
						}
						break;
					case 10:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								System.out.println("Enter message full qualified class name: ");
								String message = in.readLine();
								System.out.println("Enter handler full qualified class name: ");
								String handler = in.readLine();
								info.setMainHandler(message, handler, mainHandlersPage, Integer.parseInt(chr));
								printMainHandlers();
							}
						}
						catch(NumberFormatException nfe) {
							switch(chr) {
								case "h":
									mainHandlersPage = 0;
									printMainHandlers();
									break;
								case "p":
									if(mainHandlersPage > 0) {
										mainHandlersPage--;
										printMainHandlers();
									}
									break;
								case "n":
									if(mainHandlersPage < maxMainHandlersPage) {
										mainHandlersPage++;
										printMainHandlers();
									}
									break;
								case "e":
									mainHandlersPage = maxMainHandlersPage;
									printMainHandlers();
									break;
								case "b":
									state = 6;
									mainHandlersPage = 0;
									maxMainHandlersPage = 0;
									printMainHandlersMenu();
									break;
							}
						}
						break;
					case 11:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								info.removeMainHandler(mainHandlersPage, Integer.parseInt(chr));
								printMainHandlers();
							}
						}
						catch(NumberFormatException nfe) {
							switch(chr) {
								case "h":
									mainHandlersPage = 0;
									printMainHandlers();
									break;
								case "p":
									if(mainHandlersPage > 0) {
										mainHandlersPage--;
										printMainHandlers();
									}
									break;
								case "n":
									if(mainHandlersPage < maxMainHandlersPage) {
										mainHandlersPage++;
										printMainHandlers();
									}
									break;
								case "e":
									mainHandlersPage = maxMainHandlersPage;
									printMainHandlers();
									break;
								case "b":
									state = 6;
									mainHandlersPage = 0;
									maxMainHandlersPage = 0;
									printMainHandlersMenu();
									break;
							}
						}
						break;
					case 12:
						switch(chr) {
							case "h":
								childHandlersPage = 0;
								printChildHandlers();
								break;
							case "p":
								if(childHandlersPage > 0) {
									childHandlersPage--;
									printChildHandlers();
								}
								break;
							case "n":
								if(childHandlersPage < maxChildHandlersPage) {
									childHandlersPage++;
									printChildHandlers();
								}
								break;
							case "e":
								childHandlersPage = maxChildHandlersPage;
								printChildHandlers();
								break;
							case "b":
								state = 7;
								childHandlersPage = 0;
								maxChildHandlersPage = 0;
								printChildHandlersMenu();
								break;
						}
						break;
					case 13:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								System.out.println("Enter message full qualified class name: ");
								String message = in.readLine();
								System.out.println("Enter handler full qualified class name: ");
								String handler = in.readLine();
								info.setChildHandler(message, handler, childHandlersPage, Integer.parseInt(chr));
								printChildHandlers();
							}
						}
						catch(NumberFormatException nfe) {
							switch(chr) {
								case "h":
									childHandlersPage = 0;
									printChildHandlers();
									break;
								case "p":
									if(childHandlersPage > 0) {
										childHandlersPage--;
										printChildHandlers();
									}
									break;
								case "n":
									if(childHandlersPage < maxChildHandlersPage) {
										childHandlersPage++;
										printChildHandlers();
									}
									break;
								case "e":
									childHandlersPage = maxChildHandlersPage;
									printChildHandlers();
									break;
								case "b":
									state = 7;
									childHandlersPage = 0;
									maxChildHandlersPage = 0;
									printChildHandlersMenu();
									break;
							}
						}
						break;
					case 14:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								info.removeChildHandler(childHandlersPage, Integer.parseInt(chr));
								printChildHandlers();
							}
						}
						catch(NumberFormatException nfe) {
							switch(chr) {
								case "h":
									childHandlersPage = 0;
									printChildHandlers();
									break;
								case "p":
									if(childHandlersPage > 0) {
										childHandlersPage--;
										printChildHandlers();
									}
									break;
								case "n":
									if(childHandlersPage < maxChildHandlersPage) {
										childHandlersPage++;
										printChildHandlers();
									}
									break;
								case "e":
									childHandlersPage = maxChildHandlersPage;
									printChildHandlers();
									break;
								case "b":
									state = 7;
									childHandlersPage = 0;
									maxChildHandlersPage = 0;
									printChildHandlersMenu();
									break;
							}
						}
						break;
					case 15:
						switch(chr) {
							case "1":
								System.out.print("Enter login: ");
								String login = in.readLine();
								info.setLogin(login);
								printClientMenu();
								break;
							case "2":
								System.out.print("Enter password: ");
								String pass = in.readLine();
								System.out.print("Confirm password: ");
								String confirm = in.readLine();
								if(!pass.equals(confirm)) {
									System.out.println("Passwords don't match. Press enter");
									in.readLine();
								}
								else {
									MessageDigest md = MessageDigest.getInstance("SHA-512");
									info.setHash(md.digest(pass.getBytes()));
								}
								printClientMenu();
								break;
							case "3":
								System.out.println("Enter max allowed concurrent sessions: ");
								try {
									int maxSessions = Integer.parseInt(in.readLine());
									info.setMaxSessions(maxSessions);
								}
								catch(NumberFormatException nfe) {
									System.out.println("Wrong value. Press Enter");
									in.readLine();
								}
								printClientMenu();
								break;
							case "4":
								System.out.println("Enter max allowed child actors: ");
								try {
									int maxChilds = Integer.parseInt(in.readLine());
									info.setMaxChilds(maxChilds);
								}
								catch(NumberFormatException nfe) {
									System.out.println("Wrong value. Press Enter");
									in.readLine();
								}
								printClientMenu();
								break;
							case "5":
								state = 16;
								printMainHandlersMenu();
								break;
							case "6":
								state = 17;
								printChildHandlersMenu();
								break;
							case "s":
								state = 18;
								conn.send(new CommandMessage(CommandMessage.MODIFY, info, info.getOldLogin()));
								break;
							case "c":
								state = 0;
								list = null;
								page = 0;
								maxPage = 0;
								info = null;
								printMainMenu();
								break;
						}
						break;
					case 16:
						switch(chr) {
							case "1":
								state = 19;
								printMainHandlers();
								break;
							case "2":
								System.out.println("Enter message full qualified class name: ");
								String message = in.readLine();
								System.out.println("Enter handler full qualified class name: ");
								String handler = in.readLine();
								info.addMainHandler(message, handler);
								printMainHandlersMenu();
								break;
							case "3":
								state = 20;
								printMainHandlers();
								break;
							case "4":
								state = 21;
								printMainHandlers();
								break;
							case "b":
								state = 15;
								printClientMenu();
								break;
						}
						break;
					case 17:
						switch(chr) {
							case "1":
								state = 12;
								printChildHandlers();
								break;
							case "2":
								System.out.println("Enter message full qualified class name: ");
								String message = in.readLine();
								System.out.println("Enter handler full qualified class name: ");
								String handler = in.readLine();
								info.addChildHandler(message, handler);
								printChildHandlersMenu();
								break;
							case "3":
								state = 13;
								printChildHandlers();
								break;
							case "4":
								state = 14;
								printChildHandlers();
								break;
							case "b":
								state = 15;
								printClientMenu();
								break;
						}
						break;
					case 18:
						if(chr.equals("m")) {
							state = 0;
							list = null;
							info = null;
							page = 0;
							maxPage = 0;
							printMainMenu();
						}
						break;
					case 19:
						switch(chr) {
							case "h":
								mainHandlersPage = 0;
								printMainHandlers();
								break;
							case "p":
								if(mainHandlersPage > 0) {
									mainHandlersPage--;
									printMainHandlers();
								}
								break;
							case "n":
								if(mainHandlersPage < maxMainHandlersPage) {
									mainHandlersPage++;
									printMainHandlers();
								}
								break;
							case "e":
								mainHandlersPage = maxMainHandlersPage;
								printMainHandlers();
								break;
							case "b":
								state = 16;
								mainHandlersPage = 0;
								maxMainHandlersPage = 0;
								printMainHandlersMenu();
								break;
						}
						break;
					case 20:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								System.out.println("Enter message full qualified class name: ");
								String message = in.readLine();
								System.out.println("Enter handler full qualified class name: ");
								String handler = in.readLine();
								info.setMainHandler(message, handler, mainHandlersPage, Integer.parseInt(chr));
								printMainHandlers();
							}
						}
						catch(NumberFormatException nfe) {
							switch(chr) {
								case "h":
									mainHandlersPage = 0;
									printMainHandlers();
									break;
								case "p":
									if(mainHandlersPage > 0) {
										mainHandlersPage--;
										printMainHandlers();
									}
									break;
								case "n":
									if(mainHandlersPage < maxMainHandlersPage) {
										mainHandlersPage++;
										printMainHandlers();
									}
									break;
								case "e":
									mainHandlersPage = maxMainHandlersPage;
									printMainHandlers();
									break;
								case "b":
									state = 16;
									mainHandlersPage = 0;
									maxMainHandlersPage = 0;
									printMainHandlersMenu();
									break;
							}
						}
						break;
					case 21:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								info.removeMainHandler(mainHandlersPage, Integer.parseInt(chr));
								printMainHandlers();
							}
						}
						catch(NumberFormatException nfe) {
							switch(chr) {
								case "h":
									mainHandlersPage = 0;
									printMainHandlers();
									break;
								case "p":
									if(mainHandlersPage > 0) {
										mainHandlersPage--;
										printMainHandlers();
									}
									break;
								case "n":
									if(mainHandlersPage < maxMainHandlersPage) {
										mainHandlersPage++;
										printMainHandlers();
									}
									break;
								case "e":
									mainHandlersPage = maxMainHandlersPage;
									printMainHandlers();
									break;
								case "b":
									state = 16;
									mainHandlersPage = 0;
									maxMainHandlersPage = 0;
									printMainHandlersMenu();
									break;
							}
						}
						break;
					case 22:
						switch(chr) {
							case "h":
								childHandlersPage = 0;
								printChildHandlers();
								break;
							case "p":
								if(childHandlersPage > 0) {
									childHandlersPage--;
									printChildHandlers();
								}
								break;
							case "n":
								if(childHandlersPage < maxChildHandlersPage) {
									childHandlersPage++;
									printChildHandlers();
								}
								break;
							case "e":
								childHandlersPage = maxChildHandlersPage;
								printChildHandlers();
								break;
							case "b":
								state = 17;
								childHandlersPage = 0;
								maxChildHandlersPage = 0;
								printChildHandlersMenu();
								break;
						}
						break;
					case 23:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								System.out.println("Enter message full qualified class name: ");
								String message = in.readLine();
								System.out.println("Enter handler full qualified class name: ");
								String handler = in.readLine();
								info.setChildHandler(message, handler, childHandlersPage, Integer.parseInt(chr));
								printChildHandlers();
							}
						}
						catch(NumberFormatException nfe) {
							switch(chr) {
								case "h":
									childHandlersPage = 0;
									printChildHandlers();
									break;
								case "p":
									if(childHandlersPage > 0) {
										childHandlersPage--;
										printChildHandlers();
									}
									break;
								case "n":
									if(childHandlersPage < maxChildHandlersPage) {
										childHandlersPage++;
										printChildHandlers();
									}
									break;
								case "e":
									childHandlersPage = maxChildHandlersPage;
									printChildHandlers();
									break;
								case "b":
									state = 17;
									childHandlersPage = 0;
									maxChildHandlersPage = 0;
									printChildHandlersMenu();
									break;
							}
						}
						break;
					case 24:
						try {
							if(Integer.parseInt(chr) >= 0 && Integer.parseInt(chr) <= 9) {
								info.removeChildHandler(childHandlersPage, Integer.parseInt(chr));
								printChildHandlers();
							}
						}
						catch(NumberFormatException nfe) {
							switch(chr) {
								case "h":
									childHandlersPage = 0;
									printChildHandlers();
									break;
								case "p":
									if(childHandlersPage > 0) {
										childHandlersPage--;
										printChildHandlers();
									}
									break;
								case "n":
									if(childHandlersPage < maxChildHandlersPage) {
										childHandlersPage++;
										printChildHandlers();
									}
									break;
								case "e":
									childHandlersPage = maxChildHandlersPage;
									printChildHandlers();
									break;
								case "b":
									state = 17;
									childHandlersPage = 0;
									maxChildHandlersPage = 0;
									printChildHandlersMenu();
									break;
							}
						}
						break;
					case 25:
						if(chr.equals("m")) {
							state = 0;
							list = null;
							page = 0;
							maxPage = 0;
							printMainMenu();
						}
						break;
				}
			}
		} catch (NoSuchAlgorithmException | IOException ex) {
			ex.printStackTrace(System.out);
		}
	}

	@Override
	public void receive(Message message) {
		if(message instanceof ListMessage) {
			list = ((ListMessage) message).getList();
			maxPage = ((ListMessage) message).getMaxPage();
			printClients();
		}
		else if(message instanceof ClientMessage) {
			info = ((ClientMessage) message).getInfo();
			if(Client.state == 5) printClientInfo();
			else if(Client.state == 15) printClientMenu();
		}
		else if(message instanceof SuccessMessage) {
			if(((SuccessMessage) message).getType() == SuccessMessage.ADD)
				System.out.println("Client successfully added");
			else if(((SuccessMessage) message).getType() == SuccessMessage.MODIFY)
				System.out.println("Client successfully modified");
			else if(((SuccessMessage) message).getType() == SuccessMessage.REMOVE)
				System.out.println("Client successfully removed");
			System.out.println("[m] main menu");
			System.out.println("");
		}
		else if(message instanceof FailMessage) {
			if(((FailMessage) message).getType() == FailMessage.ADD)
				System.out.println("Client successfully added");
			else if(((FailMessage) message).getType() == FailMessage.MODIFY)
				System.out.println("Client successfully modified");
			else if(((FailMessage) message).getType() == FailMessage.REMOVE)
				System.out.println("Client successfully removed");
			System.out.println("[m] main menu");
			System.out.println("");
		}
		else if(message instanceof ErrorMessage) {
			System.out.println("Error: " + ((ErrorMessage) message).getMessage());
		}
		else if(message instanceof SystemMessage) {
			System.out.println("System: " + ((SystemMessage) message).getMessage());
		}
	}
	
	public static void printMainMenu() {
		System.out.println("");
		System.out.println("Choose an action:");
		System.out.println("[1] View client info");
		System.out.println("[2] Add client");
		System.out.println("[3] Modify client info");
		System.out.println("[4] Remove client");
		System.out.println("[0] Exit");
		System.out.println("");
	}
	
	public static void printClients() {
		for(int i = 0; i < list.size(); i++) {
			System.out.println("[" + (i + 1) % 10 + "] " + list.get(i).getLogin());
		}
		System.out.println("");
		if(page != 0) {
			System.out.println("[h] home");
			System.out.println("[p] previous page");
		}
		if(page != maxPage) {
			System.out.println("[n] next page");
			System.out.println("[e] end");
		}
		System.out.println("[m] Main menu");
		System.out.println("");
	}
	
	public static void printClientInfo() {
		System.out.println("");
		System.out.println("Login: " + info.getLogin());
		System.out.println("Hash: " + Coder.toHexString(info.getHash()));
		System.out.println("Max concurrent sessions: " + info.getMaxSessions());
		System.out.println("Max child actors: " + info.getMaxChilds());
		System.out.println("Main handlers:");
		for(Handler handler : info.getMainHandlers())
			System.out.println("  " + handler.getMessage() + " - " + handler.getHandler());
		System.out.println("Child handlers:");
		for(Handler handler : info.getChildHandlers())
			System.out.println("  " + handler.getMessage() + " - " + handler.getHandler());
		System.out.println("");
		System.out.println("[b] Back");
		System.out.println("[m] Main menu");
		System.out.println("");
	}
	
	public static void printClientMenu() {
		System.out.println("");
		System.out.println("[1] Set login: " + info.getLogin());
		System.out.println("[2] Set password: " + Coder.toHexString(info.getHash()));
		System.out.println("[3] Set max concurrent sessions: " + info.getMaxSessions());
		System.out.println("[4] Set max child actors: " + info.getMaxChilds());
		System.out.println("[5] Set main handlers:");
		for(Handler handler : info.getMainHandlers())
			System.out.println("  " + handler.getMessage() + " - " + handler.getHandler());
		System.out.println("[6] Set child handlers:");
		for(Handler handler : info.getChildHandlers())
			System.out.println("  " + handler.getMessage() + " - " + handler.getHandler());
		System.out.println("");
		System.out.println("[s] Save");
		System.out.println("[c] Cancel");
		System.out.println("");
	}
	
	public static void printMainHandlersMenu() {
		System.out.println("");
		System.out.println("[1] View main handlers");
		System.out.println("[2] Add main handler");
		System.out.println("[3] Modify main handler");
		System.out.println("[4] Remove main handler");
		System.out.println("[b] back");
		System.out.println("");
	}
	
	public static void printMainHandlers() {
		System.out.println("");
		maxMainHandlersPage = info.getMainHandlers().size() % 10 == 0 ? info.getMainHandlers().size() / 10 - 1: info.getMainHandlers().size() / 10;
		maxMainHandlersPage = maxMainHandlersPage < 0 ? 0 : maxMainHandlersPage;
		for(int i = mainHandlersPage * 10; i < (mainHandlersPage + 1) * 10 && i < info.getMainHandlers().size(); i++)
			System.out.println("[" + (i + 1) % 10 + "] " + info.getMainHandlers().get(i).getMessage() + " - " + info.getMainHandlers().get(i).getHandler());
		System.out.println("");
		if(mainHandlersPage != 0) {
			System.out.println("[h] home");
			System.out.println("[p] previous page");
		}
		if(mainHandlersPage != maxMainHandlersPage) {
			System.out.println("[n] next page");
			System.out.println("[e] end");
		}
		System.out.println("[b] back");
		System.out.println("");
	}
	
	public static void printChildHandlersMenu() {
		System.out.println("");
		System.out.println("[1] View main handlers");
		System.out.println("[2] Add main handler");
		System.out.println("[3] Modify main handler");
		System.out.println("[4] Remove main handler");
		System.out.println("[b] back");
		System.out.println("");
	}
	
	public static void printChildHandlers() {
		System.out.println("");
		maxChildHandlersPage = info.getChildHandlers().size() % 10 == 0 ? info.getChildHandlers().size() / 10 - 1 : info.getChildHandlers().size() / 10;
		maxChildHandlersPage = maxChildHandlersPage < 0 ? 0 : maxChildHandlersPage;
		for(int i = childHandlersPage * 10; i < (childHandlersPage + 1) * 10 && i < info.getChildHandlers().size(); i++)
			System.out.println("[" + (i + 1) % 10 + "] " + info.getChildHandlers().get(i).getMessage() + " - " + info.getChildHandlers().get(i).getHandler());
		System.out.println("");
		if(childHandlersPage != 0) {
			System.out.println("[h] home");
			System.out.println("[p] previous page");
		}
		if(childHandlersPage != maxChildHandlersPage) {
			System.out.println("[n] next page");
			System.out.println("[e] end");
		}
		System.out.println("[b] back");
		System.out.println("");
	}
}
