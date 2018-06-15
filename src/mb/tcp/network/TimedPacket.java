package mb.tcp.network;

public class TimedPacket 
{
	public Packet packet;
	public int timeToLive;
	
	public TimedPacket(Packet packet, int timeToLive)
	{
		this.packet = packet;
		this.timeToLive = timeToLive;
	}
	
	public boolean tick()
	{
		if (--timeToLive < 1)
			return true;
		else
			return false;
	}
}
