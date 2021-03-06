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
package infosistema.openbaas.comunication.bound;

import infosistema.openbaas.comunication.connector.IConnector;
import infosistema.openbaas.comunication.message.Message;
import infosistema.openbaas.data.Metadata;
import infosistema.openbaas.data.Result;
import infosistema.openbaas.data.enums.ModelEnum;
import infosistema.openbaas.data.models.ChatMessage;
import infosistema.openbaas.data.models.ChatRoom;
import infosistema.openbaas.data.models.Media;
import infosistema.openbaas.middleLayer.ChatMiddleLayer;
import infosistema.openbaas.middleLayer.MediaMiddleLayer;
import infosistema.openbaas.middleLayer.NotificationMiddleLayer;
import infosistema.openbaas.middleLayer.SessionMiddleLayer;
import infosistema.openbaas.utils.Log;
import infosistema.openbaas.utils.Utils;

import org.apache.commons.codec.binary.Base64;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sun.jersey.core.header.FormDataContentDisposition;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Hashtable;

public class Outbound {

	private String userId;
	private IConnector connector;
	private static ArrayList<Outbound> msgOutboundList = new ArrayList<Outbound>();
	private static Hashtable<String, Outbound> userOutbound = new Hashtable<String, Outbound>();

	private SessionMiddleLayer sessionMid = SessionMiddleLayer.getInstance();
	private ChatMiddleLayer chatMid = ChatMiddleLayer.getInstance();
	private MediaMiddleLayer mediaMid = MediaMiddleLayer.getInstance();
	private NotificationMiddleLayer noteMid = NotificationMiddleLayer.getInstance();
	
	public Outbound(IConnector connector) {
		this.connector = connector;
	}
	
	public static void setUserOutbound(String userId, Outbound outbound) {
		userOutbound.put(userId, outbound);
	}

	public static void removeUserOutbound(String userId) {
		userOutbound.remove(userId);
	}

	public static Outbound getUserOutbound(String userId) {
		return userOutbound.get(userId);
	}

	public String getUserId() {
		return this.userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}
	
	/*** RECEIVE MESSAGES ***/
	
	public void processMessage(String message) {
		String msgType = null;
		String appId = null;
		String sessionToken = null;
		String messageId = null;
		JSONObject data = null;
		try {
			JSONObject msg = new JSONObject(message);
			try {
				msgType = msg.getString(Message.TYPE);
			} catch (Exception e) {}
			try {
				appId = msg.getString(Message.APP_ID);
			} catch (Exception e) {}
			try {
				sessionToken = msg.getString(Message.SESSION_TOKEN);
			} catch (Exception e) {}
			try {
				messageId = msg.getString(Message.MESSAGE_ID);
			} catch (Exception e) {}
			try {
				data = msg.getJSONObject(Message.DATA);
			} catch (Exception e) {}

			if (!sessionMid.checkAppForToken(sessionToken, appId)) {
				sendNOKMessage(appId, messageId, "Invalid sessionToken!");
				return;
			}

			JSONObject retData = null;
			
			if (msgType == null) {
				sendNOKMessage(appId, messageId, "Type message can't be empty!");
			} else if (msgType.equals(Message.AUTHENTICATE)) {
				retData = processMsgAuthenticate(appId, messageId, sessionToken);
			} else if (msgType.equals(Message.OK)) {
				//processMsgOk(data);
			} else if (msgType.equals(Message.NOK)) {
				//processMsgNok(data);
			} else if (msgType.equals(Message.CREATE_CHAT_ROOM)) {
				retData = processMsgCreateChatRoom(data, appId, messageId, sessionToken);
			} else if (msgType.equals(Message.SENT_CHAT_MSG)) {
				retData = processMsgSentChatMsg(data, messageId, appId, sessionToken);
			} else if (msgType.equals(Message.PING)) {
				retData = processMsgPing(appId, messageId);
			} else if (msgType.equals(Message.PONG)) {
				retData = processMsgPong();
			} else {
				sendNOKMessage(appId, messageId, "Invalid message type: " + msgType + "!");
			}
			
			if (retData != null) {
				if (retData.has(Message.ERROR_MESSAGE)) {
					sendNOKMessage(appId, messageId, retData.getString(Message.ERROR_MESSAGE));
				} else {
					sendOKMessage(appId, messageId, retData);
				}
			}
		} catch (JSONException e) {
			Log.error("", this, "processMessage", "Error processing message", e);
		}
	}
	
	private JSONObject processMsgAuthenticate(String appId, String messageId, String sessionToken) {
		try {
			String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
			setUserId(userId);
			Outbound.removeOutbound(this);
			Outbound.setUserOutbound(userId, this);
			return new JSONObject(); 
		} catch (Exception e) {
			Log.error("", this, "processMsgAuthenticate", "Error processing message!", e);
			return getErrorJSONObject(appId, messageId, "Error processing message!");
		}
	}

