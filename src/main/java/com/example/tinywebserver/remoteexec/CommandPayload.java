package com.example.tinywebserver.remoteexec;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class CommandPayload {
	private String command;
	private List<String> args;
	private Boolean output_to_file=false;
	private String request_uuid=null;
	
	public Boolean getOutput_to_file() {
		return output_to_file;
	}

	public void setOutput_to_file(Boolean output_to_file) {
		this.output_to_file = output_to_file;
	}

	private String result;

	public String getCommand() {
		if (args == null || args.isEmpty())
			return command;

		return (command + " " + StringUtils.join(args, ' ')).trim();
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public List<String> getArgs() {
		return args;
	}

	public void setArgs(List<String> args) {
		this.args = args;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public CommandPayload(String string, List<String> asList) {
		this.command = string;
		this.args = asList;
	}

	public boolean isValid() {
		return command != null && !command.isEmpty();
	}

	public String getRequest_uuid() {
		return request_uuid;
	}

	public void setRequest_uuid(String request_uuid) {
		this.request_uuid = request_uuid;
	}
}
