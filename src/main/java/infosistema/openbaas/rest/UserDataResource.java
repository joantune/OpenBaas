package infosistema.openbaas.rest;

import infosistema.openbaas.data.Error;
import infosistema.openbaas.data.ListResult;
import infosistema.openbaas.data.Metadata;
import infosistema.openbaas.data.QueryParameters;
import infosistema.openbaas.data.Result;
import infosistema.openbaas.data.enums.ModelEnum;
import infosistema.openbaas.middleLayer.AppsMiddleLayer;
import infosistema.openbaas.middleLayer.DocumentMiddleLayer;
import infosistema.openbaas.middleLayer.MiddleLayerFactory;
import infosistema.openbaas.middleLayer.SessionMiddleLayer;
import infosistema.openbaas.middleLayer.UsersMiddleLayer;
import infosistema.openbaas.utils.Const;
import infosistema.openbaas.utils.Log;
import infosistema.openbaas.utils.Utils;

import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class UserDataResource {

	@Context
	UriInfo uriInfo;
	private AppsMiddleLayer appsMid;
	private DocumentMiddleLayer docMid;
	private UsersMiddleLayer usersMid;
	private String appId;
	private SessionMiddleLayer sessionMid;

	public UserDataResource(UriInfo uriInfo, String appId, String userId) {
		this.appsMid = MiddleLayerFactory.getAppsMiddleLayer();
		this.usersMid = MiddleLayerFactory.getUsersMiddleLayer();
		this.docMid = MiddleLayerFactory.getDocumentMiddleLayer();
		this.sessionMid = MiddleLayerFactory.getSessionMiddleLayer();
		this.appId = appId;
		this.uriInfo = uriInfo;
	}

	// *** CREATE *** //
	
	/**
	 * Creates the document root, this is treated differently than PUT to
	 * 
	 * @Path("/{pathId:.+ ") due to the Rest resources levels.
	 * 
	 * @param inputJsonObj
	 * @return
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createDocumentRoot(JSONObject inputJsonObj, @Context UriInfo ui, @Context HttpHeaders hh, @HeaderParam(value = Const.LOCATION) String location) {
		Response response = null;
		String sessionToken = Utils.getSessionToken(hh);
		String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
		if (sessionMid.checkAppForToken(sessionToken, appId))
			return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			JSONObject data = null;
			try {
				data = (JSONObject) inputJsonObj.get("data");
			} catch (JSONException e) {
				Log.error("", this, "createDocumentRoot", "Error parsing the JSON.", e); 
			}
			if (appsMid.appExists(appId) && usersMid.userExistsInApp(appId, userId)) {
				if (docMid.insertDocumentInPath(appId, userId, null, data, location)) {
					
					String metaKey = "apps."+appId+".users."+userId;
					Metadata meta = docMid.createMetadata(metaKey, userId, location, inputJsonObj);
					
					Result res = new Result(inputJsonObj, meta);		
					
					response = Response.status(Status.CREATED).entity(res).build();
				} else {
					response = Response.status(Status.BAD_REQUEST).entity(new Error(data.toString())).build();
				}
			} else {
				response = Response.status(Status.NOT_FOUND).entity(new Error(appId)).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity(new Error("Invalid Session Token.")).build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity(new Error("Error handling the request.")).build();
		return response;
	}

	
	// *** UPDATE *** //
	
	/**
	 * Create or replace existing elements.
	 * 
	 * @param inputJsonObj
	 * @param path
	 * @return
	 */
	@PUT
	@Path("/{pathId:.+}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createOrReplaceDocument(JSONObject inputJsonObj, @PathParam("pathId") List<PathSegment> path,
			@Context UriInfo ui, @Context HttpHeaders hh, @HeaderParam(value = Const.LOCATION) String location) {
		Response response = null;
		String sessionToken = Utils.getSessionToken(hh);
		String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
		if (sessionMid.checkAppForToken(sessionToken, appId))
			return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			JSONObject data = null;
			try {
				data = (JSONObject) inputJsonObj.get("data");
			} catch (JSONException e) {
				Log.error("", this, "createOrReplaceDocument", "Error parsing the JSON.", e); 
			}
			if (appsMid.appExists(appId)) {
				if (docMid.updateDocumentInPath(appId, userId, path, data, location)){
					String metaKey = "apps."+appId+".users."+userId+path;
					Metadata meta = docMid.updateMetadata(metaKey, userId, location, inputJsonObj);
					Result res = new Result(inputJsonObj, meta);		
					
					response = Response.status(Status.CREATED).entity(res).build();
				}
				else
					response = Response.status(Status.BAD_REQUEST).entity(new Error(data.toString())).build();
			} else {
				response = Response.status(Status.NOT_FOUND).entity(new Error(appId)).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity(new Error("Invalid Session Token.")).build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity(new Error("Error handling the request.")).build();
		return response;
	}

	// *** DELETE *** //
	
	/**
	 * Removes an element and the childs of that element if they exist.
	 * 
	 * @param path
	 * @return
	 */
	@DELETE
	@Path("/{pathId:.+}")
	public Response deleteDataInElement(
			@PathParam("pathId") List<PathSegment> path, @Context UriInfo ui,
			@Context HttpHeaders hh) {
		Response response = null;
		String sessionToken = Utils.getSessionToken(hh);
		String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
		if (sessionMid.checkAppForToken(sessionToken, appId))
			return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			if (docMid.existsDocumentInPath(appId, userId, path)) {
				if (docMid.deleteDocumentInPath(appId, userId, path)){
					String metaKey = "apps."+appId+".data."+path;
					Boolean meta = docMid.deleteMetadata(metaKey);
					if(meta)
						response = Response.status(Status.OK).entity("").build();
					else
						response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(new Error("Del Meta")).build();
				}
				else
					response = Response.status(Status.BAD_REQUEST).entity(new Error(path.toString())).build();
			} else {
				response = Response.status(Status.NOT_FOUND).entity(new Error(appId)).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity(new Error("Invalid Session Token.")).build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity(new Error("Error handling the request.")).build();
		return response;
	}


	// *** GET LIST *** //
	
	/**
	 * Gets all the users in the application.
	 * 
	 * @return
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response find(@Context UriInfo ui, @Context HttpHeaders hh,
			JSONObject query, @QueryParam(Const.RADIUS) String radiusStr,
			@QueryParam(Const.LAT) String latitudeStr, @QueryParam(Const.LONG) String longitudeStr,
			@QueryParam(Const.PAGE_NUMBER) String pageNumberStr, @QueryParam(Const.PAGE_SIZE) String pageSizeStr, 
			@QueryParam(Const.ORDER_BY) String orderByStr, @QueryParam(Const.ORDER_BY) String orderTypeStr) {
		QueryParameters qp = QueryParameters.getQueryParameters(appId, query, radiusStr, latitudeStr, longitudeStr, 
				pageNumberStr, pageSizeStr, orderByStr, orderTypeStr, ModelEnum.data);
		Response response = null;
		if (!sessionMid.checkAppForToken(Utils.getSessionToken(hh), appId))
			return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();

		int code = Utils.treatParametersAdmin(ui, hh);
		if (code == 1) {
			try {
				ListResult res = usersMid.find(qp);
				response = Response.status(Status.OK).entity(res).build();
			} catch (Exception e) {
				response = Response.status(Status.FORBIDDEN).entity(e.getMessage()).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity("Invalid Session Token.").build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity("Error handling the request.").build();
		return response;
	}

	// *** GET *** //
	
	/**
	 * Retrieves the data contained in a key.
	 * 
	 * @param path
	 * @return
	 */
	@GET
	@Path("/{pathId:.+}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getElementInDocument(@PathParam("pathId") List<PathSegment> path, @Context UriInfo ui, @Context HttpHeaders hh) {
		Response response = null;
		String sessionToken = Utils.getSessionToken(hh);
		String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
		if (sessionMid.checkAppForToken(sessionToken, appId))
			return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			if (docMid.existsDocumentInPath(appId, userId, path)) {
				String data = docMid.getDocumentInPath(appId, userId, path);
				if (data == null)
					response = Response.status(Status.BAD_REQUEST).entity(new Error(appId)).build();
				else{
					String metaKey = "apps."+appId+".users."+userId+path;
					Metadata meta = docMid.getMetadata(metaKey);
					Result res = new Result(data, meta);
					response = Response.status(Status.OK).entity(res).build();
				}
			} else {
				response = Response.status(Status.NOT_FOUND).entity(new Error(appId)).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity(new Error("Invalid Session Token.")).build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity(new Error("Error handling the request.")).build();
		return response;
	}


	// *** RESOURCES *** //

	
	// *** OTHERS *** //
	
}
