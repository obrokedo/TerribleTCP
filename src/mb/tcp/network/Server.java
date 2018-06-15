package mb.tcp.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;

public abstract class Server implements Runnable {
	// TODO More robust server shutdown and connections
	protected Hashtable<Integer, ClientOutputStream> clientOutputStreams;
	protected Integer clientIDCounter = 0;

	protected String hostIP;
	protected volatile boolean done;
	protected int port;
	private boolean acceptRequests = true;
	private Thread runningThread;
	private ArrayList<Socket> clientSockets;
	private volatile ServerSocket serverSock;
	private boolean passThroughServer = true;

	public Server(int port) {
		this.port = port;
	}

	public Server(int port, boolean passThroughServer) {
		this.port = port;
		this.passThroughServer = passThroughServer;
	}

	/**
     *
     */
	@Override
	public void run() {
		done = false;
		acceptRequests = true;
		clientIDCounter = 0;

		try {
			InetAddress addr = InetAddress.getLocalHost();
			// hostIP = addr.getHostAddress();
			hostIP = "127.0.0.1";
		} catch (UnknownHostException e) {

		}

		clientOutputStreams = new Hashtable<Integer, ClientOutputStream>();
		clientSockets = new ArrayList<Socket>();

		// Wait for clients to connect, and send them off on their own thread
		try {
			// Open a new server socket
			serverSock = new ServerSocket(port);

			if (!passThroughServer)
			{
				Thread t = new Thread(new AcceptConnectionsThread());
				t.start();
				handleNonPassThroughActions();
			}
			else
				startAcceptingConnections();

			// Close the server socket if it is still open
			if (!serverSock.isClosed())
				serverSock.close();

		}
		catch (SocketException ex)
		{
			System.out.println("The server is now shutting down");
			ex.printStackTrace();
		}
		catch (Exception ex)
		{
			System.out.println("Server exception");
			ex.printStackTrace();
		}

		/******************************/
		/* Handle server cleanup here */
		/******************************/
		// Close client sockets
		for (Socket cs : clientSockets)
		{
			try
			{
				cs.close();
			}
			catch (IOException e)
			{
				System.out.println("An error occurred closing a client socket");
				e.printStackTrace();
			}
		}

	}

