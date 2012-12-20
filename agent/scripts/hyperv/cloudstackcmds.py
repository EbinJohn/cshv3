# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

"""
Top level script for CloudStack command execution.

Using Python rather than PowerShell allows us to use an existing Hyper-V cloud
driver for our work.  The WMI syntax is more consistent with a programming
language, and Python comes with a number of libraries to deal with parsing.

Put command being executed in parameters, use stdin to pass a JSON serialised
version of the object to this script.  If '--test' option is used, test data
will be used rather than stdin.

""" 
import sys
import json
import argparse
import textwrap

import exceptions
import vmops
import vmutils
import log as logging

PARSER = argparse.ArgumentParser()
PARSER.add_argument("--test", help="Use sample data for command data. Useful for testing.",
                    action="store_true")
PARSER.add_argument("command", help="CloudStack command being executed")
ARGS = PARSER.parse_args()



LOG = logging.getLogger('root')

# todo: remove stubs
volume_ops_stub = ""

def parseCommandData(fp):
    inputCmd = json.load(fp)
    LOG.debug('Read in JSON object' + json.dumps(inputCmd))
    return inputCmd

def serialiseAnswerData(fp, answer):
    LOG.debug("Call to " + ARGS.command + " returns " + json.dumps(answer))
    json.dump(answer, fp)

def CreateCommand(cmdData):
    """
    Generate StartAnswer JSON corresponding to a StartCommand
    pass via stdin in a JSON format.
    """
    if ARGS.test:
        sample = textwrap.dedent("""\
        {
        "disks":[
                {"id":6,"name":"E:\\\\Disks\\\\Disks","mountPoint":"FakeVolume",
        "path":"FakeVolume","size":0,"type":"ROOT","storagePoolType":"Filesystem",
        "storagePoolUuid":"5fe2bad3-d785-394e-9949-89786b8a63d2","deviceId":0},
                {"id":6,"name":"Hyper-V Sample1","size":0,"type":"ISO","storagePoolType":"ISO","deviceId":3}
                ]},"contextMap":{},"wait":0}
        """)
        cmdData = json.loads(sample)

    LOG.debug('StartCommand call with data %s' % json.dumps(cmdData))

    opsObj = vmops.VMOps(volume_ops_stub)

def StartCommand(cmdData):
    """
    Generate StartAnswer JSON corresponding to a StartCommand
    pass via stdin in a JSON format.
    """
    if ARGS.test:
        sample = textwrap.dedent("""\
        {"vm":{"id":6,"name":"i-2-6-VM","type":"User","cpus":1,"speed":500,
        "minRam":536870912,"maxRam":536870912,"arch":"x86_64",
        "os":"CentOS 6.0 (64-bit)","bootArgs":"","rebootOnCrash":false,
        "enableHA":false,"limitCpuUse":false,"vncPassword":"7e24c0da0e848ad4",
        "params":{},"uuid":"3ff475a7-0ee8-44d6-970d-64fe776beb92",
        "disks":[
                {"id":6,"name":"E:\\\\Disks\\\\Disks","mountPoint":"FakeVolume",
        "path":"FakeVolume","size":0,"type":"ROOT","storagePoolType":"Filesystem",
        "storagePoolUuid":"5fe2bad3-d785-394e-9949-89786b8a63d2","deviceId":0},
                {"id":6,"name":"Hyper-V Sample1","size":0,"type":"ISO","storagePoolType":"ISO","deviceId":3}
                ],
        "nics":[
                {"deviceId":0,"networkRateMbps":100,"defaultNic":true,"uuid":
        "e146bb95-4ee4-4b9f-8d61-62cb21f7224e","ip":"10.1.1.164","netmask":"255.255.255.0",
        "gateway":"10.1.1.1","mac":"02:00:67:06:00:04","dns1":"4.4.4.4","broadcastType":"Vlan",
        "type":"Guest","broadcastUri":"vlan://261","isolationUri":"vlan://261",
        "isSecurityGroupEnabled":false}
                ]
        },"contextMap":{},"wait":0}
        """)
        cmdData = json.loads(sample)

    LOG.debug('StartCommand call with data %s' % json.dumps(cmdData))

    opsObj = vmops.VMOps(volume_ops_stub)
    try:
        opsObj.spawn(cmdData)
        answer = {"result":"true",
               "wait":0 }
        return answer
    except vmutils.HyperVException as e:
        LOG.debug('StartCommand for %s failed with msg %s' % (cmdData["vm"]["name"], e))
        answer = {"result":"false",
                   "details": e }
    except Exception as e:
        LOG.debug('StartCommand for %s failed with msg %s' % (cmdData["vm"]["name"], e))
        answer = {"result":"false",
                   "details": e }

def GetVmStatsCommand(cmdData):
    """
    Generates GetVmStatsAnswer JSON corresponding to a GetVmStatsCommand
    passed via stdin in a JSON format.


    E.g. sample output:
    {"vmStatsMap":{"otherVM":{"cpuUtilization":100.0,"networkReadKBs":100.0,"networkWriteKBs":100.0,"numCPUs":2,"entityType":"vm"},
                   "TestCentOS6.3":{"cpuUtilization":69.0,"networkReadKBs":69.9,"networkWriteKBs":69.9,"numCPUs":1,"entityType":"vm"}
                   },"result":true,"contextMap":{},"wait":0}
    """
    if ARGS.test:
        sample = ('{"vmNames":["TestCentOS6.3"],"hostGuid":"1","hostName":"localhost","contextMap":{},"wait":0}')
        cmdData = json.loads(sample)

    LOG.debug('GetVmStatsCommand call with data ' + json.dumps(cmdData))

    opsObj = vmops.VMOps(volume_ops_stub)
    answers = {}
    for vmName in cmdData['vmNames']:
        # todo: get_info can throw if the instance is not found.  Catch.  How should this affect the resulting answer?
        vmInfo=opsObj.get_info(vmName)
        LOG.debug(vmName + ' info is ' + json.dumps(vmInfo))

        vmStat = {
                'cpuUtilization': 0 if not vmInfo['cpu_utilization'] else vmInfo['cpu_utilization'],
                'networkReadKBs': 100,
                'networkWriteKBs': 100,
                'numCPUs' : vmInfo['num_cpu'],
                'entityType': "vm"}
        answers[vmName] = vmStat

    result = { "vmStatsMap": answers,
               "result":"true" }
               
    return result

def DispatchCmd(args):
    cmdData = None
    if not ARGS.test:
        cmdData = parseCommandData(sys.stdin)
    answer = globals()[args.command](cmdData)
        
    serialiseAnswerData(sys.stdout, answer)

if __name__ == '__main__':
    DispatchCmd(ARGS)



