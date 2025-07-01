package p2p.utils;

import java.util.Random;

public class Uploadutils {

    public static int generateCode(){
        int DYNAMIC_STARTING_POINT = 49152;
        int DYNAMIC_ENDING_POINT = 85535;

        Random random = new Random();
        return random.nextInt((DYNAMIC_ENDING_POINT - DYNAMIC_STARTING_POINT) + DYNAMIC_STARTING_POINT);
    }

}
