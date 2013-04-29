using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Web.Http;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ServerResource;
using ServerResource.Controllers;

namespace ServerResource.Tests.Controllers
{
    [TestClass]
    public class HypervResourceControllerTest
    {
        [TestMethod]
        public void Post()
        {
            // Arrange
            VirtualMachineController controller = new VirtualMachineController();

            // Act
            controller.Post("value");

            // Assert
        }
    }
}
