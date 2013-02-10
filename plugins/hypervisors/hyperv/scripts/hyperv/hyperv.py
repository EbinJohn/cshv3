# vim: tabstop=4 shiftwidth=4 softtabstop=4

# Copyright (c) 2010 Cloud.com, Inc
#
#    Licensed under the Apache License, Version 2.0 (the "License"); you may
#    not use this file except in compliance with the License. You may obtain
#    a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#    License for the specific language governing permissions and limitations
#    under the License.

"""
A connection to Hyper-V .
Uses Windows Management Instrumentation (WMI) calls to interact with Hyper-V
Hyper-V WMI usage:
    http://msdn.microsoft.com/en-us/library/cc723875%28v=VS.85%29.aspx
The Hyper-V object model briefly:
    The physical computer and its hosted virtual machines are each represented
    by the Msvm_ComputerSystem class.

    Each virtual machine is associated with a
    Msvm_VirtualSystemGlobalSettingData (vs_gs_data) instance and one or more
    Msvm_VirtualSystemSettingData (vmsetting) instances. For each vmsetting
    there is a series of Msvm_ResourceAllocationSettingData (rasd) objects.
    The rasd objects describe the settings for each device in a VM.
    Together, the vs_gs_data, vmsettings and rasds describe the configuration
    of the virtual machine.

    Creating new resources such as disks and nics involves cloning a default
    rasd object and appropriately modifying the clone and calling the
    AddVirtualSystemResources WMI method
    Changing resources such as memory uses the ModifyVirtualSystemResources
    WMI method

Using the Python WMI library:
    Tutorial:
        http://timgolden.me.uk/python/wmi/tutorial.html
    Hyper-V WMI objects can be retrieved simply by using the class name
    of the WMI object and optionally specifying a column to filter the
    result set. More complex filters can be formed using WQL (sql-like)
    queries.
    The parameters and return tuples of WMI method calls can gleaned by
    examining the doc string. For example:
    >>> vs_man_svc.ModifyVirtualSystemResources.__doc__
    ModifyVirtualSystemResources (ComputerSystem, ResourceSettingData[])
                 => (Job, ReturnValue)'
    When passing setting data (ResourceSettingData) to the WMI method,
    an XML representation of the data is passed in using GetText_(1).
    Available methods on a service can be determined using method.keys():
    >>> vs_man_svc.methods.keys()
    vmsettings and rasds for a vm can be retrieved using the 'associators'
    method with the appropriate return class.
    Long running WMI commands generally return a Job (an instance of
    Msvm_ConcreteJob) whose state can be polled to determine when it finishes

"""
import os
import time
import uuid
import log as logging
from log import _ as _

wmi = None


LOG = logging.getLogger(__name__)


#HYPERV_POWER_STATE = {
#    3: power_state.SHUTDOWN,
#    2: power_state.RUNNING,
#    32768: power_state.PAUSED,
#}


REQ_POWER_STATE = {
    'Enabled': 2,
    'Disabled': 3,
    'Reboot': 10,
    'Reset': 11,
    'Paused': 32768,
    'Suspended': 32769,
}


WMI_JOB_STATUS_STARTED = 4096
WMI_JOB_STATE_RUNNING = 4
WMI_JOB_STATE_COMPLETED = 7

IDE_DISK = 'Microsoft Synthetic Disk Drive'
IDE_DVD = 'Microsoft Synthetic DVD Drive'


def get_connection():
    global wmi
    if wmi is None:
        LOG.debug("Import wmi")
        wmi = __import__('wmi')
    return HyperVConnection()


