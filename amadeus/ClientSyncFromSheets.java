package com.yatra.scripts.amadeus;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.sun.xml.bind.marshaller.CharacterEscapeHandler;
import com.yatra.amadeus.helpers.AmadeusHeadersHelper;
import com.yatra.amadeus.jaxb.user.read.beans.SoapenvEnvelope;
import com.yatra.amadeus.jaxb.user.read.beans.SoapenvEnvelope.SoapenvBody;
import com.yatra.amadeus.jaxb.user.read.beans.SoapenvEnvelope.SoapenvBody.AMAProfileReadRQ.ReadRequests;
import com.yatra.amadeus.jaxb.user.read.beans.SoapenvEnvelope.SoapenvBody.AMAProfileReadRQ.ReadRequests.ProfileReadRequest;
import com.yatra.amadeus.jaxb.user.read.beans.SoapenvEnvelope.SoapenvBody.AMAProfileReadRQ.UniqueID;
import com.yatra.gds.api.util.GdsXmlServiceUtil;
import com.yatra.gds.constants.AmadeusConstants;
import com.yatra.scripts.amadeus.models.CorporateClient;


class ExcelUtils{
	private Workbook workbook;
	private Iterator<Row> currentRowIterator;
	
	static final int clientNameIndex = 0;
	static final int clientIdIndex = 1;
	static final int pccIndex = 2;
	static final int pnrIndex = 3;
	
	static private int currentRowNumber = 0;
	DataFormatter formatter = new DataFormatter();
	
	public ExcelUtils(String pathToFile) throws EncryptedDocumentException, InvalidFormatException, FileNotFoundException, IOException {
		this(pathToFile,false);
	}
	
	public ExcelUtils(String pathToFile, boolean skipHeaders) throws EncryptedDocumentException, InvalidFormatException, FileNotFoundException, IOException {
		this.workbook = WorkbookFactory.create(new FileInputStream(new File(pathToFile.trim())));
		this.currentRowIterator = workbook.getSheetAt(0).iterator();
		if(skipHeaders) {
			if(hasNext()) {
				currentRowIterator.next();
			}
		}
	}
	
	public boolean hasNext() {
		return currentRowIterator.hasNext();
	}
	
	public int getCurrentRowNumber() {
		return currentRowNumber;
	}
	
	public CorporateClient getNextClient() {
		if(!hasNext()) {
			return null;
		}
		CorporateClient client = new CorporateClient();
		Iterator<Cell> cellIterator = currentRowIterator.next().cellIterator();
		while(cellIterator.hasNext()) {
			Cell excelCell = cellIterator.next();
			String cellVal = formatter.formatCellValue(excelCell);
			switch (excelCell.getColumnIndex()) {
			case clientNameIndex:
				client.setName(cellVal);
				break;
			case clientIdIndex:
				client.setClientid(cellVal);
				break;
			case pccIndex:
				client.setPcc(cellVal);
				break;
			case pnrIndex:
				client.setPnr(cellVal);
				break;
			default:
				break;
			}
		}
		currentRowNumber += 1;
		return client;
	}
}

public class ClientSyncFromSheets {

	static ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");
	static String excelFilePath = "C:\\Users\\mohit.prasanth\\Desktop\\CUESdata.xlsx";
	static String outputFilePath = "C:\\Users\\mohit.prasanth\\Desktop\\CUESdataqueries.sql";
	static BufferedWriter bufferedWriter = getBufferedWriter(outputFilePath);
	static AmadeusHeadersHelper headersHelper = context.getBean(AmadeusHeadersHelper.class);
	static GdsXmlServiceUtil xmlServiceUtil = context.getBean(GdsXmlServiceUtil.class);
	
	public static void main(String[] args) throws ClientProtocolException, IOException {
		System.out.println("START");
		try {
			updateClientPnrsFromSheet();			
		}catch (Exception e) {
			System.out.println(e);
		}
		System.out.println("END");
	}
	
