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
package com.cloud.hypervisor.hyperv.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.apache.commons.codec.binary.Base64;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.ManageSnapshotCommand;
import com.cloud.agent.api.storage.CreateAnswer;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.api.storage.PrimaryStorageDownloadAnswer;
import com.cloud.agent.api.to.VolumeTO;
import com.cloud.hypervisor.hyperv.resource.PythonUtils;
import com.cloud.hypervisor.hyperv.storage.HypervPhysicalDisk.PhysicalDiskFormat;
import com.cloud.hypervisor.hyperv.storage.HypervStoragePool;
import com.cloud.exception.InternalErrorException;
import com.cloud.offering.ServiceOffering;
import com.cloud.offering.ServiceOffering.StorageType;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.StorageLayer;
import com.cloud.storage.StoragePool;
import com.cloud.storage.StoragePoolVO;
import com.cloud.storage.Volume;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.RuntimeCloudException;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import com.cloud.vm.DiskProfile;


public class WindowsStorageAdaptor implements StorageAdaptor {
    private static final Logger s_logger = Logger
            .getLogger(WindowsStorageAdaptor.class);
    private StorageLayer _storageLayer;
    
    // Local path for NFS schemes.
    private String _nfsLocalPath;

    public WindowsStorageAdaptor(StorageLayer storage, String nfsLocalPath) {
        _storageLayer = storage;
        _nfsLocalPath = nfsLocalPath;
    }

    // Method used by pools modelling secondary storage to create folders
    // at uuid + path.  
    // TODO:  duplicated when secondary storage design becomes more concrete
    @Override
    public boolean createFolder(String uuid, String path) {
        throw new CloudRuntimeException("Not implemented");
    }

    @Override
    public HypervStoragePool getStoragePool(String uuid) {
        throw new CloudRuntimeException("Not implemented");
    }

    @Override
    public HypervPhysicalDisk getPhysicalDisk(String volumeUuid,
            HypervStoragePool poolArg) {
    	WindowsStoragePool pool = (WindowsStoragePool) poolArg;

    	String diskPath = pool.getLocalPath() + File.separator + volumeUuid;
    	String taskMsg = "Get HypervPhysicalDisk for volume uuid " + volumeUuid + 
    					" at " + diskPath +
    					" from pool " + pool.getUuid().toString();
        s_logger.debug(taskMsg);
        
        if (!this._storageLayer.exists(diskPath)) {
        	String errMsg = "No file at " + diskPath + " for disk, failed task" + taskMsg;
        	s_logger.error(errMsg);
        	throw new CloudRuntimeException(errMsg);
        }
        HypervPhysicalDisk disk = new HypervPhysicalDisk(diskPath, volumeUuid, pool);
        s_logger.debug("HypervPhysicalDisk " + pool.name);

        // TODO:  AFAIK, usage stats for a volume come from WMI
        //disk.setSize(vol.getInfo().allocation);
        //disk.setVirtualSize(vol.getInfo().capacity);

        return disk;
    }

    @Override
    public HypervStoragePool createStoragePool(String name, String host, int port,
                                            String path, String userInfo, StoragePoolType type) {
    	String taskMsg = "Creating pool " + name + " at " + path + " of type " + type.name();
        s_logger.debug(taskMsg);

        if (!this._storageLayer.isDirectory(path)){
        	String errMsg = "Not such path for task " + taskMsg;
        	s_logger.debug(errMsg);
        	throw new CloudRuntimeException(errMsg);
        }
        
        WindowsStoragePool pool = new WindowsStoragePool(name, name, type, this);

        pool.setLocalPath(path);
            
        File dir = new File(path);
        long usableCapacity = dir.getUsableSpace();

        // Determine the used / capacity stats, not sure how to derive these.
        pool.setCapacity(usableCapacity);
        pool.setUsed(0);

        // Do not clear the folder, admin responsible for this.
        return pool;
    }

    @Override
    public boolean deleteStoragePool(String uuid) {
    	String taskMsg = "Delete pool " + uuid ;
        s_logger.debug(taskMsg);
    	return true;
    }