	private JSONObject processMsgCreateChatRoom(JSONObject data, String appId, String messageId, String sessionToken) {
		String roomName = null;
		boolean flag=false;
		JSONArray participants=null;
		Boolean flagNotification=false;
		String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
		try {
			try {
				roomName = data.getString(ChatRoom.ROOM_NAME);
			} catch (Exception e) {}
			try {
				participants = data.getJSONArray(ChatRoom.PARTICIPANTS);
			} catch (Exception e) {
				Log.error("", this, "processMsgCreateChatRoom", "Error creating chat.", e);
			}
			try {
				flagNotification =  data.optBoolean(ChatRoom.FLAG_NOTIFICATION);
			} catch (Exception e) {
				Log.error("", this, "processMsgCreateChatRoom", "Error creating chat.", e);
			}
			for(int i = 0; i < participants.length(); i++){
				String userCurr = participants.getString(i);
				if(userCurr.equals(userId)) flag = true;
			}
			if(!flag) participants.put(userId);
			if (roomName==null) {
				roomName = Utils.getStringByJSONArray(participants, ";");
			}
			ChatRoom chatRoom = chatMid.createChatRoom(appId, roomName, userId, flagNotification, participants);
			return chatRoom.serialize();
		} catch (Exception e) {
			Log.error("", this, "processMsgCreateChatRoom", "Error creating chat.", e);
			return getErrorJSONObject(appId, messageId, "Error creating chat!");
		}
	}

