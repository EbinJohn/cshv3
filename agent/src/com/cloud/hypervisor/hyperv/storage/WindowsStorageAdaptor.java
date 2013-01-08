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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.apache.commons.codec.binary.Base64;

import com.cloud.agent.api.ManageSnapshotCommand;
import com.cloud.hypervisor.hyperv.storage.HypervPhysicalDisk.PhysicalDiskFormat;
import com.cloud.exception.InternalErrorException;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.StorageLayer;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;

public class WindowsStorageAdaptor implements StorageAdaptor {
    private static final Logger s_logger = Logger
            .getLogger(WindowsStorageAdaptor.class);
    private StorageLayer _storageLayer;
    
    // TODO: need to fix mount point
    private String _mountPoint = "/mnt";
    private String _manageSnapshotPath;

    public WindowsStorageAdaptor(StorageLayer storage) {
        _storageLayer = storage;
        _manageSnapshotPath = Script.findScript("scripts/storage/qcow2/",
                "managesnapshot.sh");
    }

    @Override
    public boolean createFolder(String uuid, String path) {
        String mountPoint = _mountPoint + File.separator + uuid;
        File f = new File(mountPoint + path);
        if (!f.exists()) {
            f.mkdirs();
        }
        return true;
    }

    public StorageVol getVolume(StoragePool pool, String volName) {
        StorageVol vol = null;

        try {
            vol = pool.storageVolLookupByName(volName);
        } catch (LibvirtException e) {

        }
        if (vol == null) {
            storagePoolRefresh(pool);
            try {
                vol = pool.storageVolLookupByName(volName);
            } catch (LibvirtException e) {
                throw new CloudRuntimeException(e.toString());
            }
        }
        return vol;
    }

    public StorageVol createVolume(Connect conn, StoragePool pool, String uuid,
            long size, volFormat format) throws LibvirtException {
        LibvirtStorageVolumeDef volDef = new LibvirtStorageVolumeDef(UUID
                .randomUUID().toString(), size, format, null, null);
        s_logger.debug(volDef.toString());
        return pool.storageVolCreateXML(volDef.toString(), 0);
    }

    public void storagePoolRefresh(StoragePool pool) {
        try {
            synchronized (getStoragePool(pool.getUUIDString())) {
                pool.refresh(0);
            }
        } catch (LibvirtException e) {

        }
    }

    private StoragePool createSharedStoragePool(Connect conn, String uuid,
            String host, String path) {
        String mountPoint = path;
        if (!_storageLayer.exists(mountPoint)) {
            s_logger.error(mountPoint + " does not exists. Check local.storage.path in agent.properties.");
            return null;
        }
        LibvirtStoragePoolDef spd = new LibvirtStoragePoolDef(poolType.DIR,
                uuid, uuid, host, path, path);
        StoragePool sp = null;
        try {
            s_logger.debug(spd.toString());
            sp = conn.storagePoolDefineXML(spd.toString(), 0);
            sp.create(0);

            return sp;
        } catch (LibvirtException e) {
            s_logger.error(e.toString());
            if (sp != null) {
                try {
                    sp.undefine();
                    sp.free();
                } catch (LibvirtException l) {
                    s_logger.debug("Failed to define shared mount point storage pool with: "
                            + l.toString());
                }
            }
            return null;
        }
    }

    public StorageVol copyVolume(StoragePool destPool,
            LibvirtStorageVolumeDef destVol, StorageVol srcVol, int timeout)
            throws LibvirtException {
        StorageVol vol = destPool.storageVolCreateXML(destVol.toString(), 0);
        String srcPath = srcVol.getKey();
        String destPath = vol.getKey();
        Script.runSimpleBashScript("cp " + srcPath + " " + destPath, timeout);
        return vol;
    }

    public boolean copyVolume(String srcPath, String destPath,
            String volumeName, int timeout) throws InternalErrorException {
        _storageLayer.mkdirs(destPath);
        if (!_storageLayer.exists(srcPath)) {
            throw new InternalErrorException("volume:" + srcPath
                    + " is not exits");
        }
        String result = Script.runSimpleBashScript("cp " + srcPath + " "
                + destPath + File.separator + volumeName, timeout);
        if (result != null) {
            return false;
        } else {
            return true;
        }
    }

