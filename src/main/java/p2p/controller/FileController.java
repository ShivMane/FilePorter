package p2p.controller;

import com.sun.net.httpserver.HttpServer;
import p2p.service.FileSharer;

import java.io.File;
import java.io.IOException;
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
        server.stop();
        System.out.println("");
    }
}
