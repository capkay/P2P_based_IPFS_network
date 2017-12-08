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

// P2PNode class : handles both server/client connections to other P2P nodes and handle content shared/published over this network 
class P2PNode 
{
	// server socket variables 
	ServerSocket server = null; 	// server socket used by PeerWorker, socket for maintaining the P2P network
	ServerSocket c_server = null; 	// server socket used by Client Gateway to handle HTTP requests 

	// table that maintains content metadata published by this node
	List<Data> my_content	= new LinkedList<Data>();
	// table that maintains content metadata published in the whole P2P network 
	List<GLBInfo> world_content	= new LinkedList<GLBInfo>();
	// table that maintains content metadata unpublished in the whole P2P network 
	List<GLBInfo> unpub_content	= new LinkedList<GLBInfo>();
	// table that maintains neighbors of this node 
	List<IPData> neighbors	= new LinkedList<IPData>();

	// list of PeerWorkers 
	List<PeerWorker> pw_list = new LinkedList<PeerWorker>();
	// list of PeerClients
	List<PeerClient> pc_list = new LinkedList<PeerClient>();
	// forward list of PeerClients for PUBLISH messages
	List<PeerClient> fwd_list = new LinkedList<PeerClient>();
	// forward list of PeerClients for UNPUBLISH messages
	List<PeerClient> ufwd_list = new LinkedList<PeerClient>();

	// variables to obtain IP/port information of where the node is running
	InetAddress myip = null;
    String hostname = null;
    String ip = null;
	String port = null;
	String c_port = "5555"; 	// Client gateway requests are handled on port 5555
	P2PNode(String port)
	{
		this.port = port;
	}
	
	// CommandParser class is used to parse and execute respective commands that are entered via command line to initialize/publish/unpublish/query from an establised P2P node
	public class CommandParser extends Thread
	{
	  	// initialize patters for commands
		Pattern PEER = Pattern.compile("^PEER (.*) (\\d+)$");
		Pattern PUBLISH = Pattern.compile("^PUBLISH (.*)$");
		Pattern UNPUBLISH = Pattern.compile("^UNPUBLISH (.*)$");
		Pattern SHOW_PEERS = Pattern.compile("^SHOW_PEERS$");
		Pattern SHOW_METADATA = Pattern.compile("^SHOW_METADATA$");
		Pattern SHOW_PUBLISHED = Pattern.compile("^SHOW_PUBLISHED$");
		
		// read from inputstream, process and execute tasks accordingly	
		int rx_cmd(Scanner cmd){
			String cmd_in = null;
			if (cmd.hasNext())
				cmd_in = cmd.nextLine();
	
			Matcher m_PEER = PEER.matcher(cmd_in);
			Matcher m_PUBLISH = PUBLISH.matcher(cmd_in);
			Matcher m_UNPUBLISH = UNPUBLISH.matcher(cmd_in);
			Matcher m_SHOW_PEERS = SHOW_PEERS.matcher(cmd_in);
			Matcher m_SHOW_METADATA = SHOW_METADATA.matcher(cmd_in);
			Matcher m_SHOW_PUBLISHED = SHOW_PUBLISHED.matcher(cmd_in);
			
			// PEER <IP> <port> , create instance of PeerClient to connect to respective IP/port and add to neighbor list	
			if(m_PEER.find()){ 
				synchronized (neighbors){
					IPData t = new IPData(m_PEER.group(1),m_PEER.group(2));
					if(!neighbors.contains(t))
					{	
						neighbors.add(t);
						PeerClient w;
						w = new PeerClient(m_PEER.group(1),m_PEER.group(2),ip,port,my_content, world_content,unpub_content, neighbors,pw_list,pc_list,fwd_list,ufwd_list);
						synchronized (pc_list) {
							pc_list.add(w);
						}
					}
				}
			 }
		    // PUBLISH <file> , present in the current directory	
			else if(m_PUBLISH.find()){
				do_publish_tasks(m_PUBLISH.group(1));
			 } 
		    // UNPUBLISH <hash>
			else if(m_UNPUBLISH.find()){
			  	unpublish_hash(m_UNPUBLISH.group(1)); 
			 }
		    // SHOW_PEERS , show a list of connected neighbors 	
			else if(m_SHOW_PEERS.find()){ 
				synchronized (neighbors){
					for (int i = 0; i < neighbors.size(); i++) {
						System.out.println("\t"+(i+1)+"  ip:  "+neighbors.get(i).ip+"  port:  "+neighbors.get(i).port );
					}
				}
			 } 
			// SHOW_METADATA , show published content that is available on the P2P network
			else if(m_SHOW_METADATA.find()){ 
				synchronized (world_content){
				for (int i = 0; i < world_content.size(); i++) {
					System.out.println("\t"+(i+1)+"  name:  "+world_content.get(i).name+"  hash:  "+world_content.get(i).hash+"  type:  "+world_content.get(i).type +"  len:  "+world_content.get(i).len+"  ip:  "+world_content.get(i).ip +"  port:  "+world_content.get(i).port  );
				}
				}
			 } 
			// SHOW_PUBLISHED , show content that is published by this node
			else if(m_SHOW_PUBLISHED.find()){ 
				synchronized (my_content){
					for (int i = 0; i < my_content.size(); i++) {
						System.out.println("\t"+(i+1)+"  name:  "+my_content.get(i).name+"  hash:  "+my_content.get(i).hash+"  type:  "+my_content.get(i).type +"  len:  "+my_content.get(i).len  );
					}
				}
			 } 
			// default message
			else {
				System.out.println("Unknown command entered : please enter a valid command");
			 }
			
			// to loop forever	
			return 1;
			}
	