    public LibvirtStoragePoolDef getStoragePoolDef(Connect conn,
            StoragePool pool) throws LibvirtException {
        String poolDefXML = pool.getXMLDesc(0);
        LibvirtStoragePoolXMLParser parser = new LibvirtStoragePoolXMLParser();
        return parser.parseStoragePoolXML(poolDefXML);
    }

    public LibvirtStorageVolumeDef getStorageVolumeDef(Connect conn,
            StorageVol vol) throws LibvirtException {
        String volDefXML = vol.getXMLDesc(0);
        LibvirtStorageVolumeXMLParser parser = new LibvirtStorageVolumeXMLParser();
        return parser.parseStorageVolumeXML(volDefXML);
    }

    public StoragePool createFileBasedStoragePool(Connect conn,
            String localStoragePath, String uuid) {
        if (!(_storageLayer.exists(localStoragePath) && _storageLayer
                .isDirectory(localStoragePath))) {
            return null;
        }

        File path = new File(localStoragePath);
        if (!(path.canWrite() && path.canRead() && path.canExecute())) {
            return null;
        }

        StoragePool pool = null;

        try {
            pool = conn.storagePoolLookupByUUIDString(uuid);
        } catch (LibvirtException e) {

        }

        if (pool == null) {
            LibvirtStoragePoolDef spd = new LibvirtStoragePoolDef(poolType.DIR,
                    uuid, uuid, null, null, localStoragePath);
            try {
                pool = conn.storagePoolDefineXML(spd.toString(), 0);
                pool.create(0);
            } catch (LibvirtException e) {
                if (pool != null) {
                    try {
                        pool.destroy();
                        pool.undefine();
                    } catch (LibvirtException e1) {
                    }
                    pool = null;
                }
                throw new CloudRuntimeException(e.toString());
            }
        }

        try {
            StoragePoolInfo spi = pool.getInfo();
            if (spi.state != StoragePoolState.VIR_STORAGE_POOL_RUNNING) {
                pool.create(0);
            }

        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }

        return pool;
    }

    @Override
    public HypervStoragePool getStoragePool(String uuid) {
        StoragePool storage = null;
        try {
            Connect conn = LibvirtConnection.getConnection();
            storage = conn.storagePoolLookupByUUIDString(uuid);

            if (storage.getInfo().state != StoragePoolState.VIR_STORAGE_POOL_RUNNING) {
                storage.create(0);
            }
            LibvirtStoragePoolDef spd = getStoragePoolDef(conn, storage);
            StoragePoolType type = null;


            LibvirtStoragePool pool = new LibvirtStoragePool(uuid, storage.getName(),
                                                            type, this, storage);

            pool.setLocalPath(spd.getTargetPath());

            pool.refresh();
            pool.setCapacity(storage.getInfo().capacity);
            pool.setUsed(storage.getInfo().allocation);

            return pool;
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }
    }

    @Override
    public HypervPhysicalDisk getPhysicalDisk(String volumeUuid,
            HypervStoragePool pool) {
        LibvirtStoragePool libvirtPool = (LibvirtStoragePool) pool;

        try {
            StorageVol vol = this.getVolume(libvirtPool.getPool(), volumeUuid);
            HypervPhysicalDisk disk;
            LibvirtStorageVolumeDef voldef = getStorageVolumeDef(libvirtPool
                    .getPool().getConnect(), vol);
            disk = new HypervPhysicalDisk(vol.getPath(), vol.getName(), pool);
            disk.setSize(vol.getInfo().allocation);
            disk.setVirtualSize(vol.getInfo().capacity);
            if (voldef.getFormat() == null) {
                disk.setFormat(pool.getDefaultFormat());
            } else if (voldef.getFormat() == LibvirtStorageVolumeDef.volFormat.QCOW2) {
                disk.setFormat(HypervPhysicalDisk.PhysicalDiskFormat.QCOW2);
            } else if (voldef.getFormat() == LibvirtStorageVolumeDef.volFormat.RAW) {
                disk.setFormat(HypervPhysicalDisk.PhysicalDiskFormat.RAW);
            }
            return disk;
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }

    }

