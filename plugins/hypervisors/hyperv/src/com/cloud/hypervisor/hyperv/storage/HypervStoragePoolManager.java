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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.cloud.hypervisor.hyperv.resource.HypervResource;
import com.cloud.hypervisor.hyperv.storage.HypervPhysicalDisk.PhysicalDiskFormat;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.StorageLayer;

// Hyper-V 3.0 introduces Resource Pools, which are available with the new API
// However, our starting point was the V1 API, so we emulate storage pools.
// This involves mapping a storage pool to a specific folder, which is managed
// by the admin independent of CloudStack.
public class HypervStoragePoolManager {
    private static final Logger s_logger = Logger.getLogger(HypervStoragePoolManager.class);
	private StorageAdaptor _storageAdaptor;
	private final Map<String, HypervStoragePool> _storagePools = new ConcurrentHashMap<String, HypervStoragePool>();
	private String _secondaryStorageMount;

	public HypervStoragePoolManager(StorageLayer storagelayer,
			String secondaryStorageMount) {
		this._storageAdaptor = new WindowsStorageAdaptor(storagelayer);
		this._secondaryStorageMount = secondaryStorageMount;
	}

	public HypervStoragePool getStoragePool(String uuid) {
		synchronized (_storagePools) {
			if (!_storagePools.containsKey(uuid)) {
				return null;
			}
			return _storagePools.get(uuid);
		}
	}

	// Non-persistent pool, not cleared when deleted or created
	public HypervStoragePool getStoragePoolByURI(String uri) {
		return this._storageAdaptor.getStoragePoolByURI(uri,
				_secondaryStorageMount);
	}

	public HypervStoragePool createStoragePool(String uuid, String host,
			int port, String path, String userInfo, StoragePoolType type) {
		String taskMsg = "Create storagepool " + uuid + " at " + path ;
		s_logger.debug(taskMsg);

		HypervStoragePool pool = this._storageAdaptor.createStoragePool(uuid,
				host, port, path, userInfo, type);

		synchronized (_storagePools) {
			if (!_storagePools.containsKey(pool.getUuid())) {
				_storagePools.put(pool.getUuid(), pool);
			}
		}

		return getStoragePool(pool.getUuid());
	}

	public boolean deleteStoragePool(String uuid) {
		HypervStoragePool pool = null;
		synchronized (_storagePools) {
			if (!_storagePools.containsKey(uuid)) {
				return true;
			}
			pool = _storagePools.get(uuid);

			_storagePools.remove(uuid);
		}
		this._storageAdaptor.deleteStoragePool(uuid, pool.getLocalPath());
		return true;
	}

	public boolean deleteVbdByPath(String diskPath) {
		return this._storageAdaptor.deleteVbdByPath(diskPath);
	}

	public HypervPhysicalDisk createDiskFromTemplate(
			HypervPhysicalDisk template, String name, HypervStoragePool destPool) {
		return this._storageAdaptor.copyPhysicalDisk(template, name, destPool);
	}

	public HypervPhysicalDisk createTemplateFromDisk(HypervPhysicalDisk disk,
			String name, PhysicalDiskFormat format, long size,
			HypervStoragePool destPool) {
		return this._storageAdaptor.createTemplateFromDisk(disk, name, format,
				size, destPool);
	}

	public HypervPhysicalDisk copyPhysicalDisk(HypervPhysicalDisk disk,
			String name, HypervStoragePool destPool) {
		return this._storageAdaptor.copyPhysicalDisk(disk, name, destPool);
	}

	public HypervPhysicalDisk createDiskFromSnapshot(
			HypervPhysicalDisk snapshot, String snapshotName, String name,
			HypervStoragePool destPool) {
		return this._storageAdaptor.createDiskFromSnapshot(snapshot,
				snapshotName, name, destPool);
	}

	public HypervPhysicalDisk getPhysicalDiskFromUrl(String url) {
		return this._storageAdaptor.getPhysicalDiskFromURI(url);
	}
}
