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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.apache.commons.codec.binary.Base64;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.ManageSnapshotCommand;
import com.cloud.agent.api.storage.CreateAnswer;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.api.to.VolumeTO;
import com.cloud.hypervisor.hyperv.resource.PythonUtils;
import com.cloud.hypervisor.hyperv.storage.HypervPhysicalDisk.PhysicalDiskFormat;
import com.cloud.hypervisor.hyperv.storage.HypervStoragePool;
import com.cloud.exception.InternalErrorException;
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
    
    // TODO: need to fix mount point
    // Mount point used in cases where Pool models secondary storage.
    private String _mountPoint = "/mnt";

    public WindowsStorageAdaptor(StorageLayer storage) {
        _storageLayer = storage;
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
    	String taskMsg = "Use file system to make copy of disk " + disk.getPath() + " to " + 
    			destPath + File.separator + name;
    	s_logger.debug(taskMsg);
        // Warnings about performance issues lead to use of NIO for copy.
        // See http://stackoverflow.com/questions/106770/standard-concise-way-to-copy-a-file-in-java
        FileChannel src = null;
        FileChannel dest = null;
        File sourceFile = new File(sourcePath);
        String destFilePath = destPath + File.separator +  sourceFile.getName();
        File destFile = new File(destFilePath);
        HypervPhysicalDisk newDisk = new HypervPhysicalDisk(destPath, name, destPool);
        
        try {
            src = new FileInputStream(sourceFile).getChannel();
            dest = new FileOutputStream(destFile).getChannel();
            dest.transferFrom(src, 0, src.size());
        }
        catch (FileNotFoundException e) {
        	String errMsg = e.toString() + " for task to " + taskMsg + "";
            s_logger.debug(errMsg);
            throw new RuntimeCloudException(errMsg, e);
        }
        catch (IOException e) {
        	String errMsg = e.toString() + " for task to " + taskMsg + "";
            s_logger.debug(errMsg);
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
        
        return newDisk;
    }

    @Override
    public HypervStoragePool getStoragePoolByURI(String uri, String uriLocalPath) {
    	String taskMsg = "Creating pool over the uri " + uri;
        s_logger.debug(taskMsg);

        URI storageUri = null;

        try {
            storageUri = new URI(uri);
        } catch (URISyntaxException e) {
            s_logger.debug("Invalid URI " + e.getMessage());
            throw new CloudRuntimeException(e.toString());
        }

        String sourcePath = null;
        String uuid = null;
        String sourceHost = "";
        StoragePoolType protocal = null;
        sourcePath = storageUri.getPath();
        sourcePath = sourcePath.replace("//", "/");
        
        sourceHost = storageUri.getHost();
        uuid = UUID.nameUUIDFromBytes(
                    new String(sourceHost + sourcePath).getBytes()).toString();
            
        if (storageUri.getScheme().equalsIgnoreCase("nfs")) {
            protocal = StoragePoolType.NetworkFilesystem;
        }

        // Generate local mount corresponding to NFS share.
        protocal = StoragePoolType.Filesystem;
        
        if (!this._storageLayer.isDirectory(uriLocalPath)){
        	String errMsg = "No directory corresponding to " + uriLocalPath + " for task " + taskMsg;
        	s_logger.debug(errMsg);
        	throw new CloudRuntimeException(errMsg);
        }
        
        WindowsStoragePool pool = new WindowsStoragePool(uuid, uri, protocal, this);
        pool.setLocalPath(uriLocalPath);
            
        return pool;
    }
    
    @Override
    public HypervPhysicalDisk getPhysicalDiskFromURI(String uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public HypervPhysicalDisk createDiskFromSnapshot(HypervPhysicalDisk snapshot,
            String snapshotName, String name, HypervStoragePool destPool) {
        return null;
    }

    @Override
    public boolean refresh(HypervStoragePool pool) {
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
