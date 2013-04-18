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

namespace CloudStack.Plugin.AgentShell
{
    public partial class Service1 : ServiceBase
    {
        [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
        static extern bool AllocConsole();

        [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
        static extern bool FreeConsole();

        HttpSelfHostServer server;

        private static ILog logger = LogManager.GetLogger(typeof(Service1));

        public Service1()
        {
            InitializeComponent();

            this.ServiceName = "CloudStack ServerResource";

            // TODO:  Update to use configuration
            Uri baseUri = new Uri("http://localhost:8080");

            var config = new HttpSelfHostConfiguration(baseUri);

            // TODO:  Update to retrieve from ServerResource
            config.Routes.MapHttpRoute(
                "API Default", "api/{controller}/{id}",
                new { id = RouteParameter.Optional });

            // TODO: update to catch and log exceptions 
            server = new HttpSelfHostServer(config);

            // TODO: configure logging
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
