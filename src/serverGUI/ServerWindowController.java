package serverGUI;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import ocsf.server.ConnectionToClient;
import server.ServerControl;
import server.SqlConnector;
import serverClasses.Client;

public class ServerWindowController {

	@FXML
	private TextField txt_hostname;

	@FXML
	private TextField txt_ip;

	@FXML
	private TextField txt_port;

	@FXML
	private TextField txt_dbPath;

	@FXML
	private TextField txt_username;

	@FXML
	private Button btn_connect;

	@FXML
	private Button btn_disconnect;

	@FXML
	private Button btn_exit;

	@FXML
	private PasswordField txt_password;
	@FXML
	private TextArea console_field;

	@FXML
	private TableView<Client> table_clients;

	@FXML
	private TableColumn<Client, String> col_ip;

	@FXML
	private TableColumn<Client, String> col_host;

	@FXML
	private TableColumn<Client, String> col_status;

	private ServerControl sv;
	private static ObservableList<Client> listOfClients = FXCollections.observableArrayList();;
	private ByteArrayOutputStream baos = new ByteArrayOutputStream();
	private PrintStream ps = new PrintStream(baos);

	@FXML
	public void initialize() throws UnknownHostException {
		btn_disconnect.setDisable(true);
		txt_hostname.setText(InetAddress.getLocalHost().getHostName()); // set host name of this pc
		txt_ip.setText(InetAddress.getLocalHost().getHostAddress()); // set ip of this pc
		ClientsTableSet(); // initialise clients table
		redirectConsole(); // redirect standard system output to server console textfield
	}

	
	@FXML
	void btnConnectClick(ActionEvent event) {
		String[] args = { txt_dbPath.getText(), txt_username.getText(), txt_password.getText() }; // get connection data
																									// from fields
		SqlConnector.main(args); //connect to database
		
		int port = Integer.parseInt(txt_port.getText()); //port for server
		sv = new ServerControl(port); //create Abstract Server on port port
		ServerControl.setConnection(sv); //start server
		btn_connect.setDisable(true);
		btn_disconnect.setDisable(false);
		try {
			sv.listen(); //start listening for clients
		} catch (IOException e) {
			System.out.println("Server already listening on " + port);
		}
		
	}
	

	@FXML
	void btnDisconnectClick(ActionEvent event) throws SQLException {
		ServerControl.closeAll(); //close all connection (DB & server)
		if (sv != null && SqlConnector.getConnection()!= null && SqlConnector.getConnection().isClosed())
			System.out.println("Disconnected"); //check if everything is closed
		btn_connect.setDisable(false);
		btn_disconnect.setDisable(true);
	}

	@FXML
	void btnExitClick(ActionEvent event) throws SQLException {
		ServerControl.closeAll();
		System.out.println("Exiting server control");
		System.exit(0);
	}

	/**
	 * method to initialise clients table and create daemon thread to update it in
	 * real time
	 */
	private void ClientsTableSet() {
		col_ip.setCellValueFactory(new PropertyValueFactory<Client, String>("ip"));
		col_host.setCellValueFactory(new PropertyValueFactory<Client, String>("host"));
		col_status.setCellValueFactory(new PropertyValueFactory<Client, String>("status"));
		// create thread that updates clients table:
		Thread connectedClientsUpdate = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(500);
						table_clients.setItems(listOfClients);
					} catch (InterruptedException | NullPointerException e) {
						System.out.println("clients table update error " + e);
					}

				}

			}
		});
		connectedClientsUpdate.setDaemon(true);
		connectedClientsUpdate.start();
	}

	/**
	 * @param connectedClient connection between specific client and server method
	 *                        add new client to table or change status if client was
	 *                        connected in past
	 */
	public static void addClientToTable(ConnectionToClient connectedClient) {
		if (!listOfClients.isEmpty()) { // if there is connected clients
			for (int i = 0; i < listOfClients.size(); i++) {
				Client client = listOfClients.get(i);
				if (!client.getIp().equals(connectedClient.getInetAddress().getHostAddress())) { // if there is no
																									// client with the
																									// same ip
					Client newclient = new Client(connectedClient.getInetAddress().getHostAddress(),
							connectedClient.getInetAddress().getHostName(), "connected");
					listOfClients.add(newclient);
				} else { // if client already connected
					client.setStatus("connected"); // update status
					listOfClients.set(i, client);
				}
			}
		} else { // if no clients connected, add client to list
			Client newclient = new Client(connectedClient.getInetAddress().getHostAddress(),
					connectedClient.getInetAddress().getHostName(), "connected");
			listOfClients.add(newclient);
		}

	}

	/**
	 * @param disconnectedClient - connection between specific client and server.
	 *                           method change status of client in connected clients
	 *                           table to disconnected
	 */
	public static void clientDisconected(ConnectionToClient disconnectedClient) {
		for (int i = 0; i < listOfClients.size(); i++) {
			Client client = listOfClients.get(i);
			if (client.getIp().equals(disconnectedClient.getInetAddress().getHostAddress())) { // search client by ip
				client.setStatus("disconnected");
				listOfClients.set(i, client);
			}
		}
	}

	/**
	 * redirect standard system output to server console textfield
	 */
	private void redirectConsole() {
		System.setOut(ps);
		Thread redirectConsole = new Thread(new Runnable() {

			@Override
			public void run() { // create thread which updates output text in textfield
				while (true) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						System.out.println("Console log update thread error");
					}
					console_field.setText(baos.toString());
					console_field.setScrollTop(Double.MAX_VALUE); // scroll down the text field
				}
			}

		});
		redirectConsole.setDaemon(true);
		redirectConsole.start();
	}
}