    @Override
    public HypervStoragePool createStoragePool(String name, String host, int port,
                                            String path, String userInfo, StoragePoolType type) {
        StoragePool sp = null;
        Connect conn = null;
        try {
            conn = LibvirtConnection.getConnection();
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }

        try {
            sp = conn.storagePoolLookupByUUIDString(name);
            if (sp.getInfo().state != StoragePoolState.VIR_STORAGE_POOL_RUNNING) {
                sp.undefine();
                sp = null;
            }
        } catch (LibvirtException e) {

        }

        if (sp == null) {
        	if (type == StoragePoolType.Filesystem) {
                sp = createSharedStoragePool(conn, name, host, path);
            }
        }

        try {
            StoragePoolInfo spi = sp.getInfo();
            if (spi.state != StoragePoolState.VIR_STORAGE_POOL_RUNNING) {
                sp.create(0);
            }

            LibvirtStoragePoolDef spd = getStoragePoolDef(conn, sp);
            LibvirtStoragePool pool = new LibvirtStoragePool(name,
                    sp.getName(), type, this, sp);

            pool.setLocalPath(spd.getTargetPath());

            pool.setCapacity(sp.getInfo().capacity);
            pool.setUsed(sp.getInfo().allocation);
  
            return pool;
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }
    }

    @Override
    public boolean deleteStoragePool(String uuid) {
        Connect conn = null;
        try {
            conn = LibvirtConnection.getConnection();
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }

        StoragePool sp = null;
        Secret s = null;

        try {
            sp = conn.storagePoolLookupByUUIDString(uuid);
        } catch (LibvirtException e) {
            return true;
        }

        /*
         * Some storage pools, like RBD also have 'secret' information stored in libvirt
         * Destroy them if they exist
        */
        try {
            s = conn.secretLookupByUUIDString(uuid);
        } catch (LibvirtException e) {
        }

        try {
            sp.destroy();
            sp.undefine();
            sp.free();
            if (s != null) {
                s.undefine();
                s.free();
            }
            return true;
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }
    }

    @Override
    public HypervPhysicalDisk createPhysicalDisk(String name, HypervStoragePool pool,
            PhysicalDiskFormat format, long size) {
        LibvirtStoragePool libvirtPool = (LibvirtStoragePool) pool;
        StoragePool virtPool = libvirtPool.getPool();
        LibvirtStorageVolumeDef.volFormat libvirtformat = null;

        if (format == PhysicalDiskFormat.QCOW2) {
            libvirtformat = LibvirtStorageVolumeDef.volFormat.QCOW2;
        } else if (format == PhysicalDiskFormat.RAW) {
            libvirtformat = LibvirtStorageVolumeDef.volFormat.RAW;
        }

        LibvirtStorageVolumeDef volDef = new LibvirtStorageVolumeDef(name,
                size, libvirtformat, null, null);
        s_logger.debug(volDef.toString());
        try {
            StorageVol vol = virtPool.storageVolCreateXML(volDef.toString(), 0);
            HypervPhysicalDisk disk = new HypervPhysicalDisk(vol.getPath(),
                    vol.getName(), pool);
            disk.setFormat(format);
            disk.setSize(vol.getInfo().allocation);
            disk.setVirtualSize(vol.getInfo().capacity);
            return disk;
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }
    }

    @Override
    public boolean deletePhysicalDisk(String uuid, HypervStoragePool pool) {
        LibvirtStoragePool libvirtPool = (LibvirtStoragePool) pool;
        try {
            StorageVol vol = this.getVolume(libvirtPool.getPool(), uuid);
            vol.delete(0);
            vol.free();
            return true;
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }
    }

