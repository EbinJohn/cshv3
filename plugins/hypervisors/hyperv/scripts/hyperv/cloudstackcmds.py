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

import hyperv
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

def CreateCommand(cmdData, opsObj):
    """
    Create new volume
    """
    try:
        volume = opsObj.create_volume(cmdData)
        answer = {"result":"true",
                  "volume":volume}
    except Exception as e:
        LOG.debug('CreateCommand %s failed with msg %s' % 
                  (cmdData, str(e)))
        answer = {"result":"false", "details": json.dumps(str(e)) }
    return answer

def DestroyCommand(cmdData, opsObj):
    """
    Delete a volume.
    """
    try:
        # Detach before you destroy
        # todo, cause excpetion by changing second arg to cmdData["vmName"]
        opsObj.destroy_volume(cmdData["volume"], None)
        answer = {"result":"true",
                  "details":"success" }
    except Exception as e:
        LOG.debug('DestroyCommand %s failed with msg %s' % 
                  (cmdData, str(e)))
        answer = {"result":"false", "details": json.dumps(str(e)) }
    return answer

def StopCommand(cmdData, opsObj):
    """
    Delete VM and NICs so that these resources are released.
    Leave volumes alone, as they are managed separately.
    """
    try:
        instance = {}
        instance["name"] = cmdData["vmName"] 
        opsObj.destroy(instance, None)
        answer = {"result":"true",
                  "details":"success",
               "wait":0 }
    except Exception as e:
        LOG.debug('StopCommand %s failed with msg %s' % 
                  (cmdData, str(e)))
        answer = {"result":"false", "details": json.dumps(str(e)) }
    return answer

def StartCommand(cmdData, opsObj):
    """
    Create new VM based on specification that includes disks and nics to attach
    """
    try:
        opsObj.spawn(cmdData)
        answer = {"result":"true" }
    except Exception as e:
        LOG.debug('StartCommand %s failed with msg %s' % 
                  (cmdData, str(e)))
        answer = {"result":"false", "details": json.dumps(str(e)) }
    return answer

def GetVmStatsCommand(cmdData, opsObj):
    """
    Generates GetVmStatsAnswer JSON corresponding to a GetVmStatsCommand
    passed via stdin in a JSON format.
    """
    answers = {}
    for vmName in cmdData['vmNames']:
        # todo: get_info can throw if the instance is not found.  Catch.  How should this affect the resulting answer?
        try:
            vmInfo=opsObj.get_info(vmName)
        except Exception:
            LOG.debug(vmName + ' cannot be found: ')
            continue
            
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
    try:
        if not ARGS.test:
            cmdData = parseCommandData(sys.stdin)
    except Exception as e:
        LOG.error('Error parsing command: %s' % (str(e)))
        raise e

    try:
        LOG.debug('Create driver ')
        opsObj = hyperv.get_connection()
    except Exception as e:
        LOG.error('Error creating driver: %s' % (str(e)))
        raise e

    try:
        LOG.debug('Calling method ' + args.command)
        answer = globals()[args.command](cmdData, opsObj)
    except Exception as e:
        LOG.error('Error calling driver: %s' % (str(e)))
        raise e
        
    serialiseAnswerData(sys.stdout, answer)

if __name__ == '__main__':
    DispatchCmd(ARGS)
    sys.exit(0)
    