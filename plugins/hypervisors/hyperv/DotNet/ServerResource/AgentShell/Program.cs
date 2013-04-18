using log4net;
using System;
using System.Collections.Generic;
using System.Linq;
using System.ServiceProcess;
using System.Text;
using System.Threading.Tasks;

namespace CloudStack.Plugin.AgentShell
{
    static class Program
    {
        private static ILog logger = LogManager.GetLogger(typeof(Program));

        /// <summary>
        /// The main entry point for the application.
        /// </summary>
        static void Main(params string[] args)
        {
            string arg1 = string.Empty;

            if (args.Length > 0)
            {
                arg1 = args[0];
                logger.DebugFormat("CloudStack ServerResource arg is ", arg1);
            }

            if (string.Compare(arg1, "--console", true) == 0)
            {
                logger.InfoFormat("CloudStack ServerResource running as console app");
                new Service1().RunConsole(args);
            }
            else
            {
                logger.InfoFormat("CloudStack ServerResource running as Windows Service");
                ServiceBase[] ServicesToRun = new ServiceBase[] { new Service1() };
                ServiceBase.Run(ServicesToRun);
            }
        }
    }
}
