package p2p.service;

import p2p.utils.Uploadutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class FileSharer {

    private HashMap<Integer, String> availablefiles;

    public FileSharer(){
        availablefiles = new HashMap<>();
    }

    public int OfferFile(String filePath){
        int port;
        while (true){
            port = Uploadutils.generateCode();
            if (!availablefiles.containsKey(port)){
                availablefiles.put(port, filePath);
                return port;
            }
        }
    }

    public void startFileserver(int port){
        String filePath = availablefiles.get(port);
        if (filePath == null) {
            System.out.println("No File is Associated with port:" + port);
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Serving File "+new File(filePath).getName() + "on Port "+ port);
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client Connection: " +clientSocket.getInetAddress());
            new Thread(new FileSenderHandler(clientSocket, filePath)).start();
        }catch (IOException ex){
            System.out.println("Error handling file server on port: " +port);
        }
    }

    public static class FileSenderHandler implements Runnable{

        private final Socket clientSocket;
        private final String filePath;

        public FileSenderHandler(Socket clientSocket, String filePath){
            this.clientSocket = clientSocket;
            this.filePath = filePath;
        }

        @Override
        public void run(){
            try (FileInputStream fis = new FileInputStream(filePath)){
                OutputStream oos = clientSocket.getOutputStream();
                String fileName = new File(filePath).getName();
                String header = "Filename: "+fileName+"\n";
                oos.write(header.getBytes());

                byte[] buffer = new byte[4096];
                int byteRead;
                while ((byteRead = fis.read(buffer)) != -1){
                    oos.write(buffer, 0, byteRead);
                }
                System.out.println("File" +fileName+ "Sent to" + clientSocket.getInetAddress());

            }catch (Exception ex){
                System.out.println("Error sending file to the client" +ex.getMessage());
            }finally {
                try {
                    clientSocket.close();
                }catch (Exception ex){
                    System.err.println("Error Closing Socket: " +ex.getMessage());
                }
            }
        }
    }

}
