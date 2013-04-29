using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Web.Http;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using HypervResource;

namespace ServerResource.Tests.Controllers
{
    //[TestClass]
    public class HypervResourceControllerTest
    {
        //[TestMethod]
        public void Post()
        {
            // Arrange
            HypervResourceController controller = new HypervResourceController();

            // Act
            controller.Post("value");

            // Assert
        }
    }
}
