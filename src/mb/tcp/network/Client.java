package mb.tcp.network;
/* ----------------Networking----------------
 * Commands sent:
 *		Increase Size
 *		Decrease Size
 *		Send To Back
 *		Remove Sprite
 *		Drag Sprite
 *		Fill with Terrain
 *		Fill with Fog
 *		Clear All Fog
 *		Clear All
 *		Clear All Sprites
 *		Permissions
 *		- Able to modify sprites
 *		- Able to paint terrain
 *		- Able to paint sprites
 *		- Vision of Fog
 *		Change Space
 *		- If we needed to add the image to our list
 *		- Send everyone the path of the image.
 *		- If everyone has the image on there comp they will
 *			add the image to there list.
 *		- Otherwise, they send back an image request, and we
 *			serialize the image and send it to the users along
 *			with the images path (again).
 *		User Login
 *		- If you choose to be a dm, it checks to see if the game has
 *			a dm yet, if it doesn't then you are the new dm and you
 *			are given the ability to change permissions. Otherwise
 *			you are given an error that you can not be dm, but you
 *			still join the game.
 *		- When you login, your list is cleared except for the first
 *			element (the static element).
 *		- If you are the only person in the game, you are set as the host
 *			the person with best internet connection should be host. The
 *			host is responsible for sending new users the required
 *			information. Also, your map is reset to blank.
 *		- If you are not the first person in, then the host sends you
 *			the map info and your screen is loaded from there.
 */


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.AbstractList;
import java.util.ArrayList;

public abstract class Client implements Runnable
{
	private String ip;
	private int port;
	protected ObjectInputStream reader;
	protected ObjectOutputStream writer;
	private Socket sock;
	private AbstractList<PacketHandler> packetHandlers;
	protected boolean connected = false;
	private boolean clientClosing = false;
	private Thread runningThread;
	
	
    public Client(String ip, int port) 
    {
    	packetHandlers = new ArrayList<PacketHandler>();
    	this.ip = ip;
    	this.port = port;
    }
    
    public void connect()  throws Exception
    {
    	setUpNetworking(ip, port);
    	clientOpening();
    	clientClosing = false;
    }
    
    public void startAndConnect() throws Exception
    {
    	connect();
    	start();
    }
    
    public void start()
    {
    	runningThread = new Thread(this);
    	runningThread.start();
    }
    
    public ObjectInputStream getReader()
    {
    	return reader;
    }
    public ObjectOutputStream getWriter()
    {
    	return writer;
    }
    
    public void sendMessage(Object message) 
    {
    	//TODO Throw an exception so that we know when the client has been closed?
    	if (!connected) return;
    	try 
    	{
			writer.writeObject(message);
			writer.flush();
			writer.reset();
		} 
    	catch (IOException e) 
    	{
    		System.out.println("Error sending message");
			e.printStackTrace();
		}
    }
    
    /**
     * Attempts to close this client's connection to the target server. If this client
     * is not currently connected to a server then this is a no-op
     */
    private void closeClient(boolean join)
    {
    	if (!connected) return;
    	try
    	{
    		clientClosing();
    		clientClosing = true;
    		writer.close();
    		reader.close();
	    	sock.close();
	    	connected = false;
	    	if (join)
	    		runningThread.join();
    	}
    	catch (Exception ex)
    	{
    		System.out.println("PROBLEM CLOSING CLIENT");
    	}
    }
    
    /**
     * Attempts to close this client's connection to the target server. If this client
     * is not currently connected to a server then this is a no-op
     */
    public void closeClient()
    {
    	closeClient(true);
    }
    
    private void setUpNetworking(String ip, int port) throws Exception
    {    	
    	sock = new Socket(ip, port);	    		
    		
		if (sock != null)
		{
    		reader = new ObjectInputStream(sock.getInputStream());
    		writer = new ObjectOutputStream(sock.getOutputStream());
    		connected = true;
		}
		else
			throw new Exception("Unable to connect");
    }
	
	public void run()
	{
		Object message;
		try 
		{
			while(!clientClosing && (message = reader.readObject()) != null)
			{
				for (PacketHandler ph : packetHandlers)
					ph.handleIncomingPacket(this, message);
			}
		} 
		catch (Exception e) 
		{			
			if (!clientClosing)
			{
				// THIS IS WHERE CONTROL ENDS UP IF THE REMOTE SERVER CLOSES
				System.out.println("CLOSE IN READER");
				e.printStackTrace();
				closeClient(false);
			}
		}
	}
	
	public void registerPacketHandler(PacketHandler packetHandler)
	{
		packetHandlers.add(packetHandler);
		packetHandler.handlerRegistered(this);
	}
	
	public void unregisterPacketHandler(PacketHandler packetHandler)
	{
		packetHandlers.remove(packetHandler);
	}
    
	/**
	 * Called immediately after a connection has been established with the target server
	 */
    public abstract void clientOpening();
    
    /**
     * Called immediately before the client will disconnect from the target server
     */
    public abstract void clientClosing();

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}
}