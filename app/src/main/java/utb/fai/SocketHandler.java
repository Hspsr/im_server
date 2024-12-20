package utb.fai;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

public class SocketHandler {
	/** mySocket je socket, o který se bude tento SocketHandler starat */
	Socket mySocket;

	/** client ID je øetìzec ve formátu <IP_adresa>:<port> */
	String clientID;

	/**
	 * activeHandlers je reference na mnoinu vech právì bìících SocketHandlerù.
	 * Potøebujeme si ji udrovat, abychom mohli zprávu od tohoto klienta
	 * poslat vem ostatním!
	 */
	ActiveHandlers activeHandlers;

	/**
	 * messages je fronta pøíchozích zpráv, kterou musí mít kaý klient svoji
	 * vlastní - pokud bude je pøetíená nebo nefunkèní klientova sí,
	 * èekají zprávy na doruèení právì ve frontì messages
	 */
	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<String>(20);

	/**
	 * startSignal je synchronizaèní závora, která zaøizuje, aby oba tasky
	 * OutputHandler.run() a InputHandler.run() zaèaly ve stejný okamik.
	 */
	CountDownLatch startSignal = new CountDownLatch(2);

	/** outputHandler.run() se bude starat o OutputStream mého socketu */
	OutputHandler outputHandler = new OutputHandler();
	/** inputHandler.run() se bude starat o InputStream mého socketu */
	InputHandler inputHandler = new InputHandler();
	/**
	 * protoe v outputHandleru nedovedu detekovat uzavøení socketu, pomùe mi
	 * inputFinished
	 */
	volatile boolean inputFinished = false;

	String name = null;
	Set<String> rooms = new HashSet<>();

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
		this.mySocket = mySocket;
		clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
		this.activeHandlers = activeHandlers;
	}

	class OutputHandler implements Runnable {
		public void run() {
			OutputStreamWriter writer;
			try {
				System.err.println("DBG>Output handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Output handler running for " + clientID);
				writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
				writer.write("\nYou are connected from " + clientID + "\n");
				writer.flush();
				while (!inputFinished) {
					String m = messages.take();// blokující ètení - pokud není ve frontì zpráv nic, uspi se!
					writer.write(m + "\r\n"); // pokud nìjaké zprávy od ostatních máme,
					writer.flush(); // poleme je naemu klientovi
					System.err.println("DBG>Message sent to " + clientID + ":" + m + "\n");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.err.println("DBG>Output handler for " + clientID + " has finished.");

		}
	}

	class InputHandler implements Runnable {
		public void run() {
			try {
				System.err.println("DBG>Input handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Input handler running for " + clientID);
				String request = "";
				/**
				 * v okamiku, kdy nás Thread pool spustí, pøidáme se do mnoiny
				 * vech aktivních handlerù, aby chodily zprávy od ostatních i nám
				 */
				activeHandlers.add(SocketHandler.this);
				rooms.add("public");
				BufferedReader reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
				while ((request = reader.readLine()) != null) {
					
					if (name == null) {
						request = request.trim();
						if (request.contains(" ")) {
							request = "Your name cant have spaces";
						} else if (activeHandlers.isNameTaken(request)) {
							request = "This name is taken";
						} else {
							name = request;
							request = "Your name is now: " + name;
						}
						System.out.println(request);
						activeHandlers.sendMessageToSelf(SocketHandler.this, request);
						continue;
					}

					if (request.startsWith("#setMyName")) {
						String[] args = request.trim().split(" ", 2);
						if (args[1].contains(" ")) {
							request = "Your name cant have spaces";
						} else if (args[1].equals(name)) {
							request = "You cant set your new name to be the same as your old name";
						} else if (activeHandlers.isNameTaken(args[1])) {
							request = "This name is taken";
						} else {
							name = args[1];
							request = "Your name is now: " + name;
						}
						System.out.println(request);
						activeHandlers.sendMessageToSelf(SocketHandler.this, request);
						continue;
					}

					if (request.startsWith("#sendPrivate")) {
						String[] args = request.trim().split(" ", 3);
							request = "[" + name + "] >> " + args[2];
							System.out.println(request);
							if (!activeHandlers.sendMessageToName(request, args[1])) {
								request = "This person: " + args[1] + " couldnt be found";
								System.out.println(request);
								activeHandlers.sendMessageToSelf(SocketHandler.this, request);
							}
						continue;
					}

					if (request.startsWith("#join")) {
						String[] args = request.trim().split(" ", 2);
						if (args[1].contains(" ")) {
							request = "Room names cant have spaces";
						} else if (rooms.contains(args[1])) {
							request = "You are currently in this room";
						} else {
							rooms.add(args[1]);
							request = "You have joined the group";
						}
						System.out.println(request);
						activeHandlers.sendMessageToSelf(SocketHandler.this, request);
						continue;
					}

					if (request.startsWith("#leave")) {
						String[] args = request.trim().split(" ", 2);
						if (args[1].contains(" ")) {
							request = "Room names cant have spaces";
						} else if (!rooms.contains(args[1])) {
							request = "You are not in this room";
						} else {
							rooms.remove(args[1]);
							request = "You leaved the room";
						}
						System.out.println(request);
						activeHandlers.sendMessageToSelf(SocketHandler.this, request);
						continue;
					}
					
					if (request.startsWith("#groups")) {
						request = String.join(",", rooms);
						System.out.println(request);
						activeHandlers.sendMessageToSelf(SocketHandler.this, request);
						continue;
					}



					request = "["+name+"] >> " + request;
					System.out.println(request);
					activeHandlers.sendMessageToAll(SocketHandler.this, request);
				}
				inputFinished = true;
				messages.offer("OutputHandler, wakeup and die!");
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				// remove yourself from the set of activeHandlers
				synchronized (activeHandlers) {
					activeHandlers.remove(SocketHandler.this);
				}
			}
			System.err.println("DBG>Input handler for " + clientID + " has finished.");
		}

	}
}
