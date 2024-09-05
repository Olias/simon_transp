package com.simon_transporte.suite.db.helpers;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;

public class ListResult {

	private int count = 0;
	private int page = 0;

	private String type;

	private List<ListEntry> entrys = new ArrayList<ListResult.ListEntry>();

	@XmlElementWrapper(name = "results")
	@XmlElement(name = "result")
	public List<ListEntry> getEntrys() {
		return entrys;
	}

	public void setEntrys(List<ListEntry> entrys) {
		this.entrys = entrys;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public static class ListEntry {

		private String id;
		private String toString;

		public String getToString() {
			return toString;
		}

		public void setToString(String toString) {
			this.toString = toString;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

	}
}