		public void run() {
			System.out.println("Enter commands to set-up P2PNode : PEER and PUBLISH");
			Scanner input = new Scanner(System.in);
			while(rx_cmd(input) != 0) { }  // to loop forever
		}
	}

	// tasks to done to unpublish content from P2P network
	void unpublish_hash(String inp)  {
	  	// obtain hash and create object to iterate and find in local published table
		Data t = new Data("",inp,"","");
		synchronized (my_content){
			if(!my_content.contains(t)){ 
    			System.out.println("There is no content that was previously published with this hash = " + t.hash);
			} 
			else {
			  	my_content.remove(t);
				GLBInfo x = new GLBInfo("",inp,"","",ip,port,"0");
				synchronized (pc_list) {
					for (int i = 0; i < pc_list.size(); i++) {
						pc_list.get(i).unpublish(x);
					}
				}
			}
		}
	}

	// publish tasks, given file name
	void do_publish_tasks(String inp)  {
    	try{
		  	// create file handle and generate SHA-1, obtain extension, size 
			File file = new File("./"+inp);
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
    		InputStream fis = new FileInputStream(file);
			long file_size = file.length();
			Matcher m = Pattern.compile("(\\w+)\\.(\\w+)").matcher(inp);
			String name=inp;
			String ext= "";
			if (m.find()){ 
			   name= m.group(1);
			   ext = m.group(2);
			}
    		int n = 0;
    		byte[] buffer = new byte[8192];
    		while (n != -1) {
        		n = fis.read(buffer);
        		if (n > 0) {
        	    	digest.update(buffer, 0, n);
        		}
    		}
			byte[] mdbytes = digest.digest();
			StringBuffer sb = new StringBuffer("");
    		for (int i = 0; i < mdbytes.length; i++) {
    			sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
    		}
    		System.out.println("Digest(in hex format):: " + sb.toString());
			Data t = new Data(name,sb.toString(),ext,Long.toString(file_size));
			// add to local content table and then publish to the P2P network 
			synchronized (my_content){
				if(my_content.contains(t)){ 
    				System.out.println("Already published this content with hash = " + t.hash);
				} else {
				  	my_content.add(t);
					GLBInfo x = new GLBInfo(name,sb.toString(),ext,Long.toString(file_size),ip,port,"0");
					synchronized (pc_list) {
						for (int i = 0; i < pc_list.size(); i++) {
							pc_list.get(i).publish(x);
						}
					}
				}
			}
		} 
		catch(Exception e){
    		System.out.println("Error creating SHA-1 digest");
    	}
	}

	// method to handle client gateway requests on hash
	public void listenGateway()
	{
		// create server socket on specified port number
		try
		{
			c_server = new ServerSocket(Integer.valueOf(c_port)); 
			System.out.println("Client Gateway requests handled on port " + c_port +"," + " use ctrl-C to end");
		} 
		catch (IOException e) 
		{
			System.out.println("Error creating socket");
			System.exit(-1);
		}
		
		// threaded to handle multiple client requests 	
		Thread accept = new Thread() {
            public void run(){
                while(true){
                    try{
                        Socket s = c_server.accept();
						CGateway t = new CGateway(s,ip,port,my_content, world_content);
					}
                    catch(IOException e){ e.printStackTrace(); }
                }
            }
        };
		accept.setDaemon(true);
        accept.start();
	}

	// method to handle peering connections to other P2P nodes 
	public void listenSocket()
	{
		// create server socket on specified port number
		try
		{
		  	// create server socket and display host/addressing information of this node
			server = new ServerSocket(Integer.valueOf(port)); 
			System.out.println("P2P node running on port " + port +"," + " use ctrl-C to end");
        	myip = InetAddress.getLocalHost();
			ip = myip.getHostAddress();
        	hostname = myip.getHostName();
        	System.out.println("Your current IP address : " + ip);
        	System.out.println("Your current Hostname : " + hostname);
		} 
		catch (IOException e) 
		{
			System.out.println("Error creating socket");
			System.exit(-1);
		}
		
		// create instance of commandparser thread and start it	
		CommandParser cmdpsr = new CommandParser();
		cmdpsr.start();

		// create thread to handle peer requests from other P2P nodes
		Thread accept = new Thread() {
            public void run(){
                while(true){
                    try{
                        Socket s = server.accept();
						PeerWorker t = new PeerWorker(s,ip,port,my_content, world_content,unpub_content, neighbors,pw_list,pc_list,fwd_list,ufwd_list);
						synchronized (pw_list) {
                        	pw_list.add(t);
						}
					}
                    catch(IOException e){ e.printStackTrace(); }
                }
            }
        };
		accept.setDaemon(true);
        accept.start();
	}
	
	// method to close client gateway socket	
	protected void finalize()
	{
		try
		{
			c_server.close();
		} 
		catch (IOException e) 
		{
			System.out.println("Could not close Client Gateway socket");
			//System.exit(-1);
		}
	}
	
	public static void main(String[] args)
	{
		// check for valid number of command line arguments
		if (args.length != 1)
		{
			System.out.println("Usage: java P2PNode <port-number>");
			System.exit(1);
		}
		P2PNode server = new P2PNode(args[0]);
		// call method that handles peers
		server.listenSocket();

		// call method that handles client gateway requests
		server.listenGateway();
	}
}