	private JSONObject processMsgSentChatMsg(JSONObject data, String messageId, String appId, String sessionToken) {
		String message = null;  
		String roomId = null;

		try {
			message = data.getString(Message.TEXT);
		} catch (JSONException e) { }
		try {
			roomId = data.getString(Message.CHAT_ROOM_ID);
		} catch (JSONException e) {
			Log.error("", this, "processMsgRecvChatMsg", "Error getting data",e);
		}
		
		String messageText = null;
		String fileId = null;
		String imageId = null;
		String audioId = null;
		String videoId = null;
		String hasFile = null;
		String hasImage = null;
		String hasAudio = null;
		String hasVideo = null;
		try {
			hasFile = data.getString(Message.HAS_FILE);
		} catch (JSONException e1) { }
		try {
			hasImage = data.getString(Message.HAS_IMAGE);
		} catch (JSONException e1) { }
		try {
			hasAudio = data.getString(Message.HAS_AUDIO);
		} catch (JSONException e1) { }
		try {
			hasVideo = data.getString(Message.HAS_VIDEO);
		} catch (JSONException e1) { }
		ModelEnum flag = null;
		String userId = sessionMid.getUserIdUsingSessionToken(sessionToken);
		if (message != null){
			try {
				messageText = URLDecoder.decode(message, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				Log.error("", this, "processMsgRecvChatMsg", "Error in decoding message.", e);
				return getErrorJSONObject(appId, messageId, "Error in decoding message.");
			}
		}
		
		if (chatMid.existsChatRoom(appId, roomId)) {
			try {
				InputStream imageInputStream = null; 
				InputStream videoInputStream = null; 
				InputStream audioInputStream = null; 
				InputStream fileInputStream = null;
				String stmp = null;
				try {
					stmp = data.getString(Message.IMAGE);
					if (stmp != null) imageInputStream = convertBase64(stmp);
				} catch (Exception e) {}
				try {
					stmp = null;
					stmp = data.getString(Message.VIDEO);
					if (stmp != null) videoInputStream = convertBase64(stmp);
				} catch (Exception e) {}
				try {
					stmp = null;
					stmp = data.getString(Message.AUDIO);
					if (stmp != null) audioInputStream = convertBase64(stmp);
				} catch (Exception e) {}
				try {
					stmp = null;
					stmp = data.getString(Message.FILE);
					if (stmp != null) fileInputStream = convertBase64(stmp);
				} catch (Exception e) {}

				FormDataContentDisposition fileDetail = FormDataContentDisposition.name("media").fileName("media.txt").build();
				
				Result res = null;
				if (imageInputStream != null && fileDetail!=null) {
					fileDetail = FormDataContentDisposition.name("media").fileName("image.png").build();
					res = mediaMid.createMedia(imageInputStream, fileDetail, appId, userId, ModelEnum.image, null, Metadata.getNewMetadata(null), null);
					flag = ModelEnum.image;
				} else if (videoInputStream!=null && fileDetail!=null) {
					fileDetail = FormDataContentDisposition.name("media").fileName("video.wmv").build();
					res = mediaMid.createMedia(videoInputStream, fileDetail, appId, userId, ModelEnum.video, null, Metadata.getNewMetadata(null), null);
					flag = ModelEnum.video;
				} else if (audioInputStream!=null && fileDetail!=null) {
					fileDetail = FormDataContentDisposition.name("media").fileName("audio.mp3").build();
					res = mediaMid.createMedia(audioInputStream, fileDetail, appId, userId, ModelEnum.audio, null, Metadata.getNewMetadata(null), null);
					flag = ModelEnum.audio;
				} else if (fileInputStream!=null && fileDetail!=null) {
					res = mediaMid.createMedia(fileInputStream, fileDetail, appId, userId, ModelEnum.storage, null, Metadata.getNewMetadata(null), null);
					flag = ModelEnum.storage;
				}
				if (res!=null && flag!=null) {
					String fid = ((Media)res.getData()).get_id();
					if (flag.equals(ModelEnum.image)) {
						imageId = fid;
						hasImage = "true";
					}
					if (flag.equals(ModelEnum.storage)) {
						fileId = fid;
						hasFile = "true";
					}
					if (flag.equals(ModelEnum.audio)) {
						audioId = fid;
						hasAudio = "true";
					}
					if (flag.equals(ModelEnum.video)) {
						videoId = fid;
						hasVideo = "true";
					}
				}
				ChatMessage msg = chatMid.sendMessage(appId, userId, roomId, messageText, fileId, imageId, audioId, videoId, hasFile, hasImage, hasAudio, hasVideo);
				if (msg != null) {
					noteMid.setPushNotificationsTODO(appId, userId, roomId);
					return msg.serialize();
				}else{
					return getErrorJSONObject(appId, messageId, "Error sendMessage");
				}
			} catch (Exception e) {
				Log.error("", this, "sendMessage", "Error sendMessage.", e); 
				return getErrorJSONObject(appId, messageId, "Error sendMessage");
			}
		}else{
			return getErrorJSONObject(appId, messageId, "Chat Room not found");
		}
	}

	private JSONObject processMsgPing(String appId, String messageId) {
		sendPongMessage(appId, messageId);
		return null;
	}

	private JSONObject processMsgPong() {
		return null;
	}

	private JSONObject getErrorJSONObject(String appId, String messageId, String errorMessage) {
		JSONObject obj = new JSONObject();
		try {
			obj.put(Message.ERROR_MESSAGE, errorMessage);
		} catch (JSONException e1) {
			obj = null;
			sendNOKMessage(appId, messageId, errorMessage);
			Log.error("", this, "processMsgCreateChatRoom", "Error processing error message", e1);
		}
		return obj;
	}
	
	
	/*** SEND MESSAGES ***/

	public boolean sendPingMessage(String appId) {
		Message message = new Message(Message.PING, appId);
		return sendMessage(message);
	}

	public boolean sendPongMessage(String appId, String messageId) {
		Message message = new Message(Message.PONG, appId, messageId);
		return sendMessage(message);
	}

	public boolean sendOKMessage(String appId, String messageId, JSONObject data) {
		if (messageId == null) return true;
		Message message = new Message(Message.OK, appId, messageId);
		if (data != null) message.setData(data);
		return sendMessage(message);
	}
	
	public boolean sendNOKMessage(String appId, String messageId, String errorMessage) {
		if (messageId == null) return true;
		Message message = new Message(Message.NOK, appId, messageId);
		try {
			JSONObject data = new JSONObject ();
			data.put(Message.ERROR_MESSAGE, errorMessage);
			message.setData(data);
		} catch (JSONException e) {
			Log.error("", this, "sendNOKMessage", "Erro sending NOK message");
			return false;
		}
		return sendMessage(message);
	}

	public boolean sendRecvMessage(String appId, String roomId, ChatMessage msg, String userId) {
		Message message = new Message(Message.RECV_CHAT_MSG, appId);
		try {
			JSONObject data = new JSONObject ();
			data.put(Message.MESSAGE, msg.serialize());
			ChatRoom room = chatMid.getChatRoom(appId, roomId);
			int unReadMsgNumber = chatMid.getUnreadMsgs(appId, userId, roomId).size();
			room.setUnreadMessages(unReadMsgNumber);
			data.put(Message.MESSAGE, msg.serialize());
			data.put(Message.CHAT_ROOM, room.serialize());
			message.setData(data);
		} catch (JSONException e) {
			Log.error("", this, "sendRecvMessage", "Erro sending Recv message");
			return false;
		}
		return sendMessage(message);
	}
	
	public boolean sendMessage(Message message) {
		return connector.sendMessage(message);
	}

	
	/*** OTHERS ***/

	public static void addOutbound(Outbound out) {
		msgOutboundList.add(out);
	}

	public static void removeOutbound(Outbound out) {
		msgOutboundList.remove(out);
	}
	
	private InputStream convertBase64(String str) {		
		byte[] ba = Base64.decodeBase64(str);
		ByteArrayInputStream res = new ByteArrayInputStream(ba);
		return res;
	}

}