	private static BufferedWriter getBufferedWriter(String outputFilePath) {
		File file = new File(outputFilePath);
		FileWriter fw = null;
		try {
			fw = new FileWriter(file.getAbsoluteFile(), true);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return new BufferedWriter(fw);
	}
	
	private static void insertClientConfig(CorporateClient client) {
		String insertClientConfigQuery = new StringBuilder(
				"INSERT INTO `client_config` (`client_id`, `type`, `push_user_profile`, `push_user_card`, `pcc`, `is_enabled`, `created_on`, `updated_on`) VALUES (")
						.append(client.getClientid()).append(",\"AMADEUS\",1,1,").append("\"" + client.getPcc() + "\"")
						.append(",0, NOW(),NOW());\n").toString();
		try {
			bufferedWriter.write(insertClientConfigQuery);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void insertClientConfigData(CorporateClient client) {
		String insertClientDataQuery = new StringBuilder(
				"INSERT INTO `corporate_client_data` (`client_id`, `status`, `remarks`, `type`, `title`, `version`, `created_on`, `updated_on`) VALUES (")
						.append(client.getClientid()).append(",").append("\"UPDATION_SUCCESS\"").append(",")
						.append("\"Successful\"").append(",").append("\"AMADEUS\"").append(",")
						.append("\"" + client.getPnr() + "\"").append(",").append(client.getVersion()).append(",0, NOW(),NOW());")
						.append("\n").toString();
		try {
			bufferedWriter.write(insertClientDataQuery);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static Integer getVersionFromResponse(CorporateClient client,String response) {
		try {
			int index = response.indexOf("Instance=\"") + 10;
			StringBuilder version = new StringBuilder();
			while (response.charAt(index) != '\"') {
				version.append(response.charAt(index));
				index++;
			}
			return Integer.parseInt(version.toString());
		}
		catch (Exception e) {
			System.out.println("Exception while retrieving version from response for client: "+client.getClientid());
		}
		return null;
	}
	
	private static void updateClientPnrsFromSheet() throws EncryptedDocumentException, InvalidFormatException, FileNotFoundException, IOException {
		ExcelUtils excelReader = new ExcelUtils(excelFilePath);
		List<String> failedClients = new ArrayList<>();
		Map<String, String> failedClientsForWrongPnrs = new HashMap<>();
		
		while(excelReader.hasNext()) {
			CorporateClient client = excelReader.getNextClient();
			if (StringUtils.isBlank(client.getClientid()) || StringUtils.isBlank(client.getPcc()) || StringUtils.isBlank(client.getPnr())) {
				failedClients.add(client.getClientid());
				if(StringUtils.isBlank(client.getPnr())) {
					insertClientConfig(client);
				}
				continue;
			}
			SoapenvEnvelope soapEnv = populateCorporateReadRequest(client.getPcc(), client.getPnr());
			String xml = marshall(soapEnv);
			if(StringUtils.isNoneBlank(xml)) {
				try {
					String response = xmlServiceUtil.executeAmadeusRequest(xml, AmadeusConstants.READ_ACTION);
					Integer version = getVersionFromResponse(client, response);
					if(version!=null && version>0) {
						client.setVersion(Integer.toString(version));
						insertClientConfigData(client);
					}else {
						System.out.println("Got invalid version for client " + client.getClientid());
						failedClientsForWrongPnrs.put(client.getClientid()+" "+client.getName(),client.getPnr());
					}
				}
				catch (Exception e) {
					System.out.println("Exception while retrieving version for client: "+client.getClientid());
					failedClientsForWrongPnrs.put(client.getClientid()+" "+client.getName(),client.getPnr());
				}
				insertClientConfig(client);
			}
			System.out.println("Client ["+excelReader.getCurrentRowNumber()+"]: "+client.toString());
		}
		
		System.out.println("Failed Clients: ");
		failedClients.stream().forEach(client -> {
			System.out.print(client + ", ");
		});
		
		System.out.println("wrong pnrs list: ");
		Iterator<Map.Entry<String, String>> iterator = (Iterator<Map.Entry<String, String>>) failedClientsForWrongPnrs
				.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, String> entry = iterator.next();
			System.out.println(new StringBuilder(entry.getKey()).append(" ").append(entry.getValue()).toString());
		}
	}

	public static SoapenvEnvelope populateCorporateReadRequest(String pcc, String title) {
		SoapenvEnvelope soapEnv = new SoapenvEnvelope();
		int profileType = 3;
		headersHelper.setXmlnsElements(soapEnv);
		soapEnv.setSoapenvHeader(headersHelper.getSoapenvHeader(AmadeusConstants.READ_ACTION, pcc));
		SoapenvBody body = getSoapenvBody(pcc, title, profileType);
		if (Objects.isNull(body)) {
			System.out.println("No soapbody created for the corporate. unable to read Profile");
			return null;
		}
		soapEnv.setSoapenvBody(body);
		return soapEnv;
	}

	public static SoapenvBody getSoapenvBody(String pcc, String id, int profileType) {
		SoapenvBody body = new SoapenvBody();
		SoapenvBody.AMAProfileReadRQ profileCreateRequest = new SoapenvBody.AMAProfileReadRQ();
		body.setAMAProfileReadRQ(profileCreateRequest);
		profileCreateRequest.setVersion(12.2);

		UniqueID uniqueId1 = new UniqueID();
		uniqueId1.setID(pcc);
		uniqueId1.setIDContext("CSX");
		uniqueId1.setType(9);
		uniqueId1.setXmlns("http://xml.amadeus.com/2008/10/AMA/Profile");
		profileCreateRequest.getUniqueID().add(uniqueId1);

		UniqueID uniqueId2 = new UniqueID();
		uniqueId2.setID(id);
		uniqueId2.setIDContext("CSX");
		uniqueId2.setType(21);
		uniqueId2.setXmlns("http://xml.amadeus.com/2008/10/AMA/Profile");
		profileCreateRequest.getUniqueID().add(uniqueId2);

		ReadRequests readRequests = new ReadRequests();
		readRequests.setXmlns("http://xml.amadeus.com/2008/10/AMA/Profile");
		ProfileReadRequest readRequest = new ProfileReadRequest();
		readRequest.setStatus("A");
		readRequest.setProfileType(profileType);
		readRequests.setProfileReadRequest(readRequest);
		profileCreateRequest.setReadRequests(readRequests);
		return body;

	}

	public static String marshall(SoapenvEnvelope soapEnv) {
		JAXBContext context;
		StringWriter stringWriter = new StringWriter();
		try {
			context = JAXBContext.newInstance(soapEnv.getClass().getPackage().getName());
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
			marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
			marshaller.setProperty(CharacterEscapeHandler.class.getName(), new CharacterEscapeHandler() {
				@Override
				public void escape(char[] ac, int i, int j, boolean flag, Writer writer) throws IOException {
					writer.write(ac, i, j);
				}
			});
			marshaller.marshal(soapEnv, stringWriter);
		} catch (JAXBException e) {
			e.printStackTrace();
			return null;
		}
		return stringWriter.toString();
	}

}
