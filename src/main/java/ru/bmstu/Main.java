package ru.bmstu;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import space.crtech.utils.Logger;
//import space.crtech.utils.lo;
import space.crtech.utils.AppProperties;

import java.io.File;

public class Main {
    static public void main(String[] args) {

//        Properties props = new AppProperties();
        Logger.getLogger().enableStacktraceLogging(true);
        Logger.getLogger().enableTagsBlackList(true);
//        Logger.getLogger().addTagToBlackList("DEBUG");


        Tomcat tomcat = new Tomcat();
        tomcat.setPort(AppProperties.getProperty("tomcat.port", 6969, Integer.class));

        tomcat.setHostname(AppProperties.getProperty("tomcat.host", "localhost", String.class));
        String appBase = ".";
        tomcat.getHost().setAppBase(appBase);

        tomcat.getConnector();

        File docBase = new File(System.getProperty("java.io.tmpdir"));
        Context context = tomcat.addContext("", docBase.getAbsolutePath());

        Logger.getLogger().logInfo("absolute path: " + docBase.getAbsolutePath());

        Class sessionBrokerServletClass = SessionBrokerServlet.class;
        Tomcat.addServlet(context, sessionBrokerServletClass.getSimpleName(), sessionBrokerServletClass.getName());
        context.addServletMappingDecoded("/api/v1/*", sessionBrokerServletClass.getSimpleName());

        Class serveStaticServletClass = ServeStaticServlet.class;
        Tomcat.addServlet(context, serveStaticServletClass.getSimpleName(), serveStaticServletClass.getName());
        context.addServletMappingDecoded("/static/*", serveStaticServletClass.getSimpleName());


        try {
            tomcat.start();
        } catch (LifecycleException e) {
            Logger.getLogger().logError("Error starting tomcat!");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        Logger.getLogger().logInfo("tomcat started!");
        tomcat.getServer().await();
    }
}
