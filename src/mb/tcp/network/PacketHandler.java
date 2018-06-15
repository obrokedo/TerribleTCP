package mb.tcp.network;


public interface PacketHandler 
{
	/**
	 * Handle incoming packets from remote sources
	 * 
	 * @param client The client that received the given packet
	 * @param packet The packet that was received
	 */
	public void handleIncomingPacket(Client client, Object packet);
	
	/**
	 * Provides the PacketHandler a means to get the Client that will
	 * be receiving incoming packets
	 * 
	 * @param client The client that packets will be sent to
	 */
	public void handlerRegistered(Client client);
}
