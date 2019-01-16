# Computer Networks Lab

In this lab, we implement a limited version of SOCKS v4 (and 4A) proxy server, with an additional feature: grabbing
usernames and password from HTTP Basic Authentication ( as mentioned in RFC7617 ).

We used ideas and guidance from http://tutorials.jenkov.com/java-nio/index.html
Since this is the first time we worked with NIO.

### Sockspy    

This file is the main file in order to run a new listening server.
The class throw an error if it's not possible to open a server on relevant port.

### SockServer   

We are using one thread with NIO, listening on the relevant port, and then do relevant manipulation on 
sockClient. This class is in charge also to keep a arraylist of all clients (and allowing only 20 concurent 
connections) and using Iterator in order to give the option of multiple thread to access the list.
More relevant info about the specific choice is writen as comment in code.

### SockClient   

The sockClient is runnable. There can be as many as 20 concurent TCP connection.
We are closing connection after ERR also.
All ERR are printed to stderr, and Normal execution is printed to stdout.
