package p2p.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.IOUtils;
import p2p.service.FileSharer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileController {
    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executorService;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDir = System.getProperty("java.io.tmpdir")+ File.separator + "peerLink-Uploads";
        this.executorService = Executors.newFixedThreadPool(10);

        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()){
            uploadDirFile.mkdirs();
        }

        server.createContext("/upload", new Uploadhandler());
        server.createContext("/downloads", new DownloadHandler());
        server.createContext("/", new CORSHandler());
        server.setExecutor(executorService);
    }

    public void start(){
        server.start();
        System.out.println("API server started on port " +server.getAddress().getPort());
    }

    public void stop(){
        server.stop(0);
        System.out.println("API server Stopped");
    }

    private class CORSHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Cotent-Type, Authorozation");

            if (exchange.getRequestMethod().equals("OPTIONS")){
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String response = "NOT FOUND";
            exchange.sendResponseHeaders(404, response.getBytes().length);
            try (OutputStream oos = exchange.getResponseBody()){
                oos.write(response.getBytes());
            }
        }
    }

    private class Uploadhandler implements HttpHandler{
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            if(!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                String response = "Method Not Allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try (OutputStream oos = exchange.getResponseBody()) {
                    oos.write(response.getBytes());
                }
                return;
            }

            Headers requestHeaders = exchange.getRequestHeaders();
            String contentType = requestHeaders.getFirst("Content-Type");
            if(contentType == null || !contentType.startsWith("multipart/form-data")){
                String response = "Bad Request: Content-Type must be multipart/form-data";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try (OutputStream oos = exchange.getResponseBody()) {
                    oos.write(response.getBytes());
                }
                return;
            }

            try {

                String boundary = contentType.substring(contentType.indexOf("boundry=")+9);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                IOUtils.copy(exchange.getRequestBody(), baos);
                byte[] requestData = baos.toByteArray();

                Multiparser parser = new Multiparser(requestData, boundary);

            }catch (Exception ex){

            }
        }
    }

    private static class Multiparser{
        private final byte[] data;
        private final String boundary;

        public Multiparser(byte[] data, String boundary){
            this.data = data;
            this.boundary = boundary;
        }

        public ParseResult parse(){

        }
    }

    public static class ParseResult{
        public final String fileName;
        public final byte[] fileContent;

        public ParseResult(String fileName, byte[] fileContent){
            this.fileName = fileName;
            this.fileContent = fileContent;
        }
    }


}
