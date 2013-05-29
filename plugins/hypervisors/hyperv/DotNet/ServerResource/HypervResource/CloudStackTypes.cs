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
using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

// C# versions of certain CloudStack types to simplify JSON serialisation.
// Limit to the number of types, becasue they are written and maintained manually.
// JsonProperty used to identify property name when serialised, which allows
// later adoption of C# naming conventions if requried. 
namespace HypervResource
{

        enum VolumeType
        {
            UNKNOWN, ROOT, SWAP, DATADISK, ISO
        };

        public enum StoragePoolType {
            /// <summary>
            /// local directory
            /// </summary>
            Filesystem,         
            /// <summary>
            /// NFS or CIFS 
            /// </summary>
            NetworkFilesystem,  
            /// <summary>
            /// shared LUN, with a clusterfs overlay 
            /// </summary>
            IscsiLUN,           
            /// <summary>
            /// for e.g., ZFS Comstar 
            /// </summary>
            Iscsi,              
            /// <summary>
            /// for iso image
            /// </summary>
            ISO,                
            /// <summary>
            /// XenServer local LVM SR
            /// </summary>
            LVM, 
            /// <summary>
            /// 
            /// </summary>
            CLVM, 
            /// <summary>
            /// 
            /// </summary>
            RBD, 
            /// <summary>
            /// 
            /// </summary>
            SharedMountPoint, 
            /// <summary>
            /// VMware VMFS storage 
            /// </summary>
            VMFS, 
            /// <summary>
            /// for XenServer, Storage Pool is set up by customers. 
            /// </summary>
            PreSetup, 
            /// <summary>
            /// XenServer local EXT SR 
            /// </summary>
            EXT, 
            /// <summary>
            /// 
            /// </summary>
            OCFS2
    }

        public enum StorageResourceType
        {
            STORAGE_POOL, STORAGE_HOST, SECONDARY_STORAGE, LOCAL_SECONDARY_STORAGE
        }

        public struct VolumeInfo
        {
            public long id;
            public string type;
            public string storagePoolType;
            public string storagePoolUuid;
            public string name;
            public string mountPoint;
            public string path;
            long size;
            string chainInfo;

            public VolumeInfo(long id, string type, string poolType, String poolUuid, String name, String mountPoint, String path, long size, String chainInfo)
            {
                this.id = id;
                this.name = name;
                this.path = path;
                this.size = size;
                this.type = type;
                this.storagePoolType = poolType;
                this.storagePoolUuid = poolUuid;
                this.mountPoint = mountPoint;
                this.chainInfo = chainInfo;
            }
        }

    public struct StoragePoolInfo
    {
        [JsonProperty("uuid")]
        public String uuid;
        [JsonProperty("host")]
        String host;
        [JsonProperty("localPath")]
        String localPath;
        [JsonProperty("hostPath")]
        String hostPath;
        [JsonProperty("poolType")]
        string poolType;
        [JsonProperty("capacityBytes")]
        long capacityBytes;
        [JsonProperty("availableBytes")]
        long availableBytes;
        [JsonProperty("details")]
        Dictionary<String, String> details;

        public StoragePoolInfo(String uuid, String host, String hostPath,
                String localPath, string poolType, long capacityBytes,
                long availableBytes)
        {
            this.uuid = uuid;
            this.host = host;
            this.localPath = localPath;
            this.hostPath = hostPath;
            this.poolType = poolType;
            this.capacityBytes = capacityBytes;
            this.availableBytes = availableBytes;
            details = null;
        }

        public StoragePoolInfo(String uuid, String host, String hostPath,
                String localPath, string poolType, long capacityBytes,
                long availableBytes, Dictionary<String, String> details) : this(uuid, host, hostPath, localPath, poolType, capacityBytes, availableBytes)
        {
            this.details = details;
        }
    }

    public class VmStatsEntry
    {
        [JsonProperty("cpuUtilization")]
        public double cpuUtilization;
        [JsonProperty("networkReadKBs")]
        public double networkReadKBs;
        [JsonProperty("networkWriteKBs")]
        public double networkWriteKBs;
        [JsonProperty("numCPUs")]
        public int numCPUs;
        [JsonProperty("entityType")]
        public String entityType;
    }
}