	private void startAcceptingConnections()
	{
		try
		{
			// Keep accepting client requests until the server should be closed
			while (!done) {
				Socket clientSocket = serverSock.accept();

				// Add this socket to the client sockets list provided that the
				//server is still accepting requests
				if (!acceptRequests)
					clientSocket.close();
				else
					clientSockets.add(clientSocket);

				ClientOutputStream writer;

				int id;

				synchronized (clientIDCounter) {
					id = clientIDCounter++;
				}

				writer = new ClientOutputStream(id,
						clientSocket.getOutputStream());

				synchronized (clientOutputStreams) {
					clientOutputStreams.put(id, writer);
				}

				clientJoined(id, writer);

				ClientHandler ch = new ClientHandler(clientSocket, id);
				Thread t = new Thread(ch);
				t.start();

			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.out.println("An error occurred accepting requests");
		}
	}

	class AcceptConnectionsThread implements Runnable
	{
		@Override
		public void run()
		{
			startAcceptingConnections();
		}
	}

	/**
	 * Create a client handler for each of the clients connected to this server
	 */
	public class ClientHandler implements Runnable {
		private int id;
		private Socket sock;
		private ObjectInputStream isReader;

		public ClientHandler(Socket clientSocket, int id) {
			try {
				sock = clientSocket;
				isReader = new ObjectInputStream(sock.getInputStream());
				this.id = id;
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		@Override
		public void run() {
			// Wait for the client to send messages
			Object message;
			try {
				while ((message = isReader.readObject()) != null)
				{
					if (!messageRecieved(id, message))
						break;
				}
				closeClient();
			}
			catch (SocketException ex)
			{
				System.out.println("A client is exiting");
				closeClient();
			}
			// User has left
			catch (Exception ex)
			{
				System.out.println("An exception occurred reading messages: " + ex.getMessage());
				ex.printStackTrace();
				closeClient();
			}
		}

		private void closeClient() {
			try {
				if (sock != null && !sock.isClosed())
					sock.close();
				clientClosed(id);

				// TODO Why is this done conditionally?
				if (!done)
					removeOutputStream(id);
			} catch (IOException exc) {
				System.out.println("Server: Error Closing Sockets");
			}

			System.out.println("Client closed ");
		}
	}

	public void tellEveryone(Object message) {
		synchronized (clientOutputStreams) {
			for (ObjectOutputStream oos : clientOutputStreams.values()) {
				try {
					tellSomeone(message, oos);
				} catch (Exception ex) {
					System.out.println("Server: Socket Problem: Closed?");
				}
			}
		}

	}

	public void tellEveryoneElse(Object message, ObjectOutputStream dontTellStream) {
		synchronized (clientOutputStreams) {
			for (ObjectOutputStream oos : clientOutputStreams.values()) {
				if (oos != dontTellStream)
				{
					try {
						tellSomeone(message, oos);
					} catch (Exception ex) {
						System.out.println("Server: Socket Problem: Closed?");
					}
				}
			}
		}
	}

	public void tellEveryoneElse(Object message, int clientNumber) {
		tellEveryoneElse(message, clientOutputStreams.get(clientNumber));
	}

	public void tellSomeone(Object message, ObjectOutputStream oos) throws IOException
	{
		oos.writeObject(message);
		oos.flush();
		oos.reset();
	}

	public void tellSomeone(Object message, int clientID) throws IOException
	{
		//TODO This doesn't work once someone leaves
		tellSomeone(message, clientOutputStreams.get(clientID));
	}

	public void ping(ClientOutputStream cos) throws IOException
	{
		cos.pingSent(System.currentTimeMillis());
		this.tellSomeone(new Packet(Packet.TYPE_PING), cos);
	}

	/**
	 * Starts this server in a new thread and begins accepting incoming connections
	 */
	public void startServer() {
		if (runningThread == null)
			runningThread = new Thread(this);
		runningThread.start();
		serverStarted();
	}

	/**
	 * Attempts to close this server and end the thread maintaining it. Client requests are no longer
	 * honored after this point
	 *
	 * @throws Exception
	 */
	public void closeServer() throws Exception {
		System.out.println("Server: closeServer()");
		acceptRequests = false;
		done = true;

		serverClosing();

		// Connect to the server once more so that it falls out of
		// the run method and can be closed.
		serverSock.close();
		runningThread.join();
		System.out.println("Thread is over");
	}

	public String getLocalIP() {
		return hostIP;
	}

	public String getGlobalIP() throws Exception {
		URL page = new URL("http://www.whatismyip.com/automation/n09230945.asp");
		URLConnection yc = page.openConnection();
		BufferedReader in = new BufferedReader(new InputStreamReader(
				yc.getInputStream()));
		String inputLine;
		while ((inputLine = in.readLine()) != null) {
			return inputLine;
		}
		throw new Exception();
	}

	private void removeOutputStream(int id) throws IOException {
		synchronized (clientOutputStreams) {
			clientOutputStreams.remove(id);
		}
	}

	public boolean isAcceptRequests() {
		return acceptRequests;
	}

	public void setAcceptRequests(boolean acceptRequests) {
		this.acceptRequests = acceptRequests;
	}

	public abstract void clientClosed(int clientNumber);

	/**
	 * Invoked immediately after the server has started and can potentially begin accepting
	 * requests provided the correct flag is set
	 */
	public abstract void serverStarted();

	/**
	 * This is invoked immediately before the server socket is closed and all communication
	 * to clients becomes impossible.
	 */
	public abstract void serverClosing();

	/**
	 * This is invoked when the server receives a message from the client. The return value
	 * indicates whether the client that sent the message should continue to have access to
	 * the server (that is; a false indicates the server will disconnect the client).
	 *
	 * @param clientNumber The id of the client that sent the message
	 * @param message The message the client sent
	 * @return False if the server should disconnect the client, true otherwise
	 */
	public abstract boolean messageRecieved(int clientNumber, Object message);

	public abstract void clientJoined(int clientNumber,
			ObjectOutputStream writer) throws IOException;

	protected void handleNonPassThroughActions() {}
}