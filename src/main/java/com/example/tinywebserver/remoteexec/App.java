package com.example.tinywebserver.remoteexec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.servlet.MultipartConfigElement;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.profesorfalken.jpowershell.PowerShell;
import com.profesorfalken.jpowershell.PowerShellNotAvailableException;

/**
 * Program to remotely execute powershell command using a tiny webserver
 * /getstatus /downloadfile /executescript are the endpoints to query.
 * 
 * @author Suhail_Sullad
 *
 */
public class App {

	private static Logger log = LoggerFactory.getLogger(App.class);
	private static Gson gsonObj = new Gson();
	private static Integer DEFAULT_PORT = 80;
	private static Long MAX_SIZE = 1610612736L;
	private static Long MAX_REQ_SIZE = 1621098496L;
	private static Integer MAX_THRESHOLD = 10485760;
	private static String MEDIA_TYPE = "application/json";
	private static Map<String, String> myConfig = new HashMap<>();
	private static MultipartConfigElement upconfig = null;
	private static CompletionService<Boolean> myExecutor = new ExecutorCompletionService<>(
			Executors.newFixedThreadPool(10));
	private static List<Future<Boolean>> futureList = new CopyOnWriteArrayList<>();

	// private static Boolean executionStatus = false;
	public static HashMap<String, CommandPayload> reqMap = new HashMap<>();

	private static Options options = new Options();

