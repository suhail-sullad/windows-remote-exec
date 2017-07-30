package com.example.tinywebserver.remoteexec;

import java.io.File;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;

import com.github.axet.wget.WGet;

public class ScriptDownloader {

	public static String download(String fileUrl) throws Exception {
		String fileName = StringUtils.substringAfterLast(fileUrl, "/");
		WGet w = new WGet(new URL(fileUrl), new File("./download/" + fileName));
		w.download();
		return fileName;
	}
}
