package ru.bmstu;

import space.crtech.utils.AppProperties;
import space.crtech.utils.Logger;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import org.omnifaces.servlet.FileServlet;
import java.io.File;

@WebServlet("/static/*")
public class ServeStaticServlet extends FileServlet {
    private File folder;

    @Override
    public void init() throws ServletException {
        String staticPath = AppProperties.getProperty("serveStatic.staticFolder", null, String.class);
        if (staticPath == null) {
            Logger.getLogger().tag("ServeStaticServlet").logError("StaticPath is null");
            return;
        }
        folder = new File(staticPath);
    }

    @Override
    protected File getFile(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();

        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            throw new IllegalArgumentException();
        }

        return new File(folder, pathInfo);
    }
}
