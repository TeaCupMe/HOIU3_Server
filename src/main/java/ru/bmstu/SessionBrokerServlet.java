package ru.bmstu;

import jakarta.servlet.RequestDispatcher;
import org.jetbrains.annotations.NotNull;
import space.crtech.utils.AppProperties;
import space.crtech.utils.Logger;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.http.fileupload.IOUtils;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

public class SessionBrokerServlet extends HttpServlet {

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        Logger.getLogger().logSuccess("SessionBrokerServlet created");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        Logger.getLogger().logInfo("GET request for " + req.getRequestURI() + " from " + req.getRemoteAddr());

        String relativePath = req.getRequestURI().substring(req.getServletPath().length() + 1);
        ArrayList<String> pathElements = new ArrayList<>(Arrays.asList(relativePath.split("/")));

        switch (pathElements.getFirst()) {
            case "getSession": getSession(req, resp, new ArrayList<>(pathElements.subList(1, pathElements.size()))); return;
            case "getStatus": getStatus(req, resp, new ArrayList<>(pathElements.subList(1, pathElements.size()))); return;
            case "static": getStatic(req, resp, new ArrayList<>(pathElements.subList(1, pathElements.size()))); return;
        }
        Logger.getLogger().tag("ERROR").logError("Unknown request URI: " + req.getRequestURI());
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    private void getStatic(HttpServletRequest req, HttpServletResponse resp, ArrayList<String> strings) {
        // TODO redirect local statics requests to general static servlet
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        Logger.getLogger().logInfo("POST request to " + req.getRequestURI() + " from " + req.getRemoteAddr());

        String relativePath = req.getRequestURI().substring(req.getServletPath().length() + 1);
        ArrayList<String> pathElements = new ArrayList<>(Arrays.asList(relativePath.split("/")));

        switch (pathElements.getFirst()) {
            case "setSession": setSession(req, resp, new ArrayList<>(pathElements.subList(1, pathElements.size()))); return;
            default: resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

    }

    void getSession(HttpServletRequest req, HttpServletResponse resp, @NotNull ArrayList<String> pathElements) {
        Logger.getLogger().logInfo("Session requested");

        if (pathElements.isEmpty()) {
            Logger.getLogger().logError("No session name provided for /getSession");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        } else if (pathElements.size() > 1) {
            Logger.getLogger().logError("Too many parameters for /getSession");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        resp.setContentType("application/json");
        String sessionName = pathElements.getFirst();
        Logger.getLogger().logInfo("Serving session " + sessionName);
        String s = AppProperties.getProperty("sessionBroker.sessionsDir", null, String.class);

        if (s == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            Logger.getLogger().logError("sessionsBroker.sessionsDir not found (looks like shit happened, AppProperties.getProperty() should have handled this...)");
            return;
        }

        Path filePath = Paths.get(s);

        File file = new File(filePath.toAbsolutePath().toString() + File.separator + sessionName + ".session");
        if (!file.exists()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            Logger.getLogger().logError("Requested session " + sessionName + " does not exist");
            return;
        }

        try {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentLength((int) file.length());
            IOUtils.copy(new FileInputStream(file), resp.getOutputStream());
            resp.getOutputStream().flush();
            Logger.getLogger().logSuccess("Session " + sessionName + " successfully sent");
        } catch (IOException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Logger.getLogger().logError("IOException when sending session " + file.getName());
        }
    }

    void setSession(HttpServletRequest req, HttpServletResponse resp, ArrayList<String> pathElements) {
        Logger.getLogger().logInfo("Session uploaded");

        if (pathElements.isEmpty()) {
            Logger.getLogger().logError("No session name provided for /setSession");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        } else if (pathElements.size() > 1) {
            Logger.getLogger().logError("Too many parameters for /setSession");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (req.getContentType() == null || !req.getContentType().contains("application/json")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Logger.getLogger().logError("Incorrect content type provided for /setSession");
            return;
        }

        if (req.getContentLength() == 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Logger.getLogger().logError("No content provided for /setSession");
            return;
        }
        Logger.getLogger().tag("DEBUG").logInfo("content length: " + req.getContentLength());
        Logger.getLogger().tag("DEBUG").logInfo("content type: " + req.getContentType());
        String content = null;
        try {
            content = new String(req.getInputStream().readAllBytes());
            Logger.getLogger().tag("DEBUG").logInfo("content: " + content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String sessionName = pathElements.getFirst();
        Logger.getLogger().logInfo("Updating session " + sessionName);

        File file = getSessionFile(sessionName);
        if (file == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            Logger.getLogger().logError("Session " + sessionName + " does not exist");
            return;
        }

        if (!file.exists()) {
            boolean canCreateSession = AppProperties.getProperty("sessionBroker.allowNewSessions", false, Boolean.class);
            if (canCreateSession) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    Logger.getLogger().logError("IOException when creating session " + file.getName());
                    return;
                }
            } else {
                Logger.getLogger().logError("Attempt to set session " + sessionName + ", but allowNewSessions is false");
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }


        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(file, false);
        } catch (FileNotFoundException e) {
            Logger.getLogger().logError("FileNotFoundException when updating session " + file.getName());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
//            throw new RuntimeException(e);
        }

        try {
            IOUtils.copy(new ByteArrayInputStream(content.getBytes()), fos);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Logger.getLogger().logSuccess("Session " + sessionName + " successfully updated");
        resp.setStatus(HttpServletResponse.SC_OK);

    }

    void getStatus(HttpServletRequest req, HttpServletResponse resp, ArrayList<String> pathElements) {
        if (!pathElements.isEmpty()) {
            Logger.getLogger().logError("Too many path components for /getStatus");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_OK);

        String statusHtmlPath = AppProperties.getProperty("sessionBroker.statusHtmlPath", null, String.class);

        if (statusHtmlPath == null) {
            Logger.getLogger().logError("statusHtmlPath not found");
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path filePath = Paths.get(statusHtmlPath).toAbsolutePath();
        File statusHtmlFile = new File(filePath.toString());

        if (!statusHtmlFile.exists()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            Logger.getLogger().logError("File " + statusHtmlFile.getName() + " does not exist");
            return;
        }

        try {
            resp.setContentType("text/html;charset=UTF-8");
            resp.setContentLength((int) statusHtmlFile.length());
            IOUtils.copy(new FileInputStream(statusHtmlFile), resp.getOutputStream());
            resp.getOutputStream().flush();
            Logger.getLogger().logSuccess("Status html file sent");
        }
        catch (FileNotFoundException e) {
            Logger.getLogger().logError("FileNotFoundException when sending status html file");
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        } catch (IOException e) {
            Logger.getLogger().logError("IOException when sending status html file");
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
    }

    private static File getSessionFile(String sessionName) {
        String s = AppProperties.getProperty("sessionBroker.sessionsDir", null, String.class);

        if (s == null) {
            Logger.getLogger().logError("sessionsBroker.sessionsDir not found (looks like shit happened, AppProperties.getProperty() should have handled this...)");
            return null;
        }

        Path filePath = Paths.get(s);
        return new File(filePath.toAbsolutePath().toString() + File.separator + sessionName + ".session");
    }
}