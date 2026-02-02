package com.davixdevelop.worldshifter.utils;

import java.io.*;

public class LogUtils {
    private static String SEE_HELP_MSG = "\nSee [-help] for usage info";

    public static void log(String message) {
        System.out.println(message);
    }

    public static void log() {
        System.out.println();
    }

    public static void logHelp(String message) {
        System.out.println(message);
        System.out.println(SEE_HELP_MSG);
    }

    public static void logError(String message, Exception ex) {
        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));

        System.out.println();
        System.out.println("An exception was caught: " + message);
        System.out.println(ex.getMessage() + ":");
        System.out.println(writer);
        System.out.println();
    }

    public static void logError(String message, File file, Exception ex) {
        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));

        System.out.println();
        System.out.println("An exception was caught: " + message);
        System.out.println("File: " + file.getPath());
        System.out.println(ex.getMessage() + ":");
        System.out.println(writer);
        System.out.println();
    }
}
