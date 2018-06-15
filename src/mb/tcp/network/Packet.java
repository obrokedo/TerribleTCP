package mb.tcp.network;

import java.io.Serializable;

public class Packet implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	public static final int TYPE_PING = 0;
	public static final int TYPE_INPUT = 1;
	public static final int TYPE_LIST = 2;
	public static final int TYPE_CURR_PING = 3;
	public static final int TYPE_MAKE_CONN = 4;
	
	protected int type;
	
	public Packet(int type)
	{
		this.type = type;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}
	
	public String toString()
	{
		return "Packet: type = " + type;
	}
}

