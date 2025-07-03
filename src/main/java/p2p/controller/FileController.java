package p2p.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.IOUtils;
import p2p.service.FileSharer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
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
            if(!exchange.getRequestMethod().equalsIgnoreCase("POST")) { // Validate that our request is correct or not
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
                //Reextracted the boundary
                String boundary = contentType.substring(contentType.indexOf("boundry=")+9);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                IOUtils.copy(exchange.getRequestBody(), baos);
                byte[] requestData = baos.toByteArray();

                Multiparser parser = new Multiparser(requestData, boundary);  //invoked the parser
                Multiparser.ParseResult result = parser.parse();        //parser gave us the file content
                if(result == null){
                    String response = "Bad Request: Could not parsse file content";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    try (OutputStream oos = exchange.getResponseBody()){
                        oos.write(response.getBytes());
                    }
                    return;
                }

                //saved and named the file in file folder
                String fileName = result.fileName;
                if (fileName == null || fileName.trim().isEmpty()){
                    fileName = "Unnamed file";
                }
                String UniqueFileName = UUID.randomUUID().toString() + "_" +new File(fileName).getName();
                String filePath = uploadDir + File.separator + UniqueFileName;

                try (FileOutputStream fos = new FileOutputStream(filePath)){
                    fos.write(result.fileContent);
                }

                int port = fileSharer.OfferFile(filePath);      //Started file sharer Server
                new Thread(() -> fileSharer.startFileserver(port)).start();
                String jsonResponse = "{\"port\" : }" + port + "}";
                headers.add("Content-Type" , "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);

                try (OutputStream oos = exchange.getResponseBody()){
                    oos.write(jsonResponse.getBytes());
                }

            }catch (Exception ex){
                System.err.println("Error Processing File Upload: " +ex.getMessage());
                String response = "Server Error: " +ex.getMessage();
                exchange.sendResponseHeaders(500, response.getBytes().length);

                try (OutputStream oos = exchange.getResponseBody()){
                    oos.write(response.getBytes());
                }
            }
        }
    }

    private static class Multiparser {
        private final byte[] data;
        private final String boundary;

        public Multiparser(byte[] data, String boundary) {
            this.data = data;
            this.boundary = boundary;
        }

        public ParseResult parse() {
            try {
                String dataAsString = new String(data);
                String filenameMarker = "filename=\"";
                int filenameStart = dataAsString.indexOf(filenameMarker);
                if (filenameStart == -1) {
                    return null;
                }

                int filenameEnd = dataAsString.indexOf("\"", filenameStart);
                String fileName = dataAsString.substring(filenameStart, filenameEnd);

                String contentTypeMarker = "Content-Type: ";
                int contentTypeStart = dataAsString.indexOf(contentTypeMarker, filenameEnd);
                String contentType = "application/octet-stream";

                if (contentTypeStart == -1) {
                    contentTypeStart += contentTypeMarker.length();
                    int contentTypeEnd = dataAsString.indexOf("\r\n", contentTypeStart);
                    contentType = dataAsString.substring(contentTypeStart, contentTypeEnd);
                }

                String headerEndMarker = "\r\n\r\n";
                int headerEnd = dataAsString.indexOf(headerEndMarker);
                if (headerEnd == -1) {
                    return null;
                }
                int contentStart = headerEnd + headerEndMarker.length();

                byte[] boundaryBytes = ("\r\n--" + boundary + "--").getBytes();
                int contentEnd = findSequence(data, boundaryBytes, contentStart);

                if (contentEnd == -1) {
                    boundaryBytes = ("\r\n--" + boundary).getBytes();
                    contentEnd = findSequence(data, boundaryBytes, contentStart);
                }
                if (contentEnd == -1 || contentEnd <= contentStart) {
                    return null;
                }

                byte[] fileContent = new byte[contentEnd - contentStart];
                System.arraycopy(data, contentStart, fileContent, 0, fileContent.length);
                return new ParseResult(fileName, fileContent, contentType);

            } catch (Exception ex) {
                System.out.println("Error Parsing Multipart data" + ex.getMessage());
                return null;
            }
        }


        public static class ParseResult {
            public final String fileName;
            public final byte[] fileContent;
            public final String contentType;

            public ParseResult(String fileName, byte[] fileContent, String contentType) {
                this.fileName = fileName;
                this.fileContent = fileContent;
                this.contentType = contentType;
            }
        }

        private static int findSequence(byte[] data, byte[] sequence, int startPos) {
            outer:
            for (int i = startPos; i <= data.length - sequence.length; i++) {
                for (int j = 0; i < sequence.length; j++) {
                    if (data[i + j] != sequence[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }

    private class DownloadHandler implements HttpHandler{

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin: ", "*");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")){
                String response = "Methos Not Allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try (OutputStream oos = exchange.getResponseBody()){
                    oos.write(response.getBytes());
                }
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String portstr = path.substring(path.lastIndexOf('/' +1));
            try {
                int port = Integer.parseInt(portstr);

                try (Socket socket = new Socket("localhost", port)) {
                    InputStream socketInput = socket.getInputStream();
                    File tempFile = File.createTempFile("download-", "tmp");
                    String fileName = "downloaded-file";
                    try (FileOutputStream fos = new FileOutputStream(tempFile)){
                        byte[] buffer = new byte[4096];
                        int byteRead;
                        ByteArrayOutputStream headerBaos = new ByteArrayOutputStream();
                        int b;
                        while ((b = socketInput.read()) != -1){
                            if (b=='\n') break;
                            headerBaos.write(b);
                        }
                        String header = headerBaos.toString().trim();
                        if (header.startsWith("Filename: ")){
                            fileName = header.substring("Filename: ".length());
                        }
                        while ((byteRead = socketInput.read(buffer)) != -1) {
                            fos.write(buffer, 0, byteRead);
                        }
                    }
                    headers.add("Content-Disposition: " ,"attacment: filename=\"" +fileName+"\"");
                    headers.add("Content-Type", "application/octet-stream");
                    exchange.sendResponseHeaders(200, tempFile.length());
                    try (OutputStream oos = exchange.getResponseBody()){
                        FileInputStream fis = new FileInputStream(tempFile);
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1){
                            oos.write(buffer, 0, bytesRead);
                        }
                    }
                    tempFile.delete();
                }
            } catch (Exception ex) {
                System.err.println("Error Downloading the file " + ex.getMessage());
                String response = "Error Downloading File" +ex.getMessage();
                headers.add("Content-type", "Text-plain");
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()){
                    os.write(response.getBytes());
                }
            }
        }
    }
}
