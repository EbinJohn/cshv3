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

import streamer.ByteBuffer;
import streamer.Element;
import streamer.Link;
import streamer.MockSink;
import streamer.MockSource;
import streamer.OneTimeSwitch;
import streamer.Pipeline;
import streamer.PipelineImpl;

public class ClientMCSConnectInitial extends OneTimeSwitch {

  public ClientMCSConnectInitial(String id) {
    super(id);
  }

  @Override
  protected void handleOneTimeData(ByteBuffer buf, Link link) {
    if (buf == null)
      return;

    throw new RuntimeException("Unexpected packet: " + buf + ".");
  }

  @Override
  protected void onStart() {
    super.onStart();

    int length = 1024; // Large enough
    ByteBuffer buf = new ByteBuffer(length, true);

    /* @formatter:off */
    buf.writeBytes(new byte[] {
        // Message type: Connect-Initial
        (byte)0x7f, (byte)0x65, 
        
        // BER type length (378 bytes)
        (byte)0x82, (byte)0x01, (byte)0x7a, 
        
        // Connect-Initial::callingDomainSelector = 1
        (byte)0x04, (byte)0x01, (byte)0x01, 
        
        // Connect-Initial::calledDomainSelector = 1
        (byte)0x04, (byte)0x01, (byte)0x01, 
        
        // Connect-Initial::upwardFlag = TRUE
        (byte)0x01, (byte)0x01, (byte)0xff,
        
        //
        // Connect-Initial::targetParameters (32 bytes)
        (byte)0x30, (byte)0x20,  
        
        // DomainParameters::maxChannelIds = 34
        (byte)0x02,  (byte)0x02, (byte)0x00, (byte)0x22, 
        
        // DomainParameters::maxUserIds = 2
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x02, 
        
        //  DomainParameters::maxTokenIds = 0
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x00,
        
        //  DomainParameters::numPriorities = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        //  DomainParameters::minThroughput = 0
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x00,
        
        //  DomainParameters::maxHeight = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        //  DomainParameters::maxMCSPDUsize = 65535
        (byte)0x02, (byte)0x02, (byte)0xff, (byte)0xff,
        
        //  DomainParameters::protocolVersion = 2
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x02,
        
        //
        // Connect-Initial::minimumParameters (32 bytes)
        (byte)0x30, (byte)0x20, 
        
        // DomainParameters::maxChannelIds = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        // DomainParameters::maxUserIds = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        // DomainParameters::maxTokenIds = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        // DomainParameters::numPriorities = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        // DomainParameters::minThroughput = 0
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x00,
        
        // DomainParameters::maxHeight = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01, 
        
        // DomainParameters::maxMCSPDUsize = 1056
        (byte)0x02, (byte)0x02, (byte)0x04, (byte)0x20, 
        
        // DomainParameters::protocolVersion = 2
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x02, 
        
        //
        // Connect-Initial::maximumParameters (32 bytes)
        (byte)0x30, (byte)0x20, 
        
        // DomainParameters::maxChannelIds = 65535
        (byte)0x02, (byte)0x02, (byte)0xff, (byte)0xff, 
        
        // DomainParameters::maxUserIds = 64535
        (byte)0x02, (byte)0x02, (byte)0xfc, (byte)0x17, 
        
        // DomainParameters::maxTokenIds = 65535
        (byte)0x02, (byte)0x02, (byte)0xff, (byte)0xff,
        
        // DomainParameters::numPriorities = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01, 
        
        // DomainParameters::minThroughput = 0
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x00, 
        
        // DomainParameters::maxHeight = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01, 
        
        // DomainParameters::maxMCSPDUsize = 65535
        (byte)0x02, (byte)0x02, (byte)0xff, (byte)0xff,
        
        // DomainParameters::protocolVersion = 2
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x02, 
        
        // Connect-Initial::userData (263 bytes)
        (byte)0x04, (byte)0x82, (byte)0x01, (byte)0x07,
        
        
        // T124
        
        // PER encoded (ALIGNED variant of BASIC-PER) GCC Connection Data (ConnectData):
        // OBJ ID    OBJ Length 
        (byte)0x00, (byte)0x05, 
        // T124 Object: 0.0.20.124.0.1 (Generic conference control)
        (byte)0x00, (byte)0x14, (byte)0x7c, (byte)0x00, (byte)0x01, 
        
        //  ConnectData::connectPDU length = 254 bytes
        // Length is given by the low six bits of the first byte and the second byte.
        (byte)0x80, (byte)0xfe, 

        // PER encoded (ALIGNED variant of BASIC-PER) GCC Conference Create Request PDU:

        // 0x00:
        // 0 - extension bit (ConnectGCCPDU)
        // 0 - --\
        // 0 -   | CHOICE: From ConnectGCCPDU select conferenceCreateRequest (0)
        // 0 - --/ of type ConferenceCreateRequest
        // 0 - extension bit (ConferenceCreateRequest)
        // 0 - ConferenceCreateRequest::convenerPassword present
        // 0 - ConferenceCreateRequest::password present
        // 0 - ConferenceCreateRequest::conductorPrivileges present
        (byte)0x00,
        // 0x08:
        // 0 - ConferenceCreateRequest::conductedPrivileges present
        // 0 - ConferenceCreateRequest::nonConductedPrivileges present
        // 0 - ConferenceCreateRequest::conferenceDescription present
        // 0 - ConferenceCreateRequest::callerIdentifier present
        // 1 - ConferenceCreateRequest::userData present
        // 0 - extension bit (ConferenceName)
        // 0 - ConferenceName::text present
        // 0 - padding
        (byte)0x08, 
        // 0x00:
        // 0 - --\
        // 0 -   | 
        // 0 -   | 
        // 0 -   | ConferenceName::numeric length = 0 + 1 = 1 character
        // 0 -   | (minimum for SimpleNumericString is 1)
        // 0 -   |
        // 0 -   |
        // 0 - --/
        (byte)0x00,
        // 0x10:
        // 0 - --\
        // 0 -   | ConferenceName::numeric = "1"
        // 0 -   |
        // 1 - --/
        // 0 - ConferenceCreateRequest::lockedConference
        // 0 - ConferenceCreateRequest::listedConference
        // 0 - ConferenceCreateRequest::conducibleConference
        // 0 - extension bit (TerminationMethod)
        (byte)0x10,
        // 0x00: 
        // 0 - TerminationMethod::automatic
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        (byte)0x00,
        // 0x01: 
        // 0 - --\
        // 0 -   |
        // 0 -   |
        // 0 -   | number of UserData sets = 1
        // 0 -   | 
        // 0 -   |
        // 0 -   |
        // 1 - --/
        (byte)0x01,
        // 0xc0: 
        // 1 - UserData::value present
        // 1 - CHOICE: From Key select h221NonStandard (1) of type H221NonStandardIdentifier
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        (byte)0xc0, 
        // 0x00:
        // 0 - --\
        // 0 -   |
        // 0 -   |
        // 0 -   | h221NonStandard length = 0 + 4 = 4 octets
        // 0 -   | (minimum for H221NonStandardIdentifier is 4)
        // 0 -   |
        // 0 -   |
        // 0 - --/
        (byte)0x00,
        //      h221NonStandard (client-to-server H.221 key): "Duca" (ASCII, without trailing zero)
        (byte)0x44, (byte)0x75, (byte)0x63, (byte)0x61, 

        // UserData::value length: 240 bytes.
        // Length is given by the low six bits of the first byte and the second byte.
        (byte)0x80, (byte)0xf0, 
        
        // RDP
        
        // type: CS_CORE (0xc001)
        (byte)0x01, (byte)0xc0, 
        
        //  length: 216 bytes (LE)
        (byte)0xd8, (byte)0x00, 
        
        //  Version:  4.8
        (byte)0x04, (byte)0x00, (byte)0x08, (byte)0x00, 
        
        // Desktop width: 1024x768 (0x400 x 0x300 LE)
        (byte)0x00, (byte)0x04, (byte)0x00, (byte)0x03, 
        
        // Bits per pixel: 8bpp (0xca01 LE)
        (byte)0x01, (byte)0xca, 
        
        // SAS Sequence  (LE)
        (byte)0x03, (byte)0xaa,
        
        //  Keyboard layout: EN_US (1033)  (LE)
        (byte)0x09, (byte)0x04, (byte)0x00, (byte)0x00, 
        
        // Client build: 1000 (LE)
        (byte)0xe8, (byte)0x03, (byte)0x00, (byte)0x00,
        
        // Client name: apollo3 (fixed length string: 32 bytes/16 characters UCS2)
        (byte)0x61, (byte)0x00, (byte)0x70, (byte)0x00, (byte)0x6f, (byte)0x00, (byte)0x6c, (byte)0x00,
        (byte)0x6c, (byte)0x00, (byte)0x6f, (byte)0x00, (byte)0x33, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0xc0, (byte)0x00, (byte)0x00, (byte)0x00,
        
        // Keyboard type: unknown (0) (LE)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 

        // Keyboard subtype: 0 (LE)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        
        // Keyboard function key: 0 (LE)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        
        // IME File Name = "" (fixed string: 64 bytes/32 characters UCS2, with trailing zero character)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        
        
        // Post beta 2 color depth: 8bpp (0xca01, LE)
        (byte)0x01, (byte)0xca, 
        
        // Client product ID: 1 (LE)
        (byte)0x01, (byte)0x00, 
        
        // Serial number: 0 (LE)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        
        // High color depth: 24bpp (LE)
        (byte)0x18, (byte)0x00, 
        
        // Supported color depths: 0x01 (LE) 24bpp only  
        (byte)0x01, (byte)0x00, 
        
        // Early capability flags: 1 (SUPPORT_ERRINFO_PDU)
        (byte)0x01, (byte)0x00, 
        
        // Client UUID: "" (fixed string: 64 bytes/32 characters UCS2)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        
        // Connection type: Unknown
        (byte)0x00, 
        
        // Padding (?)
        (byte)0x00, 
        
        // Selected protocol: PROTOCOL_SSL (0x1, LE) 
        (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, 
        
        //
        // Header type: CS_CLUSTER (0xc004, LE)
        (byte)0x04, (byte)0xc0, 
        // Length: 12 bytes (LE)
        (byte)0x0c, (byte)0x00, 
        
         // Cluster flags = 0x0d 0x0d (LE) = 0x03 << 2 | 0x01 = REDIRECTION_VERSION4 << 2 | REDIRECTION_SUPPORTED
        (byte)0x0d, (byte)0x00, (byte)0x00, (byte)0x00,
        
        // Redirected session ID: 0 (LE)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        
        
        //
        // Header type: CS_SECURITY (0xc002, LE)
        (byte)0x02, (byte)0xc0,
        // Length: 12 bytes (LE)
        (byte)0x0c, (byte)0x00, 
        
        
        // Encryption methods: 0x03 (LE)= 0x01 | 0x02 = 40BIT_ENCRYPTION_FLAG | 128BIT_ENCRYPTION_FLAG 
        (byte)0x03, (byte)0x00, (byte)0x00, (byte)0x00,
        
        // Extended encryption flags: 0
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 

    });
    /* @formatter:on */

    buf.length = buf.cursor;

    pushDataToOTOut(buf);

    switchOff();
  }

  /**
   * Example.
   * 
   * @see http://msdn.microsoft.com/en-us/library/cc240836.aspx
   */
  public static void main(String args[]) {
    // System.setProperty("streamer.Link.debug", "true");
    System.setProperty("streamer.Element.debug", "true");
    // System.setProperty("streamer.Pipeline.debug", "true");

    /* @formatter:off */
    byte[] packet = new byte[] {
        // TPKT: TPKT version = 3
        (byte) 0x03,  (byte) 0x00,  
        // TPKT: Packet length: 406 bytes
        (byte) 0x01,  (byte) 0x96, 
        
        // X.224: Length indicator = 2
        (byte) 0x02,  
        // X.224: Type: Data TPDU
        (byte) 0xf0,  
        // X.224: EOT
        (byte) 0x80,  
        
        // Message type: Connect-Initial
        (byte)0x7f, (byte)0x65, 
        
        // BER type length (378 bytes)
        (byte)0x82, (byte)0x01, (byte)0x7a, 
        
        // Connect-Initial::callingDomainSelector = 1
        (byte)0x04, (byte)0x01, (byte)0x01, 
        
        // Connect-Initial::calledDomainSelector = 1
        (byte)0x04, (byte)0x01, (byte)0x01, 
        
        // Connect-Initial::upwardFlag = TRUE
        (byte)0x01, (byte)0x01, (byte)0xff,
        
        //
        // Connect-Initial::targetParameters (32 bytes)
        (byte)0x30, (byte)0x20,  
        
        // DomainParameters::maxChannelIds = 34
        (byte)0x02,  (byte)0x02, (byte)0x00, (byte)0x22, 
        
        // DomainParameters::maxUserIds = 2
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x02, 
        
        //  DomainParameters::maxTokenIds = 0
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x00,
        
        //  DomainParameters::numPriorities = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        //  DomainParameters::minThroughput = 0
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x00,
        
        //  DomainParameters::maxHeight = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        //  DomainParameters::maxMCSPDUsize = 65535
        (byte)0x02, (byte)0x02, (byte)0xff, (byte)0xff,
        
        //  DomainParameters::protocolVersion = 2
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x02,
        
        //
        // Connect-Initial::minimumParameters (32 bytes)
        (byte)0x30, (byte)0x20, 
        
        // DomainParameters::maxChannelIds = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        // DomainParameters::maxUserIds = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        // DomainParameters::maxTokenIds = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        // DomainParameters::numPriorities = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01,
        
        // DomainParameters::minThroughput = 0
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x00,
        
        // DomainParameters::maxHeight = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01, 
        
        // DomainParameters::maxMCSPDUsize = 1056
        (byte)0x02, (byte)0x02, (byte)0x04, (byte)0x20, 
        
        // DomainParameters::protocolVersion = 2
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x02, 
        
        //
        // Connect-Initial::maximumParameters (32 bytes)
        (byte)0x30, (byte)0x20, 
        
        // DomainParameters::maxChannelIds = 65535
        (byte)0x02, (byte)0x02, (byte)0xff, (byte)0xff, 
        
        // DomainParameters::maxUserIds = 64535
        (byte)0x02, (byte)0x02, (byte)0xfc, (byte)0x17, 
        
        // DomainParameters::maxTokenIds = 65535
        (byte)0x02, (byte)0x02, (byte)0xff, (byte)0xff,
        
        // DomainParameters::numPriorities = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01, 
        
        // DomainParameters::minThroughput = 0
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x00, 
        
        // DomainParameters::maxHeight = 1
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x01, 
        
        // DomainParameters::maxMCSPDUsize = 65535
        (byte)0x02, (byte)0x02, (byte)0xff, (byte)0xff,
        
        // DomainParameters::protocolVersion = 2
        (byte)0x02, (byte)0x02, (byte)0x00, (byte)0x02, 
        
        // Connect-Initial::userData (263 bytes)
        (byte)0x04, (byte)0x82, (byte)0x01, (byte)0x07,
        
        
        // T124
        
        // PER encoded (ALIGNED variant of BASIC-PER) GCC Connection Data (ConnectData):
        // OBJ ID    OBJ Length 
        (byte)0x00, (byte)0x05, 
        // T124 Object: 0.0.20.124.0.1 (Generic conference control)
        (byte)0x00, (byte)0x14, (byte)0x7c, (byte)0x00, (byte)0x01, 
        
        //  ConnectData::connectPDU length = 254 bytes
        // Length is given by the low six bits of the first byte and the second byte.
        (byte)0x80, (byte)0xfe, 

        // PER encoded (ALIGNED variant of BASIC-PER) GCC Conference Create Request PDU:

        // 0x00:
        // 0 - extension bit (ConnectGCCPDU)
        // 0 - --\
        // 0 -   | CHOICE: From ConnectGCCPDU select conferenceCreateRequest (0)
        // 0 - --/ of type ConferenceCreateRequest
        // 0 - extension bit (ConferenceCreateRequest)
        // 0 - ConferenceCreateRequest::convenerPassword present
        // 0 - ConferenceCreateRequest::password present
        // 0 - ConferenceCreateRequest::conductorPrivileges present
        (byte)0x00,
        // 0x08:
        // 0 - ConferenceCreateRequest::conductedPrivileges present
        // 0 - ConferenceCreateRequest::nonConductedPrivileges present
        // 0 - ConferenceCreateRequest::conferenceDescription present
        // 0 - ConferenceCreateRequest::callerIdentifier present
        // 1 - ConferenceCreateRequest::userData present
        // 0 - extension bit (ConferenceName)
        // 0 - ConferenceName::text present
        // 0 - padding
        (byte)0x08, 
        // 0x00:
        // 0 - --\
        // 0 -   | 
        // 0 -   | 
        // 0 -   | ConferenceName::numeric length = 0 + 1 = 1 character
        // 0 -   | (minimum for SimpleNumericString is 1)
        // 0 -   |
        // 0 -   |
        // 0 - --/
        (byte)0x00,
        // 0x10:
        // 0 - --\
        // 0 -   | ConferenceName::numeric = "1"
        // 0 -   |
        // 1 - --/
        // 0 - ConferenceCreateRequest::lockedConference
        // 0 - ConferenceCreateRequest::listedConference
        // 0 - ConferenceCreateRequest::conducibleConference
        // 0 - extension bit (TerminationMethod)
        (byte)0x10,
        // 0x00: 
        // 0 - TerminationMethod::automatic
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        (byte)0x00,
        // 0x01: 
        // 0 - --\
        // 0 -   |
        // 0 -   |
        // 0 -   | number of UserData sets = 1
        // 0 -   | 
        // 0 -   |
        // 0 -   |
        // 1 - --/
        (byte)0x01,
        // 0xc0: 
        // 1 - UserData::value present
        // 1 - CHOICE: From Key select h221NonStandard (1) of type H221NonStandardIdentifier
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        // 0 - padding
        (byte)0xc0, 
        // 0x00:
        // 0 - --\
        // 0 -   |
        // 0 -   |
        // 0 -   | h221NonStandard length = 0 + 4 = 4 octets
        // 0 -   | (minimum for H221NonStandardIdentifier is 4)
        // 0 -   |
        // 0 -   |
        // 0 - --/
        (byte)0x00,
        //      h221NonStandard (client-to-server H.221 key) = "Duca"
        (byte)0x44, (byte)0x75, (byte)0x63, (byte)0x61, 

        // UserData::value length = 240 bytes.
        // Length is given by the low six bits of the first byte and the second byte.
        (byte)0x80, (byte)0xf0, 
        
        // RDP
        
        // TS_UD_HEADER::type = CS_CORE (0xc001)
        (byte)0x01, (byte)0xc0, 
        
        //  length = 216 bytes (LE)
        (byte)0xd8, (byte)0x00, 
        
        //  Version:  4.8
        (byte)0x04, (byte)0x00, (byte)0x08, (byte)0x00, 
        
        // Desktop width: 1024x768 (0x400 x 0x300 LE)
        (byte)0x00, (byte)0x04, (byte)0x00, (byte)0x03, 
        
        // Bits per pixel: 8bpp (0xca01 LE)
        (byte)0x01, (byte)0xca, 
        
        // SAS Sequence  (LE)
        (byte)0x03, (byte)0xaa,
        
        //  Keyboard layout = 1033 EN_US (LE)
        (byte)0x09, (byte)0x04, (byte)0x00, (byte)0x00, 
        
        // Client build = 1000 (LE)
        (byte)0xe8, (byte)0x03, (byte)0x00, (byte)0x00,
        
        // Client name: apollo3 (fixed length string: 32 bytes/16 characters UCS2)
        (byte)0x61, (byte)0x00, (byte)0x70, (byte)0x00, (byte)0x6f, (byte)0x00, (byte)0x6c, (byte)0x00,
        (byte)0x6c, (byte)0x00, (byte)0x6f, (byte)0x00, (byte)0x33, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0xc0, (byte)0x00, (byte)0x00, (byte)0x00,
        
        // Keyboard type: unknown = 0 (LE)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 

        // Keyboard subtype: 0 (LE)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        
        // Keyboard function key: 0 (LE)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        
        // IME File Name = "" (fixed string: 64 bytes/32 characters UCS2)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        
        
        // Post beta 2 color depth: 8bpp (0xca01, LE)
        (byte)0x01, (byte)0xca, 
        
        // Client product ID: 1 (LE)
        (byte)0x01, (byte)0x00, 
        
        // Serial number: 0 (LE)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        
        // High color depth: 24bpp (LE)
        (byte)0x18, (byte)0x00, 
        
        // Supported color depths: 0x01 (LE) 24bpp only  
        (byte)0x01, (byte)0x00, 
        
        // Early capability flags: 1 (SUPPORT_ERRINFO_PDU)
        (byte)0x01, (byte)0x00, 
        
        // Client UUID: "" (fixed string: 64 bytes/32 characters UCS2)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        
        // Connection type: Unknown
        (byte)0x00, 
        
        // Padding (?)
        (byte)0x00, 
        
        // Selected protocol: PROTOCOL_SSL (0x1, LE) 
        (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, 
        
        //
        // Header type: CS_CLUSTER (0xc004, LE)
        (byte)0x04, (byte)0xc0, 
        // Length: 12 bytes (LE)
        (byte)0x0c, (byte)0x00, 
        
         // Cluster flags = 0x0d 0x0d (LE) = 0x03 << 2 | 0x01 = REDIRECTION_VERSION4 << 2 | REDIRECTION_SUPPORTED
        (byte)0x0d, (byte)0x00, (byte)0x00, (byte)0x00,
        
        // Redirected session ID: 0 (LE)
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
        
        
        //
        // Header type: CS_SECURITY (0xc002, LE)
        (byte)0x02, (byte)0xc0,
        // Length: 12 bytes (LE)
        (byte)0x0c, (byte)0x00, 
        
        
        // Encryption methods: 0x03 (LE)= 0x01 | 0x02 = 40BIT_ENCRYPTION_FLAG | 128BIT_ENCRYPTION_FLAG 
        (byte)0x03, (byte)0x00, (byte)0x00, (byte)0x00,
        
        // Extended encryption flags: 0
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, 
    };
    /* @formatter:on */

    MockSource source = new MockSource("source", ByteBuffer.convertByteArraysToByteBuffers(new byte[] { 1, 2, 3 }));
    Element todo = new ClientMCSConnectInitial("TODO");
    Element x224 = new ClientX224DataPdu("x224");
    Element tpkt = new ClientTpkt("tpkt");

    Element sink = new MockSink("sink", ByteBuffer.convertByteArraysToByteBuffers(packet));

    Element mainSink = new MockSink("mainSink", ByteBuffer.convertByteArraysToByteBuffers(new byte[] { 1, 2, 3 }));

    Pipeline pipeline = new PipelineImpl("test");
    pipeline.add(source, todo, x224, tpkt, sink, mainSink);
    pipeline.link("source", "TODO", "mainSink");
    pipeline.link("TODO >" + OTOUT, "x224", "tpkt", "sink");
    pipeline.runMainLoop("source", STDOUT, false, false);
  }

}
