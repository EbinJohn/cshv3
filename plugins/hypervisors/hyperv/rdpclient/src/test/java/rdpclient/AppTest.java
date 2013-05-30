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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import junit.framework.TestCase;

import org.apache.tomcat.jni.Error;

public class AppTest extends TestCase {

    public void testIsJavaCanOpenSSLConnectionToRDPHost() throws Exception {
	String hostname = "192.168.0.101";
	int port = 3389;

	System.setProperty("javax.net.debug", "ssl");

	try {

	    // MS RDP uses TLS1.0, which is insecure, so it will be upgraded to
	    // TLSv1.2 soon.
	    // JVM will try to negotiate TLS1.2, then will fallback to TLS1.0.
	    final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");

	    // Trust all certificates (FIXME: insecure)
	    sslContext.init(null, new TrustManager[] { new TrustAllX509TrustManager() }, null);

	    Socket plainSocket = SocketFactory.getDefault().createSocket(hostname, port);

	    // Send Connection Request
	    OutputStream pos = plainSocket.getOutputStream();
	    byte[] buff = new byte[] { (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x2e, (byte) 0x29, (byte) 0xe0, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		    (byte) 0x00, (byte) 0x00, (byte) 0x43, (byte) 0x6f, (byte) 0x6f, (byte) 0x6b, (byte) 0x69, (byte) 0x65, (byte) 0x3a, (byte) 0x20,
		    (byte) 0x6d, (byte) 0x73, (byte) 0x74, (byte) 0x73, (byte) 0x68, (byte) 0x61, (byte) 0x73, (byte) 0x68, (byte) 0x3d, (byte) 0x76,
		    (byte) 0x6c, (byte) 0x69, (byte) 0x73, (byte) 0x69, (byte) 0x76, (byte) 0x6b, (byte) 0x61, (byte) 0x0d, (byte) 0x0a, (byte) 0x01,
		    (byte) 0x00, (byte) 0x08, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, };
	    pos.write(buff, 0, buff.length);

	    // Read Connection Response
	    InputStream pis = plainSocket.getInputStream();
	    byte[] buffer = new byte[4096];
	    int readBytes = pis.read(buffer, 0, buffer.length);
	    if (readBytes != 19)
		throw new Exception("RDP handshake error " + readBytes + ":" + Error.strerror(readBytes));

	    // Attach SSL context to socket
	    pis = null;
	    pos = null;
	    final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
	    SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(plainSocket, hostname, port, true);
	    sslSocket.startHandshake();

	    // Send COTP message
	    OutputStream sos = sslSocket.getOutputStream();
	    // FIXME: hardcoded value
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
	    sos.write(buff);
	    sos.flush();

	    InputStream sis = sslSocket.getInputStream();

	    // Read response
	    readBytes = sis.read(buffer, 0, 100);
	    System.out.println("Read: " + readBytes + " bytes.");
	    assertEquals("Unexpected size of response.", 100, readBytes);

	    sis.close();
	    sslSocket.close();
	} catch (Exception e) {
	    System.err.println(e.getMessage());
	    throw e;
	}
    }
}