	public static void main(String[] args) {
		myConfig.put("maxWait", "1200000"); // 20 minutes wait for script output
											// to complete.
		myConfig.put("waitPause", "200"); // .1 sec pause before querying for
											// output stream.

		options.addOption("p", "port", true, "Port for spark server to ignite");
		options.addOption("m", "max-size", true, "Maximum file size for upload");
		options.addOption("r", "max-req-size", true, "Maximum request size");
		options.addOption("t", "max-threshold", true, "Threshold file size for upload");
		options.addOption("h", "help", false, "Help options");
		CommandLineParser parser = new DefaultParser();
		try {
			PowerShell ps = PowerShell.openSession();
			ps.close();
		} catch (PowerShellNotAvailableException e1) {
			log.error("Powershell not found on system. Program cannot continue.");
			System.exit(1);
		}

		File uploadDir = new File("upload");
		uploadDir.mkdir();
		File downloadDir = new File("download");
		downloadDir.mkdir();
		try {
			CommandLine line = parser.parse(options, args);
			if (line.getOptions().length == 0) {
				log.info("Using default options!! Port:" + DEFAULT_PORT + " Max file size:" + (MAX_SIZE / (1024 * 1024))
						+ " MB , Max request size:" + (MAX_REQ_SIZE / (1024 * 1024)) + " MB , File threshold:"
						+ (MAX_THRESHOLD / (1024 * 1024)) + " MB");
			}

			for (Option opt : line.getOptions()) {
				switch (opt.getOpt()) {
				case "p":
					try {
						Integer portArg = Integer.parseInt(line.getOptionValue("p"));
						if (null != portArg || portArg > 0 || portArg < 65536)
							DEFAULT_PORT = portArg;
					} catch (Exception e) {
						// ignore the input
					}
					break;
				case "m":
					try {
						MAX_SIZE = Long.parseLong(line.getOptionValue("m"));
					} catch (Exception e) {
						// ignore the input
					}
					break;
				case "r":
					try {
						MAX_REQ_SIZE = Long.parseLong(line.getOptionValue("r"));
					} catch (Exception e) {
						// ignore the input
					}
					break;
				case "t":
					try {
						MAX_THRESHOLD = Integer.parseInt(line.getOptionValue("t"));
					} catch (Exception e) {
						// ignore the input
					}
					break;
				case "h":
					new HelpFormatter().printHelp("java -jar remote-exec-x.x.x-jar-with-dependencies.jar", options);
					System.exit(0);
				}
			}
		} catch (Exception e) {
			new HelpFormatter().printHelp("java -jar remote-exec-x.x.x-jar-with-dependencies.jar", options);
			System.exit(0);
		}
		log.info("Setting upload configurations");
		upconfig = new MultipartConfigElement("./upload", MAX_SIZE, MAX_REQ_SIZE, MAX_THRESHOLD);

		log.info("Initiating remote listner on: 0.0.0.0:" + DEFAULT_PORT);
		spark.Spark.threadPool(10);
		spark.Spark.port(DEFAULT_PORT);
		spark.Spark.webSocketIdleTimeoutMillis(1200000);

		spark.Spark.before((request, response) -> {
			log.info("Receiving request from:" + request.ip());
		});

		// Spark end points for communication
		spark.Spark.get("/getstatus", (request, response) -> {
			response.type(MEDIA_TYPE);
			return gsonObj.toJson(new Error(0, "Spark is Active"));
		});

		spark.Spark.get("/getexecstatus", (request, response) -> {
			response.type(MEDIA_TYPE);
			String key = request.queryParams("reqid");
			if (null != key && null != reqMap.get(key)) {
				String data = gsonObj.toJson(reqMap.get(key));
				reqMap.remove(key);
				return data;
			}

			futureList.stream().filter(status -> status.isDone()).forEach(completedTask -> {
				futureList.remove(completedTask);
			});

			log.info("Count of Futures : " + futureList.size());
			return gsonObj.toJson(null);
		});
		spark.Spark.get("/downloadfile", (request, response) -> {
			log.info("Query parameter 'downloadurl':" + request.queryParams("downloadurl"));
			if (null == request.queryParams("downloadurl") || request.queryParams("downloadurl").trim().isEmpty()) {
				response.type(MEDIA_TYPE);
				return gsonObj.toJson(new Error(2, "Bad Script URL. [downloadurl] query parameter missing."));
			} else {
				try {
					String downloadedFile = ScriptDownloader.download(request.queryParams("downloadurl"));
					response.type(MEDIA_TYPE);
					response.status(HttpStatus.OK_200);

					return gsonObj.toJson(new Error(null, "File Downloaded:" + downloadedFile));
				} catch (Exception e) {
					return gsonObj.toJson(new Error(3, e.getMessage()));
				}
			}
		});

		spark.Spark.get("/uploadfile",
				(req, res) -> "<form method='post' action='/uploadfile' enctype='multipart/form-data'>"
						+ "    <input type='file' name='uploaded_file' accept='.txt'>"
						+ "    <button type='submit'>Upload file</button>" + "</form>");

		// boolean isMultipart = ServletFileUpload.isMultipartContent(req);

		spark.Spark.post("/uploadfile", (req, res) -> {

			req.attribute("org.eclipse.jetty.multipartConfig", upconfig);
			try {
				Part part = req.raw().getPart("uploaded_file");
				String disposition = part.getHeader("Content-Disposition");
				String fileName = disposition.replaceFirst("(?i)^.*filename=\"([^\"]+)\".*$", "$1");
				File tempFile = new File("upload/" + fileName);
				FileOutputStream fos = new FileOutputStream(tempFile);
				InputStream is = part.getInputStream();
				byte[] buffer = new byte[4096];
				int len;
				while ((len = is.read(buffer)) > 0) {
					fos.write(buffer);
					fos.flush();
				}
				fos.close();
				is.close();
				return gsonObj.toJson(new Error(null, tempFile.getName() + " has been uploaded"));
			} catch (Exception e) {
				return gsonObj.toJson(new Error(null, e.getMessage()));
			}

		});

		spark.Spark.post("/executescript", (request, response) -> {
			log.info("receiving command to execute.");
			response.type(MEDIA_TYPE);

			/*
			 * if (executionStatus) { response.status(HttpStatus.FORBIDDEN_403);
			 * return gsonObj.toJson(new Error(9,
			 * "Another execution in progress")); }
			 * 
			 * executionStatus = true;
			 */
			/*
			 * if (psSession == null) { psSession = PowerShell.openSession(); }
			 */

			if (request.body().isEmpty()) {
				response.status(HttpStatus.BAD_REQUEST_400);
				return gsonObj.toJson(new Error(0, "Invalid JSON"));
			} else {
				try {
					CommandPayload commobj = gsonObj.fromJson(request.body(), CommandPayload.class);
					if (!commobj.isValid()) {
						response.status(HttpStatus.BAD_REQUEST_400);
						return gsonObj.toJson(new Error(1, "Invalid JSON"));
					}
					// Code to execute script and return result.
					log.info("Executing command:" + commobj.getCommand());
					String x = UUID.randomUUID().toString();
					log.info("UUID:" + x);
					commobj.setRequest_uuid(x);
					reqMap.put(commobj.getRequest_uuid(), null);
					log.info("submitting callable");
					futureList.add(myExecutor.submit(new Callable<Boolean>() {
						@Override
						public Boolean call() throws PowerShellNotAvailableException {
							PowerShell psSession;
							psSession = PowerShell.openSession();
							log.info("Into callable");
							if (commobj.getOutput_to_file()) {
								psSession.configuration(myConfig).executeCommand(commobj.getCommand()
										+ " 2>&1 > $env:temp/temp" + x + ".log; Write-Host 'Done..'");

								log.info("Output written into %TEMP%/temp" + x + ".log");
								commobj.setResult(psSession.configuration(myConfig)
										.executeCommand("Get-Content $env:temp/temp" + x + ".log").getCommandOutput());

							} else
								commobj.setResult(psSession.configuration(myConfig).executeCommand(commobj.getCommand())
										.getCommandOutput());
							psSession.close();
							log.info("Callable has completed for :" + commobj.getCommand());
							reqMap.put(commobj.getRequest_uuid(), commobj);
							return true;
						}
					}));
					response.status(HttpStatus.OK_200);
					log.info(gsonObj.toJson(commobj));
					return gsonObj.toJson(commobj);
				} catch (JsonSyntaxException jsex) {
					response.status(HttpStatus.BAD_REQUEST_400);
					return gsonObj.toJson(new Error(-1, jsex.getMessage()));
				}
			}
		});

		spark.Spark.get("/getfilefromclient", (request, response) -> {
			log.info("Query parameter 'clientfilepath':" + request.queryParams("clientfilepath"));
			if (null == request.queryParams("clientfilepath")
					|| request.queryParams("clientfilepath").trim().isEmpty()) {
				response.type(MEDIA_TYPE);
				return gsonObj.toJson(new Error(2, "Bad Script URL. [clientfilepath] query parameter missing."));
			} else {
				try {

					Path path = Paths.get(request.queryParams("clientfilepath"));
					byte[] data = null;
					try {
						data = Files.readAllBytes(path);
					} catch (Exception e1) {
						return gsonObj.toJson(new Error(5, e1.getMessage()));
					}

					HttpServletResponse raw = response.raw();
					response.header("Content-Disposition", "attachment; filename=" + path.getFileName().toString());
					response.type("application/force-download");
					try {
						raw.getOutputStream().write(data);
						raw.getOutputStream().flush();
						raw.getOutputStream().close();
					} catch (Exception e) {
						return gsonObj.toJson(new Error(4, e.getMessage()));
					}
					return raw;
				} catch (Exception e) {
					return gsonObj.toJson(new Error(3, e.getMessage()));
				}
			}
		});

		Runtime.getRuntime().addShutdownHook(new Thread() {

			public void run() {
				try {
					Thread.sleep(200);
					System.out.println("Shutting down ...");
					futureList.forEach(f -> {
						f.cancel(true);
					});
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}
}
