package cc.blynk.server.launcher;

import cc.blynk.common.utils.ServerProperties;
import cc.blynk.server.Holder;
import cc.blynk.server.admin.http.HttpsAdminServer;
import cc.blynk.server.api.http.HttpAPIServer;
import cc.blynk.server.api.http.HttpsAPIServer;
import cc.blynk.server.application.AppServer;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.hardware.HardwareServer;
import cc.blynk.server.hardware.ssl.HardwareSSLServer;
import cc.blynk.utils.JarUtil;
import cc.blynk.utils.LoggerUtil;

import java.io.File;
import java.net.BindException;

/**
 * Entry point for server launch.
 *
 * By default starts 6 servers on different ports:
 *
 * 1 server socket for SSL/TLS Hardware (8441 default)
 * 1 server socket for plain tcp/ip Hardware (8442 default)
 * 1 server socket for SSL/TLS Applications (8443 default)
 * 1 server socket for HTTP API (8080 default)
 * 1 server socket for HTTPS API (9443)
 * 1 server socket for Administration UI (7443 default)
 *
 * In addition launcher start all related to business logic threads like saving user profiles thread, timers
 * processing thread, properties reload thread and so on.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/16/2015.
 */
public class ServerLauncher {

    private final BaseServer[] servers;

    private final Holder holder;

    private ServerLauncher(ServerProperties serverProperties) {
        this.holder = new Holder(serverProperties);

        servers = new BaseServer[] {
                new HardwareServer(holder),
                new HardwareSSLServer(holder),
                new AppServer(holder),
                new HttpAPIServer(holder),
                new HttpsAPIServer(holder),
                new HttpsAdminServer(holder)
        };
    }

    public static void main(String[] args) throws Exception {
        ServerProperties serverProperties = new ServerProperties();

        //required to make all loggers async with LMAX disruptor
        System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
        System.setProperty("AsyncLogger.RingBufferSize",
                serverProperties.getProperty("async.logger.ring.buffer.size", String.valueOf(8 * 1024)));

        //configurable folder for logs via property.
        System.setProperty("logs.folder", serverProperties.getProperty("logs.folder"));

        //changing log level based on properties file
        LoggerUtil.changeLogLevel(serverProperties.getProperty("log.level"));

        ArgumentsParser.parse(args, serverProperties);

        System.setProperty("data.folder", serverProperties.getProperty("data.folder"));

        JarUtil.unpackStaticFiles("admin");

        new ServerLauncher(serverProperties).run();
    }



    private void run() {
        //start servers
        try {
            for (BaseServer server : servers) {
                server.start();
            }
        } catch (BindException bindException) {
            System.out.println("Server ports are busy. Most probably server already launched. See " +
                    new File(System.getProperty("logs.folder")).getAbsolutePath() + " for more info.");
            System.exit(0);
        } catch (Exception e) {
            System.out.println("Error starting Blynk server. Stopping.");
            System.exit(0);
        }

        //Launching all background jobs.
        JobLauncher.start(holder, servers);

        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }

        System.out.println();
        System.out.println("Blynk Server successfully started.");
        System.out.println("All server output is stored in folder '" + new File(System.getProperty("logs.folder")).getAbsolutePath() + "' file.");
    }

}
