package com.simon_transporte.suite.db.pojo;

import jakarta.jws.WebService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@WebService
@Entity
@XmlAccessorType(XmlAccessType.FIELD)
public class Address {

	public Address() {
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private String firstName;

	@Column(nullable = false)
	private String lastName;

	@Column(length = 1)
	private String sex;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (lastName != null) {
			sb.append(lastName);
		}

		if (firstName != null) {
			sb.append(',');
			sb.append(firstName);
		}

		return sb.toString();
	}

	public String getSex() {
		return sex;
	}

	public void setSex(String sex) {
		this.sex = sex;
	}

	@Transient
	public void setSex(char sex) {
		this.sex = new StringBuilder().append(sex).toString();
	}

}