    @Override
    public boolean deleteStoragePool(String uuid, String localPath) {
    	String taskMsg = "Delete files in pool " + uuid + " at " + localPath;
        s_logger.debug(taskMsg);
    	this._storageLayer.deleteDir(localPath);
    	return true;
    }
    
    @Override
    public HypervPhysicalDisk createPhysicalDisk(String name, HypervStoragePool poolArg,
            PhysicalDiskFormat format, long size) {
        WindowsStoragePool pool = (WindowsStoragePool) poolArg;
        
    	String taskMsg = "creating an empty" + format.name() + "disk named " +name + " in pool " 
    				+ pool.getUuid() + " with path " + pool.getLocalPath();
        s_logger.debug(taskMsg);

        // WMI to create blank VHD for Hyper-V  (using V1.0 API)
        // Create corresponding CreateCommand to pass to Python for create insvocation!
        DiskProfile diskCharacteristics = new DiskProfile((long)-1, Volume.Type.DATADISK, 
        		name, (long)-1, size, new String[0], true, true, (long)-1);
        StoragePoolVO cmdPool = new StoragePoolVO();
        cmdPool.setPath(pool.getLocalPath());

        CreateCommand cmd = new CreateCommand(diskCharacteristics, cmdPool);

        CreateAnswer pythonResult = PythonUtils.callHypervPythonModule(cmd, CreateAnswer.class);
        VolumeTO vol = pythonResult.getVolume();
        HypervPhysicalDisk disk = new HypervPhysicalDisk(vol.getPath(),vol.getName(), pool);
        return disk;
    }

    @Override
    public boolean deletePhysicalDisk(String uuid, HypervStoragePool pool) {
    	String path = pool.getLocalPath() + File.separator + uuid;
    	String taskMsg = "requested delete disk " + uuid + " in pool " + pool.getUuid() + " with path " + pool.getLocalPath();
        s_logger.debug(taskMsg);

    	return this.deleteVbdByPath(path);
    }

    @Override
    public HypervPhysicalDisk createDiskFromTemplate(HypervPhysicalDisk template,
            String name, PhysicalDiskFormat format, long size, HypervStoragePool destPool) {

        String newUuid = UUID.randomUUID().toString();
        HypervStoragePool srcPool = template.getPool();
        HypervPhysicalDisk disk = null;

        // TODO: Is disk from template outright copy or thin copy.
        // Calls down to our createPhysicalDisk method
        disk = destPool.createPhysicalDisk(newUuid, format, template.getVirtualSize());

        return disk;
    }

    @Override
    public HypervPhysicalDisk createTemplateFromDisk(HypervPhysicalDisk disk,
            String name, PhysicalDiskFormat format, long size,
            HypervStoragePool destPool) {
        return null;
    }

    @Override
    public List<HypervPhysicalDisk> listPhysicalDisks(String storagePoolUuid,
            HypervStoragePool poolArg) {
    	WindowsStoragePool pool = (WindowsStoragePool) poolArg;

    	String taskMsg = "Return list of disks corresponding to pool, id: " + pool.getUuid().toString();
        s_logger.debug(taskMsg);
        
        List<HypervPhysicalDisk> disks = new ArrayList<HypervPhysicalDisk>();
        String diskPath = pool.getLocalPath();
        if (!this._storageLayer.exists(diskPath)) {
        	String errMsg = "No localPath " + diskPath + " for pool, failed task" + taskMsg;
        	s_logger.error(errMsg);
        	throw new CloudRuntimeException(errMsg);
        }
        
        File folder = new File(pool.getLocalPath());
        File[] listOfFiles = folder.listFiles();

        for (File file : listOfFiles) {
        	if (file.isFile()) {
        		HypervPhysicalDisk disk = new HypervPhysicalDisk(file.getPath(), file.getName(), pool);
        		disks.add(disk);
        	}
        }
        	    
        return disks;
    }

