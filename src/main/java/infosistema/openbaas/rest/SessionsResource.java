package infosistema.openbaas.rest;

import infosistema.openbaas.middleLayer.SessionMiddleLayer;
import infosistema.openbaas.middleLayer.UsersMiddleLayer;
import infosistema.openbaas.rest.AppResource.PATCH;
import infosistema.openbaas.utils.Const;
import infosistema.openbaas.utils.Log;
import infosistema.openbaas.utils.Utils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;


//@Path(/users/{userId}/sessions)
public class SessionsResource {

	private UsersMiddleLayer usersMid;
	private SessionMiddleLayer sessionMid;
	private String appId;
	private String userId;
	

	@Context
	UriInfo uriInfo;

	public SessionsResource(String appId, String userId) {
		this.usersMid = UsersMiddleLayer.getInstance();
		this.sessionMid = SessionMiddleLayer.getInstance();
		this.appId = appId;
		this.userId = userId;
	}

	// *** CREATE *** //
	
	/**
	 * Creates a user session and returns de session Identifier (generated by
	 * the server). Required fields: "userName", "password".
	 * 
	 * @param req
	 * @param inputJsonObj
	 * @return
	 */
	@POST
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	public Response createSession(@Context HttpServletRequest req,
			JSONObject inputJsonObj, @Context UriInfo ui, @Context HttpHeaders hh) {
		String userName = null; // user inserted fields
		String attemptedPassword = null; // user inserted fields
		Response response = null;
		try {
			userName = (String) inputJsonObj.get("userName");
			attemptedPassword = (String) inputJsonObj.get("password");
		} catch (JSONException e) {
			Log.error("", this, "createSession", "Error parsing the JSON.", e); 
			return Response.status(Status.BAD_REQUEST).entity("Error reading JSON").build();
		}
		if(userName == null && attemptedPassword == null)
			return Response.status(Status.BAD_REQUEST).entity("Error reading JSON").build();
		String userId = usersMid.getUserIdUsingUserName(appId, userName);
		if (userId != null) {
			boolean usersConfirmedOption = usersMid.getConfirmUsersEmailOption(appId);
			// Remember the order of evaluation in java
			if (usersConfirmedOption) {
				if (usersMid.userEmailIsConfirmed(appId, userId)) {
					Log.debug("", this, "createSession", "userId of " + userName + " is: " + userId);
					String sessionToken = Utils.getRandomString(Const.getIdLength());
					boolean validation = sessionMid.createSession(sessionToken, appId, userId, attemptedPassword);
					if (validation) {
						NewCookie identifier = new NewCookie(Const.SESSION_TOKEN, sessionToken);

						response = Response.status(Status.OK).entity(identifier).build();
					}
					response = Response.status(Status.OK).entity(sessionToken).build();
				} else {
					response = Response.status(Status.FORBIDDEN).entity(Const.getEmailConfirmationError()).build();
				}
			} else
				response = Response.status(Status.UNAUTHORIZED).entity("").build();
		} else
			response = Response.status(Status.NOT_FOUND).entity("").build();
		return response;

	}


	// *** UPDATE *** //
	
	@PATCH
	@Path("{sessionToken}")
	@Consumes({ MediaType.APPLICATION_JSON })
	public Response patchSession( @HeaderParam(Const.USER_AGENT) String userAgent, @HeaderParam(value = Const.LOCATION) String location,
			@PathParam(Const.SESSION_TOKEN) String sessionToken, @CookieParam(value = Const.SESSION_TOKEN) String sessionTokenCookie) {
		Response response = null;
		if (sessionMid.sessionTokenExists(sessionToken)) {
			if (sessionMid.sessionExistsForUser(userId)) {
				if (location != null) {
					sessionMid.refreshSession(sessionToken, location, userAgent);
					response = Response.status(Status.OK).entity("").build();
				} // if the device does not have the gps turned on we should not
					// refresh the session.
					// only refresh it when an action is performed.
			}
			Response.status(Status.NOT_FOUND).entity(sessionToken).build();
		} else
			response = Response.status(Status.FORBIDDEN).entity("You do not have permission to access.").build();
		return response;
	}

	// *** DELETE *** //
	
	/**
	 * Deletes a session (logout).
	 * 
	 * @param sessionToken
	 * @return
	 */
	@DELETE
	@Path("{sessionToken}")
	public Response deleteSession(@PathParam(Const.SESSION_TOKEN) String sessionToken) {
		Response response = null;
		if (sessionMid.deleteUserSession(sessionToken, userId))
			response = Response.status(Status.OK).entity(sessionToken).build();
		else
			response = Response.status(Status.NOT_FOUND).entity(sessionToken).build();
		return response;
	}

	/**
	 * Deletes all the sessions of the user (a user can have more than one
	 * session, one for chrome, another for iphone, ect).
	 * 
	 * @return
	 */
	@DELETE
	@Path("/all")
	public Response deleteAllSessions(@CookieParam(value = Const.SESSION_TOKEN) String sessionToken) {
		Response response = null;
		if (sessionMid.sessionTokenExists(sessionToken)) {
			Log.debug("", this, "deleteAllSession", "********DELETING ALL SESSIONS FOR THIS USER");
			boolean sucess = sessionMid.deleteAllUserSessions(userId);
			if (sucess)
				response = Response.status(Status.OK).entity(userId).build();
			else
				response = Response.status(Status.NOT_FOUND).entity("No sessions exist").build();
		} else
			response = Response.status(Status.FORBIDDEN).entity("").build();

		return response;
	}


	// *** GET LIST *** //
	
	// *** GET *** //
	
	/**
	 * Gets the session fields associated with the token.
	 * 
	 * @param sessionToken
	 * @return
	 */
	@GET
	@Path("{sessionToken}")
	public Response getSessionFields( @PathParam(Const.SESSION_TOKEN) String sessionToken,
			@CookieParam(value = Const.SESSION_TOKEN) String sessionTokenCookie) {
		Response response = null;
		if (sessionMid.sessionTokenExists(sessionTokenCookie)) {
			response = Response.status(Status.OK).entity(sessionToken).build();
		} else
			response = Response.status(Status.NOT_FOUND).entity(sessionToken).build();
		return response;
	}

	
	// *** OTHERS *** //
	
	// *** RESOURCES *** //

}
