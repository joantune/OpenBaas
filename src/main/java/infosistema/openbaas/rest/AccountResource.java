/*****************************************************************************************
Infosistema - OpenBaas
Copyright(C) 2002-2014 Infosistema, S.A.
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.
You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
www.infosistema.com
info@openbaas.com
Av. José Gomes Ferreira, 11 3rd floor, s.34
Miraflores
1495-139 Algés Portugal
****************************************************************************************/
package infosistema.openbaas.rest;

import infosistema.openbaas.middleLayer.AppsMiddleLayer;
import infosistema.openbaas.middleLayer.SessionMiddleLayer;
import infosistema.openbaas.middleLayer.UsersMiddleLayer;
import infosistema.openbaas.comunication.bound.InboundSocket;
import infosistema.openbaas.data.Error;
import infosistema.openbaas.data.Metadata;
import infosistema.openbaas.data.Result;
import infosistema.openbaas.data.models.Application;
import infosistema.openbaas.data.models.User;
import infosistema.openbaas.rest.AppResource.PATCH;
import infosistema.openbaas.utils.Const;
import infosistema.openbaas.utils.Log;
import infosistema.openbaas.utils.Utils;
import infosistema.openbaas.utils.encryption.PasswordEncryptionService;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;


public class AccountResource {

	private UsersMiddleLayer usersMid;
	private SessionMiddleLayer sessionMid;
	private AppsMiddleLayer appsMid;
	private String appId;

	@Context
	UriInfo uriInfo;

	public AccountResource(String appId) {
		this.usersMid = UsersMiddleLayer.getInstance();
		this.appId = appId;
		this.sessionMid = SessionMiddleLayer.getInstance();
		this.appsMid = AppsMiddleLayer.getInstance();
	}

	// *** CREATE *** //

	/**
	 * Creates a user in the application, necessary fields: "password";
	 * and "email". sigup the user creating a session
	 * 
	 * @param inputJsonObj
	 * @return
	 */
	@Path("/signup")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createUserAndLogin(JSONObject inputJsonObj, @Context UriInfo ui, @Context HttpHeaders hh) {
		Response response = null;
		MultivaluedMap<String, String> headerParams = hh.getRequestHeaders();
		String email = null;
		String userName = null;
		String password = null;
		Boolean baseLocationOption = null;
		String baseLocation = null;
		String userFile = null;
		String appKey = null;
		Boolean readOk = false;
		String location = null;
		try {
			appKey = headerParams.getFirst(Application.APP_KEY);
		} catch (Exception e) { }
		try {
			location = headerParams.getFirst(Const.LOCATION);
		} catch (Exception e) { }
		if(appKey==null)
			return Response.status(Status.BAD_REQUEST).entity("App Key not found").build();
		if(!appsMid.authenticateApp(appId,appKey))
			return Response.status(Status.UNAUTHORIZED).entity("Wrong App Key").build();
		if(!appsMid.appExists(appId))
			return Response.status(Status.NOT_FOUND).entity("{\"App\": "+appId+"}").build();
		try {
			userName = (String) inputJsonObj.opt("userName");
			userFile = (String) inputJsonObj.opt("userFile");
			email = (String) inputJsonObj.get("email");
			password = (String) inputJsonObj.get("password");
			baseLocationOption = (Boolean) inputJsonObj.opt("baseLocationOption");
			if (baseLocationOption == null) baseLocationOption=false;
			baseLocation = (String) inputJsonObj.opt("baseLocation");
			readOk = true;
		} catch (JSONException e) {
			Log.error("", this, "createUserAndLogin", "Error parsing the JSON.", e); 
			return Response.status(Status.BAD_REQUEST).entity("Error parsing the JSON.").build();
		}
		if (readOk) {
			if (!usersMid.userEmailExists(appId, email)) {
				if (uriInfo == null) uriInfo = ui;
				Result res = usersMid.createUserAndLogin(headerParams, ui,appId, userName, email, password, userFile, baseLocationOption, baseLocation, Metadata.getNewMetadata(location));
				((User)res.getData()).setSocketPort(InboundSocket.getNextPort().toString());
				response = Response.status(Status.CREATED).entity(res).build();
			} else {
				response = Response.status(Status.FORBIDDEN).entity(new Error("email exists" +email)).build();
			}
		} else {
			response = Response.status(Status.BAD_REQUEST).entity(new Error("")).build();
		}
		return response;
	}

