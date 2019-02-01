package com.yatra.scripts.amadeus.models;

public class CorporateClient {
	private String name;
	private String clientid;
	private String pcc;
	private String pnr;
	private String version;
	
	
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getClientid() {
		return clientid;
	}
	public void setClientid(String clientid) {
		this.clientid = clientid;
	}
	public String getPcc() {
		return pcc;
	}
	public void setPcc(String pcc) {
		this.pcc = pcc;
	}
	public String getPnr() {
		return pnr;
	}
	public void setPnr(String pnr) {
		this.pnr = pnr;
	}
	@Override
	public String toString() {
		return "CorporateClient [name=" + name + ", clientid=" + clientid + ", pcc=" + pcc + ", pnr=" + pnr + ", version=" + version + "]";
	}
}
