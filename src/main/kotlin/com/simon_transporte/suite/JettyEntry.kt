import org.eclipse.jetty.server.Server;
import javax.swing.JFrame
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JPanel
import java.awt.event.ActionListener
import java.awt.event.ActionEvent
import java.awt.Desktop
import java.net.URI
import java.io.IOException
import java.net.URISyntaxException
import java.lang.ClassLoader
import java.util.Properties
import java.text.SimpleDateFormat

fun main(args: Array<String>) {
	val je: JettyEntry = JettyEntry(8212);
	je.start();
}

open class JettyEntry(
	var port: Int
) : Thread() {

	val properties = Properties()
	val frame = JFrame("Simon Transporte: suite")
	
	init{
		this.getContextClassLoader().getResourceAsStream("properties");
		properties.load(this.getContextClassLoader().getResourceAsStream("properties"));
		val d =  SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(properties.getProperty("build.time"))
		frame.title = frame.title + SimpleDateFormat(" yyyy-MM-dd").format(d)
	}

	override fun run() {
		val server = Server(port);

		frame.setLocation(300, 300);
		frame.setSize(350, 200);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(BorderLayout(5, 5));

		val panel = JPanel(GridLayout(2, 1));
		val button2 = JButton("Link öffnen");
		button2.addActionListener(
			object : ActionListener {
				override fun actionPerformed(e: ActionEvent) {
					try {
						Desktop.getDesktop().browse(URI("http://localhost:"+port+"/"));
					} catch (e1: IOException) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					} catch (e2: URISyntaxException) {
						e2.printStackTrace();
					}
				}
			}
		);

		val button = JButton("Ende!");
		button.addActionListener(object : ActionListener {
				override fun actionPerformed(e: ActionEvent) {
					try {
						server.stop();
						server.destroy();
					} catch (e1: Exception) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					System.exit(0);
				}
			}
		);

		panel.add(button2);
		panel.add(button);

		frame.getContentPane().add(panel);

		frame.show();

		server.stopAtShutdown = true;
		server.start();
		server.join();

	}
}