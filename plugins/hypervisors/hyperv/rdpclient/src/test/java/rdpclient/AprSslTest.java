// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package rdpclient;

import junit.framework.TestCase;

import org.apache.tomcat.jni.Address;
import org.apache.tomcat.jni.Error;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.jni.SSLSocket;
import org.apache.tomcat.jni.Socket;

public class AprSslTest extends TestCase {
    static {
	try {
	    Library.initialize(null);
	    SSL.initialize(null);
	} catch (Exception e) {
	    throw new RuntimeException("Cannot load Tomcat Native Library.", e);
	}
    }

    public void debug(String str) {
	System.out.println(str);
    }

    public void testIsJavaCanOpenSSLConnectionToRDPHost() throws Exception {
	String hostname = "192.168.0.101";
	int port = 3389;

	final long pool = Pool.create(0);
	debug("Pool created.");

	try {
	    final long sslContext = SSLContext.make(pool, SSL.SSL_PROTOCOL_TLSV1, SSL.SSL_MODE_CLIENT);
	    SSLContext.setOptions(sslContext, SSL.SSL_OP_DONT_INSERT_EMPTY_FRAGMENTS | SSL.SSL_OP_TLS_BLOCK_PADDING_BUG | SSL.SSL_OP_MICROSOFT_BIG_SSLV3_BUFFER
		    | SSL.SSL_OP_MSIE_SSLV2_RSA_PADDING);
	    SSLContext.setVerify(sslContext, SSL.SSL_CVERIFY_NONE, 0);
	    debug("SSLContext initialized.");

	    final long inetAddress = Address.info(hostname, Socket.APR_UNSPEC, port, 0, pool);
	    final long socket = Socket.create(Address.getInfo(inetAddress).family, Socket.SOCK_STREAM, Socket.APR_PROTO_TCP, pool);
	    debug("Socket created.");

	    int ret = Socket.connect(socket, inetAddress);
	    if (ret != 0)
		throw new Exception("Cannot connect to remote host \"" + hostname + ":" + port + "\": " + Error.strerror(ret));
	    debug("Socket connected.");

	    // Send "Cookie: mstshash=vlisivka" FIXME: ???
	    byte[] buff = new byte[] { (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x2e, (byte) 0x29, (byte) 0xe0, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x43, (byte) 0x6f, (byte) 0x6f, (byte) 0x6b, (byte) 0x69, (byte) 0x65, (byte) 0x3a, (byte) 0x20,
		    (byte) 0x6d, (byte) 0x73, (byte) 0x74, (byte) 0x73, (byte) 0x68, (byte) 0x61, (byte) 0x73, (byte) 0x68, (byte) 0x3d, (byte) 0x76,
		    (byte) 0x6c, (byte) 0x69, (byte) 0x73, (byte) 0x69, (byte) 0x76, (byte) 0x6b, (byte) 0x61, (byte) 0x0d, (byte) 0x0a, (byte) 0x01,
		    (byte) 0x00, (byte) 0x08, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, };
	    Socket.send(socket, buff, 0, buff.length);
	    debug("Cookie send.");
	    
	    byte[] buffer = new byte[4096];
	    int readBytes = Socket.recv(socket, buffer, 0, buffer.length);
	    if (readBytes != 19)
		throw new Exception("SSL IO error " + readBytes + ":" + Error.strerror(readBytes));
	    debug("Server response read.");


	    ret = SSLSocket.attach(sslContext, socket);
	    if (ret != 0)
		throw new Exception("Cannot attach SSL context to socket: " + Error.strerror(ret));
	    debug("SSL atached to socket.");

	    buff = new byte[] { (byte) 0x03, (byte) 0x00, (byte) 0x01, (byte) 0x86, (byte) 0x02, (byte) 0xf0, (byte) 0x80, (byte) 0x7f, (byte) 0x65,
		    (byte) 0x82, (byte) 0x01, (byte) 0x7a, (byte) 0x04, (byte) 0x01, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x01, (byte) 0x01,
		    (byte) 0x01, (byte) 0xff, (byte) 0x30, (byte) 0x20, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x22, (byte) 0x02, (byte) 0x02,
		    (byte) 0x00, (byte) 0x02, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x01,
		    (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x02,
		    (byte) 0xff, (byte) 0xff, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x02, (byte) 0x30, (byte) 0x20, (byte) 0x02, (byte) 0x02,
		    (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x01,
		    (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x02,
		    (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x02, (byte) 0x04, (byte) 0x20, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x02,
		    (byte) 0x30, (byte) 0x20, (byte) 0x02, (byte) 0x02, (byte) 0xff, (byte) 0xff, (byte) 0x02, (byte) 0x02, (byte) 0xfc, (byte) 0x17,
		    (byte) 0x02, (byte) 0x02, (byte) 0xff, (byte) 0xff, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x02,
		    (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x02, (byte) 0xff, (byte) 0xff,
		    (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x02, (byte) 0x04, (byte) 0x82, (byte) 0x01, (byte) 0x07, (byte) 0x00, (byte) 0x05,
		    (byte) 0x00, (byte) 0x14, (byte) 0x7c, (byte) 0x00, (byte) 0x01, (byte) 0x80, (byte) 0xfe, (byte) 0x00, (byte) 0x08, (byte) 0x00,
		    (byte) 0x10, (byte) 0x00, (byte) 0x01, (byte) 0xc0, (byte) 0x00, (byte) 0x44, (byte) 0x75, (byte) 0x63, (byte) 0x61, (byte) 0x80,
		    (byte) 0xf0, (byte) 0x01, (byte) 0xc0, (byte) 0xd8, (byte) 0x00, (byte) 0x04, (byte) 0x00, (byte) 0x08, (byte) 0x00, (byte) 0x00,
		    (byte) 0x04, (byte) 0x00, (byte) 0x03, (byte) 0x01, (byte) 0xca, (byte) 0x03, (byte) 0xaa, (byte) 0x09, (byte) 0x04, (byte) 0x00,
		    (byte) 0x00, (byte) 0x28, (byte) 0x0a, (byte) 0x00, (byte) 0x00, (byte) 0x61, (byte) 0x00, (byte) 0x70, (byte) 0x00, (byte) 0x6f,
		    (byte) 0x00, (byte) 0x6c, (byte) 0x00, (byte) 0x6c, (byte) 0x00, (byte) 0x6f, (byte) 0x00, (byte) 0x33, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0xca, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x10, (byte) 0x00, (byte) 0x0f, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0xc0, (byte) 0x0c,
		    (byte) 0x00, (byte) 0x0d, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
		    (byte) 0xc0, (byte) 0x0c, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, };

	    Socket.send(socket, buff, 0, buff.length);
	    debug("Packet send.");

	    while ((readBytes = Socket.recv(socket, buffer, 0, buffer.length)) >= 0) {
		/* DEBUG */System.out.println("Read " + readBytes + " bytes.");
		// * DEBUG */System.out.println("Read[" + readBytes + "]: " +
		// new String(buffer));
	    }
	    if (readBytes != 0)
		throw new Exception("SSL IO error " + readBytes + ":" + Error.strerror(readBytes));

	    Socket.close(socket);
	    Socket.destroy(socket);
	    debug("Socket closed.");
	} catch (Exception e) {
	    System.err.println(e.getMessage());
	    e.printStackTrace(System.err);
	} finally {
	    Pool.clear(pool);
	    debug("Pool cleared.");
	}
    }
}
