# Copyright 2012 Citrix Systems R&D UK Ltd
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
Build on Python logging facilities such that minimal logging is available
and configurable.
"""
import os
import logging
import logging.config
import logging.handlers

def _(msg):
    return msg

def getLogger(name='roots', version='unknown'):
    if name not in _loggers:
        _loggers[name] = logging.getLogger(name)
    return _loggers[name]

#Test ability to open/close tdhe config file.
logging.config.fileConfig(os.path.dirname(os.path.realpath(__file__)) + '\hypervlog.conf')
_loggers = {}
