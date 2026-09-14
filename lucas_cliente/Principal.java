/***************************************************************** 
* Autor..............: Lucas de Menezes Chaves
* Matricula........: 202310282
* Inicio...........: 20/06/2026
* Ultima alteracao.: 28/06/2026
* Nome.............: Principal
* Funcao...........: Executa o Cliente
*************************************************************** */

import Network.Descobridor;
import Network.RecebedorUDP;
import Protocol.APDU;
import Network.Mensagens;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.control.TextInputDialog;
import java.util.Optional;
import javafx.application.Platform;

public class Principal extends Application {
  private static String ipServidor;
  private static String nomeUsuarioFinal;
  private static int portaUDPCliente;
  private static RecebedorUDP recebedor;
  private static Cliente cliente;

  /********************************************************************
   * Metodo: main
   * Funcao: inicia a execucao do programa
   * @param args argumentos
   * @return void
   * ****************************************************************** */
  public static void main(String[] args) {
    System.out.println("====================================");
    System.out.println("   BEM-VINDO AO CHAT");
    System.out.println("====================================");

    // Iniciar JavaFX
    launch(args);
  }//fim do metodo

  /********************************************************************
   * Metodo: start
   * Funcao: inicia a interface grafica do JavaFX
   * @param primaryStage palco principal do JavaFX
   * @return void
   * ****************************************************************** */
  @Override
  public void start(Stage primaryStage) {
    ipServidor = Descobridor.buscarIPServidor();

    if(ipServidor == null) {
      System.out.println("Falha ao encontrar o servidor automaticamente.");
      TextInputDialog dialogIp = new TextInputDialog();
      dialogIp.setTitle("IP do Servidor");
      dialogIp.setHeaderText("Falha ao encontrar o servidor automaticamente.\nDigite o IP do servidor manualmente (ou 'localhost'):");
      dialogIp.setContentText("IP:");
      
      Optional<String> resultIp = dialogIp.showAndWait();
      if (resultIp.isPresent()) {
        ipServidor = resultIp.get().trim();
      }//fim do if
      
      if (ipServidor == null || ipServidor.isEmpty()) {
        System.out.println("IP nao informado. Encerrando...");
        Platform.exit();
        System.exit(0);
        return;
      }//fim do if
    } else {
      System.out.println("Servidor encontrado automaticamente no IP: " + ipServidor);
    }//fim do if-else
    
    portaUDPCliente = 5000 + (int)(Math.random() * 1000);
    recebedor = new RecebedorUDP(portaUDPCliente, ipServidor, 7777);
    recebedor.start();
    
    cliente = new Cliente(ipServidor, 6789);

    TextInputDialog dialogNome = new TextInputDialog();
    dialogNome.setTitle("Nome de Usuario");
    dialogNome.setHeaderText("Digite seu nome de usuario para o chat:");
    dialogNome.setContentText("Nome:");
    
    Optional<String> resultNome = dialogNome.showAndWait();
    String nomeUsuarioUI = null;
    if (resultNome.isPresent()) {
      nomeUsuarioUI = resultNome.get().trim();
    }//fim do if

    if (nomeUsuarioUI == null || nomeUsuarioUI.isEmpty()) {
        System.out.println("Nome de usuario nao informado. Encerrando...");
        Platform.exit();
        System.exit(0);
        return;
    }//fim do if

    nomeUsuarioFinal = nomeUsuarioUI;
    Mensagens.meuNome = nomeUsuarioFinal;

    //Registra o usuario no servidor imediatamente ao conectar
    //Assim ele aparece na lista USERS mesmo sem entrar em qualquer grupo
    APDU register = new APDU("REGISTER", null, nomeUsuarioFinal, null, portaUDPCliente);
    cliente.enviarComandoTCP(register);

    Controller.ChatController controller = new Controller.ChatController(cliente, recebedor, nomeUsuarioFinal, portaUDPCliente);
    
    try {
      javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/View/ChatUI.fxml"));
      if (loader.getLocation() == null) {
        loader.setLocation(new java.io.File("View/ChatUI.fxml").toURI().toURL());
      }
      loader.setController(controller);
      javafx.scene.Parent root = loader.load();
      controller.setStage(primaryStage);
      
      javafx.scene.Scene scene = new javafx.scene.Scene(root);
      primaryStage.setScene(scene);
      primaryStage.show();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }//fim do metodo
}//fim da classe
