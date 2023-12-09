package main;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;

public class Main implements HttpHandler {

    private static final long BANDWIDTH_LIMIT = 25 * 1024 * 1024 / 8; // 25 Mbps

    public static void main(String[] args) {
        try {
        	
        	// Sets the port to 8080, I use ZROK because it's cool
        	
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/", new Main());
            server.setExecutor(null);
            server.start();

            System.out.println("Server started on port 8080");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
    	
    	
    	// gets the os for the zip
    	
        String query = t.getRequestURI().getQuery();
        String osParam = getQueryParam(query, "os");

        if (osParam != null) {
            System.out.println("Sent");
            serveZipFile(t, osParam);
        } else {
            System.out.println("404 In Header");
            t.sendResponseHeaders(404, 0);
        }
    }

    private void serveZipFile(HttpExchange t, String osParam) throws IOException {
        System.out.println("Path: res/");
        System.out.println("OS: " + osParam);

        
        // Example http://localhost:8080?os=linux
        
        
        String zipFileName = osParam.toLowerCase() + ".zip";
        String filePath = "res/" + zipFileName;

        // Reads from res from the zip iirc
        
        File zipFile = new File(filePath);
        if (!zipFile.exists()) {
            System.out.println("Zip file not found: " + zipFile.getAbsolutePath());
            t.sendResponseHeaders(418, 0);
            return;
        }

        Headers headers = t.getResponseHeaders();
        headers.add("Content-Type", "application/zip");
        headers.add("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"");
        
        

        // adds the bandwidth limiting headers
        headers.add("X-Rate-Limit-Bandwidth", String.valueOf(BANDWIDTH_LIMIT));

        t.sendResponseHeaders(200, zipFile.length());

        try (OutputStream os = t.getResponseBody(); FileInputStream fis = new FileInputStream(zipFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long startTime = System.currentTimeMillis();

            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);

                enforceBandwidthLimit(startTime, bytesRead);

                startTime = System.currentTimeMillis();
            }
        }
    }

    private void enforceBandwidthLimit(long startTime, int bytesRead) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        long currentRate = bytesRead / elapsedTime;

        if (currentRate > BANDWIDTH_LIMIT) {
            try {
                Thread.sleep(1000 - elapsedTime); // sleeps because to overworked
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

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
}
