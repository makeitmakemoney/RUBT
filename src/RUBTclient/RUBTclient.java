package RUBTclient;

//import java.net.URL;
import java.nio.ByteBuffer;
import java.io.*;

import edu.rutgers.cs.cs352.bt.TorrentInfo;
import edu.rutgers.cs.cs352.bt.exceptions.BencodingException;
import edu.rutgers.cs.cs352.bt.util.Bencoder2;
import edu.rutgers.cs.cs352.bt.util.ToolKit;

public class RUBTclient {
	
	public static void main(String[] args){
		
		if(args.length != 2){
			System.err.println("Usage: java RUBT <torrent> <destination>");
			System.exit(0);
		}
		//get args
		String torrentname = args[0];
		String destination = args[1];
		//prepare file stream
		FileInputStream fileInputStream = null;
		File torrent = new File(torrentname);
		TorrentInfo torrentinfo = null;
		
		byte[] torrentbytes = new byte[(int)torrent.length()];
		
		try{
			fileInputStream = new FileInputStream(torrent);
			fileInputStream.read(torrentbytes);
			fileInputStream.close();
		}catch(Exception e){
			e.printStackTrace();
		}
		
		try {
			torrentinfo = new TorrentInfo(torrentbytes);
		} catch (BencodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println(torrentinfo.announce_url.toString());
		
		String announce_url = torrentinfo.announce_url.toString(); 
		int port_num = 6881;
		int file_length = torrentinfo.file_length;
		ByteBuffer info_hash = torrentinfo.info_hash;
		
		GetRequest myRequest = new GetRequest();
		myRequest.constructURL(announce_url, info_hash, port_num, file_length);
		try{
			System.out.println("bencoded stuff: " +  myRequest.sendGetRequest());
		}catch(Exception e){
			//System.out.println(e.printStackTrace());
			System.out.println("no dice");
		};
			//System.out.println("url " + myRequest.getUrl());
		
	}

}