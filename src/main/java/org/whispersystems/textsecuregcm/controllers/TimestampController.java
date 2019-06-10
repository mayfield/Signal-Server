// vim: ts=2:sw=2:expandtab

package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.whispersystems.textsecuregcm.storage.Account;


@Path("/v1/timestamp")
public class TimestampController {

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getTimestamp(@Auth Account account) {
    return Response.ok().entity(System.currentTimeMillis()).build();
  }
}
