package infosistema.openbaas.rest;

import infosistema.openbaas.middleLayer.DocumentMiddleLayer;
import infosistema.openbaas.middleLayer.MiddleLayerFactory;
import infosistema.openbaas.model.IdsResultSet;
import infosistema.openbaas.rest.AppsResource.PATCH;
import infosistema.openbaas.utils.Const;
import infosistema.openbaas.utils.Utils;

import java.util.ArrayList;
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

public class DataResource {

	private DocumentMiddleLayer docMid;
	private String appId;
	@Context
	UriInfo uriInfo;

	public DataResource(@Context UriInfo uriInfo, String appId) {
		this.docMid = MiddleLayerFactory.getDocumentMiddleLayer();
		this.appId = appId;
		this.uriInfo = uriInfo;
	}
	
	// *** CREATE *** ///

	
 	// *** UPDATE *** ///
	
	
	// *** DELETE *** ///
	
	
	// *** GET *** ///
	
	
	// *** OTHERS *** ///
	
	//TODO: LOCATION
	@POST
	@Path("/{pathId:.+}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createNonPublishableDocument(JSONObject inputJsonObj,
			@PathParam("pathId") List<PathSegment> path, @Context UriInfo ui,
			@Context HttpHeaders hh,
			@HeaderParam(value = "location") String location) {
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			JSONObject data = null;
			try {
				data = (JSONObject) inputJsonObj.get("data");
			} catch (JSONException e) {
				System.out.println("Error parsing the JSON file.");
				e.printStackTrace();
			}
			if (MiddleLayerFactory.getAppsMiddleLayer().appExists(appId)) {
				if (docMid.createNonPublishableAppDocument(appId, data, path, location))
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

	//TODO: LOCATION
	/*DEV NOTES:
	 * Geolocation between points was done in a rude manner, we iterate all the points and calculate
	 * the distance between the two using haversine.
	 * 
	 * THIS IS NOT the right way to do it, if you have the time look into k-d trees ,
	 * http://en.wikipedia.org/wiki/K-d_tree
	 * 
	 * http://zaemis.blogspot.pt/2011/01/geolocation-search.html
	 * 
	 * or 
	 * http://wiki.apache.org/solr/SpatialSearch
	 *
	 */
	/**
	 * Retrieves all the data contained in this application.
	 * 
	 * 
	 * @return
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllData(@Context UriInfo ui, @Context HttpHeaders hh,
			@QueryParam("latitude") String latitude,
			@QueryParam("longitude") String longitude,
			@QueryParam("radius") String radius,
			@QueryParam("pageNumber") Integer pageNumber, @QueryParam("pageSize") Integer pageSize, 
			@QueryParam("orderBy") String orderBy, @QueryParam("orderType") String orderType ) {
		if (pageNumber == null) pageNumber = Const.PAGE_NUMBER;
		if (pageSize == null) 	pageSize = Const.PAGE_SIZE;
		if (orderBy == null) 	orderBy = Const.ORDER_BY;
		if (orderType == null) 	orderType = Const.ORDER_TYPE;
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			//query parameters are present, only return the elements 
			if (latitude != null && longitude != null && radius != null) {
				ArrayList<String> all = docMid.getAllDocsInRadius(appId, Double.parseDouble(latitude), 
						Double.parseDouble(longitude), Double.parseDouble(radius));
				IdsResultSet res = new IdsResultSet(all,pageNumber);
				
				response = Response.status(Status.OK).entity(res).build();
			//no query parameters return all docs
			} else {
				String all = docMid.getAllDocInApp(appId);
				response = Response.status(Status.OK).entity(all).build();
			}
		} else if (code == -2) {
			response = Response.status(Status.FORBIDDEN).entity("Invalid Session Token.").build();
		} else if (code == -1)
			response = Response.status(Status.BAD_REQUEST).entity("Error handling the request.").build();
		return response;
	}

	//TODO: LOCATION
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
			@HeaderParam(value = "location") String location) {
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			JSONObject data = null;
			try {
				data = (JSONObject) inputJsonObj.get("data");
			} catch (JSONException e) {
				System.out.println("Error parsing the JSON file.");
				e.printStackTrace();
			}
			if (MiddleLayerFactory.getAppsMiddleLayer().appExists(appId)) {
				if (docMid.insertAppDocumentRoot(appId, data, location)) {
					response = Response.status(Status.CREATED).entity(appId).build();
				} else {
					response = Response.status(Status.BAD_REQUEST).entity(data).build();
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
			@Context UriInfo ui,@Context HttpHeaders hh,
			@HeaderParam(value = "location")String location) {
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			if (MiddleLayerFactory.getAppsMiddleLayer().appExists(appId)) {
				String url = docMid.createAppDocPathFromListWithSlashes(appId,
						path);
				if (docMid.insertIntoAppDocument(appId, url, inputJsonObj, location))
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
	 * Retrieves the data contained in a key.
	 * 
	 * @param path
	 * @return
	 */
	@GET
	@Path("/{pathId:.+}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getElementInDocument(
			@PathParam("pathId") List<PathSegment> path, @Context UriInfo ui,
			@Context HttpHeaders hh,
			@QueryParam("lat") String latitude,
			@QueryParam("long") String longitude,
			@QueryParam("radius") String radius) {
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			if (latitude != null && longitude != null && radius != null) {
				/*Set<String> all = docMid.getElementInAppInRadius(appId, path,Double.parseDouble(latitude), 
						Double.parseDouble(longitude), Double.parseDouble(radius));*/
			//no query parameters return all docs
			} else {
				if (docMid.dataExistsForElement(appId, path)) {
					String data = docMid.getElementInAppDocument(appId, path);
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
			if (docMid.dataExistsForElement(appId, path)) {
				if (docMid.deleteDataInElement(appId, path))
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
	public Response patchDataInElement(
			@PathParam("pathId") List<PathSegment> path, JSONObject inputJson,
			@Context UriInfo ui, @Context HttpHeaders hh,
			@HeaderParam(value = "location") String location) {
		Response response = null;
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			if (docMid.dataExistsForElement(appId, path)) {
				String data = docMid.patchDataInElement(appId, path, inputJson, location);
				if (data != null)
					response = Response.status(Status.OK).entity(data).build();
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
}