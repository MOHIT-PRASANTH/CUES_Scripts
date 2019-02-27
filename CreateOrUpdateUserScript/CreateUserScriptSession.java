package com.yatra.test;

import java.io.StringReader;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.yatra.amadeus.jaxb.common.beans.SoapenvHeader;
import com.yatra.amadeus.jaxb.common.beans.SoapenvHeader.AwsseSession;
import com.yatra.gds.constants.AmadeusConstants;
import com.yatra.platform.properties.reader.TypedPropertyReader;

public class CreateUserScriptSession {
	public static TypedPropertyReader propertyReader;
	
	public static String awsseSessionId = "";
	public static Integer awsseSequenceNumber = 1;
	public static String awsseSecurityToken = "";
	public static final String TRANSACTION_STATUS_CODE_START = "Start";
	public static final String TRANSACTION_STATUS_CODE_IN_SERIES = "InSeries";
	public static final String TRANSACTION_STATUS_CODE_END = "End";
	public static String currentSession = "";
	
	public static void parseResponse(String xml) {
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.parse(new InputSource(new StringReader(xml)));
			NodeList nList = doc.getElementsByTagName("awsse:Session");
			if (nList != null && nList.getLength() > 0) {
				Element eElement = (Element) nList.item(0);
				CreateUserScriptSession.awsseSessionId = eElement.getElementsByTagName("awsse:SessionId").item(0).getTextContent();
				String SequenceNumber = eElement.getElementsByTagName("awsse:SequenceNumber").item(0).getTextContent();
				if (StringUtils.isNotBlank(SequenceNumber)) {
					CreateUserScriptSession.awsseSequenceNumber = Integer.valueOf(SequenceNumber);
				}
				CreateUserScriptSession.awsseSecurityToken = eElement.getElementsByTagName("awsse:SecurityToken").item(0).getTextContent();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static SoapenvHeader getSoapenvHeaderForStateFull(String statusCode,String action) {
		SoapenvHeader header = new SoapenvHeader();
		header.setXmlnsAdd(AmadeusConstants.XMLNS_ADD);
		header.setAddMessageID(UUID.randomUUID().toString());
		header.setAddAction(action);
		header.setAddTo(propertyReader.getString(AmadeusConstants.WEB_SERVICES_ENDPOINT_PREFIX)
				+ propertyReader.getString(AmadeusConstants.WSAP));
		AwsseSession session = new AwsseSession();
		session.setTransactionStatusCode(statusCode);
		session.setXmlnsAwsse(AmadeusConstants.AWS_SE_SESSION_XMLNS);
		session.setAwsseSessionId(CreateUserScriptSession.awsseSessionId);
		session.setAwsseSequenceNumber(CreateUserScriptSession.awsseSequenceNumber);
		session.setAwsseSecurityToken(CreateUserScriptSession.awsseSecurityToken);
		header.setAwsseSession(session);
		return header;
	}
}