	/**
	 * Creates a user session and returns de session Identifier (generated by
	 * the server). Required fields: "email", "password".
	 * 
	 * @param req
	 * @param inputJsonObj
	 * @return
	 */
	@Path("/signin")
	@POST
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces(MediaType.APPLICATION_JSON)
	public Response createSession(@Context HttpServletRequest req, JSONObject inputJsonObj, @Context UriInfo ui, @Context HttpHeaders hh) {
		MultivaluedMap<String, String> headerParams = hh.getRequestHeaders();
		String email = null; // user inserted fields
		String attemptedPassword = null; // user inserted fields
		Response response = null;
		User outUser = new User();
		String userAgent = null;
		String location = null;
		String appKey = null;
		Boolean refreshCode = false;
		String lastLocation =null;
		
		try {
			email = (String) inputJsonObj.get("email");
			attemptedPassword = (String) inputJsonObj.get("password");
		} catch (JSONException e) {
			Log.error("", this, "createSession", "Error parsing the JSON.", e); 
			return Response.status(Status.BAD_REQUEST).entity(new Error("Error reading JSON")).build();
		}
		try {
			location = headerParams.getFirst(Const.LOCATION);
		} catch (Exception e) { }
		try {
			userAgent = headerParams.getFirst(Const.USER_AGENT);
		} catch (Exception e) { }
		try {
			appKey = headerParams.getFirst(Application.APP_KEY);
		} catch (Exception e) { }
		if(appKey==null)
			return Response.status(Status.BAD_REQUEST).entity("App Key not found").build();
		if(!appsMid.authenticateApp(appId,appKey))
			return Response.status(Status.UNAUTHORIZED).entity("Wrong App Key").build();
		if(email == null && attemptedPassword == null)
			return Response.status(Status.BAD_REQUEST).entity("Error reading JSON").build();
		Result res = usersMid.getUserUsingEmail(appId, email);
		outUser = (User)res.getData();
		if (outUser != null && outUser.get_id() != null) {
			boolean usersConfirmedOption = usersMid.getConfirmUsersEmailOption(appId);
			// Remember the order of evaluation in java
			if (usersConfirmedOption) {
				if (usersMid.userEmailIsConfirmed(appId, outUser.get_id())) {
					String sessionToken = Utils.getRandomString(Const.getIdLength());
					boolean validation = sessionMid.createSession(sessionToken, appId, outUser.get_id(), attemptedPassword);
					sessionMid.refreshSession(sessionToken, location, userAgent);
					lastLocation = usersMid.updateUserLocation(outUser.get_id(), appId, location, Metadata.getNewMetadata(location));
					if(lastLocation==null)
						lastLocation = outUser.getLocation();
					refreshCode = true;
					if (validation && refreshCode) {
						outUser.set_id(outUser.get_id());
						outUser.setReturnToken(sessionToken);
						outUser.setEmail(email);
						outUser.setUserName(outUser.getUserName());
						outUser.setUserFile(outUser.getUserFile());
						outUser.setBaseLocation(outUser.getBaseLocation());
						outUser.setBaseLocationOption(outUser.getBaseLocationOption());
						outUser.setLocation(lastLocation);
						outUser.setOnline("true");
						outUser.setSocketPort(InboundSocket.getNextPort().toString());
						response = Response.status(Status.OK).entity(res).build();
					}
				} else {
					response = Response.status(Status.FORBIDDEN).entity(new Error(Const.getEmailConfirmationError())).build();
				}
			} else {
				String sessionToken = Utils.getRandomString(Const.getIdLength());
				boolean validation = sessionMid.createSession(sessionToken, appId, outUser.get_id(), attemptedPassword);
				if(validation){
					refreshCode = sessionMid.refreshSession(sessionToken, location, userAgent);
					lastLocation = usersMid.updateUserLocation(outUser.get_id(), appId, location, Metadata.getNewMetadata(location));
					if (validation && refreshCode) {
						outUser.set_id(outUser.get_id());
						outUser.setReturnToken(sessionToken);
						outUser.setEmail(email);
						outUser.setUserName(outUser.getUserName());
						outUser.setUserFile(outUser.getUserFile());
						outUser.setBaseLocation(outUser.getBaseLocation());
						outUser.setBaseLocationOption(outUser.getBaseLocationOption());
						outUser.setLocation(lastLocation);
						outUser.setOnline("true");
						outUser.setSocketPort(InboundSocket.getNextPort().toString());
						response = Response.status(Status.OK).entity(res).build();
					}
				} else {
					response = Response.status(Status.UNAUTHORIZED).entity(new Error("")).build();
				}
			}
		} else {
			response = Response.status(Status.NOT_FOUND).entity(new Error("")).build();
		}
		return response;
	}


