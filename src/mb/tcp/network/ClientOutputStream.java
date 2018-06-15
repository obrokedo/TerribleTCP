package mb.tcp.network;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;

public class ClientOutputStream extends ObjectOutputStream 
{
	private int id = 0;
	private int ping = 0;
	private long[] lastPings = {0, 0, 0};
	private int pingIdx = 0;
	private ArrayList<TimedPacket> timedPackets;
	
	public ClientOutputStream(int id, OutputStream arg0) throws IOException 
	{
		super(arg0);
		this.id = id;
		this.timedPackets = new ArrayList<TimedPacket>();
	}

	public int getId() {
		return id;
	}
	
	public void processPackets(Server server) throws IOException
	{
		Iterator<TimedPacket> it = timedPackets.iterator();
		while (it.hasNext())
		{
			TimedPacket tp = it.next();
			if (tp.tick())
			{
				server.tellSomeone(tp.packet, this);
				it.remove();
			}
		}
	}
	
	public void pingSent(long pingReturnTime)
	{
		lastPings[pingIdx] = pingReturnTime;
	}
	
	public void pingRecieved(long pingReturnTime)
	{
		int newPing = 0;
		
		lastPings[pingIdx] = (pingReturnTime - lastPings[pingIdx]) / 2;
		
		for (int i = 0; i < lastPings.length; i++)
			newPing += lastPings[i];
		
		this.ping = newPing / lastPings.length;
		
		pingIdx = (pingIdx + 1) % lastPings.length;
	}
	
	public void addPacket(long artificialLat, Packet packet, long tickLength)
	{
		timedPackets.add(new TimedPacket(packet, (int) ((artificialLat - ping) / tickLength)));
	}

	public int getPing() {
		return ping;
	}
}