    @Override
    public HypervPhysicalDisk createDiskFromTemplate(HypervPhysicalDisk template,
            String name, PhysicalDiskFormat format, long size, HypervStoragePool destPool) {

        String newUuid = UUID.randomUUID().toString();
        HypervStoragePool srcPool = template.getPool();
        HypervPhysicalDisk disk = null;

        /*
            With RBD you can't run qemu-img convert with an existing RBD image as destination
            qemu-img will exit with the error that the destination already exists.
            So for RBD we don't create the image, but let qemu-img do that for us.

            We then create a HypervPhysicalDisk object that we can return
        */

        disk = destPool.createPhysicalDisk(newUuid, format, template.getVirtualSize());

        if (format == PhysicalDiskFormat.QCOW2) {
        	Script.runSimpleBashScript("qemu-img create -f "
        			+ template.getFormat() + " -b  " + template.getPath() + " "
        			+ disk.getPath());
        } else if (format == PhysicalDiskFormat.RAW) {
        	Script.runSimpleBashScript("qemu-img convert -f "
        			+ template.getFormat() + " -O raw " + template.getPath()
        			+ " " + disk.getPath());
        }
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
            HypervStoragePool pool) {
        LibvirtStoragePool libvirtPool = (LibvirtStoragePool) pool;
        StoragePool virtPool = libvirtPool.getPool();
        List<HypervPhysicalDisk> disks = new ArrayList<HypervPhysicalDisk>();
        try {
            String[] vols = virtPool.listVolumes();
            for (String volName : vols) {
                HypervPhysicalDisk disk = this.getPhysicalDisk(volName, pool);
                disks.add(disk);
            }
            return disks;
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }
    }

    @Override
    public HypervPhysicalDisk copyPhysicalDisk(HypervPhysicalDisk disk, String name,
            HypervStoragePool destPool) {

        /*
            With RBD you can't run qemu-img convert with an existing RBD image as destination
            qemu-img will exit with the error that the destination already exists.
            So for RBD we don't create the image, but let qemu-img do that for us.

            We then create a HypervPhysicalDisk object that we can return
        */

        HypervPhysicalDisk newDisk;
        if (destPool.getType() != StoragePoolType.RBD) {
            newDisk = destPool.createPhysicalDisk(name, disk.getVirtualSize());
        }
        
        HypervStoragePool srcPool = disk.getPool();
        String destPath = newDisk.getPath();
        String sourcePath = disk.getPath();
        PhysicalDiskFormat sourceFormat = disk.getFormat();
        PhysicalDiskFormat destFormat = newDisk.getFormat();

        if ((srcPool.getType() != StoragePoolType.RBD) && (destPool.getType() != StoragePoolType.RBD)) {
            Script.runSimpleBashScript("qemu-img convert -f " + sourceFormat
                + " -O " + destFormat
                + " " + sourcePath
                + " " + destPath);
        } 

        return newDisk;
    }

    @Override
    public HypervStoragePool getStoragePoolByURI(String uri) {
        URI storageUri = null;

        try {
            storageUri = new URI(uri);
        } catch (URISyntaxException e) {
            throw new CloudRuntimeException(e.toString());
        }

        String sourcePath = null;
        String uuid = null;
        String sourceHost = "";
        StoragePoolType protocal = null;

        return createStoragePool(uuid, sourceHost, 0, sourcePath, "", protocal);
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
        LibvirtStoragePool libvirtPool = (LibvirtStoragePool) pool;
        StoragePool virtPool = libvirtPool.getPool();
        try {
            virtPool.refresh(0);
        } catch (LibvirtException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean deleteStoragePool(HypervStoragePool pool) {
        LibvirtStoragePool libvirtPool = (LibvirtStoragePool) pool;
        StoragePool virtPool = libvirtPool.getPool();
        try {
            virtPool.destroy();
            virtPool.undefine();
            virtPool.free();
        } catch (LibvirtException e) {
            return false;
        }

        return true;
    }

    public boolean deleteVbdByPath(String diskPath) {
        Connect conn;
        try {
            conn = LibvirtConnection.getConnection();
            StorageVol vol = conn.storageVolLookupByPath(diskPath);
            if(vol != null) {
                s_logger.debug("requested delete disk " + diskPath);
                vol.delete(0);
            }
        } catch (LibvirtException e) {
            s_logger.debug("Libvirt error in attempting to find and delete patch disk:" + e.toString());
            return false;
        }
        return true;
    }

}
