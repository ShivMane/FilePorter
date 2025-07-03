package p2p;

import p2p.controller.FileController;

public class App {
    public static void main(String[] args) {
        try {
            FileController fileController = new FileController(8080);
            fileController.start();
            System.out.println("Peerlink Server is started on port 8080");
            System.out.println("UI is Available at htto://localhost:3000");
            Runtime.getRuntime().addShutdownHook(
                new Thread(
                        () ->{
                            System.out.println("Shutting Down the Server");
                            fileController.stop();
                        }
                )
            );

            System.out.println("Press Enter to Stop The Server");
            System.in.read();   //HomeWork to Stop the server is somewone presses Enter
        }catch (Exception ex){
            System.err.println("Failed to start the server at port 8080");
            ex.printStackTrace();
        }
    }
}
