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

// PeerWorker class instances handle client connections coming towards the server
class PeerWorker
{
	PrintWriter out = null; 	// to send data from server to the client
	BufferedReader in = null; 	// to read data coming to server from client
	Socket client;      // socket instance for the connecting client
	String ip = null; 			// remote ip address
	String port = null; 		// remote port 
	String my_ip = null; 		// ip of this node
	String my_port = null; 		// port of this node

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
	PeerWorker(Socket client,String my_ip,String my_port,List<Data> my_content,List<GLBInfo> world_content,List<GLBInfo> unpub_content,List<IPData> neighbors,List<PeerWorker> pw_list,List<PeerClient> pc_list,List<PeerClient> fwd_list,List<PeerClient> ufwd_list) 
	{
		this.client  = client;
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
		try 
		{
			in = new BufferedReader(new InputStreamReader(client.getInputStream()));
			out = new PrintWriter(client.getOutputStream(), true);
		} 
		catch (IOException e) 
		{
			System.out.println("in or out failed");
			System.exit(-1);
		}
        try{
		  	// send cmd "1" , so that client responds with IP and port information
			out.println("1");
			// get the client IP and port information	
			ip= in.readLine();
			port= in.readLine();
			System.out.println("PW : neighbor connected, ip:" + ip + "  port = " + port);
			// share global content if table is not empty
			// send metadata if there is published content already existing on the P2P network and this node is connecting after PUBLISH events happened in the network
			synchronized (world_content) {
			if (world_content != null && !world_content.isEmpty()) {
					for (int i = 0; i < world_content.size(); i++) {
					  	out.println(world_content.get(i).name);
					  	out.println(world_content.get(i).hash);
					  	out.println(world_content.get(i).type);
					  	out.println(world_content.get(i).len);
					  	out.println(world_content.get(i).ip);
					  	out.println(world_content.get(i).port);
					  	out.println(world_content.get(i).seq);
					}
				}
			}
			// share local content if table is not empty
			synchronized (my_content) {
			if (my_content != null && !my_content.isEmpty()) {
					for (int i = 0; i < my_content.size(); i++) {
					  	out.println(my_content.get(i).name);
					  	out.println(my_content.get(i).hash);
					  	out.println(my_content.get(i).type);
					  	out.println(my_content.get(i).len);
					  	out.println(my_ip);
					  	out.println(my_port);
					  	out.println("0");
					}
				}
			}
			// send EOM to indicate termination of messages
			out.println("EOM");
			// create another socket connection to client node to exchange messages in a one-way manner in each line
			// another socket is created only when this node is not present already in the neighbor list
			synchronized (neighbors){
				IPData x = new IPData(ip,port);
				if(!neighbors.contains(x))
				{	
					neighbors.add(x);
					System.out.println("PC : outgoing to, ip:" + ip + "  port = " + port);
					PeerClient w;
					w = new PeerClient(ip,port,my_ip,my_port,my_content, world_content,unpub_content, neighbors,pw_list,pc_list,fwd_list,ufwd_list);
					synchronized (pc_list) {
						pc_list.add(w);
					}
				}
			}
		}
		catch (IOException e)
		{
			System.out.println("Read failed");
			System.exit(1);
		}
		// handle unexpected connection loss during a session
		catch (NullPointerException e)
		{
			System.out.println("peer connection lost");
		}
		// run and start thread to process incoming commands which is part of the custom protocol to handle PUBLISH and UNPUBLISH messages
		Thread read = new Thread(){
			public void run(){
				while(rx_cmd(in,out) != 0) { }
                }
			};
		read.setDaemon(true); 	// terminate when main ends
        read.start(); 			// start the thread	
	}

