package com.yatra.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.yatra.amadeus.helpers.AmadeusHeadersHelper;
import com.yatra.amadeus.helpers.AmadeusUserProfileHelper;
import com.yatra.export.manager.converters.CorporateUserManagerConverter;
import com.yatra.export.mybatis.mapper.beans.CorporateUserDO;
import com.yatra.galileo.corporate.user.manager.beans.CorporateUserBO;
import com.yatra.galileo.corporate.user.manager.enums.CorporateUserCreateOrUpdateStatus;
import com.yatra.galileo.jaxb.marshaller.SoapenvMarshaller;
import com.yatra.galileo.service.connectors.CorporateProfileServiceConnector;
import com.yatra.galileo.service.connectors.CorporateUserProfileServiceConnector;
import com.yatra.galileo.service.connectors.SingleSignOnServiceConnector;
import com.yatra.gds.api.util.GdsXmlServiceUtil;
import com.yatra.gds.constants.CommonConstants;
import com.yatra.gds.manager.utils.AmadeusResponseUtil;
import com.yatra.gds.mapper.AmadeusBOMapper;
import com.yatra.platform.properties.reader.TypedPropertyReader;

public class CreateUserScript {
	static ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");
	static List<Long> givenUserIds = new ArrayList<>(Arrays.asList(2082875542L,1173902514L,290451390L));
	
	public static void main(String[] args) {
		CreateUserScriptManager.cpsConnector = context.getBean(CorporateProfileServiceConnector.class);
		CreateUserScriptManager.cupsConnector = context.getBean(CorporateUserProfileServiceConnector.class);
		CreateUserScriptManager.ssoConnector = context.getBean(SingleSignOnServiceConnector.class);
		CreateUserScriptManager.converter = context.getBean(CorporateUserManagerConverter.class);
		CreateUserScriptCreateOperation.mapper = context.getBean(AmadeusBOMapper.class);
		CreateUserScriptCreateOperation.marshaller = context.getBean(SoapenvMarshaller.class);
		CreateUserScriptCreateOperation.headersHelper = context.getBean(AmadeusHeadersHelper.class);
		CreateUserScriptSession.propertyReader = context.getBean(TypedPropertyReader.class);
		CreateUserScriptCreateOperation.profileHelper = context.getBean(AmadeusUserProfileHelper.class);
		CreateUserScriptCreateOperation.responseUtil = context.getBean(AmadeusResponseUtil.class); 
		CreateUserScriptCreateOperation.xmlServiceUtil = context.getBean(GdsXmlServiceUtil.class);
		CreateUserScriptUpdateOperation.mapper = context.getBean(AmadeusBOMapper.class);
		CreateUserScriptUpdateOperation.marshaller = context.getBean(SoapenvMarshaller.class);
		CreateUserScriptUpdateOperation.headersHelper = context.getBean(AmadeusHeadersHelper.class);
		CreateUserScriptUpdateOperation.profileHelper = context.getBean(AmadeusUserProfileHelper.class);
		CreateUserScriptGetVersion.marshaller = context.getBean(SoapenvMarshaller.class);
		CreateUserScriptGetVersion.xmlServiceUtil = context.getBean(GdsXmlServiceUtil.class);
		CreateUserScriptUpdateOperation.responseUtil = context.getBean(AmadeusResponseUtil.class);
		CreateUserScriptUpdateOperation.xmlServiceUtil = context.getBean(GdsXmlServiceUtil.class);
		
		System.out.println("START");
		Map<Long, String> clientVsPcc = CreateUserScriptDao.getClientVsPcc(givenUserIds);
		System.out.println("MIDDLE");
		for(Map.Entry<Long, String> entry : clientVsPcc.entrySet()) {
			Long clientId = entry.getKey();
			String pcc = entry.getValue();
			createOrUpdateUsersUnderClient(givenUserIds,clientId,clientVsPcc);
		}
		System.out.println("END");
	}
		
	private static void createOrUpdateUsersUnderClient(List<Long> userIds, Long clientId, Map<Long, String> clientVsPcc) {
		System.out.println("Processing client : "+clientId);
		Map<Long, CorporateUserDO> usersUnderClient = CreateUserScriptDao.getUsersUnderClient(userIds,clientId,false,CommonConstants.GDS_AMADEUS);
		System.out.println("Number of Users under client :"+usersUnderClient.size());
		if(usersUnderClient.size()==0) {
			System.out.println("No users found");
			return;
		}
		userIds = new ArrayList<>(usersUnderClient.keySet()); 
		Map<Long, CorporateUserBO> usersMap = new HashMap<>();
		CorporateUserCreateOrUpdateStatus status = CreateUserScriptManager.updateCompleteUserInfo(userIds, usersMap); 
		if (Objects.nonNull(status)
				&& !CorporateUserCreateOrUpdateStatus.SUCCESS_CREATE_OR_UPDATE_CORPORATE_USER_PROFILE.equals(status)) {
			return;
		}
		Map<Long, CorporateUserDO> alreadyExistingUsers = new HashMap<>();
		
		for(Entry<Long, CorporateUserDO> entry : usersUnderClient.entrySet()) {
			if(entry.getValue().getVersion() != null) {
				alreadyExistingUsers.put(entry.getKey(), entry.getValue());
			}
		}
		status = CreateUserScriptManager.createOrUpdateUsersInAmadeus(usersMap,userIds,clientId,alreadyExistingUsers);
		if (Objects.nonNull(status)) {
			System.out.println("User Data sync failed for amadeus");
		}
	}
}
