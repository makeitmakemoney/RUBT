package RUBTClient;

import java.net.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.Timer;


/**
 * @author Manuel Lopez
 * @author Ben Green
 * @author Christopher Rios
 *
 */


/** Peer object handles all communication between client and peer
 */
public class Peer extends Thread {

	private int 				port;				//port number to access the peer
	private String  			ip;          		//ip address of peer
	private String 				peer_id;			//identifying name of peer
	private Socket 				peerSocket;			//socket connection to peer
	private DataOutputStream	peerOutputStream;	//OuputStream to peer for sending messages
	private DataInputStream 	peerInputStream;	//InputStream to peer for reading responses
	private boolean 			choked; 			//checks if we are being choked
	private boolean 			choking; 			//checks if we are choking the connected peer
	private boolean			 	connected;			//checks if peer is disconnected
	private boolean 			interested;
	private boolean 			remote_interested;
	private byte[] 				response;
	private byte[] 				bitfield;
	private Timer 				send_timer; 		//timers for sends
	private Timer 				receive_timer; 		//timers for receives
	private RUBTClient 			client;
	private MessageTask 		message;
	private boolean				first_sent;  //flag check that the first message after the handshake was sent. is used to make sure bitfield isnt sent in the wrong order. 
	
	/**
	 * Usual constructor of Peer, when we create and connect to a peer first
	 * @param ip ip of the peer
	 * @param peer_id id of the peer
	 * @param port port the peer is on
	 */
	public Peer(String ip, String peer_id, Integer port) {
		super();
		this.ip = ip;
		this.peer_id = peer_id;
		this.port = port;
		this.peerSocket = null;
		this.peerInputStream = null;
		this.peerOutputStream = null;
		this.first_sent = false;
		this.choked = true;
		this.choking = true;
		this.connected = false;
		this.interested = false;
		
		send_timer = new Timer();
		receive_timer = new Timer();
	}
	
	/**
	 * Constructor of Peer when a remote peer connects to us first
	 * @param peerSocket  socket that the peer connected to
	 * @param peerInputStream Input stream for the socket
	 * @param peerOutputStream Output stream for the socket
	 */
	public Peer(Socket peerSocket, DataInputStream peerInputStream, DataOutputStream peerOutputStream) {
		this.peerSocket = peerSocket;
		try {
			this.peerSocket.setSoTimeout(130*1000);
		} catch (SocketException e) {
			System.out.println("peer socket exception");
		}

		this.peerInputStream = peerInputStream;
		this.peerOutputStream = peerOutputStream;
		this.first_sent = false;
		this.choked = true;
		this.choking = true;
		this.connected = false;
		this.interested = false;
		
		send_timer = new Timer();
		receive_timer = new Timer();
		
		send_timer.schedule(new SendTimerTask(this),115*1000 ); //set a new timer for sent messages, set for 1 minute 55 seconds.
		receive_timer.schedule(new ReceiveTimerTask(this),125*1000);
	}
	
	
	/**
	 * @author Manuel Lopez
	 * @author Ben Green
	 * @author Christopher Rios
	 * Timer task for sending info through a socket. If the timer runs out, the Peer sends a keep alive.
	 * 
	 */
	private static class SendTimerTask extends TimerTask{
		private Peer peer;
		private Message message;
		private byte[] keep_alive;
		public SendTimerTask(Peer peer)
		{
			this.peer = peer;
			Message message = new Message();
			keep_alive = new byte[4];
		}
		public void run() {
			// TODO Do something when the timer is up
			System.out.println("send timer is up");
			System.out.println(peer.connected);
			if(peer.connected)
			{
			byte[] keep_alive = {0,0,0,0};
			try {
				peer.sendMessage(keep_alive);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.err.println("Socket already closed");
			}
			}
			
		}
	}
	
	/**
	 * @author Manuel Lopez
	 * @author Ben Green
	 * @author Christopher Rios
	 * Timer task for receiving info through a socket. If the timer runs out, we disconnect the peer
	 * 
	 */
	private static class ReceiveTimerTask extends TimerTask{
		private Peer peer;
		public ReceiveTimerTask(Peer peer)
		{
			this.peer = peer;
		}
		public void run() 
		{
			
			System.err.println("recieve timer is up,disconnecting peer");
			peer.closeConnections();//disconnects from peer when timer is up.
		}
	}
	
	/* 
	 * Overloaded run method for peer. Reads from the input stream until the connection is closed. Once it correctly reads
	 * a message, it sends that message up to the client to be processed. 
	 */
	public void run(){
		
		while(connected){   //runs until we are no longer connected to the Peer
			try {
				Thread.sleep(1*200);
				try{
					if(peerInputStream.available()==0){
						continue;     //means the peer hasn't written anything to the socket yet, I would like to find a better way to do this
					}
				}catch(IOException e){
					return;
				}
				
				int length_prefix = peerInputStream.readInt();
				if(length_prefix==0){ //means this is a keep alive from the peer
					receive_timer.cancel();  //cancels the current timer for messages
			        receive_timer = new Timer();
			        receive_timer.schedule(new ReceiveTimerTask(this), 125*1000);  //resets it for 2 minutes and 5 seconds from last sent, extra 5 to be generous	
					continue;
				}
				if(length_prefix<0)  //bad error, sometimes we would read a large negative number.
				{
					continue;     
				}
				response = new byte[length_prefix];
				peerInputStream.read(response,0,length_prefix);
				if(response[0] == Message.BITFIELD&&first_sent==false) //if the id is a bitfield, set this peers bitfield to this byte array, as long as it is sent at the right time.
				{      
					System.out.println("setting the bitfield");
					bitfield = new byte[length_prefix-1];
					System.arraycopy(response,1,this.bitfield,0,bitfield.length);
				}
				message = new MessageTask(this, response);//makes the response into a  new message task, passes a peer as well
				client.addMessageTask(message); //puts the message in its clients  task queue and resets timers
				receive_timer.cancel();
		        receive_timer = new Timer();
		        receive_timer.schedule(new ReceiveTimerTask(this), 125*1000);
			}catch (EOFException e) {
					continue;
			}catch (Exception e){
				System.err.println("Other Exception");
			}
		}
	}
		