	// method to process incoming commands and data associated with them
	public int rx_cmd(BufferedReader cmd,PrintWriter out){
		try
		{
			String cmd_in = cmd.readLine();
			// PUBLISH message received, get metadata and initiate forwarding accordingly 
			if(cmd_in.equals("P")){
			  	// read all data and chunk into object
				String name = in.readLine();
				String hash = in.readLine();
				String type = in.readLine();
				String len  = in.readLine();
				String ip  = in.readLine();
				String port  = in.readLine();
				String seq  = in.readLine();
				GLBInfo t = new GLBInfo(name,hash,type,len,ip,port,seq);
				GLBInfo t_int = new GLBInfo("",hash,"","","","","");
				// add to glocal content table if this is a new PUBLISH message
				synchronized (world_content){
					if( !world_content.contains(t) || (world_content != null && world_content.isEmpty()) ){
						int temp = Integer.parseInt(t.seq); 
						// increment seq number and save updated data
						t.seq = Integer.toString(++temp); 
						world_content.add(t);
						synchronized (pc_list) {
							fwd_list = new LinkedList<PeerClient>(pc_list);
						}
					} else {
					  	// remove from forwarding list if this message is a duplicate message from another node, probably because of a loop in the network topology
						int loc = world_content.indexOf(t);
					    t_int = world_content.get(loc);
						int seq_int = Integer.parseInt(t_int.seq);
					  	int temp = Integer.parseInt(t.seq);
						System.out.println("PC other removal");
						PeerClient do_not_fwd = new PeerClient(ip,port,"","",null, null,null, null,null,null,null,null);
						if( temp <= seq_int ){
					  	synchronized (fwd_list){	  
							if( fwd_list.contains(do_not_fwd) )
							  	fwd_list.remove(do_not_fwd);
							}
						}
					}
				}
				// remove from forwarding list so that we dont forward this message to the node which sent the message
				System.out.println("PC own removal");
				PeerClient own = new PeerClient(ip,port,"","",null, null,null,null, null,null,null,null);
				synchronized (fwd_list){	  
					if( fwd_list.contains(own) )
					  	fwd_list.remove(own);
				}
				// call the publish method in PeerClient forward list to publish this message to connected nodes
				for (int i = 0; i < fwd_list.size(); i++) {
    				System.out.println(fwd_list.get(i));
				  	fwd_list.get(i).publish(t);
				}
			}
			// similar logic as PUBLISH, using separate forward list and unpublished content to handle UNPUBLISH messages	
			else if(cmd_in.equals("U")){ 
				String hash = in.readLine();
				String ip  = in.readLine();
				String port  = in.readLine();
				String seq  = in.readLine();
				GLBInfo t = new GLBInfo("",hash,"","",ip,port,seq);
				GLBInfo t_int = new GLBInfo("",hash,"","","","","");
				synchronized (world_content){
					if( world_content.contains(t) ){
						int temp = Integer.parseInt(t.seq); 
						t.seq = Integer.toString(++temp);
						world_content.remove(t);
						synchronized (unpub_content) {
					    	unpub_content.add(t);	
						}
						synchronized (pc_list) {
							ufwd_list = new LinkedList<PeerClient>(pc_list);
						}
					} else {
					  	synchronized (unpub_content){
						  if(unpub_content.contains(t)){
								int loc = unpub_content.indexOf(t);
					    		t_int = unpub_content.get(loc);
								int seq_int = Integer.parseInt(t_int.seq);
					  			int temp = Integer.parseInt(t.seq);
								System.out.println("PC Unpublish other removal");
								PeerClient do_not_fwd = new PeerClient(ip,port,"","",null,null,null, null, null,null,null,null);
								if( temp <= seq_int ){
					  			synchronized (ufwd_list){	  
									if( ufwd_list.contains(do_not_fwd) )
									  	ufwd_list.remove(do_not_fwd);
									}
								}
						  }
						}
					}
				}

				System.out.println("PC Unpublish own removal");
				PeerClient own = new PeerClient(ip,port,"","",null,null,null, null, null,null,null,null);
				synchronized (ufwd_list){	  
					if( ufwd_list.contains(own) )
					  	ufwd_list.remove(own);
				}
				for (int i = 0; i < ufwd_list.size(); i++) {
    				System.out.println(ufwd_list.get(i));
				  	ufwd_list.get(i).unpublish(t);
				}
			}
			else if (cmd_in.equals("Q")){
				String rd_in = null;
				Matcher m_eom = eom.matcher("start");  // initializing the matcher. "start" does not mean anything
				// obtain metadata from server till EOM is received 
				while(!m_eom.find()){
					rd_in = in.readLine();
					m_eom = eom.matcher(rd_in);
					if(!m_eom.find()){
						// add name to respective list
						String name = in.readLine();
						String hash = in.readLine();
						String type = in.readLine();
						String len  = in.readLine();
						String ip  = in.readLine();
						String port  = in.readLine();
						String seq  = in.readLine();
						GLBInfo t = new GLBInfo(name,hash,type,len,ip,port,seq);
						synchronized (world_content){
							world_content.add(t);
						}
					} else { break; }
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
