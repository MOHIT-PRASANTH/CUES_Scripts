package com.yatra.test;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.transport.Session;
import org.springframework.beans.factory.annotation.Autowired;

import com.yatra.amadeus.beans.AmadeusClientRecord;
import com.yatra.amadeus.beans.AmadeusUserRecord;
import com.yatra.amadeus.helpers.AmadeusHeadersHelper;
import com.yatra.amadeus.helpers.AmadeusUserProfileHelper;
import com.yatra.amadeus.jaxb.common.beans.Profile;
import com.yatra.amadeus.jaxb.user.update.beans.SoapenvEnvelope;
import com.yatra.amadeus.jaxb.user.update.beans.SoapenvEnvelope.SoapenvBody;
import com.yatra.amadeus.jaxb.user.update.beans.SoapenvEnvelope.SoapenvBody.AMAUpdateRQ;
import com.yatra.amadeus.jaxb.user.update.beans.SoapenvEnvelope.SoapenvBody.AMAUpdateRQ.Position;
import com.yatra.amadeus.jaxb.user.update.beans.SoapenvEnvelope.SoapenvBody.AMAUpdateRQ.UniqueID;
import com.yatra.amadeus.jaxb.user.update.beans.SoapenvEnvelope.SoapenvBody.AMAUpdateRQ.Position.Root;
import com.yatra.export.mybatis.mapper.beans.CorporateClientDO;
import com.yatra.export.mybatis.mapper.beans.CorporateUserDO;
import com.yatra.galileo.common.beans.Status;
import com.yatra.galileo.corporate.user.manager.beans.CorporateUserBO;
import com.yatra.galileo.jaxb.marshaller.SoapenvMarshaller;
import com.yatra.gds.api.util.GdsXmlServiceUtil;
import com.yatra.gds.common.beans.AccountRecord;
import com.yatra.gds.common.beans.ValidationResponseBO;
import com.yatra.gds.constants.AmadeusConstants;
import com.yatra.gds.constants.CommonConstants;
import com.yatra.gds.jaxb.common.beans.AmadeusSoapenvEnvelope;
import com.yatra.gds.manager.enums.AmadeusXmlType;
import com.yatra.gds.manager.utils.AmadeusResponseUtil;
import com.yatra.gds.mapper.AmadeusBOMapper;

public class CreateUserScriptUpdateOperation {
	public static AmadeusBOMapper mapper;
	public static SoapenvMarshaller marshaller;
	public static AmadeusHeadersHelper headersHelper;
	public static AmadeusUserProfileHelper profileHelper;
	public static AmadeusResponseUtil responseUtil;
	public static GdsXmlServiceUtil xmlServiceUtil;
	
	public static ValidationResponseBO updateUserProfile(CorporateUserBO user, ValidationResponseBO responseBO, boolean isRetry) {
		System.out.println("Update Operation for"+user.toString()+responseBO.toString());
		return updateUser(mapper.convert(user),responseBO, isRetry);
	}

	public static ValidationResponseBO updateUser(AmadeusUserRecord userRecord,ValidationResponseBO responseBO, boolean isRetry) {
		String xml = marshalUserUpdateRecordIntoXml(userRecord, responseBO, isRetry);
		System.out.println("Amadeus user update request Xml: " + xml);
		return uploadDataToGDS(xml, responseBO, AmadeusConstants.UPDATE_ACTION, userRecord);
	}
	
	private static String marshalUserUpdateRecordIntoXml(AmadeusUserRecord userRecord, ValidationResponseBO responseBO, boolean isRetry) {
		AmadeusSoapenvEnvelope soapEnv = populateUserUpdateRequest(userRecord, responseBO, isRetry);
		if (Objects.nonNull(soapEnv)) {
			return marshaller.marshall(soapEnv, AmadeusXmlType.USER_UPDATE);
		}
		return null;
	}
	
	public static ValidationResponseBO uploadDataToGDS(String xml, ValidationResponseBO responseBO, String action,
			AccountRecord record) {
		record = record instanceof AmadeusClientRecord ? (AmadeusClientRecord) record : (AmadeusUserRecord) record;
		if (StringUtils.isBlank(xml)) {
			System.out.println("Can't export empty xml for id"+ record.getId());
			return responseUtil.updateFailureResponse(responseBO, record, action, null);
		}
		try {
			String result = xmlServiceUtil.executeAmadeusRequest(xml, action);
			CreateUserScriptSession.parseResponse(result);
			System.out.println("result:" + xmlServiceUtil.prettyFormat(result));
			responseBO = responseUtil.buildResponseFromResult(responseBO, result, action, record);
		} catch (Exception e) {
			System.out.println("Exception occured while exporting {} details"+ record.getClass().getSimpleName() + e);
			return responseUtil.updateFailureResponse(responseBO, record, action,
					AmadeusConstants.FAILURE_WITH_EXCEPTION_RESPONSE.concat(e.getClass().getSimpleName()));
		}
		return responseBO;
	}
	
