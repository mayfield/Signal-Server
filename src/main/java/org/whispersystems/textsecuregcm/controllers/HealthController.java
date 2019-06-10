// vim: ts=2:sw=2:expandtab

package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;


@Path("/health")
public class HealthController {

  @Timed
  @GET
  public Response getHealth() {
    // TODO: Check databases and perform sanity checks.
    return Response.ok().build();
  }
}
