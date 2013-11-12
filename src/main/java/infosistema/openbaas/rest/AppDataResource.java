package infosistema.openbaas.rest;

import infosistema.openbaas.middleLayer.DocumentMiddleLayer;
import infosistema.openbaas.middleLayer.MiddleLayerFactory;
import infosistema.openbaas.rest.AppResource.PATCH;
import infosistema.openbaas.utils.Const;
import infosistema.openbaas.utils.Utils;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
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
import org.codehaus.jettison.json.JSONObject;

public class AppDataResource {

	private DocumentMiddleLayer docMid;
	private String appId;
	
	@Context
	UriInfo uriInfo;

	public AppDataResource(@Context UriInfo uriInfo, String appId) {
		this.docMid = MiddleLayerFactory.getDocumentMiddleLayer();
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
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createDocumentRoot(JSONObject inputJsonObj,@Context UriInfo ui, @Context HttpHeaders hh,
			@HeaderParam(value = Const.LOCATION) String location) {
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			if (MiddleLayerFactory.getAppsMiddleLayer().appExists(appId)) {
				if (docMid.insertDocumentInPath(appId, null, null, inputJsonObj, location)) {
					response = Response.status(Status.OK).entity(inputJsonObj).build();
				} else {
					response = Response.status(Status.BAD_REQUEST).entity(inputJsonObj).build();
				}
			} else {
				response = Response.status(Status.NOT_FOUND).entity(appId).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity("Invalid Session Token.").build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity("Error handling the request.").build();
		return response;
	}
	

	// *** UPDATE *** //
	
	//TODO: LOCATION
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
	public Response createOrReplaceDocument(JSONObject inputJsonObj,@PathParam("pathId") List<PathSegment> path, 
			@Context UriInfo ui,@Context HttpHeaders hh, @HeaderParam(value = Const.LOCATION)String location) {
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			if (MiddleLayerFactory.getAppsMiddleLayer().appExists(appId)) {
				if (docMid.insertDocumentInPath(appId, null, path, inputJsonObj, location))
					response = Response.status(Status.CREATED).entity(appId).build();
				else
					response = Response.status(Status.BAD_REQUEST).entity(inputJsonObj).build();
			} else {
				response = Response.status(Status.NOT_FOUND).entity(appId).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity("Invalid Session Token.").build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity("Error handling the request.").build();
		return response;
	}

	//TODO: LOCATION
	/**
	 * Partial updates, adds non existing fields and edits existing ones.
	 * 
	 * @param path
	 * @param inputJson
	 * @return
	 */
	@PATCH
	@Path("/{pathId:.+}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response patchDataInElement( @PathParam("pathId") List<PathSegment> path, JSONObject inputJson,
			@Context UriInfo ui, @Context HttpHeaders hh, @HeaderParam(value = Const.LOCATION) String location) {
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			if (docMid.existsDocumentInPath(appId, null, path)) {
				if (docMid.updateDocumentInPath(appId, null, path, inputJson, location))
					response = Response.status(Status.OK).entity(appId).build();
				else
					response = Response.status(Status.BAD_REQUEST).entity(appId).build();
			} else {
				response = Response.status(Status.NOT_FOUND).entity(appId).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity("Invalid Session Token.").build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity("Error handling the request.").build();
		return response;
	}

	
	// *** DELETE *** //
	
	//TODO: LOCATION
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
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			if (docMid.existsDocumentInPath(appId, null, path)) {
				if (docMid.deleteDocumentInPath(appId, null, path))
					response = Response.status(Status.OK).entity("").build();
				else
					response = Response.status(Status.BAD_REQUEST).entity(path).build();
			} else {
				response = Response.status(Status.NOT_FOUND).entity(appId).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity("Invalid Session Token.").build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity("Error handling the request.").build();
		return response;
	}

	
	// *** GET LIST *** //
	
	//XPTO: Refazer isto tudo
	//TODO: LOCATION
	/**
	 * Retrieves all the data contained in this application.
	 * 
	 * @return
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllData(@Context UriInfo ui, @Context HttpHeaders hh,
			@QueryParam("latitude") String latitude,
			@QueryParam("longitude") String longitude,
			@QueryParam("radius") String radius,
			@QueryParam(Const.PAGE_NUMBER) Integer pageNumber, @QueryParam(Const.PAGE_SIZE) Integer pageSize, 
			@QueryParam(Const.ORDER_BY) String orderBy, @QueryParam(Const.ORDER_BY) String orderType ) {
		if (pageNumber == null) pageNumber = Const.getPageNumber();
		if (pageSize == null) 	pageSize = Const.getPageSize();
		if (orderBy == null) 	orderBy = Const.getOrderBy();
		if (orderType == null) 	orderType = Const.getOrderType();
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			//query parameters are present, only return the elements 
			if (latitude != null && longitude != null && radius != null) {
			//no query parameters return all docs
			} else {
				String all = "docMid.getAllDocInApp(appId)";
				response = Response.status(Status.OK).entity(all).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity("Invalid Session Token.").build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity("Error handling the request.").build();
		return response;
	}

	
	// *** GET *** //
	
	//TODO: LOCATION
	/**
	 * Retrieves the data contained in a key.
	 * 
	 * @param path
	 * @return
	 */
	@GET
	@Path("/{pathId:.+}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getElementInDocument( @PathParam("pathId") List<PathSegment> path, @Context UriInfo ui, @Context HttpHeaders hh,
			@QueryParam("lat") String latitude, @QueryParam("long") String longitude, @QueryParam("radius") String radius) {
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			if (latitude != null && longitude != null && radius != null) {
			} else {
				if (docMid.existsDocumentInPath(appId, null, path)) {
					String data = docMid.getDocumentInPath(appId, null, path);
					if (data == null)
						response = Response.status(Status.BAD_REQUEST).entity(appId).build();
					else
						response = Response.status(Status.OK).entity(data).build();
				} else {
					response = Response.status(Status.NOT_FOUND).entity(appId).build();
				}
			}
			
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity("Invalid Session Token.").build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity("Error handling the request.").build();
		return response;
	}


	// *** OTHERS *** //
	
	// *** RESOURCES *** //

}