	public static SoapenvEnvelope populateUserUpdateRequest(AmadeusUserRecord record, ValidationResponseBO responseBO,
			boolean isRetry) {
		CorporateClientDO clientDO = CreateUserScriptDao.getclient(record.getUserProfileData().getClientId(),CommonConstants.GDS_AMADEUS);
		if (Objects.nonNull(clientDO)) {
			String companyTitle = clientDO.getTitle();
			SoapenvEnvelope soapEnv = new SoapenvEnvelope();
			headersHelper.setXmlnsElements(soapEnv);
			String session = CreateUserScriptSession.currentSession;
			if ( !CreateUserScriptSession.TRANSACTION_STATUS_CODE_START.equalsIgnoreCase(session) && StringUtils.isNotBlank(CreateUserScriptSession.awsseSecurityToken)
					&& StringUtils.isNotBlank(CreateUserScriptSession.awsseSecurityToken) && CreateUserScriptSession.awsseSequenceNumber != null
					&& CreateUserScriptSession.awsseSequenceNumber != 0) {
				soapEnv.setSoapenvHeader(CreateUserScriptSession.getSoapenvHeaderForStateFull(session,AmadeusConstants.UPDATE_ACTION));
			} else {
				soapEnv.setSoapenvHeader(headersHelper.getSoapenvHeader(AmadeusConstants.UPDATE_ACTION,record.getOfficeId() ));
			}
			SoapenvBody body = getSoapenvBody(record, companyTitle, responseBO, isRetry);
			if (Objects.isNull(body)) {
				System.out.println("No soapbody created for the user. unable to update Profile");
				return null;
			}
			soapEnv.setSoapenvBody(body);
			return soapEnv;
		} else {
			System.out.println("Company for the user {} has not been saved at Amadeus. Unable to save this user." + record.getUserProfileData().getUserId());
			responseBO.setMessage("Company info hasn't been saved for this user at Amadeus");
			return null;
		}
	}
	
	public static SoapenvBody getSoapenvBody(AmadeusUserRecord record, String companyTitle, ValidationResponseBO responseBO,
			boolean isRetry) {
		Long userId = record.getUserProfileData().getUserId();
		CorporateUserDO userDO = CreateUserScriptDao.getUser(userId, CommonConstants.GDS_AMADEUS);
		if (Objects.isNull(userDO) || StringUtils.isBlank(userDO.getTitle())) {
			System.out.println("Could not find existing profile for user {}. Unable to update profile for this user.");
			responseBO.setMessage("No existing profile found for the user to update");
			return null;
		}
		SoapenvBody body = new SoapenvBody();
		AMAUpdateRQ updateRequest = new AMAUpdateRQ();
		body.setAMAUpdateRQ(updateRequest);
		updateRequest.setXmlns(AmadeusConstants.UNIQUE_ID_XMLNS);
		updateRequest.setVersion(AmadeusConstants.AMADEUS_PROFILE_WEBSERVICE_VERSION);

		UniqueID uniqueId = new UniqueID();
		uniqueId.setID(userDO.getTitle());
		uniqueId.setIDContext(AmadeusConstants.AMADEUS_DATABASE);
		uniqueId.setType(AmadeusConstants.PROFILE_UNIQUE_ID);
		if (!isRetry) {
			uniqueId.setInstance(userDO.getVersion());
		} else {
			Integer currentVersion = getCurrentVersionFromAmadeus(record,userDO.getTitle(), responseBO);
			if (Objects.nonNull(currentVersion) && currentVersion > 0) {
				System.out.printf(
						"Current version for profile %s found in amadeus is %d. updating the profile with this version.",
						userDO.getTitle(), currentVersion);
				uniqueId.setInstance(currentVersion);
			} else {
				System.out.printf("No profile found in amadeus for PNR %s. Unable to update this profile.",
						userDO.getTitle());
				responseBO.setStatus(Status.UPDATION_FAILURE);
				responseBO.setTitle(userDO.getTitle());
				responseBO.setMessage("No profile found in amadeus for PNR");
				return null;
			}
		}
		updateRequest.setUniqueID(uniqueId);
		Position position = new Position();
		position.setXPath(AmadeusConstants.XPATH);
		updateRequest.setPosition(position);
		updatePositionElements(position, record, companyTitle);
		return body;
	}

	private static void updatePositionElements(Position position, AmadeusUserRecord record, String companyTitle) {
		Root root = new Root();
		position.setRoot(root);
		root.setOperation(AmadeusConstants.OPERATION_REPLACE);

		SoapenvEnvelope.SoapenvBody.AMAUpdateRQ.Position.Root.UniqueID officeUniqueId = new SoapenvEnvelope.SoapenvBody.AMAUpdateRQ.Position.Root.UniqueID();
		officeUniqueId.setID(record.getOfficeId());
		officeUniqueId.setIDContext(AmadeusConstants.AMADEUS_DATABASE);
		officeUniqueId.setType(AmadeusConstants.UNIQUE_ID_TYPE);
		root.setUniqueID(officeUniqueId);

		Profile profile = new Profile();
		profile.setProfileType(AmadeusConstants.USER_PROFILE_TYPE);
		profile.setStatus(AmadeusConstants.ACTIVE_PROFILE);
		root.setProfile(profile);
		profileHelper.updateCustomerFieldsInProfile(profile, record, companyTitle);
		profileHelper.updatePrefCollectionInProfile(profile, record);
	}

	private static Integer getCurrentVersionFromAmadeus(AmadeusUserRecord record,String title, ValidationResponseBO responseBO) {
		return CreateUserScriptGetVersion.getCurrentProfileVersion(record,title, responseBO);
	}
}