	/**connectToPeer() sets socket connections and input/output streams to the peer
	 * @return true if successful/false if failed
	 */
	public boolean connectToPeer(){
		//open sockets and input/output streams
		try{
			this.peerSocket = new Socket(ip, port);
			this.peerSocket.setSoTimeout(130*1000); //set the socket timeout for 2 minutes and 30 seconds
			this.peerOutputStream = new DataOutputStream(peerSocket.getOutputStream());  
			this.peerInputStream = new DataInputStream(peerSocket.getInputStream());
			connected = true;
		}catch (UnknownHostException e){
			System.err.println("UnknownHostException");
			return false;
		}catch (IOException e){
			System.err.println("IOException");
			return false;
		}
		send_timer.schedule(new SendTimerTask(this),115*1000 ); //set a new timer for sent messages, set for 1 minute 55 seconds.
		receive_timer.schedule(new ReceiveTimerTask(this),125*1000);
		return true;
	}
	
	
	/** closes input/outputstreams and socket connections 
	 */
	public void closeConnections(){
		//close all streams
		try{
			peerInputStream.close();
			peerOutputStream.close();
			peerSocket.close();
			connected = false;
			send_timer.cancel();  
			receive_timer.cancel();
		}catch (IOException e){
			return;
		}
	}
	
	/**wait() uses Thread.sleep to allow time for peer to respond to requests
	 * @param milliseconds interval before read
	 */
	
	/**
	 * @return the peers ip
	 */
	public String getIp() {
		return ip;
	}
	
	/**
	 * This method sets a peers connected field to true or false
	 * @param connected if the peer is connected or not
	 */
	public void setConnected(boolean connected){
		this.connected = connected;
	}


	/**
	 * @return the peer's peer_id
	 */
	public String getPeer_id() {
		return peer_id;
	}


	/**
	 * @return port of the peer
	 */
	public int getPort() {
		return port;
	}


	/**
	 * @return returns if the peer is interested or not
	 */
	public boolean isInterested() {
		return interested;
	}
	
	
	/**
	 * @return if the peer is choked or not
	 */
	public boolean isChoked() {
		return choked;
	}

	/**
	 * @return if our peer is choking the remote peer
	 */
	public boolean isChoking() {
		return choking;
	}

	/**
	 * This method sets if the peer is being choked or not
	 * @param state if the peer is choked or not
	 */
	public void setChoked(boolean state){
		this.choked = state;
	}
	
	/**
	 * This method sets if the remote peer is interested in the pieces that our client has or not
	 * @param state if the remote peer is interested or not
	 */
	public void setRemoteInterested(boolean state){
		this.remote_interested = state;
	}
	/**
	 * Sends a message to the peer
	 * Source: Taken From Rob Moore's skeleton code in our Sakai Resources folder
	 * @param Message message to be sent by the peer
	 * @throws IOException 
	 */
	public synchronized void sendMessage(byte[] Message) throws IOException {
		if(this.peerOutputStream == null){
			System.out.println("stream is null");
		}else {
			peerOutputStream.write(Message);
		}
		send_timer.cancel();  //cancels the current timer for sent messages, starts a new one
        send_timer = new Timer();
        send_timer.schedule(new SendTimerTask(this), 115*1000);
		
	}
	
	/**
	 * @return the remote peers bitfield
	 */
	public byte[] getBitfield(){
		return this.bitfield;
	}
	
	/**
	 * This method Gets and passes on a remote peers handshake
	 * @returns the remote peers handshake
	 */
	public byte[] handshake(){
		
		byte[] phandshake = new byte[68]; //Receives initial handshake
		try{
			peerInputStream.readFully(phandshake);
		}catch (EOFException e){  //Usually happens when the tracker is probing us
			return null;
		}catch (IOException e1){
			System.err.println("Handshake Error");  //there was an error reading the handshake, disconnects from the peer.
			closeConnections();
		}
		return phandshake;
	}
	
	/**
	 * This method sets the peer's client, and also initializes its bitfield to the correct length
	 * @param client client that is associated with this peer
	 */
	public void setClient(RUBTClient client){
		this.client = client;
		this.bitfield = new byte[client.getbitfield().length]; 
	}

	/**
	 * This method sets the local peers interested state
	 * @param state if the client as a whole is interested in the pieces a remote peer has
	 */
	public void setInterested(boolean state){
		interested = state;
	}
	
	/**
	 * @return if the remote peer has sent its first message after a handshake
	 */
	public boolean getFirstSent(){
		return first_sent;
	}
	
	/**
	 * This method helps keep track of if a peer is sending a bitfield out of order
	 * @param first_sent if first message after handshake has been received yet
	 */
	public void setFirstSent(boolean first_sent){
		this.first_sent = first_sent;
	}
	
	public boolean equals(Peer peer){
		return(this.ip == peer.getIp() && this.peer_id.equals(peer.getPeer_id()));
	}

	/**
	 * This method sets if we are choking the remote peer or not
	 * @param b if we are choking the remote peer
	 */
	public void setChoking(boolean b) {
		
		this.choking=b;
		
	}
}