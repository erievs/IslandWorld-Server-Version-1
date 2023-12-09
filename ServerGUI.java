package main;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;

public class Main implements HttpHandler {

	private HttpServer server;
	private boolean isServerRunning = false;
	private long downloadRate;

	private JFrame frame;
	private JLabel statusLabel;
	private JLabel downloadRateLabel;

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new Main().createAndShowGUI());
	}

	private void createAndShowGUI() {
		

		// UI

		downloadRateLabel = new JLabel("IslandWorld Sever 1");

		frame = new JFrame("HTTP Server with Bandwidth Limiting");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		statusLabel = new JLabel("Server is stopped");

		JButton startButton = new JButton("Start Server");
		JButton stopButton = new JButton("Stop Server");

		// Set button actions
		startButton.addActionListener(e -> startServer());
		stopButton.addActionListener(e -> stopServer());

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(statusLabel);
		panel.add(downloadRateLabel);
		panel.add(Box.createVerticalStrut(10));
		panel.add(startButton);
		panel.add(Box.createVerticalStrut(10));
		panel.add(stopButton);

		frame.getContentPane().add(BorderLayout.CENTER, panel);
		frame.setSize(150, 200);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private void startServer() {
		if (!isServerRunning) {
			try {
				
				// 8080 is what is used for Zork (or what I use)

				server = HttpServer.create(new InetSocketAddress(8080), 0); // binds to port 8080
				
				
				server.createContext("/", this); // like the base of the url for example localhost:8080/
				
				server.setExecutor(null);
				server.start();

				statusLabel.setText("Server Is Running On Port 8080");

				// I only used
				
				isServerRunning = true;

				new Thread(() -> updateDownloadRateLabel()).start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void stopServer() {
		if (isServerRunning) {
			server.stop(0);
			statusLabel.setText("Server: Offline");
			isServerRunning = false;
		}
	}

	private void updateDownloadRateLabel() {
		while (isServerRunning) {

			SwingUtilities
					.invokeLater(() -> downloadRateLabel.setText("Download Rate: " + formatBytes(downloadRate) + "/s"));
			downloadRate = 0;

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void handle(HttpExchange t) throws IOException {
		String query = t.getRequestURI().getQuery();
		String osParam = getQueryParam(query, "os");

		
		// Example:
		// http://localhost:8080?os=linux
		// To add more operating systems just put in res (serverZipFolder for your folder)
		
		if (osParam != null) {
			System.out.println("Sent");
			serveZipFile(t, osParam);
		} else {
			System.out.println("404/418 In Header");
			t.sendResponseHeaders(418, 0); 
			// It's a 418 because well the file isn't found proper parameters are not 
			// being passed so it's like you're telling a teapot to brew a cup of coffee, it avoids confusion with a real 404 
			// if the ZIP isn't found, also it is funny.
		}
	}

	private void serveZipFile(HttpExchange t, String osParam) throws IOException {
		
		System.out.println("Path: res/");
		System.out.println("OS: " + osParam);
		
		// Detects the OS for the path, we look in res folder and find the proper zip.
		

		String zipFileName = osParam.toLowerCase() + ".zip";
		String filePath = "res/" + zipFileName;

		File zipFile = new File(filePath);
		if (!zipFile.exists()) {
			System.out.println("Zip file not found: " + zipFile.getAbsolutePath());
			t.sendResponseHeaders(404, 0);
			return;
		}

		long bandwidthLimit = 25 * 1024 * 1024 / 8; // 25mpbs up and down 

		// Set's the type to zip
		
		t.getResponseHeaders().add("Content-Type", "application/zip");
		
		
		t.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"");

		t.getResponseHeaders().add("X-Rate-Limit-Bandwidth", String.valueOf(bandwidthLimit));

		// We rate limit to 25 mpbs
		
		t.sendResponseHeaders(200, zipFile.length());

		try (OutputStream os = t.getResponseBody(); FileInputStream fis = new FileInputStream(zipFile)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			long startTime = System.currentTimeMillis();

			while ((bytesRead = fis.read(buffer)) != -1) {
				os.write(buffer, 0, bytesRead);

				downloadRate += bytesRead;

				long elapsedTime = System.currentTimeMillis() - startTime;
				if (elapsedTime > 1000) {
					long currentRate = downloadRate / elapsedTime;
					if (currentRate > bandwidthLimit) {
						try {
							Thread.sleep(1000 - elapsedTime);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					startTime = System.currentTimeMillis();
				}
			}
		}
	}

	// Just gets the parameters and such you don't really need & for now 
	
	private String getQueryParam(String query, String paramName) {
		if (query != null) {
			String[] params = query.split("&");
			for (String param : params) {
				String[] keyValue = param.split("=");
				if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
					return keyValue[1];
				}
			}
		}
		return null;
	}

	// Formats bytes into proper data formats, if you don't care about GBs you probably could remove them and only use 32bit integers.
	
	private String formatBytes(long bytes) {
		String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
		if (bytes <= 0)
			return "0 B";
		int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
		return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups),
				units[Math.min(digitGroups, units.length - 1)]);
	}

}