    @Override
    public HypervPhysicalDisk copyPhysicalDisk(HypervPhysicalDisk disk, String name,
            HypervStoragePool destPool) {

    	// Use file system to complete task.
        String sourcePath = disk.getPath();
        String destPath = destPool.getLocalPath();
        String destFilePath = destPath + File.separator +  name;
        
    	copyDiskViaNIO(sourcePath, destFilePath);
        
        HypervPhysicalDisk newDisk = new HypervPhysicalDisk(destFilePath, name, destPool);
        return newDisk;
    }

	private void copyDiskViaNIO(String sourcePath, String destFilePath) {
		String taskMsg = "Use file system to make copy of disk " + sourcePath + " to " + 
    			destFilePath;
    	s_logger.debug(taskMsg);
        // Warnings about performance issues lead to use of NIO for copy.
        // See http://stackoverflow.com/questions/106770/standard-concise-way-to-copy-a-file-in-java
        FileChannel src = null;
        FileChannel dest = null;
        File sourceFile = new File(sourcePath);
        File destFile = new File(destFilePath);
        try {
            src = new FileInputStream(sourceFile).getChannel();
            dest = new FileOutputStream(destFile).getChannel();
            dest.transferFrom(src, 0, src.size());
        }
        catch (FileNotFoundException e) {
        	String errMsg = "Failed in " + taskMsg + " due to " + e.toString();
        	s_logger.debug(errMsg);
            throw new RuntimeCloudException(errMsg, e);
        }
        catch (IOException e) {
        	String errMsg = "Failed in " + taskMsg + " due to " + e.toString();
            s_logger.error(errMsg, e);
            throw new RuntimeCloudException(errMsg, e);
        }
        finally {
        	try {
            if(src != null) {
            	src.close();
            }
            if(dest != null) {
            	dest.close();
            }
        	}
            catch (IOException e) {
            	String errMsg = e.toString() + " when cleaning up after task " + taskMsg + "";
                s_logger.debug(errMsg);
                throw new RuntimeCloudException(errMsg, e);
            }
        }
	}
    
