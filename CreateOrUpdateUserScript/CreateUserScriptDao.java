package com.yatra.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.yatra.export.mybatis.mapper.beans.ClientConfigDO;
import com.yatra.export.mybatis.mapper.beans.CorporateClientDO;
import com.yatra.export.mybatis.mapper.beans.CorporateUserDO;
import com.yatra.galileo.corporate.user.manager.beans.CorporateUserBO;
import com.yatra.gds.common.beans.ValidationResponseBO;

@Component
public class CreateUserScriptDao {
	static final String db_url = "jdbc:mysql://192.168.61.21:3306/galileo_integration";
	static final String db_username = "galileodb";
	static final String db_password = "Gal!l3O_!ntr";
	static final String dbDriver = "com.mysql.jdbc.Driver";
	static Connection dbConnection;
	static {
		try {
			Class.forName(dbDriver);
			dbConnection = DriverManager.getConnection(db_url, db_username, db_password);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static Map<Long, String> getClientVsPcc(List<Long> userIds) {
		Map<Long, String> clientIdvsPcc = new HashMap<>();
		String userIdsString = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
		String sql = "SELECT DISTINCT cud.client_id,cc.pcc FROM atb_data_dump cud,client_config cc WHERE cud.client_id=cc.client_id AND cud.user_id IN ("+userIdsString+");";
		try {
			PreparedStatement statement = dbConnection.prepareStatement(sql);
			ResultSet rs = statement.executeQuery();
			while(rs.next()) {
				Long clientId = rs.getLong(1);
				String pcc = rs.getString(2);
				if(clientId!=null && pcc!=null)
					clientIdvsPcc.put(clientId, pcc);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return clientIdvsPcc;
	}
	
	public static Map<Long, CorporateUserDO> getUsersUnderClient(List<Long> userIds, Long clientId , Boolean onlyHasVersion, String type) {
		Map<Long, CorporateUserDO> usersUnderClient = new HashMap<>();
		String userIdsString = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
		String sql = "SELECT  cud.user_id,cud.pnr,cud.version FROM atb_data_dump cud  WHERE cud.user_id IN ("+userIdsString+") and cud.client_id= "+String.valueOf(clientId)+";";
		if(onlyHasVersion) {
			sql = sql + "and version is not null";
		}
		sql = sql + ";";
			
		try {
			PreparedStatement statement = dbConnection.prepareStatement(sql);
			ResultSet rs = statement.executeQuery();
			while(rs.next()) {
				Long userId = rs.getLong(1);
				String pnr = rs.getString(2);
				Integer version = rs.getInt(3);
				if(rs.wasNull())
					version = null;
				if(userId!=null && pnr!=null)
				{
					CorporateUserDO userDO = new CorporateUserDO();
					userDO.setClientId(clientId);
					userDO.setUserId(userId);
					userDO.setVersion(version);
					usersUnderClient.put(userId, userDO);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return usersUnderClient;
	}
	
	public static ClientConfigDO getClientConfig(Long clientId, String gds) {
		ClientConfigDO clientConfigDO = new ClientConfigDO();
		String sql = "SELECT id,client_Id,type,push_User_profile,push_user_card,pcc,bar,created_on,updated_on,is_enabled FROM client_config WHERE client_id="+clientId+" and type='"+gds+"';";
		try {
			PreparedStatement statement = dbConnection.prepareStatement(sql);
			ResultSet rs = statement.executeQuery();
			while(rs.next()) {
				Long id = rs.getLong(1);
				Long rclientId = rs.getLong(2);
				String type = rs.getString(3);
				Boolean pushUserProfile = rs.getBoolean(4);
				Boolean pushUserCard = rs.getBoolean(5);
				String pcc = rs.getString(6);
				String bar = rs.getString(7);
				Date createdOn = rs.getDate(8);
				Date updatedOn = rs.getDate(9);
				Boolean isEnabled = rs.getBoolean(10);
				if(clientId!=null && pcc!=null)
				{
					clientConfigDO.setId(id);
					clientConfigDO.setClientId(rclientId);
					clientConfigDO.setType(type);
					clientConfigDO.setPushUserProfile(pushUserProfile);
					clientConfigDO.setPushUserCard(pushUserCard);
					clientConfigDO.setPcc(pcc);
					clientConfigDO.setCreatedOn(createdOn);
					clientConfigDO.setUpdatedOn(updatedOn);
					clientConfigDO.setIsEnabled(isEnabled);
					return clientConfigDO;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static CorporateClientDO getclient(Long clientId, String gdsAmadeus) {
		CorporateClientDO clientDO = new CorporateClientDO();
		String sql = "SELECT client_id,status,type,remarks,title,version,created_on,updated_on from corporate_client_data where client_id ="+clientId+" and type = '"+gdsAmadeus+"';";
		try {
			PreparedStatement statement = dbConnection.prepareStatement(sql);
			ResultSet rs = statement.executeQuery();
			while(rs.next()) {
				clientDO.setClientId(rs.getLong(1));
				clientDO.setStatus(rs.getString(2));
				clientDO.setType(rs.getString(3));
				clientDO.setRemarks(rs.getString(4));
				clientDO.setTitle(rs.getString(5));
				clientDO.setVersion(rs.getInt(6));
				clientDO.setCreatedOn(rs.getDate(7));
				clientDO.setUpdatedOn(rs.getDate(8));
				return clientDO;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static void updateUploadStatusinDb(Long userId, String gds, CorporateUserBO userBO, ValidationResponseBO validationResponseBO) {
		System.out.println("insert into db");
		String title = validationResponseBO.getTitle();
		Integer version = validationResponseBO.getVersion();
		if(title==null || version==null) {
			System.out.println("version or title is null");
			return;
		}
		String sql = "UPDATE atb_data_dump SET version ="+version+",pnr = '"+title+"' WHERE user_id = "+userId+";";
		try {
			PreparedStatement statement = dbConnection.prepareStatement(sql);
			statement.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static CorporateUserDO getUser(Long userId, String gds) {
		String sql = "SELECT user_id,client_id,pnr,version,created_on,updated_on from atb_data_dump where user_id = "+userId+";";
		CorporateUserDO userDO = new CorporateUserDO();
		try {
			PreparedStatement statement = dbConnection.prepareStatement(sql);
			ResultSet rs =  statement.executeQuery();
			while(rs.next()) {
				userDO.setUserId(rs.getLong(1));
				userDO.setClientId(rs.getLong(2));
				userDO.setTitle(rs.getString(3));
				userDO.setVersion(rs.getInt(4));
				userDO.setCreatedOn(rs.getDate(5));
				userDO.setUpdatedOn(rs.getDate(6));
			}
		}catch(Exception exception) {
			System.out.println(exception.getMessage());
		}
		return userDO;
	}
}
