import java.util.Date;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedList;
import java.lang.management.*;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.text.*;
import java.io.*;
import java.net.*;
// PeerClient class instances handle connections going towards the server/P2P node
class PeerClient
{
	Socket socket = null;     	// socket instance for this connection, passed to this variable 
	PrintWriter out = null; 	// to send data 
	BufferedReader in = null; 	// to read data
	String ip = null; 			// ip of remote node to connect to
	String port = null; 		// port of remote node to connect to
	String my_ip = null; 		// ip of this node
	String my_port = null; 		// ip of this node

	List<PeerWorker> pw_list = null; 	// handle to PeerWorker list that is maintained by the node
	List<PeerClient> pc_list = null; 	// handle to PeerClient list that is maintained by the node
	List<PeerClient> fwd_list = null; 	// handle to PeeeClient list, to handle publish looping, that is maintained by the node
	List<PeerClient> ufwd_list = null;  // handle to PeeeClient list, to handle unpublish looping, that is maintained by the node 
	List<Data> my_content	= null; 	// handle to local content list
	List<GLBInfo> world_content	= null; // handle to global content list
	List<GLBInfo> unpub_content	= null; // handle to table that holds unpublished content
	List<IPData> neighbors	= null;     // handle to neighbor table

	public static Pattern eom = Pattern.compile("^EOM");  // generic 'end of message' pattern

	// constructor to connect respective variables
	PeerClient(String ip, String port,String my_ip,String my_port,List<Data> my_content,List<GLBInfo> world_content,List<GLBInfo> unpub_content,List<IPData> neighbors,List<PeerWorker> pw_list,List<PeerClient> pc_list,List<PeerClient> fwd_list,List<PeerClient> ufwd_list) 
	{
		this.ip = ip;
		this.port = port;
		this.my_ip = my_ip;
		this.my_port = my_port;
		this.my_content = my_content;
		this.world_content = world_content;
		this.unpub_content = unpub_content;
		this.neighbors = neighbors;
		this.pw_list = pw_list;
		this.pc_list = pc_list;
		this.fwd_list = fwd_list;
		this.ufwd_list = ufwd_list;
		
		//Create socket connection
		if(my_ip.equals("") && my_port.equals(""))
		{
			// Dummy client object (nothing done here) used to find clients in PeerClient list with same IP/port by using the overridden equals method defined in this class
			//System.out.println("Dummy PC");
		}
		else 
		{
		    // connect to IP/port specified in the constructor, and get the I/O streams of this socket
			try
			{
				socket = new Socket(ip, Integer.valueOf(port));
				out = new PrintWriter(socket.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			} 
			catch (UnknownHostException e) 
			{
				System.out.println("Unknown host");
				//System.exit(1);
			} 
			catch (IOException e) 
			{
				System.out.println("No I/O");
				//System.exit(1);
			}

			// start processing commands in a thread that may me recieved on this line
			Thread read = new Thread(){
				public void run(){
						while(rx_cmd(in,out) != 0) { }
	
				}
			};
			read.setDaemon(true); 	// terminate when main ends
        	read.start(); 			// start the thread
		}
	}
	
	// override the equals method which is used by the contains/remove methods to identify the object that needs to be processed, checking only IP/port variables when a dummy PeerClient object is created
	public boolean equals(Object t) {
    	try {
			if ( (ip.equals(((PeerClient)t).ip))  && (port.equals(((PeerClient)t).port)) ){
				return true;
			} else {
				return false;
			}
    	} 
		catch (ClassCastException e) {
    	    return false; 
		}
	}

	// publish method sends metadata accordingly with command P
	public void publish(GLBInfo t){
		out.println("P");
		out.println(t.name);
		out.println(t.hash);
		out.println(t.type);
		out.println(t.len);
		out.println(t.ip);
		out.println(t.port);
		out.println(t.seq);
	}

	// unpublish method sends metadata accordingly with command U
	public void unpublish(GLBInfo t){
		out.println("U");
		out.println(t.hash);
		out.println(t.ip);
		out.println(t.port);
		out.println(t.seq);
	}
	
	// method to process incoming commands and data associated with them
	public int rx_cmd(BufferedReader cmd,PrintWriter out){
		try
		{
			String cmd_in = cmd.readLine();
			// send ip and host of this P2P node
			if(cmd_in.equals("1")){
				out.println(my_ip);
				out.println(my_port);
				String rd_in = null;
				// obtain metadata if there is published content already existing on the P2P network
				Matcher m_eom = eom.matcher("start");  // initializing the matcher. "start" does not mean anything
				// obtain metadata from server till EOM is received 
				while(!m_eom.find()){
					rd_in = in.readLine();
					m_eom = eom.matcher(rd_in);
					if(!m_eom.find()){
						// add metadata to respective list
						String name = rd_in;
						String hash = in.readLine();
						String type = in.readLine();
						String len  = in.readLine();
						String t_ip  = in.readLine();
						String t_port  = in.readLine();
						String seq  = in.readLine();
						GLBInfo t = new GLBInfo(name,hash,type,len,t_ip,t_port,seq);
						synchronized (world_content){
						  if(!world_content.contains(t))
								world_content.add(t);
						}
					} else { break; }  // break out of loop when EOM is received
				}
			} 
		}
		catch (IOException e) 
		{
			System.out.println("Read failed");
			//System.exit(-1);
		}

		// default : return 1, to continue processing further commands 
		return 1;
	}
}
