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

// CGateway class instances handle HTTP client gateway connections coming towards the server usually from a web browser
class CGateway
{
	// client socket instance variable will be updated when thread is created
	PrintWriter out = null; 	// to send data from server to the client
	BufferedReader in = null; 	// to read data coming to server from client
	Socket client;      // socket instance for the connecting client
	String ip = null; 			// remote ip address
	String port = null; 		// remote port 
	String my_ip = null; 		// ip of this node
	String my_port = null; 		// port of this node

	List<Data> my_content	= null; 	// handle to local content list
	List<GLBInfo> world_content	= null; // handle to global content list

	// constructor to connect respective variables
	CGateway(Socket client,String my_ip,String my_port,List<Data> my_content,List<GLBInfo> world_content) 
	{
		this.client  = client;
		this.my_ip = my_ip;
		this.my_port = my_port;
		this.my_content = my_content;
		this.world_content = world_content;
		// get IO streams
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
        // logic to handle HTTP requests and generate responses with respective content
		try
		{
			System.out.println("CG : client connected ");
			// get the GET request 
			String request = in.readLine();
			// create outputstream handle to send data in bytes
			OutputStream os = client.getOutputStream();
			// display the GET request and parse the requested hash and display
			System.out.println("request : "+request);
			String[] requestParam = request.split(" ");
			String hash = requestParam[1].substring(1);
			System.out.println("requested hash: "+hash);
			// create objects that will be used to find the content either locally or globally using the hash
			Data t = new Data("",hash,"","");
			GLBInfo x = new GLBInfo("",hash,"","",hash,hash,"0");
			// flags to indicate local / global hit of content
			boolean local_hit = false;
			boolean glb_hit = false;
			// check local / global content and set flags accordingly
			synchronized (my_content){
				if(my_content.contains(t)){
				 	local_hit = true; 
					int loc = my_content.indexOf(t);
					t = my_content.get(loc);
				}
			} 
			synchronized (world_content){
				if(world_content.contains(x)){
				 	glb_hit = true; 
					int loc = world_content.indexOf(x);
					x = world_content.get(loc);
				}
			}

		    // send responses for local hit 	
			if(local_hit){ 
    			System.out.println("Content present on this server");
				File file = new File("./"+t.name+"."+t.type);
				if (!file.exists()) {
					System.out.println("File does not exist");
				}
    			FileInputStream fis = new FileInputStream(file);
    			byte[] data = new byte[(int) file.length()];
    			fis.read(data);
    			fis.close();
    			DataOutputStream binaryOut = new DataOutputStream(os);
    			binaryOut.writeBytes("HTTP/1.1 200 OK\r\n");
				String ctype = (t.type).equals("png") ? "image/png" : "text/html";
    			binaryOut.writeBytes("Content-Type: "+ctype+"\r\n");
    			binaryOut.writeBytes("Content-Length: " + data.length+"\r\n");
        		binaryOut.writeBytes("Connection: Closed\r\n"); // End of headers 
    			binaryOut.writeBytes("\r\n");
    			binaryOut.write(data);
    			binaryOut.close();
			}
		    // send responses for global hit, it is HTTP 302 redirection response with the location of the requested content	
			else if(glb_hit) {
    			System.out.println("Content available on P2P network");
    			DataOutputStream binaryOut = new DataOutputStream(os);
    			binaryOut.writeBytes("HTTP/1.1 302 Found\r\n");
    			binaryOut.writeBytes("Location: http://" +x.ip+":5555/"+x.hash+"\r\n");
        		binaryOut.writeBytes("Connection: Closed\r\n"); // End of headers 
    			binaryOut.writeBytes("\r\n");
    			binaryOut.close();
			} 
			// load HTTP 404 page when content is not present anywhere 
			else {
    			System.out.println("Content is not available on P2P network");
				File file = new File("./http_404.html");
    			FileInputStream fis = new FileInputStream(file);
    			byte[] data = new byte[(int) file.length()];
    			fis.read(data);
    			fis.close();
    			DataOutputStream binaryOut = new DataOutputStream(os);
    			binaryOut.writeBytes("HTTP/1.1 404 Not Found\r\n");
    			binaryOut.writeBytes("Content-Type: text/html\r\n");
    			binaryOut.writeBytes("Content-Length: " + data.length+"\r\n");
        		binaryOut.writeBytes("Connection: Closed\r\n"); // End of headers 
    			binaryOut.writeBytes("\r\n");
    			binaryOut.write(data);
    			binaryOut.close();
			}
			in.close();
			out.close();
		}
		catch (IOException e)
		{
			System.out.println("IO exception");
			//System.exit(1);
		}
		// handle unexpected connection loss during a session
		catch (NullPointerException e)
		{
			System.out.println("peer connection lost");
		}
	}
}
