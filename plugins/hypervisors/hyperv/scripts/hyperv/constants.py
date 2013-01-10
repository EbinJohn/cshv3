# vim: tabstop=4 shiftwidth=4 softtabstop=4

# Copyright 2012 Citrix Systems R&D UK Ltd
# Copyright 2012 Cloudbase Solutions Srl
# All Rights Reserved.
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
Constants used in ops classes
"""

import power_state

HYPERV_VM_STATE_ENABLED = 2
HYPERV_VM_STATE_DISABLED = 3
HYPERV_VM_STATE_REBOOT = 10
HYPERV_VM_STATE_RESET = 11
HYPERV_VM_STATE_PAUSED = 32768
HYPERV_VM_STATE_SUSPENDED = 32769

class VmPowerState:
    # VM is offline and not using any resources
    HALTED = HYPERV_VM_STATE_DISABLED
    # Running
    RUNNING = HYPERV_VM_STATE_ENABLED,
    # All resources have been allocated but the VM itself is paused and its vCPUs are not running
    PAUSED = HYPERV_VM_STATE_PAUSED,
    # VM state has been saved to disk and it is nolonger running. Note that disks remain in-use while the VM is suspended.
    SUSPENDED = HYPERV_VM_STATE_SUSPENDED
    # The value does not belong to this enumeration
    UNRECOGNIZED = None

HYPERV_POWER_STATE = {
    HYPERV_VM_STATE_ENABLED: VmPowerState.RUNNING,
    HYPERV_VM_STATE_DISABLED: VmPowerState.SUSPENDED,
    HYPERV_VM_STATE_PAUSED: VmPowerState.PAUSED,
    HYPERV_VM_STATE_SUSPENDED: VmPowerState.HALTED
}


REQ_POWER_STATE = {
    'Enabled': HYPERV_VM_STATE_ENABLED,
    'Disabled': HYPERV_VM_STATE_DISABLED,
    'Reboot': HYPERV_VM_STATE_REBOOT,
    'Reset': HYPERV_VM_STATE_RESET,
    'Paused': HYPERV_VM_STATE_PAUSED,
    'Suspended': HYPERV_VM_STATE_SUSPENDED,
}


WMI_WIN32_PROCESSOR_ARCHITECTURE = {
    0: 'x86',
    1: 'MIPS',
    2: 'Alpha',
    3: 'PowerPC',
    5: 'ARM',
    6: 'Itanium-based systems',
    9: 'x64',
}

PROCESSOR_FEATURE = {
    7: '3dnow',
    3: 'mmx',
    12: 'nx',
    9: 'pae',
    8: 'rdtsc',
    20: 'slat',
    13: 'sse3',
    21: 'vmx',
    6: 'sse',
    10: 'sse2',
    17: 'xsave',
}

WMI_JOB_STATUS_STARTED = 4096
WMI_JOB_STATE_RUNNING = 4
WMI_JOB_STATE_COMPLETED = 7

VM_SUMMARY_NUM_PROCS = 4
VM_SUMMARY_ENABLED_STATE = 100
VM_SUMMARY_PROCESSOR_LOAD = 101
VM_SUMMARY_PROCESSOR_LOAD_HISTORY = 102
VM_SUMMARY_MEMORY_USAGE = 103
VM_SUMMARY_UPTIME = 105

IDE_DISK = "VHD"
IDE_DVD = "DVD"