class HyperVConnection():
    def __init__(self):
        self._conn = wmi.WMI(moniker='//./root/virtualization')
        self._cim_conn = wmi.WMI(moniker='//./root/cimv2')

    def init_host(self, host):
        #FIXME(chiradeep): implement this
        LOG.debug(_('In init host'))
        pass

    def list_instances(self):
        """ Return the names of all the instances known to Hyper-V. """
        #updated to get only virtual machines!
        vms = [v.ElementName \
                for v in self._conn.Msvm_ComputerSystem(['ElementName'],
                    Caption="Virtual Machine")]
        return vms

    def create_volume(self, cmdData):
        volume = {}
        volume["id"] = cmdData["volId"];
        volume["name"] = cmdData["diskCharacteristics"]["name"];
        volume["size"] = cmdData["diskCharacteristics"]["size"];
        volume["type"] = cmdData["diskCharacteristics"]["type"];
        volume["path"] = cmdData["pool"]["path"] + os.path.sep + volume["name"] + '.' + volume["type"]
        volume["mountPoint"] = cmdData["pool"]["path"];
        
        #volume["storagePoolType"] = cmdData["pool"]["type"];
        #if not volume["storagePoolType"] == "Filesystem":
        #    raise Exception(_('Hyper-V only supports Filesystem pools, not %s pools') %
        #        volume["storagePoolType"])
        # todo: support DATADISK types
        if not volume["type"] == "DATADISK" and not volume["type"] == "ISO":
            raise Exception(_('No support for volumes of type %s pools') %
                volume["type"])

        image_service = self._conn.query("Select * from Msvm_ImageManagementService")[0]
        (job, ret_val) = image_service.CreateDynamicVirtualHardDisk(
                            Path=volume["path"], Size=volume["size"])
        LOG.debug("Creating DATADISK disk: JobID=%s, Path=%s, Size=%s",
                    job, volume["path"], volume["size"])
        if ret_val == WMI_JOB_STATUS_STARTED:
            success = self._check_job_status(job)
        else:
            success = (ret_val == 0)

        if not success:
            raise Exception(_('Failed to create Difference Disk from '
                            '%(base)s to %(target)s') % locals())
        return volume

    def spawn(self, cmdData):
        """ Create a new VM and start it."""
        instance = cmdData["vm"]
        instance['vcpus'] = instance['cpus']
        instance['memory_mb'] =  instance['maxRam'] / 1048576

        for nic in instance["nics"]:
            nic["address"] = nic["mac"]
            
            if nic.has_key("isolationUri") and nic["isolationUri"] is not None and nic["isolationUri"].startswith("vlan://"):
                nic["vlan"] = nic["isolationUri"].strip("vlan://")
            else:
                nic["vlan"] = None

        LOG.debug("Create VM %s , %s vcpus, %s MB RAM",
                   instance["name"], instance['vcpus'],instance['memory_mb'])
        
        # Does it exist?
        vms = self._lookup_details_multiple(instance['name'])
        if vms is not None:
            LOG.debug("VMs exist with name %s", instance['name'])
            # CitrixResourceBase.execute will destroy Halted VMs until it comes
            # across one that is not running.  Return on the running one
            # or go one to create a new one.
            for vm in vms:
                # vm object is a Msvm_ComputerSystem
                # http://msdn.microsoft.com/en-us/library/cc136822%28v=vs.85%29.aspx
                if vm.EnabledState == REQ_POWER_STATE['Disabled']:
                    #todo Destroy existing, HALTED VM, carry on with create
                    LOG.debug("Deleting a VM with name %s", instance['name'])
                    self.destroy(instance, None, False)
                elif vm.EnabledState == REQ_POWER_STATE['Enabled']:
                    #Report VM already running, answer false
                    errorMsg = _('VM %s is running on host') % instance['name']
                    LOG.debug(errorMsg)
                    raise Exception(errorMsg)
                else:
                    # Report existing VM, answer false
                    errorMsg = 'The VM having the name %s has EnabledState value of %s' %(instance['name'], vm.EnabledState)
                    LOG.debug(errorMsg)
                    raise Exception(errorMsg)
        try:
            # Create VM carcass
            self._create_vm(instance)

            # Attach volumes
            # todo: create root volume properly
            disks = cmdData["vm"]["disks"]
            for disk in disks:
                if disk["type"] == "ROOT":
                    vhdfile = disk["path"]
                    self._create_disk(instance['name'], vhdfile, 0, IDE_DISK)
                elif disk["type"] == "ISO":
                    try:
                        vhdfile = disk["path"]
                    except KeyError:
                        vhdfile = None
                    self._create_disk(instance['name'], vhdfile, 1, IDE_DVD)
                else:
                    errorMsg = _('Unknown disk type %s, for disk %s') % (disk["type"], disk["name"])
                    LOG.debug(errorMsg)
                    raise Exception(errorMsg)

            # Add NIC to VM
            for nic in instance["nics"]:
                self._create_nic(instance['name'], nic['address'], nic["vlan"])

            LOG.debug(_('Starting VM %s '), instance['name'])
            self._set_vm_state(instance['name'], 'Enabled')
            LOG.info(_('Started VM %s '), instance['name'])
        except Exception as exn:
            LOG.error(_('spawn vm failed: %s') % exn)
            self.destroy(instance, instance["nics"], False)
            raise exn

    def _create_vm(self, instance):
        """Create a VM but don't start it. 
        
        instance["name"]
        instance['memory_mb']
        instance['vcpus']
        """
        vs_man_svc = self._conn.Msvm_VirtualSystemManagementService()[0]

        vs_gs_data = self._conn.Msvm_VirtualSystemGlobalSettingData.new()
        vs_gs_data.ElementName = instance['name']
        (job, ret_val) = vs_man_svc.DefineVirtualSystem(
                [], None, vs_gs_data.GetText_(1))[1:]
        if ret_val == WMI_JOB_STATUS_STARTED:
            success = self._check_job_status(job)
        else:
            success = (ret_val == 0)

        if not success:
            raise Exception(_('Failed to create VM %s'), instance["name"])

        LOG.debug(_('Created VM %s...'), instance["name"])
        vm = self._conn.Msvm_ComputerSystem(ElementName=instance["name"])[0]

        # Look up for setting data via queries using the associator operation
        vmsettings = vm.associators(
                          wmi_result_class='Msvm_VirtualSystemSettingData')
        # remove snapshots from the list, select first setting obj
        vmsetting = [s for s in vmsettings 
                        if s.SettingType == 3][0]
        memsetting = vmsetting.associators(
                           wmi_result_class='Msvm_MemorySettingData')[0]
        #No Dynamic Memory, so reservation, limit and quantity are identical.
        mem = long(str(instance['memory_mb']))
        memsetting.VirtualQuantity = mem
        memsetting.Reservation = mem
        memsetting.Limit = mem

        (job, ret_val) = vs_man_svc.ModifyVirtualSystemResources(
                vm.path_(), [memsetting.GetText_(1)])
        #todo: why not wait for the job to finish?
        LOG.debug(_('Set memory for vm %s...'), instance['name'])
        procsetting = vmsetting.associators(
                wmi_result_class='Msvm_ProcessorSettingData')[0]
        vcpus = long(instance['vcpus'])
        procsetting.VirtualQuantity = vcpus
        procsetting.Reservation = vcpus
        procsetting.Limit = 100000  # static assignment to 100%

        (job, ret_val) = vs_man_svc.ModifyVirtualSystemResources(
                vm.path_(), [procsetting.GetText_(1)])
        #todo: why not wait for the job to finish?
        LOG.debug(_('Set vcpus for vm %s...'), instance["name"])

    def _create_disk(self, vm_name, vhdfile, ctrller_addr, 
                            drive_type=IDE_DISK):
        """Create a disk and attach it to the vm"""
        LOG.debug(_('Creating disk for %(vm_name)s by attaching'
                ' disk file %(vhdfile)s') % locals())
        #Find the IDE controller for the vm.
        vms = self._conn.MSVM_ComputerSystem(ElementName=vm_name)
        vm = vms[0]
        vmsettings = vm.associators(
                wmi_result_class='Msvm_VirtualSystemSettingData')
        rasds = vmsettings[0].associators(
                wmi_result_class='MSVM_ResourceAllocationSettingData')
        ctrller = [r for r in rasds
                   if r.ResourceSubType == 'Microsoft Emulated IDE Controller'\
                   and r.Address == str(ctrller_addr)]
        if not len(ctrller) > 0:
            raise Exception(
                ('Cannot find Emulated IDE Controller at controller\
                    address %(ctrller_addr)s for %(ctrller_addr)s') % locals())
        #Find the default disk drive object for the vm and clone it.
        diskdflt = self._conn.query(
                "SELECT * FROM Msvm_ResourceAllocationSettingData \
                WHERE ResourceSubType LIKE '%(drive_type)s'\
                AND InstanceID LIKE '%%Default%%'" % locals())[0]
        if diskdflt is None:
            raise Exception('Failed to find Msvm_ResourceAllocationSettingData')
        diskdrive = self._clone_wmi_obj(
                'Msvm_ResourceAllocationSettingData', diskdflt)
        #Set the IDE ctrller as parent.
        diskdrive.Parent = ctrller[0].path_()
        diskdrive.Address = 0
        #LOG.debug('For disk, parent is %s, and drive address is %s'
        #        % (ctrller[0].path_(), diskdrive.Address))
        
        #Add the cloned disk drive object to the vm.
        new_resources, result_msg = self._add_virt_resource(diskdrive, vm)
        if new_resources is None:
            raise Exception(_('Failed to add diskdrive to VM %s, feedback was %s', ) %
                    vm_name, result_msg)
        diskdrive_path = new_resources[0]
        LOG.debug(_('New %(drive_type)s drive path is %(diskdrive_path)s') %
            locals())

        # Update to allow ISO-type devices to be attached
        if drive_type == IDE_DISK:
            resSubType = 'Microsoft Virtual Hard Disk'
        elif drive_type == IDE_DVD:
            resSubType = 'Microsoft Virtual CD/DVD Disk'
        
        #Unless there is an ISO to put the in drive, we are done.
        if (vhdfile is None):
            LOG.debug(resSubType + ' requires no disk to be added to drive, we are done')
            return

        #Find the default VHD disk object.
        vhddefault = self._conn.query(
                "SELECT * FROM Msvm_ResourceAllocationSettingData \
                 WHERE ResourceSubType LIKE '%(resSubType)s' AND \
                 InstanceID LIKE '%%Default%%' " % locals())[0]

        #Clone the default and point it to the image file.
        vhddisk = self._clone_wmi_obj(
                'Msvm_ResourceAllocationSettingData', vhddefault)
        #Set the new drive as the parent.
        vhddisk.Parent = diskdrive_path
        vhddisk.Connection = [vhdfile]

        #Add the new vhd object as a virtual hard disk to the vm.
        new_resources, result_msg = self._add_virt_resource(vhddisk, vm)
        if new_resources is None:
            raise Exception(_('Failed to add %(drive_type)s image to VM %(vm_name)s, feedback was %(result_msg)s') %
                    locals())
        LOG.info(_('Created disk for %s'), vm_name)

    # TODO:  changed from emulated to sythetic NIC for performance reasons.
    #        is this the best solution?
    def _create_nic(self, vm_name, mac, vlan):
        """Create a (synthetic) nic and attach it to the vm"""
        LOG.debug(_('Creating nic for %s '), vm_name)
        #Find the vswitch that is connected to the physical nic.
        vms = self._conn.Msvm_ComputerSystem(ElementName=vm_name)
        extswitch = self._find_external_network()
        vm = vms[0]
        if extswitch is None:
            raise Exception(_('No vSwitch attached to external network'))
        switch_svc = self._conn.Msvm_VirtualSwitchManagementService()[0]
        #Find the default nic and clone it to create a new nic for the vm.
        #Use Msvm_SyntheticEthernetPortSettingData for Windows or Linux with
        #Linux Integration Components installed.
        syntheticnics_data = self._conn.Msvm_SyntheticEthernetPortSettingData()
        default_nic_data = [n for n in syntheticnics_data
                            if n.InstanceID.rfind('Default') > 0]
        new_nic_data = self._clone_wmi_obj(
                'Msvm_SyntheticEthernetPortSettingData',
                default_nic_data[0])
        #Create a port on the vswitch.
        (new_port, ret_val) = switch_svc.CreateSwitchPort(
            Name=str(uuid.uuid4()),
            FriendlyName=vm_name,
            ScopeOfResidence="",
            VirtualSwitch=extswitch.path_())
        if ret_val != 0:
            LOG.error(_('Failed creating a port on the external vswitch'))
            raise Exception(_('Failed creating port for %s'),
                    vm_name)
            
        # if we need a vlan, get and set the VLANEndpointSettingData
        if vlan is not None:
            LOG.debug(_('Setting VLAN to %s'), vlan)
            # new_port is a reference, and not the object.
            switches = self._conn.Msvm_SwitchPort()
            found = False
            for switch in switches:
                if switch.path_() == new_port:
                    LOG.debug(_('Found switch port'))
                    found = True
                    break
            
            if (not found):
                errmsg = 'Failed to find switch port for %s' % new_port
                LOG.error(errmsg)
                raise Exception(_('Failed creating port for %s'),
                    vm_name)
                    
            vlansettings =switch.associators(wmi_result_class='Msvm_VLANEndpoint')[0]\
                .associators(wmi_result_class='Msvm_VLANEndpointSettingData')[0]
            vlansettings.AccessVLAN = vlan
        
        ext_path = extswitch.path_()
        LOG.debug(_("Created switch port %(vm_name)s on switch %(ext_path)s")
                % locals())
        #Connect the new nic to the new port.
        new_nic_data.Connection = [new_port]
        new_nic_data.ElementName = vm_name + ' nic'
        new_nic_data.Address = ''.join(mac.split(':'))
        new_nic_data.StaticMacAddress = 'TRUE'
        new_nic_data.VirtualSystemIdentifiers = ['{' + str(uuid.uuid4()) + '}']
        #Add the new nic to the vm.
        new_resources, result_msg = self._add_virt_resource(new_nic_data, vm)
        if new_resources is None:
            raise Exception(_('Failed to add nic to VM %(vm_name)s, feedback was %(result_msg)s', ) %
                    locals())
        LOG.info(_("Created nic for %s "), vm_name)

    def _add_virt_resource(self, res_setting_data, target_vm):
        """Add a new resource (disk/nic) to the VM"""
        vs_man_svc = self._conn.Msvm_VirtualSystemManagementService()[0]
        # Multiple results returned due to [OUT] parameters on 
        # AddVirtualSystemResources, but notice the order!
        (job, new_resources, ret_val) = vs_man_svc.\
                    AddVirtualSystemResources([res_setting_data.GetText_(1)], target_vm.path_())
        success = True
        if ret_val == WMI_JOB_STATUS_STARTED:
            success, msg = self._check_job_status(job)
        else:
            success = (ret_val == 0)
            msg = "Return value was %(ret_val)s" % locals()
        if success:
            return new_resources, msg
        else:
            return None, msg

    #TODO: use the reactor to poll instead of sleep
    def _check_job_status(self, jobpath):
        """Poll WMI job state for completion"""
        #Jobs have a path of the form:
        #\\WIN-P5IG7367DAG\root\virtualization:Msvm_ConcreteJob.InstanceID=
        #"8A496B9C-AF4D-4E98-BD3C-1128CD85320D"
        inst_id = jobpath.split('=')[1].strip('"')
        jobs = self._conn.Msvm_ConcreteJob(InstanceID=inst_id)
        if len(jobs) == 0:
            return False
        job = jobs[0]
        while job.JobState == WMI_JOB_STATE_RUNNING:
            time.sleep(0.1)
            job = self._conn.Msvm_ConcreteJob(InstanceID=inst_id)[0]
        if job.JobState != WMI_JOB_STATE_COMPLETED:
            err_msg = "WMI job failed: %s" % job.ErrorSummaryDescription
            LOG.debug(err_msg)
            return (False, err_msg)
        desc = job.Description
        elap = job.ElapsedTime
        result_msg = "WMI job succeeded: %(desc)s, Elapsed=%(elap)s" % locals()
        return (True, result_msg)

    def _find_external_network(self):
        """Find the vswitch that is connected to the physical nic.
           Assumes only one physical nic on the host
        """
        #If there are no physical nics connected to networks, return.
        bound = self._conn.Msvm_ExternalEthernetPort(IsBound='TRUE')
        if len(bound) == 0:
            LOG.debug(_("No vSwitch available"))
            return None
        return self._conn.Msvm_ExternalEthernetPort(IsBound='TRUE')[0]\
            .associators(wmi_result_class='Msvm_SwitchLANEndpoint')[0]\
            .associators(wmi_result_class='Msvm_SwitchPort')[0]\
            .associators(wmi_result_class='Msvm_VirtualSwitch')[0]

    def _clone_wmi_obj(self, wmi_class, wmi_obj):
        """Clone a WMI object"""
        cl = self._conn.__getattr__(wmi_class)  # get the class
        newinst = cl.new()
        #Copy the properties from the original.
        for prop in wmi_obj._properties:
            if prop == "VirtualSystemIdentifiers":
                strguid = []
                strguid.append(str(uuid.uuid4()))
                newinst.Properties_.Item(prop).Value = strguid
            else:
                newinst.Properties_.Item(prop).Value = \
                    wmi_obj.Properties_.Item(prop).Value
        return newinst

    def reboot(self, instance, network_info):
        """Reboot the specified instance."""
        vm = self._lookup(instance["name"])
        if vm is None:
            raise Exception(_('InstanceNotFound %s') %
                instance["id"])
        self._set_vm_state(instance["name"], 'Reboot')

    # TODO:  delete the NICs as well!
    def destroy(self, instance, network_info, cleanup=True):
        """Destroy the VM. Do not destroy the associated VHD disk files"""
        LOG.debug(_("Got request to destroy vm %s"), instance['name'])
        vm = self._lookup(instance['name'])
        if vm is None:
            LOG.debug(_("No such VM destroy, vm %s"), instance['name'])
            return
        vm = self._conn.Msvm_ComputerSystem(ElementName=instance['name'])[0]
        vs_man_svc = self._conn.Msvm_VirtualSystemManagementService()[0]
        #Stop the VM first.
        LOG.debug(_("Stop vm %s"), instance['name'])
        self._set_vm_state(instance['name'], 'Disabled')
        #Nuke the VM. Does not destroy disks.
        LOG.debug(_("Destroy vm %s, but not volumes"), instance['name'])
        (job, ret_val) = vs_man_svc.DestroyVirtualSystem(vm.path_())
        if ret_val == WMI_JOB_STATUS_STARTED:
            success = self._check_job_status(job)
        elif ret_val == 0:
            success = True
        if not success:
            raise Exception(_('Failed to destroy vm %s') % instance['name'])

    def destroy_volume(self, volume, vmname):
        #Delete  vhd disk files
        disk = volume["path"]

        LOG.debug(_("delete volume [%s]"), disk)
        if vmname is not None:
            self.detach_volume(vmname, disk)
        try:
            os.remove(disk)
        except Exception as e:
            LOG.debug(_("Cannot delete [%s] due to %s"), locals())
            raise e

    def get_info(self, instance_id):
        """
        Get information about the VM

        Sample output:
            E.g. sample output:
        {"TestCentOS6.3":{"cpuUtilization":69.0,"networkReadKBs":69.9,"networkWriteKBs":69.9,"numCPUs":1,"entityType":"vm"}},"result":true,"contextMap":{},"wait":0}
        """
        LOG.debug("get_info called for instance %s" % instance_id)
        vm = self._lookup(instance_id)
        if vm is None:
            errMsg = 'InstanceNotFound %s' % instance_id
            LOG.debug(errMsg)
            raise Exception(errMsg)
            
        vm = self._conn.Msvm_ComputerSystem(ElementName=instance_id)[0]
        vs_man_svc = self._conn.Msvm_VirtualSystemManagementService()[0]
        vmsettings = vm.associators(
                       wmi_result_class='Msvm_VirtualSystemSettingData')
        settings_paths = [v.path_() for v in vmsettings]
        #See http://msdn.microsoft.com/en-us/library/cc160706%28VS.85%29.aspx
        summary_info = vs_man_svc.GetSummaryInformation(
                                       [4, 100, 103, 105, 101, 111],
                                            settings_paths)[1]

        info = summary_info[0]
        state = str(info.EnabledState) 
        memusage = str(info.MemoryUsage)
        numprocs = str(info.NumberOfProcessors)
        uptime = str(info.UpTime)
        cpu_utilization = str(info.ProcessorLoad)

        LOG.debug(_("Got Info for vm %(instance_id)s: state=%(state)s,"
                " mem=%(memusage)s, num_cpu=%(numprocs)s,"
                " cpu_time=%(uptime)s, cpu_utilization=%(cpu_utilization)s"), locals())

        return {'state': state,
                'max_mem': info.MemoryUsage,
                'mem': info.MemoryUsage,
                'num_cpu': info.NumberOfProcessors,
                'cpu_utilization' : info.ProcessorLoad,
                'cpu_time': info.UpTime}

    def _lookup(self, i):
        vms = self._conn.Msvm_ComputerSystem(ElementName=i)
        n = len(vms)
        if n == 0:
            return None
        elif n > 1:
            raise Exception(_('duplicate name found: %s') % i)
        else:
            return vms[0].ElementName

    def _lookup_detail(self, i):
        vms = self._conn.Msvm_ComputerSystem(ElementName=i)
        n = len(vms)
        if n == 0:
            return None
        else:
            return vms[0]

    def _lookup_details_multiple(self, i):
        vms = self._conn.Msvm_ComputerSystem(ElementName=i)
        n = len(vms)
        if n == 0:
            return None
        else:
            return vms

    # update to make callers use an enumeration rather string literals
    def _set_vm_state(self, vm_name, req_state):
        """Set the desired state of the VM"""
        vms = self._conn.Msvm_ComputerSystem(ElementName=vm_name)
        if len(vms) == 0:
            return False
        (job, ret_val) = vms[0].RequestStateChange(REQ_POWER_STATE[req_state])
        success = False
        if ret_val == WMI_JOB_STATUS_STARTED:
            success = self._check_job_status(job)
        elif ret_val == 0:
            success = True
        elif ret_val == 32775:
            #Invalid state for current operation. Typically means it is
            #already in the state requested
            success = True
        if success:
            LOG.info(_("Successfully changed vm state of %(vm_name)s"
                    " to %(req_state)s") % locals())
        else:
            msg = _("Failed to change vm state of %(vm_name)s"
                    " to %(req_state)s") % locals()
            LOG.error(msg)
            raise Exception(msg)

    def attach_volume(self, instance_name, device_path, mountpoint):
        vm = self._lookup(instance_name)
        if vm is None:
            raise Exception(_("No such vm %s" % instance_name))

    def detach_volume(self, instance_name, mountpoint):
        vm = self._lookup(instance_name)
        if vm is None:
            raise Exception(_("No such vm %s" % instance_name))

    def poll_rescued_instances(self, timeout):
        pass

    def update_available_resource(self, ctxt, host):
        """This method is supported only by libvirt."""
        return

    def update_host_status(self):
        """See xenapi_conn.py implementation."""
        pass

    def get_host_stats(self, refresh=False):
        """See xenapi_conn.py implementation."""
        pass

    def host_power_action(self, host, action):
        """Reboots, shuts down or powers up the host."""
        pass

    def set_host_enabled(self, host, enabled):
        """Sets the specified host's ability to accept new instances."""
        pass
