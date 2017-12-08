import java.io.*;
// Data structure to store local content published by a node
public class Data implements Serializable 
{
	public String name;
	public String hash;
	public String type;
	public String len;
	Data(String name,String hash,String type,String len)
	{
		this.name = name;
		this.hash = hash;
		this.type = type;
		this.len  = len;
	}
	
	// override default equals method which is used by the contains/remove methods to check equality of only hash since it will be unique
	public boolean equals(Object t) {
    try {
        return (hash.equals(((Data)t).hash));
    } 
	catch (ClassCastException e) {
        return false; 
	}
	
	}
}
