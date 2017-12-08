import java.io.*;
// Data structure to store global content published by a node
public class GLBInfo implements Serializable 
{
	public String name;
	public String hash;
	public String type;
	public String len;
	public String ip;
	public String port;
	public String seq;
	GLBInfo(String name,String hash,String type,String len,String ip,String port,String seq)
	{
		this.name = name;
		this.hash = hash;
		this.type = type;
		this.len  = len;
		this.ip = ip;
		this.port = port;
		this.seq = seq;
	}
	
	// override default equals method which is used by the contains/remove methods to check equality of hash, ip and port since this 3-tuple will be unique
	public boolean equals(Object t) {
    try {
	  	// to check only with hash ( to locate content on the server )
		if ( (hash.equals(((GLBInfo)t).hash)) && (ip.equals(((GLBInfo)t).hash)) && (port.equals(((GLBInfo)t).hash))   ){
			return true;
		} else if ( (hash.equals(((GLBInfo)t).hash)) && (ip.equals(((GLBInfo)t).ip)) && (port.equals(((GLBInfo)t).port)) ){
			return true;
		} else {
			return false;
		}
    } catch (ClassCastException e) {
        return false; 
	}
	
	}
}