	// *** UPDATE *** //

	@PATCH
	@Path("/sessions/{sessionToken}")
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	public Response patchSession( @HeaderParam(Const.USER_AGENT) String userAgent, @HeaderParam(Const.LOCATION) String location,
			@PathParam(Const.SESSION_TOKEN) String sessionToken) {
		Response response = null;
		if (sessionMid.sessionTokenExists(sessionToken)) {
			String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
			Result res = usersMid.getUserInApp(appId, userId);
			User user = (User)res.getData(); 
			if (!sessionMid.checkAppForToken(sessionToken, appId))
				return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
			if (location != null) {
				String lastLocation = usersMid.updateUserLocation(userId, appId, location, Metadata.getNewMetadata(location));
				user.setLocation(lastLocation);
				Metadata meta = (Metadata)res.getMetadata();
				meta.setLocation(lastLocation);
				sessionMid.refreshSession(sessionToken, location, userAgent);					
				response = Response.status(Status.OK).entity(res).build();
			}
			// if the device does not have the gps turned on we should not
			// refresh the session.
			// only refresh it when an action is performed.

			response = Response.status(Status.OK).entity(("SessionToken: "+sessionToken)).build();
		} else
			response = Response.status(Status.FORBIDDEN).entity(new Error("You do not have permission to access.")).build();
		return response;
	}


	// *** DELETE *** //

	/**
	 * Deletes a session (signout).
	 * 
	 * @param sessionToken
	 * @return@Context HttpHeaders hh
	 */
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	@POST
	@Path("/signout")
	public Response deleteSession(JSONObject inputJsonObj, @Context HttpHeaders hh) {
		Response response = null;
		String sessionToken = null;
		MultivaluedMap<String, String> headerParams = hh.getRequestHeaders();
		sessionToken = headerParams.getFirst(Const.SESSION_TOKEN);
		
		Boolean flagAll = (Boolean) inputJsonObj.optBoolean("all",false);
		String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
		if(userId!=null){
			if (sessionMid.sessionTokenExists(sessionToken)) {
				if(!flagAll){
					if (!sessionMid.checkAppForToken(sessionToken, appId))
						return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
					if (sessionMid.deleteUserSession(sessionToken, userId)){
						Result res = new Result("Signout OK", null);
						response = Response.status(Status.OK).entity(res).build();
					}
					else{
						response = Response.status(Status.NOT_FOUND).entity(new Error("Not found")).build();
					}
				}else{
					if (!sessionMid.checkAppForToken(sessionToken, appId))
						return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
					//deletes all sessions user
					boolean sucess = sessionMid.deleteAllUserSessions(userId);
					if (sucess){
						Result res = new Result("Signout OK", null);
						response = Response.status(Status.OK).entity(res).build();
					}
					else
						response = Response.status(Status.NOT_FOUND).entity(new Error("No sessions exist")).build();
				} 
			}
		}
		else 
			response = Response.status(Status.FORBIDDEN).entity(new Error("No user found")).build();		
		return response;
	}


	// *** GET LIST *** //


	// *** GET *** //

