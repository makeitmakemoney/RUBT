package RUBTclient;

import java.nio.ByteBuffer;
import java.util.*;

import edu.rutgers.cs.cs352.bt.exceptions.BencodingException;
import edu.rutgers.cs.cs352.bt.util.Bencoder2;

public class Response {
	
	String message;
	ArrayList<Peer> peers = new ArrayList<Peer>();
	@SuppressWarnings("rawtypes")
	List peerdict;
	Integer downloaded;
	Integer complete;
	Integer min_interval;
	Integer interval;
	
	/**
	 * @param String containing a properly formatted, bencoded tracker response to a GET request.
	 */
	@SuppressWarnings("rawtypes")
	public Response(String getrequest) {
		super();
		
		Map peerdict = null;
		
		//Make sure this is a valid, bencoded dictonary
		try {
			peerdict = (Map) Bencoder2.decode(getrequest.getBytes());
		} catch (BencodingException e) {
			System.err.println("Error decoding response to GET request.");
			e.printStackTrace();
		}
		
		//check for 'failure reason'
		final Iterator dict_iter_error = peerdict.keySet().iterator();
        Object key_error = null;
        while (dict_iter_error.hasNext() && (key_error = dict_iter_error.next()) != null)
        {
        	String string_key_error = asString((ByteBuffer) key_error);
        	if(string_key_error.equals("failure reason")){
        		System.err.println("Tracker-reported failure: Reason " + (Integer)peerdict.get(key_error));
        	}
        }
		
		//Iterate through dictionary until peer list is found (heavily based on ToolKit.printMap())
		final Iterator dict_iter = peerdict.keySet().iterator();
        Object key = null;
        while (dict_iter.hasNext() && (key = dict_iter.next()) != null)
        {
        	String string_key = asString((ByteBuffer) key);
            if (string_key.equals("peers")){
            	
            	//Grab peer list and iterate through it
            	ArrayList peerlist = (ArrayList) peerdict.get(key);
            	Iterator peer_iter = peerlist.iterator();
            	
            	while(peer_iter.hasNext()){
            		
            		//Grab each peer and iterate through it, looking for peer id, port, and IP
            		HashMap peer = (HashMap) peer_iter.next();
            		Iterator peerinfo_iter = peer.keySet().iterator();
            		
            		String temp_peer_id = null;
        			String temp_ip = null;
        			Integer temp_port = null;
            		
            		while(peerinfo_iter.hasNext()){
            			
            			Object next = peerinfo_iter.next();
            			String temp_info = asString((ByteBuffer) next);
            			
            			if(temp_info.equals("peer id")){
            				temp_peer_id = asString((ByteBuffer) peer.get(next));
            			}else if(temp_info.equals("port")){
            				temp_port = (Integer) peer.get(next);
            			}else if(temp_info.equals("ip")){
            				temp_ip = asString((ByteBuffer) peer.get(next));
            			}
            		}
            		//Create new Peer object and append it to the ArrayList
        			this.peers.add(new Peer(temp_ip, temp_peer_id, temp_port,null));
            	}
            //Grab other information as needed
            }else if(string_key.equals("interval")){
            	this.interval = (Integer) peerdict.get(key);
            }else if(string_key.equals("min interval")){
            	this.min_interval = (Integer) peerdict.get(key);
            }
        }
	}
	//shamelessly stolen from the forums, original author Prof. Moore
	private static String asString(ByteBuffer buff){
		  StringBuilder sb = new StringBuilder();
		  byte[] b = buff.array();
		  for(int i = 0; i < b.length; ++i){
		    if(b[i] > 31 || b[i] < 127){
		      sb.append((char)b[i]);
		    }else {
		      sb.append(String.format("%02x",b[i]));
		    }
		  }
		  return sb.toString();
		}
	/**
	 * Iterates through a Response's ArrayList of Peer objects and prints each one's info
	 */
	public void printPeers() {
		System.out.println("Printing " + this.peers.size() + " peer(s):");
		
		Iterator<Peer> iter = this.peers.iterator();
		int count = 0;
		while(iter.hasNext()){
			Peer temp = iter.next();
			System.out.println("Peer " + ++count + " of " + this.peers.size() + ":");
			System.out.println("IP: " + temp.ip);
			System.out.println("Port: " + temp.port);
			System.out.println("Peer ID: " + temp.peer_id);
			System.out.println("----------------------");
		}
		
	}
	/**
	 * @return String array containing a peer that matches specific parameters 
	 *         and is found in the Response peer list from the tracker.
	 */
	public String[] getValidPeer(){
		Iterator<Peer> iter = this.peers.iterator();
		String[] results = new String[3];
		//0 IP address, 1 port, 2 peer_id
		while(iter.hasNext()){
			Peer temp = iter.next();
			if(temp.peer_id.startsWith("RUBT11") && temp.ip.equals("128.6.171.130")){
				results[0] = temp.peer_id;
				results[1] = temp.ip;
				results[2] = Integer.toString(temp.port);
				return results;
			}
		}
		return null;
	}
}
