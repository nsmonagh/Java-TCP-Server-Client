import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class ChatMessenger  {

	// for I/O
	private ObjectInputStream sInput;
	private ObjectOutputStream sOutput;
	private Socket socket;
	
	// the server, the port and the username
	private String server, username;
	private int port;
	
	// a unique ID for each connection
	private static int uniqueId;
	// an ArrayList to keep the list of the Client
	private ArrayList<ClientThread> al;
	// to display time
	private SimpleDateFormat sdf;
	// the boolean that will be turned off to stop the server
	private boolean keepGoing;
	// the boolean that distinguishes the server from the client
	private static boolean isServer;

	public ChatMessenger(int port) {
		this.port = port;
		sdf = new SimpleDateFormat("HH:mm:ss");
		al = new ArrayList<ClientThread>();
		isServer = true;
	}
	
	ChatMessenger(String server, int port, String username) {
		this.server = server;
		this.port = port;
		this.username = username;
	}

	public boolean start() {
		if(isServer) {
			keepGoing = true;
			// create socket server and wait for connection requests
			try {
				// the socket used by the server
				ServerSocket serverSocket = new ServerSocket(port);
				// infinite loop to wait for connections
				while(keepGoing) {
					// format message to show user the server is waiting
					display("Server waiting for Clients on port " + port + ".");
					Socket socket = serverSocket.accept();
					// checks to see if server was requested to stop
					if(!keepGoing)
						break;
					// accepts the client requesting access to server
					ClientThread t = new ClientThread(socket);
					// saves it in the ArrayList
					al.add(t);
					t.start();
				}
				// stops server
				try {
					serverSocket.close();
					for(int i = 0; i < al.size(); ++i) {
						ClientThread tc = al.get(i);
						try {
							tc.sInput.close();
							tc.sOutput.close();
							tc.socket.close();
						}
						catch(IOException ioE) {}
					}
				}
				catch(Exception e) {
					display("Exception closing the server and clients: " + e);
				}
			}
			catch (IOException e) {
	            String msg = sdf.format(new Date()) + " Exception on new ServerSocket: " + e + "\n";
				display(msg);
			}
			return true;
		}
		else {
			// try to connect to the server
			try {
				socket = new Socket(server, port);
			} 
			catch(Exception ec) {
				display("Error connectiong to server:" + ec);
				return false;
			}
			String msg = "Connection accepted " + socket.getInetAddress() + ":" + socket.getPort();
			display(msg);
			// creates the input and output data steams
			try {
				sInput  = new ObjectInputStream(socket.getInputStream());
				sOutput = new ObjectOutputStream(socket.getOutputStream());
			}
			catch (IOException eIO) {
				display("Exception creating new Input/output Streams: " + eIO);
				return false;
			}
			// creates the thread to listen from the server
			new ListenFromServer().start();
			// sends username to the server
			try {
				sOutput.writeObject(username);
			}
			catch (IOException eIO) {
				display("Exception doing login : " + eIO);
				disconnect();
				return false;
			}
			return true;
		}
	}

	// displays an event to the console
	private void display(String msg) {
		if(isServer) {
			String time = sdf.format(new Date()) + " " + msg;
			System.out.println(time);
		}
		else {
			System.out.println(msg);
		}
	}
	
	// sends a message to the server
	void sendMessage(ChatMessage msg) {
		try {
			sOutput.writeObject(msg);
		}
		catch(IOException e) {
			display("Exception writing to server: " + e);
		}
	}

	// disconnects the client upon error
	private void disconnect() {
		try { 
			if(sInput != null) sInput.close();
		}
		catch(Exception e) {}
		try {
			if(sOutput != null) sOutput.close();
		}
		catch(Exception e) {}
        try{
			if(socket != null) socket.close();
		}
		catch(Exception e) {}
	}
	
	// stops the server
	@SuppressWarnings("resource")
	protected void stop() {
		keepGoing = false;
		try {
			new Socket("localhost", port);
		}
		catch(Exception e) {}
	}

	// broadcast a message to all clients for a disconnected client
	private synchronized void broadcast(String message) {
		String time = sdf.format(new Date());
		String messageLf = time + " " + message + "\n";
		System.out.print(messageLf);
		for(int i = al.size(); --i >= 0;) {
			ClientThread ct = al.get(i);
			if(!ct.writeMsg(messageLf)) {
				al.remove(i);
				display("Disconnected Client " + ct.username + " removed from list.");
			}
		}
	}

	// for a client who logs off using the LOGOUT message
	synchronized void remove(int id) {
		for(int i = 0; i < al.size(); ++i) {
			ClientThread ct = al.get(i);
			if(ct.id == id) {
				al.remove(i);
				return;
			}
		}
	}

	public static void main(String[] args) throws ParseException {
		// default settings
		int portNumber = 1500;
		String serverAddress = "localhost";
		String userName = "Anonymous";
		// creations of command-line parser
		Options options = new Options();
		options.addOption("s", false, "server mode");
		options.addOption("c", false, "client mode");
		options.addOption("p", true, "port to listen on or connect to depending on mode");
		options.addOption("H", true, "server to connect to when in client mode");
		options.addOption("h", false, "print helpful usage information");
		options.addOption("u", true, "username to connect as");
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);
		// makes sure that -c and -s were not checked at same time
		if(cmd.hasOption("c") && cmd.hasOption("s")) {
			System.out.println("Client mode and server mode cannot be activated at the same time.");
			return;
		}
		try {
			// help statement
			if(cmd.hasOption("h")) {
				new HelpFormatter().printHelp("java -jar ChatMessenger.jar", options, true);
				return;
			}
			// client mode
			else if(cmd.hasOption("c")) {
				// user-submitted host-name
				if(cmd.hasOption("H"))
					serverAddress = cmd.getOptionValue("H");
				// user-submitted port number
				if(cmd.hasOption("p")) {
					try {
						portNumber = Integer.parseInt(cmd.getOptionValue("p"));
					}
					catch(Exception e) {
						System.out.println("Invalid port number.");
						System.out.println("Enter a valid integer.");
						return;
					}
				}
				// user-submitted username
				if(cmd.hasOption("u"))
					userName = cmd.getOptionValue("u");
				// creates the client object
				ChatMessenger client = new ChatMessenger(serverAddress, portNumber, userName);
				// tests connection to server
				if(!client.start())
					return;
				// wait for messages from user
				Scanner scan = new Scanner(System.in);
				while(true) {
					System.out.print("> ");
					// read message from user
					String msg = scan.nextLine();
					// disconnects user
					if(msg.equalsIgnoreCase("LOGOUT")) {
						client.sendMessage(new ChatMessage(ChatMessage.LOGOUT, ""));
						break;
					}
					// displays all other currently connected clients
					else if(msg.equalsIgnoreCase("WHOISIN")) {
						client.sendMessage(new ChatMessage(ChatMessage.WHOISIN, ""));				
					}
					// displays regular message
					else {
						client.sendMessage(new ChatMessage(ChatMessage.MESSAGE, msg));
					}
				}
				client.disconnect();	
				scan.close();
			}
			// server mode
			else if(cmd.hasOption("s")) {
				// user-submitted port number
				if(cmd.hasOption("p")) {
					try {
						portNumber = Integer.parseInt(cmd.getOptionValue("p"));
					}
					catch(Exception e) {
						System.out.println("Invalid port number.");
						System.out.println("Enter a valid integer.");
						return;
					}
				}
				// creates a server object and starts it
				ChatMessenger server = new ChatMessenger(portNumber);
				server.start();
			}
			else
				System.out.println("Invalid mode.");
		}
		catch(ArrayIndexOutOfBoundsException e) {
			System.out.println(e.getStackTrace());
			return;
		}
	}

	// this class waits for the message from the server and gives it to the client
	class ListenFromServer extends Thread {
		public void run() {
			while(true) {
				try {
					String msg = (String) sInput.readObject();
					System.out.println(msg);
					System.out.print("> ");
				}
				catch(IOException e) {
					display("Server has close the connection: " + e);
					break;
				}
				catch(ClassNotFoundException e2) {}
			}
		}
	}
	
	// allows client to run
	class ClientThread extends Thread {
		// the socket where to listen/talk
		Socket socket;
		ObjectInputStream sInput;
		ObjectOutputStream sOutput;
		// unique id for client
		int id;
		// username of client
		String username;
		// type of message client receives
		ChatMessage cm;
		// date client connected
		String date;

		ClientThread(Socket socket) {
			id = ++uniqueId;
			this.socket = socket;
			System.out.println("Thread trying to create Object Input/Output Streams");
			try {
				sOutput = new ObjectOutputStream(socket.getOutputStream());
				sInput  = new ObjectInputStream(socket.getInputStream());
				username = (String) sInput.readObject();
				display(username + " just connected.");
			}
			catch (IOException e) {
				display("Exception creating new Input/output Streams: " + e);
				return;
			}
			catch (ClassNotFoundException e) {}
            date = new Date().toString() + "\n";
		}

		public void run() {
			boolean keepGoing = true;
			// loops until LOGOUT
			while(keepGoing) {
				try {
					cm = (ChatMessage) sInput.readObject();
				}
				catch (IOException e) {
					display(username + " Exception reading Streams: " + e);
					break;				
				}
				catch(ClassNotFoundException e2) {
					break;
				}
				// message part of the ChatMessage
				String message = cm.getMessage();
				// switch to check type of message received
				switch(cm.getType()) {
					case ChatMessage.MESSAGE:
						broadcast(username + ": " + message);
						break;
					case ChatMessage.LOGOUT:
						display(username + " disconnected with a LOGOUT message.");
						keepGoing = false;
						break;
					case ChatMessage.WHOISIN:
						writeMsg("List of the users connected at " + sdf.format(new Date()) + "\n");
						for(int i = 0; i < al.size(); ++i) {
							ClientThread ct = al.get(i);
							writeMsg((i+1) + ") " + ct.username + " since " + ct.date);
						}
						break;
				}
			}
			remove(id);
			close();
		}
		
		// closes everything
		private void close() {
			try {
				if(sOutput != null) sOutput.close();
			}
			catch(Exception e) {}
			try {
				if(sInput != null) sInput.close();
			}
			catch(Exception e) {};
			try {
				if(socket != null) socket.close();
			}
			catch (Exception e) {}
		}

		// wrties a string to the client output stream
		private boolean writeMsg(String msg) {
			if(!socket.isConnected()) {
				close();
				return false;
			}
			try {
				sOutput.writeObject(msg);
			}
			catch(IOException e) {
				display("Error sending message to " + username);
				display(e.toString());
			}
			return true;
		}
	}
	
	// chat message object
	static class ChatMessage implements Serializable {
		protected static final long serialVersionUID = 1112122200L;
		static final int WHOISIN = 0, MESSAGE = 1, LOGOUT = 2;
		private int type;
		private String message;

		ChatMessage(int type, String message) {
			this.type = type;
			this.message = message;
		}	
		int getType() {
			return type;
		}
		String getMessage() {
			return message;
		}
	}
}