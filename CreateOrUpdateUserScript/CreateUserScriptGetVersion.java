package com.yatra.test;

import java.io.IOException;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.yatra.amadeus.beans.AmadeusUserRecord;
import com.yatra.amadeus.helpers.AmadeusHeadersHelper;
import com.yatra.amadeus.jaxb.user.read.beans.SoapenvEnvelope;
import com.yatra.amadeus.jaxb.user.read.beans.SoapenvEnvelope.SoapenvBody;
import com.yatra.amadeus.jaxb.user.read.beans.SoapenvEnvelope.SoapenvBody.AMAProfileReadRQ.ReadRequests;
import com.yatra.amadeus.jaxb.user.read.beans.SoapenvEnvelope.SoapenvBody.AMAProfileReadRQ.UniqueID;
import com.yatra.amadeus.jaxb.user.read.beans.SoapenvEnvelope.SoapenvBody.AMAProfileReadRQ.ReadRequests.ProfileReadRequest;
import com.yatra.galileo.jaxb.marshaller.SoapenvMarshaller;
import com.yatra.gds.api.util.GdsXmlServiceUtil;
import com.yatra.gds.common.beans.ValidationResponseBO;
import com.yatra.gds.constants.AmadeusConstants;
import com.yatra.gds.manager.enums.AmadeusXmlType;

public class CreateUserScriptGetVersion {
	public static SoapenvMarshaller marshaller;
	public static GdsXmlServiceUtil xmlServiceUtil;
	public static AmadeusHeadersHelper headersHelper;
	
	public static Integer getCurrentProfileVersion(AmadeusUserRecord record, String title, ValidationResponseBO responseBO) {
		SoapenvEnvelope soapEnvelope = populateUserReadRequest(record, title, responseBO);
		String xml = null;
		if (Objects.nonNull(soapEnvelope)) {
			xml = marshaller.marshall(soapEnvelope, AmadeusXmlType.USER_READ);
		} else {
			System.out.printf("SoapEnvelope obtained for read request is empty. Unable to read profile for PNR %s", title);
			return null;
		}
		String response = null;
		if (StringUtils.isNotBlank(xml)) {
			try {
				response = xmlServiceUtil.executeAmadeusRequest(xml, AmadeusConstants.READ_ACTION);
			} catch (IOException e) {
				System.out.printf("Exception occured while executing amadeus read profile request for pnr %s " + e, title);
			}
		}
		if (StringUtils.isNotBlank(response)) {
			CreateUserScriptSession.parseResponse(response);
			int index = response.indexOf("Instance=\"") + 10;
			StringBuilder sb = new StringBuilder();
			while (response.charAt(index) != '\"') {
				sb.append(response.charAt(index));
				index++;
			}
			try {
				Integer version = Integer.valueOf(sb.toString());
				return version;
			} catch (Exception e) {
				System.out.printf("Exception occured while fetching version from response xml for PNR %s read request. ",
						title);
			}

		} else {
			System.out.printf("No response received for profile read request for PNR %d", title);
			return null;
		}
		return null;
	}

	public static SoapenvEnvelope populateUserReadRequest(AmadeusUserRecord record, String title,
			ValidationResponseBO responseBO) {
		SoapenvEnvelope soapEnv = new SoapenvEnvelope();
		headersHelper.setXmlnsElements(soapEnv);
		String pcc = record.getOfficeId();
		
		String session = CreateUserScriptSession.currentSession;
		if ( !CreateUserScriptSession.TRANSACTION_STATUS_CODE_START.equalsIgnoreCase(session) && StringUtils.isNotBlank(CreateUserScriptSession.awsseSecurityToken)
				&& StringUtils.isNotBlank(CreateUserScriptSession.awsseSecurityToken) && CreateUserScriptSession.awsseSequenceNumber != null
				&& CreateUserScriptSession.awsseSequenceNumber != 0) {
			soapEnv.setSoapenvHeader(CreateUserScriptSession.getSoapenvHeaderForStateFull(session,AmadeusConstants.UPDATE_ACTION));
		} else {
			soapEnv.setSoapenvHeader(headersHelper.getSoapenvHeader(AmadeusConstants.UPDATE_ACTION,record.getOfficeId() ));
		}
		
		SoapenvBody body = getSoapenvBody(pcc, title, responseBO);
		if (Objects.isNull(body)) {
			System.out.println("No soapbody created for the user. unable to read Profile");
			return null;
		}
		soapEnv.setSoapenvBody(body);
		return soapEnv;
	}

	public static SoapenvBody getSoapenvBody(String pcc, String title, ValidationResponseBO responseBO) {
		SoapenvBody body = new SoapenvBody();
		SoapenvBody.AMAProfileReadRQ profileCreateRequest = new SoapenvBody.AMAProfileReadRQ();
		body.setAMAProfileReadRQ(profileCreateRequest);
		profileCreateRequest.setVersion(12.2);

		UniqueID uniqueId1 = new UniqueID();
		uniqueId1.setID(pcc);
		uniqueId1.setIDContext(AmadeusConstants.AMADEUS_DATABASE);
		uniqueId1.setType(AmadeusConstants.UNIQUE_ID_TYPE);
		uniqueId1.setXmlns(AmadeusConstants.UNIQUE_ID_XMLNS);
		profileCreateRequest.getUniqueID().add(uniqueId1);

		UniqueID uniqueId2 = new UniqueID();
		uniqueId2.setID(title);
		uniqueId2.setIDContext(AmadeusConstants.AMADEUS_DATABASE);
		uniqueId2.setType(AmadeusConstants.PROFILE_UNIQUE_ID);
		uniqueId2.setXmlns(AmadeusConstants.UNIQUE_ID_XMLNS);
		profileCreateRequest.getUniqueID().add(uniqueId2);

		ReadRequests readRequests = new ReadRequests();
		readRequests.setXmlns(AmadeusConstants.UNIQUE_ID_XMLNS);
		ProfileReadRequest readRequest = new ProfileReadRequest();
		readRequest.setStatus(AmadeusConstants.ACTIVE_PROFILE);
		readRequest.setProfileType(AmadeusConstants.USER_PROFILE_TYPE);
		readRequests.setProfileReadRequest(readRequest);
		profileCreateRequest.setReadRequests(readRequests);
		return body;
	}

}
