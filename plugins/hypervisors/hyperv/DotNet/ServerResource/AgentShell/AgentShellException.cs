using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace CloudStack.Plugin.AgentShell
{
    class AgentShellException : Exception
    {
        public AgentShellException(string errMsg) : base(errMsg) { }
    }
}
