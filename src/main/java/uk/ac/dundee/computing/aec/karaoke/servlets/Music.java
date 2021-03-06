package uk.ac.dundee.computing.aec.karaoke.servlets;

import com.datastax.driver.core.Cluster;
import uk.ac.dundee.computing.aec.karaoke.models.MusicModel;
import uk.ac.dundee.computing.aec.karaoke.stores.Track;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import org.apache.tika.Tika;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import uk.ac.dundee.computing.aec.karaoke.lib.CassandraHosts;
import uk.ac.dundee.computing.aec.karaoke.lib.Convertors;
import uk.ac.dundee.computing.aec.karaoke.models.PlayModel;
import uk.ac.dundee.computing.aec.karaoke.stores.Likes;
import uk.ac.dundee.computing.aec.karaoke.stores.LoggedIn;

@WebServlet(name = "Music", urlPatterns = {
    "/Music",
    "/Music/*",
    "/Fetch/*",
    "/Upload",
    "/Like/*",
    "/Play",
    "/Play/*"
})

public class Music extends HttpServlet {

    private Cluster cluster = null;
    private HashMap CommandsMap = new HashMap();
    private MusicModel mm;
    private String[] args;
    private RequestDispatcher rd;

    @Override
    public void init(ServletConfig config) throws ServletException {
        cluster = CassandraHosts.getCluster();
        mm = new MusicModel();
        CommandsMap.put("Music", 1);
        CommandsMap.put("Fetch", 2);
        CommandsMap.put("Upload", 3);
        CommandsMap.put("Like", 4);
        CommandsMap.put("Play", 5);
    }//end init()

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        LoggedIn li = (LoggedIn) request.getSession().getAttribute("LoggedIn");
        if (li == null) {
            response.sendRedirect("/Karaoke/login.jsp");
        } else {

            args = Convertors.SplitRequestPath(request);
            mm.setCluster(cluster);

            int command;
            try {
                command = (Integer) CommandsMap.get(args[1]);
            } catch (Exception et) {
                error("Bad request", response);
                return;
            }
            switch (command) {
                /* /Music */
                case 1:
                    if (args.length == 2) {

                        LinkedList<Track> songs = mm.getTrackList();
                        LinkedList<Track> topTracks = mm.getTopTracks();
                        request.setAttribute("tracks", songs);
                        request.setAttribute("topTracks", topTracks);
                        if (songs != null) {
                            rd = request.getRequestDispatcher("/tracks.jsp");
                        } else {
                            rd = request.getRequestDispatcher("/Upload");
                        }
                        rd.forward(request, response);

                    } /* /Music/track */ else if (args.length == 3) {
                        Track t = mm.getTrack(UUID.fromString(args[2]));
                        if (t != null) {
                            rd = request.getRequestDispatcher("../track.jsp");
                            request.setAttribute("track", t);
                            rd.forward(request, response);
                        } else {
                            response.sendRedirect("/Karaoke/Music");
                        }
                    }
                    break;

                /* /Fetch/trackID */
                case 2:
                    sendTrackData(args[2], response);
                    break;

                /* /Upload */
                case 3:
                    rd = request.getRequestDispatcher("/upload.jsp");
                    rd.forward(request, response);
                    break;

                case 4:
                    Likes l = mm.getLikes(UUID.fromString(args[2]));
                    if (l == null) {
                        response.getWriter().write("" + 0);
                    } else {
                        response.getWriter().write("" + l.getTotalLikes());
                    }
                    break;

                /* /Play */
                case 5:
                    PlayModel pm = new PlayModel();
                    pm.setCluster(cluster);
                    HashMap map = pm.getPlayEvents(UUID.fromString(args[2]));
                    Set set = map.entrySet();
                    Iterator iterator = set.iterator();
                    PrintWriter out = response.getWriter();
                    StringBuilder jsonStr = new StringBuilder();
                    jsonStr.append("[");
                    while (iterator.hasNext()) {
                        Map.Entry mentry = (Map.Entry) iterator.next();
                        String strArray[] = mentry.getKey().toString().split("_");
                        jsonStr.append(" {\" label \" :" + "\"" + strArray[1].toString() + "\",");
                        jsonStr.append("\" value\" :" + "\"" + mentry.getValue().toString() + "\"},");
                    }
                    jsonStr.setLength(jsonStr.length() - 1);
                    jsonStr.append("]");
                    if (jsonStr.length() == 1) {
                        out.print("");
                    } else {
                        out.println(jsonStr);
                    }
                    break;
            }//end switch
        }
    }//end doGet()

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        args = Convertors.SplitRequestPath(request);
        mm.setCluster(cluster);
        int command;
        try {
            command = (Integer) CommandsMap.get(args[1]);
        } catch (Exception et) {
            error("Bad request", response);
            return;
        }
        switch (command) {
            /* /Music */
            case 1:
                for (Part part : request.getParts()) {
                    String type = part.getContentType();
                    System.out.println(type);
                    String filename = part.getSubmittedFileName();
                    InputStream is = request.getPart(part.getName()).getInputStream();
                    Tika tika = new Tika();
                    String result = tika.detect(filename);
                    int i = is.available();
                    byte[] b = new byte[i + 1];
                    is.read(b);
                    MusicModel mm = new MusicModel();
                    mm.setCluster(cluster);
                    mm.insertTrack(b, type, filename, "Scott", part.getName());
                    is.close();
                }//end for loop
                response.sendRedirect("/Karaoke/Music");
                break;

            /* /Like/trackID */
            case 4:
                HttpSession session = request.getSession();
                LoggedIn li = (LoggedIn) session.getAttribute("LoggedIn");
                Track t = mm.getTrack(UUID.fromString(args[2]));
                mm.insertLike(UUID.fromString(args[2]), li.getUsername(), t.getName());
                break;

            /* /Play/* */
            case 5:
                PlayModel pm = new PlayModel();
                pm.setCluster(cluster);
                pm.insertPlay(UUID.fromString(request.getParameter("track")), "TestUser");
                break;
        }//end switch
    }//end doPost()

    @Override
    public String getServletInfo() {
        return "Short description";
    }//end getServletInfo()

    //Displays a single image
    private void sendTrackData(String Image, HttpServletResponse response) throws ServletException, IOException {
        MusicModel mm = new MusicModel();
        mm.setCluster(cluster);
        Track t = mm.getTrack(java.util.UUID.fromString(Image));
        OutputStream out = response.getOutputStream();
        response.setContentType(t.getType());
        response.setContentLength(t.getLength());
        InputStream is = new ByteArrayInputStream(t.getBytes());
        BufferedInputStream input = new BufferedInputStream(is);
        byte[] buffer = new byte[8192];
        for (int length = 0; (length = input.read(buffer)) > 0;) {
            out.write(buffer, 0, length);
        }//end for loop
        out.close();
    }//end DisplayImage()

    //displays an error message
    private void error(String mess, HttpServletResponse response) throws ServletException, IOException {
        PrintWriter out = new PrintWriter(response.getOutputStream());
        out.println("<h1>You have a an error in your input</h1>");
        out.println("<h2>" + mess + "</h2>");
        out.close();
    }//end error()
}//end class
