package com.gamer0kayf1n3.velocity_prefix_auth;

import java.util.UUID;
import java.lang.reflect.Method;

public class FloodgateDetection {

    private static Method isFloodgatePlayerMethod = null;
    private static Object floodgateApi = null;
    private static boolean floodgateChecked = false;

    public static boolean isFloodgatePlayer(UUID uuid) {

        if (!floodgateChecked) {
            floodgateChecked = true;
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Method getInstance = apiClass.getMethod("getInstance");
                
                floodgateApi = getInstance.invoke(null);
                if (floodgateApi != null) isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            } 
            catch (Exception ignored) {}
        }
        if (floodgateApi == null || isFloodgatePlayerMethod == null) return false;
        try {return (boolean) isFloodgatePlayerMethod.invoke(floodgateApi, uuid);}
        catch (Exception e) {
            return false;
        }
    }
}
