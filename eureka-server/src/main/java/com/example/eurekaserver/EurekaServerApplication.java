package com.example.eurekaserver;

/**
 * Compatibility wrapper that forwards the legacy package entry point to the
 * actively used Eureka server bootstrap class.
 */
public class EurekaServerApplication {

    public static void main(String[] args) {
        com.connecthub.eurekaserver.EurekaServerApplication.main(args);
    }
}