    @Override
    // TODO: caller must preserve the extension if that is the intent.
    public HypervPhysicalDisk copyPhysicalDisk(URI srcDiskURI, String newVolumeUUID,
            HypervStoragePool destPool){
    	// The HypervPhysicalDisk returned should have the same extension as the URI.
    	// Make sure other copy commands are consistent with this analysis.
    	// Otherwise the extension will grow.
    	
    	// TODO: revise variable names where they are UUIDandExtension
        int index = srcDiskURI.getPath().lastIndexOf("/");
        String destFilePath = destPool.getLocalPath() + File.separator + newVolumeUUID;
        String templateUUIDandExtension = srcDiskURI.getPath().substring(index + 1);

    	// TODO: Test HTTP URIs and NFS URIs
    	if (srcDiskURI.getScheme().equalsIgnoreCase("nfs"))
    	{
        	String srcDiskPath = this._nfsLocalPath + File.separator + templateUUIDandExtension;
        	String taskMsg = "Copy NFS url in " + srcDiskURI.toString() +
        			" at " + srcDiskPath + " to pool " + destPool.getUuid();
            s_logger.debug(taskMsg);
            
            if (!this._storageLayer.exists(srcDiskPath)) {
            	String errMsg = "No file at " + srcDiskPath + " for disk, failed task" + taskMsg;
            	s_logger.error(errMsg);
            	throw new CloudRuntimeException(errMsg);
            }
        	copyDiskViaNIO(srcDiskPath, destFilePath);
    	}
    	else if (srcDiskURI.getScheme().equalsIgnoreCase("http"))
    	{
        	// for HTTP, use NIO streams to download.
    		URL srcUrl;
			try {
				srcUrl = new URL(srcDiskURI.toString());
			} catch (MalformedURLException e1) {
            	String errMsg = "Malformed URI " + srcDiskURI.toString();
            	s_logger.error(errMsg, e1);
            	throw new CloudRuntimeException(errMsg);
			}

    		FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(destFilePath);
			} catch (FileNotFoundException e1) {
            	String errMsg = "Cannot create file " + destFilePath;
            	s_logger.error(errMsg, e1);
            	throw new CloudRuntimeException(errMsg);
			}
			
			InputStream inStream;
			try {
				inStream = srcUrl.openStream();
			} catch (IOException e1) {
            	String errMsg = "Cannot reach file " + srcUrl.toString();
            	s_logger.error(errMsg, e1);
				try {
					fos.close();
				}
				catch(IOException e) {
	            	String errMsg2 = "Also, could not close InputStream for file " + srcUrl.toString();
	            	s_logger.error(errMsg2, e);
				}
            	throw new CloudRuntimeException(errMsg);
			}

    		BufferedOutputStream outStrm = null;
    		BufferedInputStream inStrm = null;
			try {
				// TODO:  May be possible to optimise with NIO calls provided size of file can be determined
				// See http://stackoverflow.com/questions/921262/how-to-download-and-save-a-file-from-internet-using-java/921400#921400
				inStrm = new BufferedInputStream(inStream);
				outStrm = new BufferedOutputStream(fos,1 << 20);
				byte buffer[] = new byte[1 << 20];
				int byteCount;
				while((byteCount = inStrm.read(buffer,0,1 << 20))>=0) {
					outStrm.write(buffer, 0, byteCount);
				}
			} catch (IOException e) {
            	String errMsg = "Failed to write " + srcUrl.toString() + " to " + destFilePath;
            	s_logger.error(errMsg, e);
            	throw new CloudRuntimeException(errMsg);
			} finally {
				try {
					if (inStrm != null) {
						inStrm.close();
					}
				} catch (IOException e) {
	            	String errMsg = "Failed to clean up BufferedInputStream for " + srcUrl.toString();
	            	s_logger.error(errMsg);
				}
				try {
					if (outStrm != null) {
						outStrm.close();
					}
				} catch (IOException e) {
	            	String errMsg = "Failed to clean up BufferedOutputStream for " + destFilePath;
	            	s_logger.error(errMsg);
				}
			}
    	}
    	else
    	{
        	String errMsg = "Invalid schema in URI " + srcDiskURI.toString();
        	s_logger.error(errMsg);
        	throw new CloudRuntimeException(errMsg);
		}
    	return new HypervPhysicalDisk(destFilePath, newVolumeUUID, destPool);
    }

    @Override
    public HypervPhysicalDisk getPhysicalDiskFromURI(String uri) {
        return null;
    }
    

    @Override
    public HypervPhysicalDisk createDiskFromSnapshot(HypervPhysicalDisk snapshot,
            String snapshotName, String name, HypervStoragePool destPool) {
        return null;
    }

    @Override
    public boolean refresh(HypervStoragePool pool) {
    	WindowsStoragePool winPool = (WindowsStoragePool)pool;
    	
    	// TODO:  add test to verify capacity statistics change
        File dir = new File(pool.getLocalPath());
        long currCapacity = dir.getUsableSpace();
        long origCapacity = winPool.getCapacity();
        
        long consumedCapacity = origCapacity - currCapacity;
        s_logger.debug("Pool " + pool.getUuid() + " used capacity refreshed to " + consumedCapacity );
        winPool.setUsed(consumedCapacity);
        return true;
    }

    @Override
    public boolean deleteStoragePool(HypervStoragePool poolArg) {
        WindowsStoragePool pool = (WindowsStoragePool) poolArg;
    	String path = pool.getLocalPath();
    	String taskMsg = "Delete call on storage pool " + pool.getName() + " at " + path;
    	s_logger.debug(taskMsg);
        return true;
    }

    public boolean deleteVbdByPath(String diskPath) {
    	String taskMsg = "requested delete disk " + diskPath;
    	s_logger.debug(taskMsg);
    	boolean result = this._storageLayer.delete(diskPath);
    	if (!result) {
            s_logger.debug("Failed to perform task " + taskMsg);
    	}
  
    	return result;
    }
}
