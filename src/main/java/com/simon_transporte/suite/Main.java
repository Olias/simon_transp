package com.simon_transporte.suite;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.BasicConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.openjpa.enhance.RuntimeUnenhancedClassesModes;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.w3c.dom.NodeList;

import com.simon_transporte.suite.db.pojo.Address;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceConfiguration;
import jakarta.persistence.SchemaManager;
import jakarta.persistence.SchemaValidationException;;

/**
 * Example of using JSP's with embedded jetty and using a lighter-weight
 * ServletContextHandler instead of a WebAppContext.
 * 
 * This example is somewhat odd in that it uses custom tag libs which reside in
 * a WEB-INF directory, even though WEB-INF is not meaningful to a
 * ServletContextHandler. This just shows that once we have properly initialized
 * the jsp engine, you can even use this type of custom taglib, even if you
 * don't have a full-fledged webapp.
 * 
 */
public class Main {

	public static void main(String[] args) throws Exception {
		String fn = args.length > 0 ? args[0] : System.getProperty("simon.configfile");
		if (fn == null) {
			System.err.println("Configfile argument missing / env simon.configfile not set");
			System.exit(1);
		}
		File conffile = new File(fn);
		if (!conffile.isFile() || !conffile.canRead()) {
			System.err.println("config file not found! " + conffile.getAbsolutePath());
			System.exit(1);
		}
		
		XMLConfiguration conf = new BasicConfigurationBuilder<>(XMLConfiguration.class)
				.configure(new Parameters().xml()).getConfiguration();
		FileHandler fh = new FileHandler(conf);
		fh.load(new FileInputStream(conffile));
		
		int port = conf.getInt("server.port");
		

		Main m = new Main(port, getEmFactory(conf));
		m.start();
		m.waitForInterrupt();
		m.stop();
	}

	private static EntityManagerFactory getEmFactory(XMLConfiguration conf) {
		String persistanceUnit = conf.getString("persistance.persistanceUnit");
		List<HierarchicalConfiguration<ImmutableNode>> props = conf.configurationsAt("persistance.prop", false);
		Map<String, String> properies = new HashMap<String, String>();
		for (HierarchicalConfiguration prop : props) {
			String name = prop.getString(".[@name]");
			String val = prop.getString(".");
			properies.put(name, val);
		}
		
		
		return Persistence.createEntityManagerFactory(persistanceUnit, properies);
	}

	private static final Logger LOG = Logger.getLogger(Main.class.getName());

	private int port;
	private Server server;

	private EntityManagerFactory emFactory;

	public Main(int port, EntityManagerFactory emFactory) {
		this.port = port;
		this.emFactory = emFactory;
	}

	public void start() throws Exception {
		server = new Server();

		// Define ServerConnector
		ServerConnector connector = new ServerConnector(server);
		connector.setPort(port);
		server.addConnector(connector);

		// Create Servlet context
		ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		servletContextHandler.setContextPath("/");

		// Default Servlet (always last, always named "default")
		WebserviceServet wss = new WebserviceServet();
		wss.entityManagerFactory = emFactory;

		ServletHolder holderDefault = new ServletHolder("default", wss);
		holderDefault.setInitParameter("ws-package", Address.class.getPackageName());

		servletContextHandler.addServlet(holderDefault, "/api/db/*");
		server.setHandler(servletContextHandler);

		// Start Server
		server.start();

		// Show server state
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine(server.dump());
		}
	}

	public void stop() throws Exception {
		server.stop();
	}

	/**
	 * Cause server to keep running until it receives a Interrupt.
	 * <p>
	 * Interrupt Signal, or SIGINT (Unix Signal), is typically seen as a result of a
	 * kill -TERM {pid} or Ctrl+C
	 * 
	 * @throws InterruptedException if interrupted
	 */
	public void waitForInterrupt() throws InterruptedException {
		server.join();
	}

}
