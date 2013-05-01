using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Diagnostics;
using System.Linq;
using System.ServiceProcess;
using System.Text;
using System.Threading.Tasks;
using System.Web.Http.SelfHost;
using System.Web.Http;
using log4net;
using HypervResource;

namespace CloudStack.Plugin.AgentShell
{
    public partial class AgentService : ServiceBase
    {
        [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
        static extern bool AllocConsole();

        [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
        static extern bool FreeConsole();

        HttpSelfHostServer server;

        private static ILog logger = LogManager.GetLogger(typeof(AgentService));

        public AgentService()
        {
            InitializeComponent();

            UriBuilder baseUri = new UriBuilder("http", AgentShell.Default.private_ip_address, AgentShell.Default.port);

            var config = new HttpSelfHostConfiguration(baseUri.Uri);

           // Allow ActionName to be applied to methods in ApiController, which allows it to serve multiple POST URLs
            config.Routes.MapHttpRoute(
                  "API Default", "api/{controller}/{action}",
                  new { action = RouteParameter.Optional }
                    );

            // Load controller assemblies that we want to config to route to.
            // TODO:  Update to allow assembly to be specified in the settings file.
            HypervResourceController.Initialize();

            AssertControllerAssemblyAvailable(config, typeof(HypervResourceController), "Cannot load Controller of type" + typeof(HypervResourceController));

            server = new HttpSelfHostServer(config);
        }

        // TODO:  update to examine not the assembly resolver, but the list of available controllers themselves!
        private static bool AssertControllerAssemblyAvailable(HttpSelfHostConfiguration config, Type controllerType, string errorMessage)
        {
            var assemblies = config.Services.GetAssembliesResolver().GetAssemblies();
            foreach (var assembly in assemblies)
            {
                string name = assembly.GetName().Name;
                if (controllerType.Assembly.GetName().Name.Equals(name))
                {
                    logger.DebugFormat("Controller {0} is available", controllerType.Name);
                    return true;
                }
            }

            logger.Error(errorMessage);
            throw new AgentShellException(errorMessage);
        }

        protected override void OnStart(string[] args)
        {
            server.OpenAsync().Wait();
        }

        protected override void OnStop()
        {
            server.CloseAsync().Wait();
        }

        internal void RunConsole(string[] args)
        {
            OnStart(args);

            AllocConsole();

            Console.WriteLine("Service running ... press <ENTER> to stop");

            Console.ReadLine();

            FreeConsole();

            OnStop();
        }
    }
}
