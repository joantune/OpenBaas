package infosistema.openbaas.rest;

import infosistema.openbaas.data.Error;
import infosistema.openbaas.data.ListResult;
import infosistema.openbaas.data.Metadata;
import infosistema.openbaas.data.Result;
import infosistema.openbaas.data.enums.ModelEnum;
import infosistema.openbaas.data.models.Audio;
import infosistema.openbaas.middleLayer.AppsMiddleLayer;
import infosistema.openbaas.middleLayer.MediaMiddleLayer;
import infosistema.openbaas.middleLayer.MiddleLayerFactory;
import infosistema.openbaas.middleLayer.SessionMiddleLayer;
import infosistema.openbaas.utils.Const;
import infosistema.openbaas.utils.Log;
import infosistema.openbaas.utils.Utils;

import java.io.InputStream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response.Status;

//@Path("/apps/{appId}/media/audio")
public class AudioResource {
	static Map<String, Audio> audio = new HashMap<String, Audio>();
	String appId;
	private MediaMiddleLayer mediaMid;
	private AppsMiddleLayer appsMid;
	private SessionMiddleLayer sessionsMid;

	public AudioResource(String appId) {
		this.appId = appId;
		this.mediaMid = MiddleLayerFactory.getMediaMiddleLayer();
		this.sessionsMid = MiddleLayerFactory.getSessionMiddleLayer();
	}

	
	// *** CREATE *** //

