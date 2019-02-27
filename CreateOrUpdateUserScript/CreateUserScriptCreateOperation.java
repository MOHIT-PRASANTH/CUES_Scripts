package com.yatra.test;

import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.yatra.amadeus.beans.AmadeusClientRecord;
import com.yatra.amadeus.beans.AmadeusUserRecord;
import com.yatra.amadeus.helpers.AmadeusHeadersHelper;
import com.yatra.amadeus.helpers.AmadeusUserProfileHelper;
import com.yatra.amadeus.jaxb.common.beans.Profile;
import com.yatra.amadeus.jaxb.common.beans.SoapenvHeader;
import com.yatra.amadeus.jaxb.common.beans.SoapenvHeader.AwsseSession;
import com.yatra.amadeus.jaxb.user.create.beans.SoapenvEnvelope;
import com.yatra.amadeus.jaxb.user.create.beans.SoapenvEnvelope.SoapenvBody;
import com.yatra.amadeus.jaxb.user.create.beans.SoapenvEnvelope.SoapenvBody.AMAProfileCreateRQ;
import com.yatra.amadeus.jaxb.user.create.beans.SoapenvEnvelope.SoapenvBody.AMAProfileCreateRQ.UniqueID;
import com.yatra.export.mybatis.mapper.beans.CorporateClientDO;
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
import com.yatra.platform.properties.reader.TypedPropertyReader;

@Component
public class CreateUserScriptCreateOperation {
	public static AmadeusBOMapper mapper;
	public static SoapenvMarshaller marshaller;
	public static AmadeusHeadersHelper headersHelper;
	public static AmadeusUserProfileHelper profileHelper;
	public static AmadeusResponseUtil responseUtil;
	public static GdsXmlServiceUtil xmlServiceUtil;
	
	public static ValidationResponseBO createUserProfile(CorporateUserBO user, ValidationResponseBO responseBO) {
		System.out.println("Create Operation for"+user.toString()+responseBO.toString());
		return createUser(mapper.convert(user),responseBO);
	}

	private static ValidationResponseBO createUser(AmadeusUserRecord userRecord, ValidationResponseBO responseBO) {
		String xml = marshalUserCreationRecordIntoXml(userRecord, responseBO);
		System.out.println("Amadeus user Creation request Xml: "+ xml);
		return uploadDataToGDS(xml, responseBO, AmadeusConstants.CREATE_ACTION, userRecord);
	}
	
	private static String marshalUserCreationRecordIntoXml(AmadeusUserRecord userRecord, ValidationResponseBO responseBO) {
		AmadeusSoapenvEnvelope soapEnv = populateUserCreationRequest(userRecord, responseBO);
		if (Objects.nonNull(soapEnv)) {
			return marshaller.marshall(soapEnv, AmadeusXmlType.USER_CREATION);
		}
		return null;
	}
	
	public static SoapenvEnvelope populateUserCreationRequest(AmadeusUserRecord record, ValidationResponseBO responseBO) {
		CorporateClientDO clientDO = CreateUserScriptDao.getclient(record.getUserProfileData().getClientId(),CommonConstants.GDS_AMADEUS);
		if (Objects.nonNull(clientDO)) {
			String companyTitle = clientDO.getTitle();
			SoapenvEnvelope soapEnv = new SoapenvEnvelope();
			headersHelper.setXmlnsElements(soapEnv);
			String session = CreateUserScriptSession.currentSession;
			if (!CreateUserScriptSession.TRANSACTION_STATUS_CODE_START.equalsIgnoreCase(session) && StringUtils.isNotBlank(CreateUserScriptSession.awsseSecurityToken)
					&& StringUtils.isNotBlank(CreateUserScriptSession.awsseSecurityToken) && CreateUserScriptSession.awsseSequenceNumber != null
					&& CreateUserScriptSession.awsseSequenceNumber != 0) {
				soapEnv.setSoapenvHeader(CreateUserScriptSession.getSoapenvHeaderForStateFull(session,AmadeusConstants.READ_ACTION));
			} else {
				soapEnv.setSoapenvHeader(headersHelper.getSoapenvHeader(AmadeusConstants.CREATE_ACTION, record.getOfficeId()));
			}
			soapEnv.setSoapenvBody(getSoapenvBody(record, companyTitle));
			return soapEnv;
		} else {
			System.out.println("Company for the user {} has not been saved at Amadeus. Unable to save this user." + record.getUserProfileData().getUserId());
			responseBO.setMessage("Company info hasn't been saved for this user at Amadeus");
			return null;
		}

	}

	public static SoapenvBody getSoapenvBody(AmadeusUserRecord record, String companyTitle) {
		SoapenvBody body = new SoapenvBody();
		AMAProfileCreateRQ profileCreateRequest = new AMAProfileCreateRQ();
		body.setAMAProfileCreateRQ(profileCreateRequest);
		profileCreateRequest.setVersion(12.2);

		UniqueID uniqueId = new UniqueID();
		uniqueId.setID(record.getOfficeId());
		uniqueId.setIDContext(AmadeusConstants.AMADEUS_DATABASE);
		uniqueId.setType(AmadeusConstants.UNIQUE_ID_TYPE);
		profileCreateRequest.setUniqueID(uniqueId);

		Profile profile = new Profile();
		profile.setProfileType(AmadeusConstants.USER_PROFILE_TYPE);
		profile.setStatus(AmadeusConstants.ACTIVE_PROFILE);
		profileCreateRequest.setProfile(profile);

		profileHelper.updateCustomerFieldsInProfile(profile, record, companyTitle);
		profileHelper.updatePrefCollectionInProfile(profile, record);
		return body;
	}
	
	public static ValidationResponseBO uploadDataToGDS(String xml, ValidationResponseBO responseBO, String action,
			AccountRecord record) {
		record = record instanceof AmadeusClientRecord ? (AmadeusClientRecord) record : (AmadeusUserRecord) record;
		if (StringUtils.isBlank(xml)) {
			System.out.println("Can't export empty xml for id "+ record.getId());
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

}
