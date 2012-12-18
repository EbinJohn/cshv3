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

import vmops
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

def GetVmStatsCommand(cmdData):
    """
    Generates GetVmStatsAnswer JSON corresponding to a GetVmStatsCommand
    passed via stdin in a JSON format.

    E.g. sample input:
    {"vmNames":["TestCentOS6.3"],"hostGuid":"1","hostName":"localhost","contextMap":{},"wait":0}

    E.g. sample output:
    {"vmStatsMap":{"otherVM":{"cpuUtilization":100.0,"networkReadKBs":100.0,"networkWriteKBs":100.0,"numCPUs":2,"entityType":"vm"},
                   "TestCentOS6.3":{"cpuUtilization":69.0,"networkReadKBs":69.9,"networkWriteKBs":69.9,"numCPUs":1,"entityType":"vm"}
                   },"result":true,"contextMap":{},"wait":0}
    """
    if ARGS.test:
        sample = ('{"vmNames":["TestCentOS6.3"],"hostGuid":"1","hostName":"localhost","contextMap":{},"wait":0}')
        cmdData = json.loads(sample)

    LOG.debug('GetVmStatsAnswer call with data ' + json.dumps(cmdData))

    opsObj = vmops.VMOps(volume_ops_stub)
    answers = {}
    for vmName in cmdData['vmNames']:
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
               "result":"true",
               "wait":0 }
               
    return result

def DispatchCmd(args):
    cmdData = None
    if not ARGS.test:
        cmdData = parseCommandData(sys.stdin)
    answer = globals()[args.command](cmdData)
        
    serialiseAnswerData(sys.stdout, answer)

if __name__ == '__main__':
    DispatchCmd(ARGS)