	//TODO: LOCATION
	/**
	 * To upload a file simply send a Json object with the directory of the file
	 * you want to send and send the "compact" flag (it takes the value of true
	 * or false). If the compactInfo is true then only the id of the audio is
	 * returned, otherwise the entire information of the object is sent.
	 * 
	 * @param request
	 * @param headers
	 * @param inputJsonObj
	 * @return
	 */
	@POST
	@Consumes({ MediaType.MULTIPART_FORM_DATA })
	@Produces({ MediaType.APPLICATION_JSON })
	public Response uploadAudio(@Context HttpServletRequest request, @Context UriInfo ui, @Context HttpHeaders hh,
			@FormDataParam(Const.FILE) InputStream uploadedInputStream, @FormDataParam(Const.FILE) FormDataContentDisposition fileDetail,
			@PathParam("appId") String appId, @HeaderParam(value = Const.LOCATION) String location) {
		Response response = null;
		Cookie sessionToken=null;
		MultivaluedMap<String, String> headerParams = hh.getRequestHeaders();
		for (Entry<String, List<String>> entry : headerParams.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(Const.SESSION_TOKEN))
				sessionToken = new Cookie(Const.SESSION_TOKEN, entry.getValue().get(0));
		}		
		String userId = sessionsMid.getUserIdUsingSessionToken(sessionToken.getValue());
		if(Utils.getAppIdFromToken(sessionToken.getValue(), userId)!=appId)
			return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			String audioId = mediaMid.createMedia(uploadedInputStream, fileDetail, appId, ModelEnum.audio, location);
			if (audioId == null) { 
				response = Response.status(Status.BAD_REQUEST).entity(new Error(appId)).build();
			} else {
				
				String metaKey = "apps."+appId+".media.audio."+audioId;
				Metadata meta = mediaMid.createMetadata(metaKey, userId, location);
				Result res = new Result(audioId, meta);
				
				response = Response.status(Status.OK).entity(res).build();
			}
		} else if(code == -2) {
			response = Response.status(Status.FORBIDDEN).entity(new Error("Invalid Session Token.")).build();
		} else if(code == -1)
			response = Response.status(Status.BAD_REQUEST).entity(new Error("Error handling the request.")).build();
		return response;
	}

	
	// *** UPDATE *** //

	
	// *** DELETE *** //

	//TODO: LOCATION
	/**
	 * Deletes the audio File (from the DB and FileSystem).
	 * @param audioId
	 * @return
	 */
	@Path("{audioId}")
	@DELETE
	@Produces({ MediaType.APPLICATION_JSON })
	public Response deleteAudio(@PathParam("audioId") String audioId,
			@Context UriInfo ui, @Context HttpHeaders hh) {
		Response response = null;
		
		Cookie sessionToken=null;
		MultivaluedMap<String, String> headerParams = hh.getRequestHeaders();
		for (Entry<String, List<String>> entry : headerParams.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(Const.SESSION_TOKEN))
				sessionToken = new Cookie(Const.SESSION_TOKEN, entry.getValue().get(0));
		}
		String userId = sessionsMid.getUserIdUsingSessionToken(sessionToken.getValue());
		if(Utils.getAppIdFromToken(sessionToken.getValue(), userId)!=appId)
			return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			Log.debug("", this, "deleteAudio", "***********Deleting Audio***********");
			if (mediaMid.mediaExists(appId, ModelEnum.audio, audioId)) {
				this.mediaMid.deleteMedia(appId, ModelEnum.audio, audioId);
				
				String metaKey = "apps."+appId+".media.audio."+audioId;
				
				Boolean meta = mediaMid.deleteMetadata(metaKey);
				if(meta)
					response = Response.status(Status.OK).entity("").build();
				else
					response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(new Error("Del Meta")).build();
			} else {
				response = Response.status(Status.NOT_FOUND).entity(new Error(appId)).build();
			}
		}else if(code == -2){
			response = Response.status(Status.FORBIDDEN).entity(new Error("Invalid Session Token.")).build();
		}else if(code == -1)
			response = Response.status(Status.BAD_REQUEST).entity(new Error("Error handling the request.")).build();
		return response;
	}

	
	// *** GET LIST *** //

	//TODO: LOCATION
	/**
	 * Retrieve all the audio Ids for this application.
	 * @return
	 */
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	public Response findAllAudioIds(@Context UriInfo ui, @Context HttpHeaders hh,@QueryParam("lat") 
	String latitude,@QueryParam("long") String longitude,@QueryParam("radius") String radius,
	@QueryParam(Const.PAGE_NUMBER) Integer pageNumber, @QueryParam(Const.PAGE_SIZE) Integer pageSize, 
	@QueryParam(Const.ORDER_BY) String orderBy, @QueryParam(Const.ORDER_BY) String orderType ) {
		Response response = null;
		Cookie sessionToken=null;
		MultivaluedMap<String, String> headerParams = hh.getRequestHeaders();
		for (Entry<String, List<String>> entry : headerParams.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(Const.SESSION_TOKEN))
				sessionToken = new Cookie(Const.SESSION_TOKEN, entry.getValue().get(0));
		}
		String userId = sessionsMid.getUserIdUsingSessionToken(sessionToken.getValue());
		if(Utils.getAppIdFromToken(sessionToken.getValue(), userId)!=appId)
			return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			Log.debug("", this, "findAllAudioIds", "********Finding all Audio**********");
			ArrayList<String> audioIds = null;
			if (latitude != null && longitude != null && radius != null) {
				audioIds = mediaMid.getAllAudioIdsInRadius(appId, Double.parseDouble(latitude),
						Double.parseDouble(longitude), Double.parseDouble(radius));
			}else
				audioIds = mediaMid.getAllMediaIds(appId, ModelEnum.audio, pageNumber, pageSize, orderBy, orderType);
			ListResult res = new ListResult(audioIds,pageNumber);
			response = Response.status(Status.OK).entity(res).build();
		} else if(code == -2){
			response = Response.status(Status.FORBIDDEN).entity(new Error("Invalid Session Token.")).build();
		}else if(code == -1)
			response = Response.status(Status.BAD_REQUEST).entity(new Error("Error handling the request.")).build();
		return response;
	}

	
	// *** GET *** //

	/**
	 * Retrieve the audio Metadata using its ID.
	 * @param audioId
	 * @return
	 */
	@Path("{audioId}")
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	public Response findAudioById(@PathParam("audioId") String audioId, 
			@Context UriInfo ui, @Context HttpHeaders hh
			) {
		Response response = null;
		Cookie sessionToken=null;
		MultivaluedMap<String, String> headerParams = hh.getRequestHeaders();
		for (Entry<String, List<String>> entry : headerParams.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(Const.SESSION_TOKEN))
				sessionToken = new Cookie(Const.SESSION_TOKEN, entry.getValue().get(0));
		}
		String userId = sessionsMid.getUserIdUsingSessionToken(sessionToken.getValue());
		if(Utils.getAppIdFromToken(sessionToken.getValue(), userId)!=appId)
			return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			Log.debug("", this, "findAudioById", "********Finding Audio Meta**********");
			Audio temp = null;
			if (appsMid.appExists(this.appId)) {
				if (mediaMid.mediaExists(this.appId, ModelEnum.audio, audioId)) {
					temp = (Audio)(mediaMid.getMedia(appId, ModelEnum.audio, audioId));
					
					String metaKey = "apps."+appId+".media.audio."+audioId;
					Metadata meta = mediaMid.getMetadata(metaKey);
					Result res = new Result(temp, meta);
					
					response = Response.status(Status.OK).entity(res).build();
				} else {
					response = Response.status(Status.NOT_FOUND).entity(new Error("")).build();
				}
			} else {
				response = Response.status(Status.NOT_FOUND).entity(new Error(appId)).build();
			}
		}else if(code == -2){
			response = Response.status(Status.FORBIDDEN).entity(new Error("Invalid Session Token.")).build();
		}else if(code == -1)
			response = Response.status(Status.BAD_REQUEST).entity(new Error("Error handling the request.")).build();
		return response;
	}

	// *** DOWNLOAD *** //

	/**
	 * Downloads the audio in the specified quality.
	 * @param audioId
	 * @return
	 */
	@Path("{audioId}/{quality}/download")
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	public Response downloadAudio(@PathParam("audioId") String audioId,
			@Context UriInfo ui, @Context HttpHeaders hh) {
		Response response = null;
		byte[] sucess = null;
		Cookie sessionToken=null;
		MultivaluedMap<String, String> headerParams = hh.getRequestHeaders();
		for (Entry<String, List<String>> entry : headerParams.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(Const.SESSION_TOKEN))
				sessionToken = new Cookie(Const.SESSION_TOKEN, entry.getValue().get(0));
		}
		String userId = sessionsMid.getUserIdUsingSessionToken(sessionToken.getValue());
		if(Utils.getAppIdFromToken(sessionToken.getValue(), userId)!=appId)
			return Response.status(Status.UNAUTHORIZED).entity(new Error("Action in wrong app: "+appId)).build();
		int code = Utils.treatParameters(ui, hh);
		if (code == 1) {
			Log.debug("", this, "downloadAudio", "*********Downloading Audio**********");
			if (this.mediaMid.mediaExists(appId, ModelEnum.audio, audioId)) {
				Audio audio = (Audio)(mediaMid.getMedia(appId, ModelEnum.audio, audioId));
				sucess = mediaMid.download(appId, ModelEnum.audio, audioId,audio.getFileExtension());
				if (sucess!=null)
					return Response.ok(sucess, MediaType.APPLICATION_OCTET_STREAM)
							.header("content-disposition","attachment; filename = "+audio.getFileName()+"."+audio.getFileExtension()).build();
			} else
				response = Response.status(Status.NOT_FOUND).entity(audioId)
				.build();
		}else if(code == -2){
			response = Response.status(Status.FORBIDDEN).entity("Invalid Session Token.")
					.build();
		}else if(code == -1)
			response = Response.status(Status.BAD_REQUEST).entity("Error handling the request.")
			.build();
		return response;
	}

	// *** OTHERS *** //

	// *** RESOURCES *** //

}
