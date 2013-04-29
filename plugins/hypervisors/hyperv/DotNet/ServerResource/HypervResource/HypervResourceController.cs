using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Web.Http;

namespace HypervResource
{
    public class HypervResourceController : ApiController
    {
        public static void Initialize()
        {
        }

        // GET api/HypervResource
        // Useful for testing!
        //
        public IEnumerable<string> Get()
        {
            return new string[] { "value1", "value2" };
        }

        // POST api/HypervResource
        public void Post([FromBody]string value)
        {
            // TODO:  Dispatch according to command

        }
    }
}