/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opencastproject.terminationstate.endpoint.impl;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import org.opencastproject.terminationstate.api.TerminationStateService;
import org.opencastproject.terminationstate.endpoint.api.TerminationStateRestService;
import org.opencastproject.util.Log;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestParameter.Type;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import org.json.simple.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.LoggerFactory;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
@RestService(name = "terminationstateservice", title = "Termination State Service",
        abstractText = "When put into a termination 'wait' state, this service stops the node accepting further jobs,"
        + " and once any running jobs complete will change termination to 'ready'."
        + " NOTE: The service does not actually shut down the node or instance.",
        notes = {
          "All paths above are relative to the REST endpoint base (something like http://your.server/termination/)",
          "If the service is down or not working it will return a status 503, this means the the underlying service is "
          + "not working and is either restarting or has failed",
          "A status code 500 means a general failure has occurred which is not recoverable and was not anticipated. In "
          + "other words, there is a bug! You should file an error report with your server logs from the time when the "
          + "error occurred: <a href=\"https://github.com/opencast/opencast/issues\">Opencast Issue Tracker</a>"})
@Component(
    immediate = true,
    service = TerminationStateRestServiceImpl.class,
    property = {
        "service.description=Termination State Rest Service",
        "opencast.service.type=org.opencastproject.terminationstate.impl",
        "opencast.service.path=/termination"
    }
)
public class TerminationStateRestServiceImpl implements TerminationStateRestService {

  private static final Log logger = new Log(LoggerFactory.getLogger(TerminationStateRestServiceImpl.class));

  private TerminationStateService service;

  @Override
  @GET
  @Path("/state")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(
      name = "stateasjson",
      description = "Returns the Termination State as JSON. Possible termination states are none, wait and ready.",
      returnDescription = "A JSON representation of the termination state.",
      responses = {
          @RestResponse(
              responseCode = SC_OK,
              description = "A JSON representation of the termination state."
          ),
          @RestResponse(
              responseCode = SC_SERVICE_UNAVAILABLE,
              description = "The Termination State Service is disabled or unavailable"
          ),
      }
  )
  public Response getState() {
    if (service != null) {
      JSONObject json = new JSONObject();
      String state = service.getState().toString();
      json.put("state", state);
      return Response.ok(json.toJSONString()).build();
    } else {
      logger.error("TerminationStateService is not available");
      return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
    }
  }

  @Override
  @PUT
  @Path("/state")
  @RestQuery(
      name = "setstate",
      description = "Set the termination state. The only permissable values are 'none', 'wait' or 'ready'",
      returnDescription = "Whether the termination state was set successfully",
      restParameters = {
          @RestParameter(
              name = "state",
              type = Type.STRING,
              defaultValue = "wait",
              description = "The termination state, the only valid value is 'wait'",
              isRequired = true
          ),
      },
      responses = {
          @RestResponse(
              responseCode = SC_NO_CONTENT,
              description = "The node is preparing to terminate"
          ),
          @RestResponse(
              responseCode = SC_BAD_REQUEST,
              description = "The state was not valid"
          ),
          @RestResponse(
              responseCode = SC_SERVICE_UNAVAILABLE,
              description = "The Termination State Service is disabled or unavailable"
          ),
      }
  )
  public Response setState(@FormParam("state") String state) {
    if (service != null) {
      TerminationStateService.TerminationState tState;
      switch (state) {
        case "none":
          tState = TerminationStateService.TerminationState.NONE;
          break;
        case "wait":
          tState = TerminationStateService.TerminationState.WAIT;
          break;
        case "ready":
          tState = TerminationStateService.TerminationState.READY;
          break;
        default:
          logger.error("state {} is invalid", state);
          return Response.status(Response.Status.BAD_REQUEST).build();
      }

      service.setState(tState);
      return Response.noContent().build();
    }

    logger.error("AWS Autoscaling Termination State Serice is not available");
    return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
  }

  /**
   OSGI injection callback
   @param service termination state service instance
   */
  @Reference(
      target = "(&(vendor.name=opencast)(vendor.service=basic))"
  )
  public void setService(TerminationStateService service) {
    this.service = service;
  }
}
