# P2P_based_IPFS_network
A custom P2P network based on IPFS, academic project for advanced computer networks (Fall 2017).
p2pweb, an application designed for content-addressable peer-to-peer hypermedia sharing, a simplified version of InterPlanetary File
System (IPFS). Users will be able to share contents to p2pweb from their local storage and view contents shared by other users. It is an application layer protocol for sharing this content among p2pweb nodes, and use an existing protocol, HTTP, to serve shared
contents to clients when they request.
p2pweb network consists of interconnected p2pweb nodes. A p2pweb node can be separated to three logical components: Content Provider, Content Tracker and Client Gateway.

Content Provider is responsible for publishing contents to p2pweb from local storage. Currently html and png are the contents handled, this can be expanded to handle any media. Each content has three properties: (Message Digest, Content-Type, Content-Length). This
tuple will be referred as metadata here. 

Message Digest is the message digest of the binary content. It is also an identifier referring to this content, therefore it needs to be unique. If two contents are exactly the same, then their message digest is exactly the same as well. Here, we use SHA-1 as the message digest algorithm.

Content-Type corresponds to the type of file such as “text/html” or “image/png”. This field is used to infer type of binary data when client gateway serving that content to clients.

Content-Length corresponds to the length of the content in bytes.

Content tracker keeps track of metadata of each content published to p2pweb and IP endpoint (i.e. IP address and port) of the p2pweb node hosting that content. It exchanges metadata with node’s peers. It also peers and exchanges metadata with those peers. Only 
metadata and IP endpoint information is exchanged, content itself is not exchanged.

Each p2p node handles these commands read from standard input:
• PEER <peer-hostname> <peer-port>: Peers to another p2pweb node whose hostname and port is given. After peering process, they exchange previously known metadatas with each other. They exchange new learned announcements as they learn them.
• PUBLISH <filename>: Publishes a file into p2pweb. 
• UNPUBLISH <hash>: Unpublish a file with given hash published previously by this node.
• SHOW_PEERS: Print a table of peer endpoints.
• SHOW_METADATA: Print a table of metadata and endpoint information stored by this node.
• SHOW_PUBLISHED: Print a table of metadata information currently being published by this node.

Client Gateway
Content of p2pweb can be accessed using HTTP. A translator between p2pweb and HTTP is
used for this purpose. Client gateway takes HTTP request from a client and delivers the requested content to that client.
A client (e.g. a web browser) can request a content by its digest via HTTP. When a request is received, client gateway looks up the IP endpoint of the content from content tracker. Then it fetches this content from the endpoint hosting that content, and serves it to the client with appropriate HTTP headers. If the content couldn’t be found, it returns a HTTP response code of 404.
Ex: http://localhost:5555/3768ef75611583f0ef129273db63031269e48ddc
In above example, a p2pweb node running on localhost listening on port 5555 receives a request from a browser for a document with digest ‘3768e...dc’. Response will be the document corresponding to this message digest.


Pending Tasks :
* Need to test sequence number logic to avoid looping in case of message broadcasts.
* need to detach/clean up file handling and include support for etended media types.
* Start working on a GUI for this application