	/**
	 * Gets the session fields associated with thif (!sessionMid.checkAppForToken(sessionToken, appId))
				return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();e token.
	 * 
	 * @param sessionToken
	 * @return
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	@Path("/sessions/{sessionToken}")
	public Response getUserIdWithSession(
			@PathParam(Const.SESSION_TOKEN) String sessionToken) {
		Response response = null;
		if (sessionMid.sessionTokenExists(sessionToken)) {
			String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
			if (!sessionMid.checkAppForToken(sessionToken, appId))
				return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
			Result res = new Result(userId, null);
			response = Response.status(Status.OK).entity(res).build();
		} else
			response = Response.status(Status.NOT_FOUND).entity(new Error(sessionToken)).build();
		return response;
	}

	/**@HeaderParam(value = Const.LOCATION) String location
	 * Gets the session fields associated with the token.
	 * 
	 * @param sessionToken
	 * @return
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	@Path("/sessions")
	public Response getSessionFields(@Context HttpHeaders hh) {
		Response response = null;
		String sessionToken = null;
		MultivaluedMap<String, String> headerParams = hh.getRequestHeaders();
		sessionToken = headerParams.getFirst(Const.SESSION_TOKEN);
		if (sessionMid.sessionTokenExists(sessionToken)) {
			String userId 	= sessionMid.getUserIdUsingSessionToken(sessionToken);
			if (!sessionMid.checkAppForToken(sessionToken, appId))
				return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
			Result res = usersMid.getUserInApp(appId, userId);
			User outUser = (User)res.getData(); 
			outUser.setReturnToken(sessionToken);
			response = Response.status(Status.OK).entity(res).build();
		} else
			response = Response.status(Status.NOT_FOUND).entity(new Error("Token NOT_FOUND")).build();
		return response;
	}


	// *** OTHERS *** //

	@POST
	@Path("/recovery")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response makeRecoveryRequest(JSONObject inputJson, @Context UriInfo ui, @Context HttpHeaders hh,
			@HeaderParam(value = Const.LOCATION) String location, @HeaderParam(value = Application.APP_KEY) String appKey){
		Response response = null;
		String email = null;
		String newPass = Utils.getRandomString(Const.getPasswordLength());
		byte[] salt = null;
		byte[] hash = null;
		try {
			email = (String) inputJson.get("email");
		} catch (JSONException e) {
			Log.error("", this, "makeRecoveryRequest", "Error parsing the JSON.", e); 
		}
		PasswordEncryptionService service = new PasswordEncryptionService();
		try {
			salt = service.generateSalt();
			hash = service.getEncryptedPassword(newPass, salt);
		} catch (NoSuchAlgorithmException e) {
			Log.error("", this, "makeRecoveryRequest", "Hashing Algorithm failed, please review the PasswordEncryptionService.", e); 
		} catch (InvalidKeySpecException e) {
			Log.error("", this, "makeRecoveryRequest", "Invalid Key.", e); 
		}
		String userId = usersMid.getUserIdUsingEmail(appId, email);
		if(userId==null)
			return Response.status(Status.BAD_REQUEST).entity(new Error("Wrong email.")).build();
		if(appKey==null)
			return Response.status(Status.BAD_REQUEST).entity("App Key not found").build();
		if(!appsMid.authenticateApp(appId,appKey))
			return Response.status(Status.UNAUTHORIZED).entity("Wrong App Key").build();
		boolean opOk = usersMid.recoverUser(appId, userId, email, ui, newPass, hash, salt, Metadata.getNewMetadata(location));
		if(opOk){
			Result res = new Result("Email sent with recovery details.", null);
			response = Response.status(Status.OK).entity(res).build();
		}
		else
			response = Response.status(Status.BAD_REQUEST).entity(new Error("Wrong email.")).build();
		return response;

	}

	@POST
	@Path("/changepassword")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response changePasswordRequest(JSONObject inputJsonObj, @Context UriInfo ui, @Context HttpHeaders hh){
		Response response = null;
		MultivaluedMap<String, String> headerParams = hh.getRequestHeaders();
		String oldPassword = null;
		String newPassword = null; 
		String userAgent = null;
		String location = null;
		try {
			newPassword = (String) inputJsonObj.get("newPassword");
			oldPassword = (String) inputJsonObj.get("oldPassword");
		} catch (JSONException e) {
			Log.error("", this, "createSession", "Error parsing the JSON.", e); 
			return Response.status(Status.BAD_REQUEST).entity(new Error("Error reading JSON")).build();
		}
		try {
			location = headerParams.getFirst(Const.LOCATION);
		} catch (Exception e) { }
		try {
			userAgent = headerParams.getFirst(Const.USER_AGENT);
		} catch (Exception e) { }

		try{
			String sessionToken = Utils.getSessionToken(hh);
			String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
			if (!sessionMid.checkAppForToken(Utils.getSessionToken(hh), appId))
				return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
			Boolean auth = sessionMid.authenticateUser(appId, userId, oldPassword);
			if(auth){
				usersMid.updateUserPassword(appId, userId, newPassword, Metadata.getNewMetadata(location));
				if(location!=null)
					sessionMid.refreshSession(sessionToken, location, userAgent);
				response = Response.status(Status.OK).entity("Passoword correctly changed.").build();
			}else
				response = Response.status(Status.BAD_REQUEST).entity(new Error("Wrong old password.")).build();
		}catch (Exception e) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity(new Error("INTERNAL_SERVER_ERROR")).build();
		}
		return response;

	}


	// *** RESOURCES *** //

	/**
	 * Launches the resource integration requests.
	 * 
	 * @param appId
	 * @return
	 */
	@Path("integration")
	public IntegrationResource integration() {
		try {
			return new IntegrationResource(appId);
		} catch (IllegalArgumentException e) {
			Log.error("", this, "integration", "Illegal Argument.", e); 
			throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity(new Error("Parse error")).build());
		}
	}
}
