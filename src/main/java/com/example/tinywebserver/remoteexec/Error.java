package com.example.tinywebserver.remoteexec;

public class Error {
	private Integer errorCode;
	private String errorMsg;

	public Error(Integer errorCode, String errorMsg) {
		super();
		this.errorCode = errorCode;
		this.errorMsg = errorMsg;
	}

}
