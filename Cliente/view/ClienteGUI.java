/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: ClienteGUI
 * Funcao: Interface Grafica (JavaFX) principal do Cliente E.D.E.N, gerenciando paineis, temas e chats.
 */

package view;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.shape.SVGPath;

import model.ClienteTCP;
import model.ClienteUDP;
import model.MessageListener;
import Protocol.APDU;
import utils.InfoUser;
import utils.Protocolo;

/**
 * Interface grafica principal da aplicacao cliente do sistema E.D.E.N.
 * Gerencia a navegacao em telas (Splash, Login, Sobre, Chat), lista de canais,
 * membros online e despacho de mensagens confiaveis e assincronas.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class ClienteGUI extends Application implements MessageListener {

  private StackPane root;
  private ImageView watermark;
  private ClienteTCP tcp;
  private ClienteUDP udp;
  private InfoUser eu;
  private String ipServidor = "127.0.0.1";
  private int portaServidor = Protocolo.PORTA_SERVIDOR_TCP;
  private Thread threadRecepcao;

  // Chat state
  private Map<String, VBox> chatHistories = new HashMap<>();
  private String currentChat = null;
  private ScrollPane chatScroll;
  private Label lblChatHeader;
  private ListView<String> groupList;
  private ListView<String> onlineUsersList;
  private VBox emptyStateBox;

  private ObservableList<String> masterGroupData = FXCollections.observableArrayList();
  private ObservableList<String> masterUsersData = FXCollections.observableArrayList();

  // New features state
  private Map<String, Integer> unreadCounts = new HashMap<>();
  private Map<String, Set<String>> knownGroupMembers = new HashMap<>();
  private Map<String, Label> messageTickLabels = new HashMap<>();
  private Map<String, List<MessageConfirmTask>> unreadMessageIds = new HashMap<>();
  private Map<String, String> messageToChatMap = new HashMap<>();
  private Map<String, Set<String>> messageReadConfirmations = new HashMap<>();
  private Map<String, Set<String>> messageDeliveryConfirmations = new HashMap<>();
  private Map<String, Set<String>> messageExpectedMembers = new HashMap<>();
  private boolean isVuMode = false;
  private Set<String> openedVuMessageIds = new HashSet<>();
  private Set<String> meusBloqueados = new HashSet<>();
  private ToggleButton btnToggleVURef;

  private static class MessageConfirmTask {
    String idMensagem;
    String senderName;
    boolean isPrivate;

    MessageConfirmTask(String idMensagem, String senderName, boolean isPrivate) {
      this.idMensagem = idMensagem;
      this.senderName = senderName;
      this.isPrivate = isPrivate;
    }
  }

  @Override
  public void start(Stage primaryStage) {
    root = new StackPane();
    root.setStyle("-fx-background-color: #1a1e0b;");

    Scene scene = new Scene(root, 950, 650);

    try {
      scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
    } catch (Exception e) {
      System.out.println("[GUI] style.css nao encontrado, usando estilos inline.");
    }

    primaryStage.setTitle("E.D.E.N. - Sistema de Comunicacao Interno");

    // Icone da janela
    try {
      primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/view/edenIcon.png")));
    } catch (Exception ex) {
      System.out.println("[GUI] Warning: nao foi possivel definir o icone da janela.");
    }

    primaryStage.setScene(scene);
    primaryStage.show();

    // Watermark permanente - fica no fundo, mas ocultaremos na tela do chat
    try {
      watermark = new ImageView(new Image(getClass().getResourceAsStream("/view/edenIcon.png")));
      watermark.setOpacity(0.18);
      watermark.setFitWidth(500);
      watermark.setPreserveRatio(true);
      watermark.setMouseTransparent(true);
      root.getChildren().add(watermark);
      StackPane.setAlignment(watermark, Pos.CENTER);
    } catch (Exception ex) {
      System.out.println("[GUI] Warning: edenIcon.png nao encontrado.");
    }

    root.getChildren().add(createSplash());
  }

  // =========================================================================
  // TRANSICAO ANIMADA
  // =========================================================================
  private void switchView(Node newView) {
    Node oldView = root.getChildren().get(root.getChildren().size() - 1);
    FadeTransition fadeOut = new FadeTransition(Duration.millis(200), oldView);
    fadeOut.setFromValue(1.0);
    fadeOut.setToValue(0.0);
    fadeOut.setOnFinished(e -> {
      root.getChildren().remove(oldView);
      newView.setOpacity(0.0);
      newView.setTranslateY(12);
      root.getChildren().add(newView);

      FadeTransition fadeIn = new FadeTransition(Duration.millis(300), newView);
      fadeIn.setFromValue(0.0);
      fadeIn.setToValue(1.0);

      TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), newView);
      fadeIn.play();
      slideIn.setFromY(12);
      slideIn.setToY(0);
      slideIn.play();
    });
    fadeOut.play();
  }

  // =========================================================================
  // HELPER: ANIMATION
  // =========================================================================
  private void addHoverScale(Button btn) {
    ScaleTransition stEnter = new ScaleTransition(Duration.millis(150), btn);
    stEnter.setToX(1.05);
    stEnter.setToY(1.05);

    ScaleTransition stExit = new ScaleTransition(Duration.millis(150), btn);
    stExit.setToX(1.0);
    stExit.setToY(1.0);

    btn.setOnMouseEntered(e -> stEnter.playFromStart());
    btn.setOnMouseExited(e -> stExit.playFromStart());
  }

  // =========================================================================
  // TELA 1 - SPLASH (Login & Server Connect)
  // =========================================================================
  private Node createSplash() {
    VBox splash = new VBox(22);
    splash.setAlignment(Pos.CENTER);
    splash.setStyle("-fx-background-color: transparent;");

    Label lblClearance = new Label("[ TERMINAL DE ACESSO RESTRITO ]");
    lblClearance.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
    lblClearance.setTextFill(Color.web("#8a9b3a"));
    lblClearance.setStyle("-fx-background-color: rgba(46,58,20,0.5); -fx-padding: 4px 14px; -fx-background-radius: 12px; -fx-border-color: #5b6623; -fx-border-radius: 12px; -fx-border-width: 1px;");

    Label title1 = new Label("Sistema de Comunicacao Interno");
    title1.setFont(Font.font("Impact", FontWeight.BOLD, 26));
    title1.setTextFill(Color.web("#a4b455"));
    title1.setTextAlignment(TextAlignment.CENTER);

    Label titleDa = new Label("da");
    titleDa.setFont(Font.font("Impact", FontWeight.NORMAL, 20));
    titleDa.setTextFill(Color.web("#8a9b3a"));

    Label title2 = new Label("E.D.E.N.");
    title2.setFont(Font.font("Impact", FontWeight.BOLD, 74));
    title2.setTextFill(Color.web("#c9d873"));
    title2.setStyle("-fx-effect: dropshadow(gaussian, rgba(138,155,58,0.55), 20, 0.4, 0, 0);");

    VBox titleBox = new VBox(4);
    titleBox.setAlignment(Pos.CENTER);
    titleBox.getChildren().addAll(lblClearance, title1, titleDa, title2);

    Button btnEntrar = new Button("Entrar");
    btnEntrar.getStyleClass().add("btn-eden");
    btnEntrar.setPrefWidth(200);
    btnEntrar.setOnAction(e -> switchView(createLogin()));
    addHoverScale(btnEntrar);

    Button btnSobre = new Button("Sobre");
    btnSobre.getStyleClass().add("btn-eden");
    btnSobre.setPrefWidth(200);
    btnSobre.setOnAction(e -> switchView(createSobre()));
    addHoverScale(btnSobre);

    VBox buttonBox = new VBox(12);
    buttonBox.setAlignment(Pos.CENTER);
    buttonBox.getChildren().addAll(btnEntrar, btnSobre);

    splash.getChildren().addAll(titleBox, buttonBox);
    return splash;
  }

  private Node createSobre() {
    VBox sobre = new VBox(25);
    sobre.setAlignment(Pos.CENTER);
    sobre.setStyle("-fx-background-color: transparent; -fx-padding: 40px;");

    Label title = new Label("E.D.E.N.");
    title.setFont(Font.font("Impact", FontWeight.BOLD, 48));
    title.setTextFill(Color.web("#c9d873"));
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(138,155,58,0.5), 15, 0.4, 0, 0);");

    VBox contentBox = new VBox(20);
    contentBox.setAlignment(Pos.CENTER);
    contentBox.setMaxWidth(650);
    contentBox.setStyle(
        "-fx-background-color: #232d0f; -fx-padding: 30px; -fx-background-radius: 15px; -fx-border-color: #8a9b3a; -fx-border-width: 2px; -fx-border-radius: 15px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 18, 0, 0, 8);");

    Label text = new Label(
        "Seja bem-vindo ao sistema de comunica\u00E7\u00E3o interna do E.D.E.N.. Se voc\u00EA est\u00E1 acessando esta interface, sua transi\u00E7\u00E3o foi conclu\u00EDda: a partir de agora, voc\u00EA faz parte do Jardim, e suas vidas nunca mais ser\u00E3o as mesmas. O E.D.E.N. \u00E9 o presente, o passado e o pr\u00F3prio futuro; n\u00F3s somos a raiz invis\u00EDvel que sustenta o novo amanhecer, e hoje voc\u00EA se torna um membro valioso desta funda\u00E7\u00E3o. Deixamos para tr\u00E1s o que era velho, quebrado e sem prop\u00F3sito para trabalharmos juntos na verdadeira transforma\u00E7\u00E3o do mundo. Saiba que voc\u00EA n\u00E3o est\u00E1 aqui por acaso; voc\u00EA foi cirurgicamente escolhido, e o Conselho est\u00E1 de olho em cada uma de suas a\u00E7\u00F5es. Use este canal interno com absoluta disciplina para coordenar suas diretrizes entre os outros agentes do Jardim. Lembre-se diariamente da import\u00E2ncia do seu papel nesta engrenagem: n\u00F3s somos o amanh\u00E3 constru\u00EDdo hoje. N\u00F3s somos o futuro.");
    text.setWrapText(true);
    text.setTextAlignment(TextAlignment.JUSTIFY);
    text.setFont(Font.font("Consolas", 14));
    text.setTextFill(Color.web("#e5e8d7"));

    Separator sep = new Separator();
    sep.setStyle("-fx-background-color: #5b6623; -fx-opacity: 0.6;");

    Label readme = new Label(
        "--- TECH README ---\nDesenvolvedor: Iury Ramos Sodre (202310440)\nProjeto: App de Chat P2P/Server Hibrido\nDisciplina: Sistemas Distribuidos (UESB)\nProtocolos: TCP (Controle) / UDP (Mensagens)\nInterface: JavaFX (Custom UI)\nAno: 2026");
    readme.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
    readme.setTextFill(Color.web("#c9d873"));
    readme.setAlignment(Pos.CENTER);
    readme.setTextAlignment(TextAlignment.CENTER);
    readme.setMaxWidth(Double.MAX_VALUE);

    contentBox.getChildren().addAll(text, sep, readme);

    Label techFooter = new Label("v1.0 | Build 2026 // CLASSIFIED ACCESS");
    techFooter.setTextAlignment(TextAlignment.CENTER);
    techFooter.setFont(Font.font("Consolas", 12));
    techFooter.setTextFill(Color.web("#8a9b3a"));

    Button btnVoltar = new Button("\u2190 Voltar");
    btnVoltar.getStyleClass().add("btn-eden");
    btnVoltar.setOnAction(e -> switchView(createSplash()));
    addHoverScale(btnVoltar);

    sobre.getChildren().addAll(title, contentBox, techFooter, btnVoltar);

    ScrollPane scroll = new ScrollPane(sobre);
    scroll.setFitToWidth(true);
    scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
    return scroll;
  }

  // =========================================================================
  // TELA 2 - LOGIN
  // =========================================================================
  private Node createLogin() {
    VBox login = new VBox(20);
    login.setAlignment(Pos.CENTER);
    login.setStyle("-fx-background-color: transparent;");

    Label title = new Label("Identificacao de Agente");
    title.setFont(Font.font("Impact", FontWeight.BOLD, 36));
    title.setTextFill(Color.web("#c9d873"));
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(138,155,58,0.5), 15, 0.4, 0, 0);");

    // Avatar circle
    StackPane avatarContainer = new StackPane();
    Circle outerCircle = new Circle(75, Color.web("#232d0f"));
    outerCircle.setStroke(Color.web("#8a9b3a"));
    outerCircle.setStrokeWidth(2.5);
    Circle innerCircle = new Circle(52, Color.web("#2e3a14"));

    // Simple user icon with circles
    Circle head = new Circle(18, Color.web("#c9d873"));
    head.setTranslateY(-14);
    Circle body = new Circle(28, Color.web("#c9d873"));
    body.setTranslateY(24);

    avatarContainer.getChildren().addAll(outerCircle, innerCircle, head, body);
    avatarContainer.setMaxSize(150, 150);

    // Verifica se o IP foi passado por parametro no console
    String defaultIp = "127.0.0.1";
    if (getParameters() != null && !getParameters().getRaw().isEmpty()) {
      defaultIp = getParameters().getRaw().get(0);
    }

    TextField txtIpServidor = new TextField(defaultIp);
    txtIpServidor.setPromptText("IP do Servidor (ex: 192.168.0.10)");
    txtIpServidor.setPrefWidth(220);
    txtIpServidor.getStyleClass().add("text-input");
    txtIpServidor.setStyle(
        "-fx-background-color: rgba(35, 48, 15, 0.8); -fx-text-fill: #e5e8d7; -fx-prompt-text-fill: #8a9b3a; -fx-background-radius: 20px; -fx-border-color: #5b6623; -fx-border-radius: 20px; -fx-border-width: 1px; -fx-padding: 11px 18px; -fx-font-size: 14px;");

    Button btnDiscover = new Button("Buscar");
    btnDiscover.getStyleClass().add("btn-eden");
    btnDiscover.setStyle("-fx-padding: 10px 16px; -fx-font-size: 12px;");
    addHoverScale(btnDiscover);
    btnDiscover.setOnAction(e -> {
      btnDiscover.setText("Buscando...");
      btnDiscover.setDisable(true);
      new Thread(() -> {
        java.util.Set<String> ipsDescobertos = new java.util.LinkedHashSet<>();
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
          socket.setBroadcast(true);
          socket.setSoTimeout(1000);

          // Envia busca padrao (8888) e legada EDEN (5001) para todas as interfaces ativas
          byte[] dadosPadrao = "SERVIDOR_IP".getBytes(StandardCharsets.UTF_8);
          byte[] dadosEden = "DISCOVER_EDEN".getBytes(StandardCharsets.UTF_8);

          List<java.net.InetAddress> destinos = coletarBroadcasts();
          for (java.net.InetAddress dest : destinos) {
            try {
              socket.send(new java.net.DatagramPacket(dadosPadrao, dadosPadrao.length, dest, Protocolo.PORTA_DISCOVERY));
              socket.send(new java.net.DatagramPacket(dadosEden, dadosEden.length, dest, Protocolo.PORTA_DISCOVERY_EDEN));
            } catch (Exception ignored) {
            }
          }

          long tempoLimite = System.currentTimeMillis() + 1800;
          byte[] buffer = new byte[256];
          while (System.currentTimeMillis() < tempoLimite) {
            try {
              java.net.DatagramPacket resposta = new java.net.DatagramPacket(buffer, buffer.length);
              socket.receive(resposta);
              String msg = new String(resposta.getData(), 0, resposta.getLength(), StandardCharsets.UTF_8).trim();
              if (msg.equals("IP") || msg.equals("EDEN_HERE")) {
                ipsDescobertos.add(resposta.getAddress().getHostAddress());
              }
            } catch (java.net.SocketTimeoutException ste) {
              if (!ipsDescobertos.isEmpty() && System.currentTimeMillis() >= tempoLimite - 800) {
                break;
              }
            }
          }
        } catch (Exception ex) {
          System.out.println("[GUI] Erro no discovery: " + ex.getMessage());
        }

        Platform.runLater(() -> {
          if (ipsDescobertos.isEmpty()) {
            btnDiscover.setText("Nao achou");
            txtIpServidor.setPromptText("Nao achou, digite o IP...");
          } else if (ipsDescobertos.size() == 1) {
            String ipUnico = ipsDescobertos.iterator().next();
            txtIpServidor.setText(ipUnico);
            btnDiscover.setText("Encontrado!");
          } else {
            btnDiscover.setText(ipsDescobertos.size() + " achados!");
            mostrarSeletorServidores(ipsDescobertos, ipEscolhido -> {
              txtIpServidor.setText(ipEscolhido);
              btnDiscover.setText("Encontrado!");
            });
          }
        });

        try {
          Thread.sleep(2000);
        } catch (Exception ignored) {
        }
        Platform.runLater(() -> {
          btnDiscover.setText("Buscar");
          btnDiscover.setDisable(false);
        });
      }).start();
    });

    HBox ipBox = new HBox(10);
    ipBox.setAlignment(Pos.CENTER);
    ipBox.getChildren().addAll(txtIpServidor, btnDiscover);

    TextField txtUsername = new TextField();
    txtUsername.setPromptText("Digite seu Codinome de Agente");
    txtUsername.setMaxWidth(330);
    txtUsername.getStyleClass().add("text-input");
    txtUsername.setStyle(
        "-fx-background-color: rgba(35, 48, 15, 0.8); -fx-text-fill: #e5e8d7; -fx-prompt-text-fill: #8a9b3a; -fx-background-radius: 20px; -fx-border-color: #5b6623; -fx-border-radius: 20px; -fx-border-width: 1px; -fx-padding: 11px 18px; -fx-font-size: 14px;");

    Button btnConfirmar = new Button("Conectar ao Jardim");
    btnConfirmar.getStyleClass().add("btn-eden");
    btnConfirmar.setPrefWidth(220);
    addHoverScale(btnConfirmar);
    btnConfirmar.setOnAction(e -> {
      String nome = txtUsername.getText();
      String ip = txtIpServidor.getText();
      if (nome != null && !nome.trim().isEmpty() && ip != null && !ip.trim().isEmpty()) {
        ipServidor = ip.trim();
        tentarLogin(nome.trim());
      }
    });

    Button btnVoltar = new Button("\u2190 Voltar");
    btnVoltar.setStyle(
        "-fx-background-color: transparent; -fx-text-fill: #8a9b3a; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 4px 12px;");
    btnVoltar.setOnAction(e -> switchView(createSplash()));
    addHoverScale(btnVoltar);

    // Enter key support
    txtUsername.setOnAction(e -> btnConfirmar.fire());
    txtIpServidor.setOnAction(e -> txtUsername.requestFocus());

    login.getChildren().addAll(title, avatarContainer, ipBox, txtUsername, btnConfirmar, btnVoltar);
    return login;
  }

  private void tentarLogin(String nome) {
    // Mostra feedback visual imediato
    Label lblConectando = new Label("[ CONECTANDO AO SERVIDOR ]");
    lblConectando.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
    lblConectando.setTextFill(Color.web("#c9d873"));
    lblConectando.setStyle(
        "-fx-background-color: #232d0f; -fx-border-color: #8a9b3a; -fx-border-width: 1.5px; -fx-padding: 12px 24px; -fx-background-radius: 12px; -fx-border-radius: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 4);");
    StackPane.setAlignment(lblConectando, Pos.BOTTOM_CENTER);
    StackPane.setMargin(lblConectando, new Insets(0, 0, 30, 0));
    root.getChildren().add(lblConectando);

    // Roda em thread separada para nao travar a UI durante conexao remota
    new Thread(() -> {
      try {
        int portaTcp = portaServidor;
        int portaUdp = (portaServidor == Protocolo.PORTA_SERVIDOR_LEGACY) ? Protocolo.PORTA_SERVIDOR_LEGACY : Protocolo.PORTA_SERVIDOR_UDP;
        ClienteTCP tcpNovo = new ClienteTCP(ipServidor, portaTcp);
        ClienteUDP udpNovo = new ClienteUDP(ipServidor, portaUdp);
        udpNovo.setListener(this);
        udpNovo.setMeuNome(nome);

        InfoUser usuario = new InfoUser(nome, tcpNovo.getIpLocal(), udpNovo.getPortaLocal());
        APDU resRegistro = tcpNovo.register(usuario);

        Platform.runLater(() -> {
          root.getChildren().remove(lblConectando);
          if (resRegistro != null && Protocolo.ERRO.equals(resRegistro.getOperacao())) {
            showErrorOverlay("Violacao de Protocolo", resRegistro.getTextoMensagem());
          } else {
            tcp = tcpNovo;
            udp = udpNovo;
            eu = usuario;
            threadRecepcao = new Thread(udp);
            threadRecepcao.setDaemon(true);
            threadRecepcao.start();
            switchView(createChat());
          }
        });
      } catch (Exception ex) {
        Platform.runLater(() -> {
          root.getChildren().remove(lblConectando);
          showErrorOverlay("Erro de Conexao", "Nao foi possivel conectar ao servidor em " + ipServidor + "."
              + "\nVerifique o IP e se o servidor esta rodando."
              + "\nDetalhe: " + ex.getMessage());
        });
      }
    }).start();
  }

  // =========================================================================
  // TELA 3 - CHAT PRINCIPAL
  // =========================================================================
  private Node createChat() {
    if (watermark != null) {
      watermark.setVisible(false); // Oculta o global para nao duplicar com o do chat
    }

    BorderPane chatPane = new BorderPane();
    // Fundo transparente para o watermark do root aparecer por baixo
    chatPane.setStyle("-fx-background-color: transparent;");
    chatPane.setBackground(javafx.scene.layout.Background.EMPTY);

    // ===== SIDEBAR ESQUERDA =====
    VBox sidebar = new VBox(8);
    sidebar.setPrefWidth(260);
    sidebar.setPadding(new Insets(12));
    sidebar.setStyle("-fx-background-color: #2e3a14; -fx-background-radius: 0;");

    FilteredList<String> filteredGroups = new FilteredList<>(masterGroupData, p -> true);
    FilteredList<String> filteredUsers = new FilteredList<>(masterUsersData, p -> true);

    // -- Secao: Grupos --
    HBox boxGruposHeader = new HBox(8);
    boxGruposHeader.setAlignment(Pos.CENTER_LEFT);

    Label lblGrupos = new Label("GRUPOS");
    lblGrupos.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
    lblGrupos.setTextFill(Color.web("#c9d873"));
    lblGrupos.setPadding(new Insets(5, 0, 5, 5));
    HBox.setHgrow(lblGrupos, Priority.ALWAYS);
    lblGrupos.setMaxWidth(Double.MAX_VALUE);

    SVGPath searchIconGroups = new SVGPath();
    searchIconGroups.setContent(
        "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z");
    searchIconGroups.setFill(Color.web("#8a9b3a"));
    searchIconGroups.setStyle("-fx-cursor: hand;");

    boxGruposHeader.getChildren().addAll(lblGrupos, searchIconGroups);

    TextField txtSearchGroups = new TextField();
    txtSearchGroups.setPromptText("Buscar grupo...");
    txtSearchGroups.setStyle(
        "-fx-background-color: rgba(255, 255, 255, 0.85); -fx-background-radius: 20px; -fx-padding: 6px 15px; -fx-font-size: 13.5px; -fx-text-fill: #1a1e0b; -fx-prompt-text-fill: #555555;");
    txtSearchGroups.setVisible(false);
    txtSearchGroups.setManaged(false);

    searchIconGroups.setOnMouseClicked(e -> {
      boolean vis = txtSearchGroups.isVisible();
      txtSearchGroups.setVisible(!vis);
      txtSearchGroups.setManaged(!vis);
      if (!vis)
        txtSearchGroups.requestFocus();
      else
        txtSearchGroups.clear();
    });

    txtSearchGroups.textProperty().addListener((obs, oldVal, newVal) -> {
      String lower = newVal.toLowerCase();
      filteredGroups.setPredicate(item -> {
        if (newVal == null || newVal.isEmpty())
          return true;
        return item.toLowerCase().contains(lower);
      });
    });

    groupList = new ListView<>(filteredGroups);
    groupList.setStyle("-fx-background-color: transparent; -fx-control-inner-background: transparent;");
    groupList.setPrefHeight(300);
    VBox.setVgrow(groupList, Priority.ALWAYS);
    groupList.setCellFactory(lv -> createStyledCell());

    groupList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null) {
        onlineUsersList.getSelectionModel().clearSelection();
        switchChatTo(newVal);
      }
    });

    // Botoes de grupo
    HBox grpBtns = new HBox(8);
    grpBtns.setAlignment(Pos.CENTER);

    Button btnJoin = new Button("+ Criar/Entrar");
    btnJoin.getStyleClass().add("btn-eden");
    btnJoin.setStyle("-fx-font-size: 11px; -fx-padding: 5px 12px;");
    btnJoin.setOnAction(e -> onJoinGroup());
    addHoverScale(btnJoin);

    Button btnLeave = new Button("- Sair");
    btnLeave.setStyle(
        "-fx-background-color: rgba(120, 35, 35, 0.80); -fx-text-fill: #e5c0c0; -fx-font-size: 11px; -fx-padding: 5px 12px; -fx-background-radius: 20px; -fx-border-color: #7a2828; -fx-border-radius: 20px; -fx-border-width: 1px; -fx-cursor: hand; -fx-font-weight: bold;");
    btnLeave.setOnAction(e -> onLeaveGroup());
    addHoverScale(btnLeave);

    Button btnListGroups = new Button("Listar Grupos");
    btnListGroups.getStyleClass().add("btn-eden");
    btnListGroups.setStyle("-fx-font-size: 11px; -fx-padding: 5px 12px;");
    btnListGroups.setOnAction(e -> onListGroups());
    addHoverScale(btnListGroups);

    grpBtns.getChildren().addAll(btnJoin, btnLeave);

    HBox grpBtns2 = new HBox(8);
    grpBtns2.setAlignment(Pos.CENTER);
    grpBtns2.getChildren().add(btnListGroups);

    // Separador
    Separator sep = new Separator();
    sep.setStyle("-fx-background-color: #8a9b3a;");

    // -- Secao: Usuarios Online --
    HBox boxUsersHeader = new HBox(8);
    boxUsersHeader.setAlignment(Pos.CENTER_LEFT);

    Label lblUsers = new Label("USUARIOS ONLINE");
    lblUsers.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
    lblUsers.setTextFill(Color.web("#c9d873"));
    lblUsers.setPadding(new Insets(5, 0, 5, 5));
    HBox.setHgrow(lblUsers, Priority.ALWAYS);
    lblUsers.setMaxWidth(Double.MAX_VALUE);

    SVGPath searchIconUsers = new SVGPath();
    searchIconUsers.setContent(
        "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z");
    searchIconUsers.setFill(Color.web("#8a9b3a"));
    searchIconUsers.setStyle("-fx-cursor: hand;");

    boxUsersHeader.getChildren().addAll(lblUsers, searchIconUsers);

    TextField txtSearchUsers = new TextField();
    txtSearchUsers.setPromptText("Buscar usu\u00E1rio...");
    txtSearchUsers.setStyle(
        "-fx-background-color: rgba(255, 255, 255, 0.85); -fx-background-radius: 20px; -fx-padding: 6px 15px; -fx-font-size: 13.5px; -fx-text-fill: #1a1e0b; -fx-prompt-text-fill: #555555;");
    txtSearchUsers.setVisible(false);
    txtSearchUsers.setManaged(false);

    searchIconUsers.setOnMouseClicked(e -> {
      boolean vis = txtSearchUsers.isVisible();
      txtSearchUsers.setVisible(!vis);
      txtSearchUsers.setManaged(!vis);
      if (!vis)
        txtSearchUsers.requestFocus();
      else
        txtSearchUsers.clear();
    });

    txtSearchUsers.textProperty().addListener((obs, oldVal, newVal) -> {
      String lower = newVal.toLowerCase();
      filteredUsers.setPredicate(item -> {
        if (newVal == null || newVal.isEmpty())
          return true;
        return item.toLowerCase().contains(lower);
      });
    });

    onlineUsersList = new ListView<>(filteredUsers);
    onlineUsersList.setStyle("-fx-background-color: transparent; -fx-control-inner-background: transparent;");
    onlineUsersList.setPrefHeight(150);
    onlineUsersList.setCellFactory(lv -> createStyledCell());

    onlineUsersList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null) {
        groupList.getSelectionModel().clearSelection();
        switchChatTo("[PVT] " + newVal);
      }
    });

    Button btnRefresh = new Button("Atualizar Lista");
    btnRefresh.getStyleClass().add("btn-eden");
    btnRefresh.setStyle("-fx-font-size: 11px; -fx-padding: 5px 12px;");
    btnRefresh.setOnAction(e -> refreshOnlineUsers());
    addHoverScale(btnRefresh);

    // -- Status de Conexao & Botao Voltar --
    Separator sep2 = new Separator();
    sep2.setStyle("-fx-background-color: #8a9b3a;");

    HBox statusBox = new HBox(5);
    statusBox.setAlignment(Pos.CENTER_LEFT);
    Circle statusDot = new Circle(4, Color.web("#c9d873"));
    // Pulsing animation for the dot
    FadeTransition pulse = new FadeTransition(Duration.seconds(1), statusDot);
    pulse.setFromValue(0.4);
    pulse.setToValue(1.0);
    pulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
    pulse.setAutoReverse(true);
    pulse.play();

    Label lblStatus = new Label("Conectado a " + ipServidor);
    lblStatus.setFont(Font.font("Segoe UI", 11));
    lblStatus.setTextFill(Color.web("#8a9b3a"));
    statusBox.getChildren().addAll(statusDot, lblStatus);

    Button btnDesconectar = new Button("\u2190 Desconectar");
    btnDesconectar.setStyle(
        "-fx-background-color: rgba(120, 35, 35, 0.80); -fx-text-fill: #e5c0c0; -fx-font-size: 11px; -fx-padding: 6px 12px; -fx-background-radius: 20px; -fx-border-color: #7a2828; -fx-border-radius: 20px; -fx-border-width: 1px; -fx-cursor: hand; -fx-font-weight: bold;");
    btnDesconectar.setMaxWidth(Double.MAX_VALUE);
    addHoverScale(btnDesconectar);
    btnDesconectar.setOnAction(e -> {
      desconectarLimpo();
      watermark.setOpacity(0.18); // restore watermark opacity
      watermark.setVisible(true); // make sure it's visible again
      switchView(createSplash());
    });

    Button btnTutorial = new Button("\u2753 Iniciar Tutorial");
    btnTutorial.setStyle(
        "-fx-background-color: rgba(70, 90, 25, 0.55); -fx-text-fill: #c9d873; -fx-font-size: 11px; -fx-padding: 6px 12px; -fx-background-radius: 20px; -fx-cursor: hand; -fx-font-weight: bold; -fx-border-color: #5b6623; -fx-border-radius: 20px; -fx-border-width: 1px;");
    btnTutorial.setMaxWidth(Double.MAX_VALUE);
    addHoverScale(btnTutorial);

    VBox.setVgrow(groupList, Priority.SOMETIMES);
    VBox.setVgrow(onlineUsersList, Priority.SOMETIMES);

    sidebar.getChildren().addAll(
        boxGruposHeader, txtSearchGroups, groupList, grpBtns, grpBtns2,
        sep,
        boxUsersHeader, txtSearchUsers, onlineUsersList, btnRefresh,
        sep2, statusBox, btnTutorial, btnDesconectar);

    // ===== AREA CENTRAL DO CHAT =====
    VBox centerArea = new VBox(0);
    centerArea.setStyle("-fx-background-color: transparent;");

    // Header
    HBox header = new HBox(10);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(12, 15, 12, 15));
    header.setStyle("-fx-background-color: #1e2a0e; -fx-border-color: transparent transparent #5b6623 transparent; -fx-border-width: 0 0 2px 0;");

    lblChatHeader = new Label("Selecione um grupo ou usuario");
    lblChatHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
    lblChatHeader.setTextFill(Color.web("#c9d873"));

    header.getChildren().add(lblChatHeader);

    // Chat scroll area
    chatScroll = new ScrollPane();
    chatScroll.setFitToWidth(true);
    chatScroll.getStyleClass().add("chat-scroll-pane");
    chatScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
    chatScroll.setVisible(false); // Escondido inicialmente

    // Estado Vazio (Empty State) - Apenas o texto
    emptyStateBox = new VBox(15);
    emptyStateBox.setAlignment(Pos.CENTER);
    emptyStateBox.setMouseTransparent(true);

    Label lblEmptyTitle = new Label("CANAL INATIVO");
    lblEmptyTitle.setFont(Font.font("Impact", FontWeight.BOLD, 22));
    lblEmptyTitle.setTextFill(Color.web("#3f4a23"));

    Label lblEmpty = new Label("Selecione um canal seguro\npara iniciar a transmissao...");
    lblEmpty.setFont(Font.font("Consolas", 13));
    lblEmpty.setTextFill(Color.web("#8a9b3a"));
    lblEmpty.setTextAlignment(TextAlignment.CENTER);
    emptyStateBox.getChildren().addAll(lblEmptyTitle, lblEmpty);

    StackPane chatContainer = new StackPane();

    // Marca d'agua permanente no fundo do chat
    try {
      ImageView chatWatermark = new ImageView(new Image(getClass().getResourceAsStream("/view/edenIcon.png")));
      chatWatermark.setOpacity(0.18);
      chatWatermark.setFitWidth(500);
      chatWatermark.setPreserveRatio(true);
      chatWatermark.setMouseTransparent(true);
      chatContainer.getChildren().add(chatWatermark);
      StackPane.setAlignment(chatWatermark, Pos.CENTER);
    } catch (Exception ex) {
      System.out.println("[GUI] Warning: edenIcon.png nao encontrado para o chatContainer.");
    }

    chatContainer.getChildren().addAll(emptyStateBox, chatScroll);
    VBox.setVgrow(chatContainer, Priority.ALWAYS);

    // Input bar
    HBox inputBar = new HBox(10);
    inputBar.setAlignment(Pos.CENTER);
    inputBar.setPadding(new Insets(10, 15, 10, 15));
    inputBar.setStyle("-fx-background-color: #1e2a0e;");

    TextField txtMsg = new TextField();
    txtMsg.setPromptText("Digite sua mensagem");
    txtMsg.setStyle(
        "-fx-background-color: rgba(255,255,255,0.85); -fx-background-radius: 20px; -fx-padding: 10px 18px; -fx-font-size: 14px; -fx-text-fill: #000000; -fx-prompt-text-fill: #555555;");
    HBox.setHgrow(txtMsg, Priority.ALWAYS);

    Button btnSend = new Button("Enviar");
    SVGPath sendIcon = new SVGPath();
    sendIcon.setContent("M2.01 21L23 12 2.01 3 2 10l15 2-15 2z");
    sendIcon.setFill(Color.web("#d8e87d"));
    btnSend.setGraphic(sendIcon);
    btnSend.setStyle(
        "-fx-background-color: linear-gradient(to bottom, #4f5a2d, #3f4a23); -fx-text-fill: #d8e87d; -fx-background-radius: 20px; -fx-border-color: #5b6623; -fx-border-radius: 20px; -fx-border-width: 1px; -fx-padding: 10px 20px; -fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand;");
    addHoverScale(btnSend);

    ToggleButton btnToggleVU = new ToggleButton("\uD83D\uDD12 VU");
    btnToggleVU.setTooltip(new Tooltip("Visualizacao Unica (Mensagem Temporaria)"));
    btnToggleVU.setStyle(
        "-fx-background-color: transparent; -fx-text-fill: #c9d873; -fx-border-color: #5b6623; -fx-border-radius: 20px; -fx-background-radius: 20px; -fx-padding: 8px 12px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
    btnToggleVU.setOnAction(e -> {
      isVuMode = btnToggleVU.isSelected();
      if (isVuMode) {
        btnToggleVU.setStyle(
            "-fx-background-color: #8a9b3a; -fx-text-fill: #1a1e0b; -fx-border-color: #c9d873; -fx-border-radius: 20px; -fx-background-radius: 20px; -fx-padding: 8px 12px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(138,155,58,0.7), 6, 0.4, 0, 0);");
      } else {
        btnToggleVU.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #c9d873; -fx-border-color: #5b6623; -fx-border-radius: 20px; -fx-background-radius: 20px; -fx-padding: 8px 12px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
      }
    });
    this.btnToggleVURef = btnToggleVU;

    btnSend.setOnAction(e -> onSendMessage(txtMsg));
    txtMsg.setOnAction(e -> onSendMessage(txtMsg));

    inputBar.getChildren().addAll(btnToggleVU, txtMsg, btnSend);

    centerArea.getChildren().addAll(header, chatContainer, inputBar);

    chatPane.setLeft(sidebar);
    chatPane.setCenter(centerArea);

    btnTutorial.setOnAction(e -> {
      List<TutorialOverlay.TutorialStep> steps = new ArrayList<>();
      steps.add(new TutorialOverlay.TutorialStep(
          sidebar,
          "Diretrizes do Jardim",
          "Bem-vindo ao E.D.E.N.. Esta e a sua estacao de comunicacao segura. Siga estas diretrizes para se familiarizar com os recursos avancados do sistema."));
      steps.add(new TutorialOverlay.TutorialStep(
          groupList,
          "Salas e Grupos Protegidos",
          "Aqui ficam os grupos dos quais voce faz parte. As comunicacoes em grupo sao distribuidas em tempo real para todos os membros autorizados."));
      steps.add(new TutorialOverlay.TutorialStep(
          grpBtns,
          "Gerenciamento de Grupos",
          "Use '+ Criar/Entrar' para abrir uma nova sala ou ingressar em uma existente, e '- Sair' para se desligar do grupo selecionado."));
      steps.add(new TutorialOverlay.TutorialStep(
          grpBtns2,
          "Frequencias Ativas (Lista Global)",
          "Solicita ao servidor a listagem completa de todas as salas ativas na rede para que voce possa ingressar em novas operacoes."));
      steps.add(new TutorialOverlay.TutorialStep(
          onlineUsersList,
          "Agentes Online & Canais Privados",
          "Exibe todos os agentes conectados. Clique em qualquer agente para abrir uma frequencia de transmissao privada e direta (PVT)."));
      steps.add(new TutorialOverlay.TutorialStep(
          header,
          "Controles de Canal, Bloqueio e Detalhes",
          "No topo da conversa, você encontra acoes especiais: em chats privados, use '\u2298 Bloquear' para restringir comunicacoes mutuas; em grupos, use 'Detalhes' para inspecionar os membros da sala."));
      steps.add(new TutorialOverlay.TutorialStep(
          btnToggleVU,
          "Transmissao Classificada (Modo VU)",
          "Ative o botao '\uD83D\uDD12 VU' para enviar mensagens de Visualizacao Unica. O conteudo so podera ser lido uma unica vez por cada destinatario em um pop-up protegido que expira permanentemente."));
      steps.add(new TutorialOverlay.TutorialStep(
          inputBar,
          "Barra de Transmissao & Ticks em Tempo Real",
          "Digite suas mensagens aqui. Suas transmissoes contam com rastreamento: \uD83D\uDD52 (Enviando), \u2713 (Servidor), \u2713\u2713 Lima (Entregue), \u2713\u2713 Verde Neon (Lido por todos) e \u2715 (Erro/Bloqueio)."));

      TutorialOverlay overlay = new TutorialOverlay(root, steps);
      overlay.start();
    });

    return chatPane;
  }

  // =========================================================================
  // CELL FACTORY - Estilo dos itens da lista
  // =========================================================================
  private ListCell<String> createStyledCell() {
    return new ListCell<String>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          setStyle("-fx-background-color: transparent;");
          setOnMouseEntered(null);
          setOnMouseExited(null);
        } else {
          setText(null);

          HBox row = new HBox();
          row.setAlignment(Pos.CENTER_LEFT);

          Label nameLbl = new Label(item);
          nameLbl.setFont(Font.font("Segoe UI", 13));
          nameLbl.setTextFill(Color.web("#d8e87d"));
          HBox.setHgrow(nameLbl, Priority.ALWAYS);
          nameLbl.setMaxWidth(Double.MAX_VALUE);

          row.getChildren().add(nameLbl);

          int unread = unreadCounts.getOrDefault(item, unreadCounts.getOrDefault("[PVT] " + item, 0));

          if (unread > 0) {
            Label badge = new Label(String.valueOf(unread));
            badge.setStyle(
                "-fx-background-color: #c9d873; -fx-text-fill: #1a1e0b; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 1px 6px; -fx-background-radius: 10px;");
            row.getChildren().add(badge);
          }

          setGraphic(row);

          Runnable updateStyle = () -> {
            if (isSelected()) {
              nameLbl.setTextFill(Color.web("#1a1e0b"));
              setStyle(
                  "-fx-background-color: #c9d873; -fx-background-radius: 8px; -fx-padding: 8px 12px;");
            } else if (isHover()) {
              nameLbl.setTextFill(Color.web("#e5e8d7"));
              setStyle(
                  "-fx-background-color: rgba(91, 102, 35, 0.45); -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand;");
            } else {
              nameLbl.setTextFill(Color.web("#d8e87d"));
              setStyle("-fx-background-color: transparent; -fx-padding: 8px 12px; -fx-cursor: hand;");
            }
          };

          updateStyle.run();

          // Adicionando os listeners de hover interativo
          setOnMouseEntered(e -> updateStyle.run());
          setOnMouseExited(e -> updateStyle.run());
          selectedProperty().addListener((obs, old, isSel) -> updateStyle.run());
        }
      }
    };
  }

  // =========================================================================
  // ACOES
  // =========================================================================
  private void onJoinGroup() {
    showInputOverlay("Criar / Entrar em Grupo", "Digite o nome do grupo:", resultado -> {
      if (resultado != null && !resultado.trim().isEmpty()) {
        String grupo = resultado.trim();
        if (grupo.startsWith("@")) {
          showErrorOverlay("Nome Invalido", "Nomes de grupo nao podem comecar com '@'. Selecione o usuario na lista 'Usuarios Online' para enviar mensagens privadas.");
          return;
        }
        APDU resposta = tcp.join(grupo, eu);
        if (resposta != null && Protocolo.OK.equals(resposta.getOperacao())) {
          if (!masterGroupData.contains(grupo)) {
            masterGroupData.add(grupo);
          }
          groupList.getSelectionModel().select(grupo);
          addChatBubble(grupo, "SYSTEM", "Voc\u00EA entrou no grupo " + grupo + ".", false, false, true);
        } else if (resposta != null && Protocolo.ERRO.equals(resposta.getOperacao())) {
          showErrorOverlay("Erro ao Entrar", resposta.getTextoMensagem());
        }
      }
    });
  }

  private void onLeaveGroup() {
    String selected = groupList.getSelectionModel().getSelectedItem();
    if (selected == null)
      return;

    APDU resposta = tcp.leave(selected, eu);
    if (resposta != null && Protocolo.OK.equals(resposta.getOperacao())) {
      addChatBubble(selected, "SYSTEM", "Voc\u00EA saiu do grupo.", false, false, true);
      // We delay removal slightly so the user sees the message? No, if we remove it,
      // the history goes away.
      // But leaving a group should maybe just remove it from the list.
      masterGroupData.remove(selected);
      chatHistories.remove(selected);
      unreadCounts.remove(selected);
      knownGroupMembers.remove(selected);
      unreadMessageIds.remove(selected);
      if (!masterGroupData.isEmpty()) {
        groupList.getSelectionModel().selectFirst();
      } else {
        currentChat = null;
        lblChatHeader.setText("Selecione um grupo ou usuario");
        chatScroll.setContent(null);
      }
    } else if (resposta != null && Protocolo.ERRO.equals(resposta.getOperacao())) {
      showErrorOverlay("Erro ao Sair", resposta.getTextoMensagem());
    }
  }

  private void onListGroups() {
    if (tcp == null)
      return;

    new Thread(() -> {
      try {
        APDU resposta = tcp.list();
        Platform.runLater(() -> {
          if (resposta != null && Protocolo.OK.equals(resposta.getOperacao())) {
            String data = resposta.getTextoMensagem();

            if (data.contains("Nenhum grupo")) {
              showErrorOverlay("Grupos no Servidor", "Nenhum grupo ativo no momento. Crie o primeiro!");
              return;
            }

            String[] grupos = data.split(",");
            List<String> gruposList = new ArrayList<>();
            for (String g : grupos) {
              g = g.trim();
              if (!g.isEmpty())
                gruposList.add(g);
            }

            showChoiceOverlay("Grupos Disponiveis", "Selecione um grupo para entrar:", gruposList,
                grupoEscolhido -> {
                  if (grupoEscolhido != null) {
                    APDU res = tcp.join(grupoEscolhido, eu);
                    if (res != null && Protocolo.OK.equals(res.getOperacao())) {
                      if (!masterGroupData.contains(grupoEscolhido)) {
                        masterGroupData.add(grupoEscolhido);
                      }
                      groupList.getSelectionModel().select(grupoEscolhido);
                      addChatBubble(grupoEscolhido, "SYSTEM",
                          "Voc\u00EA entrou no grupo " + grupoEscolhido + ".", false, false, true);
                    } else if (res != null && Protocolo.ERRO.equals(res.getOperacao())) {
                      showErrorOverlay("Erro ao Entrar", res.getTextoMensagem());
                    }
                  }
                });
          } else if (resposta != null && Protocolo.ERRO.equals(resposta.getOperacao())) {
            showErrorOverlay("Erro", resposta.getTextoMensagem());
          }
        });
      } catch (Exception e) {
        System.out.println("[GUI] Erro ao listar grupos: " + e.getMessage());
      }
    }).start();
  }

  private void refreshOnlineUsers() {
    if (tcp == null)
      return;

    System.out.println("[GUI] [INFO] Solicitando lista de usuarios online...");

    // Executa em thread separada para nao travar a interface
    new Thread(() -> {
      try {
        APDU resposta = tcp.listUsers();
        System.out.println("[GUI] [INFO] Resposta do LISTUSERS: " + resposta);
        Platform.runLater(() -> {
          if (resposta != null && Protocolo.OK.equals(resposta.getOperacao())) {
            String data = resposta.getTextoMensagem();
            masterUsersData.clear();
            if (data != null && !data.isEmpty()) {
              String[] nomes = data.split(",");
              for (String nome : nomes) {
                nome = nome.trim();
                if (!nome.isEmpty() && !nome.equalsIgnoreCase(eu.getNome())) {
                  masterUsersData.add(nome);
                }
              }
            }
            System.out.println("[GUI] [INFO] Usuarios online atualizados: "
                + masterUsersData.size() + " exibidos.");
          } else {
            System.out.println("[GUI] [WARNING] Resposta inesperada do LISTUSERS: " + resposta);
          }
        });
      } catch (Exception e) {
        System.out.println("[GUI] [ERROR] Erro ao buscar usuarios online: " + e.getMessage());
        e.printStackTrace();
      }
    }).start();
  }

  private void switchChatTo(String chatId) {
    currentChat = chatId;

    // Hide empty state and show scroll
    if (emptyStateBox != null)
      emptyStateBox.setVisible(false);
    if (chatScroll != null)
      chatScroll.setVisible(true);

    // Clear unread counts for this chat
    if (unreadCounts.containsKey(chatId)) {
      unreadCounts.remove(chatId);
      if (chatId.startsWith("[PVT] "))
        onlineUsersList.refresh();
      else
        groupList.refresh();
    }

    // Dispara confirmacoes de leitura (Status 3 = Lido) para mensagens pendentes desta conversa
    if (unreadMessageIds.containsKey(chatId)) {
      List<MessageConfirmTask> pending = unreadMessageIds.remove(chatId);
      if (pending != null && udp != null) {
        for (MessageConfirmTask task : pending) {
          String destinoConfirm = task.isPrivate ? ("@" + eu.getNome()) : chatId;
          udp.sendConfirm(task.idMensagem, 3, destinoConfirm, task.senderName);
        }
      }
    }

    // Animar a troca do header
    FadeTransition headerFade = new FadeTransition(Duration.millis(200), lblChatHeader);
    headerFade.setFromValue(0.3);
    headerFade.setToValue(1.0);
    headerFade.play();

    HBox parentHeader = (HBox) lblChatHeader.getParent();
    if (parentHeader != null) {
      parentHeader.getChildren().clear();
      parentHeader.getChildren().add(lblChatHeader);
      HBox.setHgrow(lblChatHeader, Priority.ALWAYS);
      lblChatHeader.setMaxWidth(Double.MAX_VALUE);

      if (chatId.startsWith("[PVT] ")) {
        String targetUser = chatId.substring(6);
        lblChatHeader.setText("Mensagem Privada: " + targetUser);

        boolean estaBloqueado = meusBloqueados.contains(targetUser);
        Button btnBlockAction = new Button(estaBloqueado ? "\u2298 Desbloquear" : "\u2298 Bloquear");
        btnBlockAction.setStyle(estaBloqueado
            ? "-fx-background-color: rgba(160, 100, 20, 0.30); -fx-text-fill: #d4b06a; -fx-border-color: #d4b06a; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-font-size: 11px; -fx-padding: 4px 10px; -fx-cursor: hand; -fx-font-weight: bold;"
            : "-fx-background-color: transparent; -fx-text-fill: #c9d873; -fx-border-color: #5b6623; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-font-size: 11px; -fx-padding: 4px 10px; -fx-cursor: hand; -fx-font-weight: bold;");

        btnBlockAction.setOnAction(e -> {
          if (meusBloqueados.contains(targetUser)) {
            if (tcp != null) tcp.unblock(targetUser, eu);
            meusBloqueados.remove(targetUser);
            btnBlockAction.setText("\u2298 Bloquear");
            btnBlockAction.setStyle("-fx-background-color: transparent; -fx-text-fill: #c9d873; -fx-border-color: #5b6623; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-font-size: 11px; -fx-padding: 4px 10px; -fx-cursor: hand; -fx-font-weight: bold;");
          } else {
            if (tcp != null) tcp.block(targetUser, eu);
            meusBloqueados.add(targetUser);
            btnBlockAction.setText("\u2298 Desbloquear");
            btnBlockAction.setStyle("-fx-background-color: rgba(160, 100, 20, 0.30); -fx-text-fill: #d4b06a; -fx-border-color: #d4b06a; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-font-size: 11px; -fx-padding: 4px 10px; -fx-cursor: hand; -fx-font-weight: bold;");
          }
        });
        parentHeader.getChildren().add(btnBlockAction);

      } else {
        lblChatHeader.setText("Grupo: " + chatId);

        Button btnDetails = new Button("Detalhes");
        SVGPath menuIcon = new SVGPath();
        menuIcon.setContent("M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z");
        menuIcon.setFill(Color.web("#c9d873"));
        btnDetails.setGraphic(menuIcon);
        btnDetails.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #c9d873; -fx-font-size: 12px; -fx-cursor: hand; -fx-border-color: #5b6623; -fx-border-radius: 12px; -fx-padding: 4px 10px;");
        btnDetails.setOnAction(e -> showGroupDetailsOverlay(chatId));
        parentHeader.getChildren().add(btnDetails);
      }
    }

    // Buscar ou criar historico
    if (!chatHistories.containsKey(chatId)) {
      VBox newHistory = new VBox(10);
      newHistory.setPadding(new Insets(15));
      chatHistories.put(chatId, newHistory);
    }

    VBox history = chatHistories.get(chatId);
    chatScroll.setContent(history);

    // Animar entrada do chat
    history.setOpacity(0);
    TranslateTransition slide = new TranslateTransition(Duration.millis(200), history);
    slide.setFromY(15);
    slide.setToY(0);
    slide.play();

    FadeTransition chatFade = new FadeTransition(Duration.millis(250), history);
    chatFade.setFromValue(0.0);
    chatFade.setToValue(1.0);
    chatFade.play();
  }

  private void onSendMessage(TextField txtMsg) {
    String msg = txtMsg.getText();
    if (msg == null || msg.trim().isEmpty() || currentChat == null)
      return;

    try {
      String idMensagem;
      boolean sentAsVu = isVuMode;
      if (currentChat.startsWith("[PVT] ")) {
        String destino = currentChat.substring(6);
        if (meusBloqueados.contains(destino)) {
          showErrorOverlay("Usuario Bloqueado", "Voce bloqueou este usuario. Desbloqueie-o para poder enviar mensagens.");
          return;
        }
        if (sentAsVu) {
          idMensagem = udp.sendPvtVu(destino, eu, msg);
        } else {
          idMensagem = udp.sendPvt(destino, eu, msg);
        }
      } else {
        if (sentAsVu) {
          idMensagem = udp.sendVu(currentChat, eu, msg);
        } else {
          idMensagem = udp.send(currentChat, eu, msg);
        }
      }

      if (idMensagem != null) {
        messageToChatMap.put(idMensagem, currentChat);
        messageReadConfirmations.put(idMensagem, new HashSet<>());
        messageDeliveryConfirmations.put(idMensagem, new HashSet<>());

        if (!currentChat.startsWith("[PVT] ")) {
          Set<String> expected = new HashSet<>();
          if (tcp != null) {
            try {
              APDU respMembers = tcp.listMembers(currentChat);
              if (respMembers != null && Protocolo.OK.equals(respMembers.getOperacao())) {
                String data = respMembers.getTextoMensagem();
                if (data != null && !data.isEmpty()) {
                  for (String m : data.split(",")) {
                    String norm = normalizarNomeUser(m);
                    if (!norm.isEmpty() && eu != null && !norm.equalsIgnoreCase(normalizarNomeUser(eu.getNome()))) {
                      expected.add(norm);
                    }
                  }
                }
              }
            } catch (Exception ignored) {
            }
          }
          if (expected.isEmpty() && knownGroupMembers.containsKey(currentChat)) {
            for (String km : knownGroupMembers.get(currentChat)) {
              String norm = normalizarNomeUser(km);
              if (!norm.isEmpty() && eu != null && !norm.equalsIgnoreCase(normalizarNomeUser(eu.getNome()))) {
                expected.add(norm);
              }
            }
          }
          messageExpectedMembers.put(idMensagem, expected);
        }
      }

      addChatBubble(currentChat, eu.getNome(), msg, true, false, false, idMensagem, sentAsVu);
      txtMsg.clear();

      if (isVuMode && btnToggleVURef != null) {
        isVuMode = false;
        btnToggleVURef.setSelected(false);
        btnToggleVURef.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #c9d873; -fx-border-color: #5b6623; -fx-border-radius: 20px; -fx-background-radius: 20px; -fx-padding: 8px 12px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
      }
    } catch (exceptions.ConexaoException e) {
      showErrorOverlay("Erro de Envio", "Falha ao enviar mensagem: " + e.getMessage());
    }
  }

  // =========================================================================
  // BOLHAS DE CHAT E DETALHES
  // =========================================================================
  private Color getColorForName(String name) {
    int hash = name.hashCode();
    // Generate a deterministic color in the EDEN palette range
    int[] palette = {
        0xc4a05a, 0x8c9e5e, 0x5b6623, 0x79874c, 0xc9d873, 0xa0b050, 0x8a9b3a
    };
    int colorHex = palette[Math.abs(hash) % palette.length];
    return Color.web(String.format("#%06x", colorHex));
  }

  private void addChatBubble(String chatId, String senderName, String text, boolean sentByMe, boolean isPrivate,
      boolean isSystem) {
    addChatBubble(chatId, senderName, text, sentByMe, isPrivate, isSystem, null, false);
  }

  private void addChatBubble(String chatId, String senderName, String text, boolean sentByMe, boolean isPrivate,
      boolean isSystem, String idMensagem) {
    addChatBubble(chatId, senderName, text, sentByMe, isPrivate, isSystem, idMensagem, false);
  }

  private void addChatBubble(String chatId, String senderName, String text, boolean sentByMe, boolean isPrivate,
      boolean isSystem, String idMensagem, boolean isVisualizacaoUnica) {
    if (!chatHistories.containsKey(chatId)) {
      VBox newHistory = new VBox(10);
      newHistory.setPadding(new Insets(15));
      chatHistories.put(chatId, newHistory);
    }

    VBox history = chatHistories.get(chatId);

    HBox row = new HBox();
    row.setPadding(new Insets(4, 0, 4, 0));

    if (isSystem) {
      Label lblSys = new Label("-- " + text + " --");
      lblSys.setFont(Font.font("Consolas", 11));
      lblSys.setTextFill(Color.web("#8a9b3a"));
      lblSys.setAlignment(Pos.CENTER);
      row.setAlignment(Pos.CENTER);
      row.getChildren().add(lblSys);
    } else {
      VBox bubbleContainer = new VBox(4);
      bubbleContainer.setMaxWidth(400);

      // Avatar and Name header (para mensagens de terceiros)
      HBox header = new HBox(6);
      header.setAlignment(Pos.CENTER_LEFT);

      Circle avatar = new Circle(10, getColorForName(senderName));
      Label initial = new Label(senderName.substring(0, 1).toUpperCase());
      initial.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
      initial.setTextFill(Color.web("#1a1e0b"));
      StackPane avatarStack = new StackPane(avatar, initial);

      Label nameLbl = new Label(senderName);
      nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
      nameLbl.setTextFill(Color.web("#c9d873"));

      String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
      Label timeLbl = new Label(timestamp);
      timeLbl.setFont(Font.font("Consolas", 10));
      timeLbl.setStyle("-fx-text-fill: #a4b455;");

      HBox metaBox = new HBox(3);
      metaBox.setAlignment(Pos.CENTER_RIGHT);
      metaBox.setPadding(new Insets(2, 0, 0, 0));
      metaBox.getChildren().add(timeLbl);

      if (sentByMe) {
        if (isVisualizacaoUnica) {
          Label lblVU = new Label(" \uD83D\uDD12 VU ");
          lblVU.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
          lblVU.setStyle("-fx-text-fill: #8a9b3a; -fx-font-weight: bold;");
          metaBox.getChildren().add(lblVU);
        }
        Label lblTick = new Label(" \uD83D\uDD52"); // 🕒 Relógio: Enviando / aguardando servidor
        lblTick.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        lblTick.setStyle("-fx-text-fill: #8a9b3a;");
        if (idMensagem != null) {
          messageTickLabels.put(idMensagem, lblTick);
        }
        metaBox.getChildren().add(lblTick);
      }

      VBox bubble = new VBox(2);
      bubble.setPadding(new Insets(8, 12, 6, 12));

      if (sentByMe) {
        bubble.setStyle(
            "-fx-background-color: rgba(45, 58, 12, 0.98); -fx-background-radius: 15px 3px 15px 15px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 5, 0, -2, 2);");

        Label lblMsg = new Label(isVisualizacaoUnica ? ("\uD83D\uDD12 " + text + " [VU]") : text);
        lblMsg.setWrapText(true);
        lblMsg.setFont(Font.font("Segoe UI", 13));
        lblMsg.setStyle("-fx-text-fill: #e5e8d7;");

        bubbleContainer.setAlignment(Pos.CENTER_RIGHT);
        bubble.getChildren().addAll(lblMsg, metaBox);
      } else {
        header.getChildren().addAll(avatarStack, nameLbl);
        bubble.setStyle(
            "-fx-background-color: rgba(62, 80, 22, 0.95); -fx-background-radius: 3px 15px 15px 15px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 5, 0, 2, 2);");
        if (isPrivate) {
          bubble.setStyle(
              "-fx-background-color: rgba(55, 72, 18, 0.97); -fx-background-radius: 3px 15px 15px 15px; -fx-border-color: #8a9b3a; -fx-border-width: 1.5px; -fx-border-radius: 3px 15px 15px 15px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 5, 0, 2, 2);");
        }

        bubbleContainer.setAlignment(Pos.CENTER_LEFT);

        if (meusBloqueados.contains(senderName) || "~BLOCKED~".equals(text)) {
          Label lblBlocked = new Label("\u2298 [Mensagem de usuario bloqueado]");
          lblBlocked.setFont(Font.font("Segoe UI", 12));
          lblBlocked.setStyle("-fx-text-fill: #6b7b4a; -fx-font-style: italic;");
          bubble.getChildren().addAll(header, lblBlocked, metaBox);
        } else if (isVisualizacaoUnica) {
          if (idMensagem != null && openedVuMessageIds.contains(idMensagem)) {
            Label lblExpired = new Label("\uD83D\uDD12 Transmissao Classificada Expirada");
            lblExpired.setFont(Font.font("Segoe UI", 12));
            lblExpired.setStyle("-fx-text-fill: #6b7b4a; -fx-font-style: italic;");
            bubble.getChildren().addAll(header, lblExpired, metaBox);
          } else {
            Button btnOpenVU = new Button("\uD83D\uDD12 Abrir Transmissao Classificada (1x)");
            btnOpenVU.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            btnOpenVU.setStyle(
                "-fx-background-color: rgba(40, 55, 12, 0.9); -fx-text-fill: #c9d873; -fx-border-color: #8a9b3a; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px 12px; -fx-cursor: hand;");
            btnOpenVU.setOnAction(e -> showVuModalOverlay(idMensagem, senderName, text, chatId, isPrivate, btnOpenVU));
            bubble.getChildren().addAll(header, btnOpenVU, metaBox);
          }
        } else {
          Label lblMsg = new Label(text);
          lblMsg.setWrapText(true);
          lblMsg.setFont(Font.font("Segoe UI", 13));
          lblMsg.setStyle("-fx-text-fill: #d8e87d;");
          bubble.getChildren().addAll(header, lblMsg, metaBox);
        }
      }

      bubbleContainer.getChildren().add(bubble);

      row.getChildren().add(bubbleContainer);
      if (sentByMe)
        row.setAlignment(Pos.CENTER_RIGHT);
      else
        row.setAlignment(Pos.CENTER_LEFT);
    }

    // Animate entrance
    row.setOpacity(0);
    row.setTranslateY(10);

    history.getChildren().add(row);

    FadeTransition fade = new FadeTransition(Duration.millis(200), row);
    fade.setFromValue(0);
    fade.setToValue(1);
    fade.play();

    TranslateTransition slide = new TranslateTransition(Duration.millis(200), row);
    slide.setFromY(10);
    slide.setToY(0);
    slide.play();
  }

  private void showVuModalOverlay(String idMensagem, String senderName, String secretText, String chatId, boolean isPrivate, Button btnOpenVU) {
    StackPane overlay = new StackPane();
    overlay.setStyle("-fx-background-color: rgba(15, 20, 5, 0.82);");
    overlay.setPadding(new Insets(20));

    VBox modalCard = new VBox(15);
    modalCard.setMaxWidth(420);
    modalCard.setPadding(new Insets(25));
    modalCard.setAlignment(Pos.CENTER);
    modalCard.setStyle(
        "-fx-background-color: #232d0f; -fx-border-color: #8a9b3a; -fx-border-width: 2px; -fx-border-radius: 14px; -fx-background-radius: 14px; -fx-effect: dropshadow(gaussian, rgba(60,80,20,0.7), 16, 0, 0, 0);");

    Label title = new Label("\uD83D\uDD12 Transmissao Classificada — Acesso Unico");
    title.setFont(Font.font("Impact", FontWeight.BOLD, 16));
    title.setTextFill(Color.web("#c9d873"));
    title.setTextAlignment(TextAlignment.CENTER);
    title.setWrapText(true);

    Label subtitle = new Label("Agente: " + senderName);
    subtitle.setFont(Font.font("Consolas", FontWeight.NORMAL, 12));
    subtitle.setTextFill(Color.web("#8a9b3a"));

    Separator divider = new Separator();
    divider.setStyle("-fx-background-color: #5b6623; -fx-opacity: 0.6;");

    Label content = new Label(secretText);
    content.setWrapText(true);
    content.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
    content.setTextFill(Color.web("#e5e8d7"));
    content.setStyle("-fx-background-color: rgba(50,68,15,0.7); -fx-padding: 15px; -fx-background-radius: 10px;");

    Label warning = new Label("[ ATENCAO ] Esta transmissao expira permanentemente ao fechar este painel.");
    warning.setFont(Font.font("Consolas", 11));
    warning.setTextFill(Color.web("#d4a82a"));
    warning.setWrapText(true);
    warning.setTextAlignment(TextAlignment.CENTER);

    Button btnClose = new Button("\u2713 Confirmar Leitura e Encerrar");
    btnClose.setStyle(
        "-fx-background-color: linear-gradient(to bottom, #8a9b3a, #5b6623); -fx-text-fill: #e5e8d7; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 20px; -fx-padding: 8px 20px; -fx-cursor: hand; -fx-border-color: #3f4a23; -fx-border-radius: 20px; -fx-border-width: 1px;");

    Runnable doCloseAndExpire = () -> {
      root.getChildren().remove(overlay);
      if (idMensagem != null) {
        openedVuMessageIds.add(idMensagem);
        // Envia confirmacao de leitura (Status 3 = Lido)
        if (udp != null) {
          String destinoConfirm = isPrivate ? ("@" + eu.getNome()) : chatId;
          udp.sendConfirm(idMensagem, 3, destinoConfirm, senderName);
        }
      }
      if (btnOpenVU != null) {
        btnOpenVU.setText("\uD83D\uDD12 Transmissao Expirada");
        btnOpenVU.setDisable(true);
        btnOpenVU.setStyle("-fx-background-color: rgba(30, 38, 10, 0.7); -fx-text-fill: #6b7b4a; -fx-border-color: #3f4a23; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px 12px;");
      }
    };

    btnClose.setOnAction(e -> doCloseAndExpire.run());

    modalCard.getChildren().addAll(title, subtitle, divider, content, warning, btnClose);
    overlay.getChildren().add(modalCard);

    root.getChildren().add(overlay);
  }

  // =========================================================================
  // OVERLAY DE ERRO
  // =========================================================================
  // OVERLAY DE DETALHES DO GRUPO
  // =========================================================================
  private void showGroupDetailsOverlay(String grupo) {
    VBox box = new VBox(15);
    box.setAlignment(Pos.TOP_CENTER);
    box.setMaxSize(350, 400);
    box.setStyle(
        "-fx-background-color: #232d0f;" +
            "-fx-border-color: #8a9b3a; -fx-border-width: 2px;" +
            "-fx-background-radius: 14px; -fx-border-radius: 14px;" +
            "-fx-padding: 30px;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.45), 15, 0, 0, 5);");

    Label lblTitle = new Label("Detalhes do Grupo");
    lblTitle.setFont(Font.font("Impact", FontWeight.BOLD, 22));
    lblTitle.setTextFill(Color.web("#c9d873"));

    Label lblGroup = new Label(grupo);
    lblGroup.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
    lblGroup.setTextFill(Color.web("#e5e8d7"));

    Label lblMembers = new Label("Membros no Servidor:");
    lblMembers.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
    lblMembers.setTextFill(Color.web("#8a9b3a"));

    ListView<String> membersList = new ListView<>();
    membersList.setStyle("-fx-background-color: rgba(40,58,12,0.5); -fx-background-radius: 8px;");

    StackPane overlay = new StackPane();

    Runnable closeOverlay = () -> {
      FadeTransition fadeOut = new FadeTransition(Duration.millis(200), overlay);
      fadeOut.setFromValue(1);
      fadeOut.setToValue(0);
      fadeOut.setOnFinished(ev -> root.getChildren().remove(overlay));
      fadeOut.play();
    };

    membersList.setCellFactory(lv -> new ListCell<String>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          setStyle("-fx-background-color: transparent;");
        } else {
          HBox cellBox = new HBox(10);
          cellBox.setAlignment(Pos.CENTER_LEFT);
          Label lblName = new Label(item);
          lblName.setTextFill(Color.web("#d8e87d"));
          lblName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
          HBox.setHgrow(lblName, Priority.ALWAYS);
          lblName.setMaxWidth(Double.MAX_VALUE);

          cellBox.getChildren().add(lblName);

          if (!item.contains("(Voc\u00EA)")) {
            SVGPath pvtIcon = new SVGPath();
            pvtIcon.setContent("M2.01 21L23 12 2.01 3 2 10l15 2-15 2z");
            pvtIcon.setFill(Color.web("#8a9b3a"));

            Button btnPvt = new Button();
            btnPvt.setGraphic(pvtIcon);
            btnPvt.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0;");
            addHoverScale(btnPvt);

            btnPvt.setOnAction(e -> {
              closeOverlay.run();
              groupList.getSelectionModel().clearSelection();
              switchChatTo("[PVT] " + item);
            });

            cellBox.getChildren().add(btnPvt);
          }

          setGraphic(cellBox);
          setStyle(
              "-fx-background-color: transparent; -fx-padding: 8px; -fx-border-color: #3f4a23; -fx-border-width: 0 0 1px 0;");
        }
      }
    });

    membersList.getItems().add(eu.getNome() + " (Voc\u00EA)");
    if (tcp != null) {
      APDU resp = tcp.listMembers(grupo);
      if (resp != null && Protocolo.OK.equals(resp.getOperacao())) {
        String data = resp.getTextoMensagem();
        if (data != null && !data.isEmpty()) {
          String[] mArr = data.split(",");
          for (String m : mArr) {
            if (!m.trim().equalsIgnoreCase(eu.getNome())) {
              membersList.getItems().add(m.trim());
            }
          }
        }
      } else {
        Set<String> members = knownGroupMembers.getOrDefault(grupo, new HashSet<>());
        for (String m : members) {
          if (!m.equals(eu.getNome()))
            membersList.getItems().add(m);
        }
      }
    }

    HBox buttons = new HBox(12);
    buttons.setAlignment(Pos.CENTER);

    Button btnClose = new Button("Fechar");
    btnClose.getStyleClass().add("btn-eden");

    Button btnLeave = new Button("Sair do Grupo");
    btnLeave.setStyle(
        "-fx-background-color: rgba(120, 35, 35, 0.80); -fx-text-fill: #e5c0c0; -fx-background-radius: 20px; -fx-border-color: #7a2828; -fx-border-radius: 20px; -fx-border-width: 1px; -fx-padding: 8px 20px; -fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand;");

    buttons.getChildren().addAll(btnClose, btnLeave);
    box.getChildren().addAll(lblTitle, lblGroup, lblMembers, membersList, buttons);
    overlay.getChildren().add(box);
    overlay.setStyle("-fx-background-color: rgba(15, 20, 5, 0.65);");

    btnClose.setOnAction(e -> closeOverlay.run());
    btnLeave.setOnAction(e -> {
      closeOverlay.run();
      // Reuse the existing leave logic
      if (masterGroupData.contains(grupo)) {
        groupList.getSelectionModel().select(grupo);
        onLeaveGroup();
      }
    });

    overlay.setOpacity(0);
    root.getChildren().add(overlay);
    FadeTransition fadeIn = new FadeTransition(Duration.millis(200), overlay);
    fadeIn.setFromValue(0);
    fadeIn.setToValue(1);
    fadeIn.play();
  }

  // =========================================================================
  // OVERLAY DE ERRO (substitui Alert)
  // =========================================================================
  private void showErrorOverlay(String title, String message) {
    showErrorOverlay(title, message, null);
  }

  private void showErrorOverlay(String title, String message, Runnable onOkAction) {
    VBox errorBox = new VBox(15);
    errorBox.setAlignment(Pos.CENTER);
    errorBox.setMaxSize(420, 260);
    errorBox.setStyle(
        "-fx-background-color: #232d0f;" +
            "-fx-border-color: #8a9b3a; -fx-border-width: 2px;" +
            "-fx-background-radius: 14px; -fx-border-radius: 14px;" +
            "-fx-padding: 30px;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.45), 15, 0, 0, 5);");

    Label lblTitle = new Label("[!] " + title);
    lblTitle.setFont(Font.font("Impact", FontWeight.BOLD, 26));
    lblTitle.setTextFill(Color.web("#c9d873"));

    Label lblMsg = new Label(message);
    lblMsg.setFont(Font.font("Segoe UI", 15));
    lblMsg.setTextFill(Color.web("#a4b455"));
    lblMsg.setWrapText(true);
    lblMsg.setTextAlignment(TextAlignment.CENTER);

    Button btnOk = new Button("Reconhecer");
    btnOk.getStyleClass().add("btn-eden");

    errorBox.getChildren().addAll(lblTitle, lblMsg, btnOk);

    StackPane overlay = new StackPane(errorBox);
    overlay.setStyle("-fx-background-color: rgba(15, 20, 5, 0.65);");

    btnOk.setOnAction(e -> {
      FadeTransition fadeOut = new FadeTransition(Duration.millis(200), overlay);
      fadeOut.setFromValue(1);
      fadeOut.setToValue(0);
      fadeOut.setOnFinished(ev -> {
        root.getChildren().remove(overlay);
        if (onOkAction != null) {
          onOkAction.run();
        }
      });
      fadeOut.play();
    });

    overlay.setOpacity(0);
    root.getChildren().add(overlay);
    FadeTransition fadeIn = new FadeTransition(Duration.millis(200), overlay);
    fadeIn.setFromValue(0);
    fadeIn.setToValue(1);
    fadeIn.play();
  }

  /**
   * Coleta todos os enderecos de broadcast de todas as interfaces de rede ativas,
   * alem de 255.255.255.255 e 127.0.0.1.
   */
  private List<java.net.InetAddress> coletarBroadcasts() {
    List<java.net.InetAddress> lista = new ArrayList<>();
    try {
      java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
      while (interfaces != null && interfaces.hasMoreElements()) {
        java.net.NetworkInterface ni = interfaces.nextElement();
        if (ni.isLoopback() || !ni.isUp()) continue;
        for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
          java.net.InetAddress bcast = ia.getBroadcast();
          if (bcast != null && !lista.contains(bcast)) {
            lista.add(bcast);
          }
        }
      }
    } catch (Exception ignored) {
    }
    try {
      java.net.InetAddress global = java.net.InetAddress.getByName("255.255.255.255");
      if (!lista.contains(global)) lista.add(global);
      java.net.InetAddress loopback = java.net.InetAddress.getByName("127.0.0.1");
      if (!lista.contains(loopback)) lista.add(loopback);
    } catch (Exception ignored) {
    }
    return lista;
  }

  /**
   * Exibe overlay quando multiplos servidores forem encontrados na rede local,
   * permitindo ao usuario escolher a qual deseja se conectar.
   */
  private void mostrarSeletorServidores(java.util.Set<String> ips, java.util.function.Consumer<String> onSelected) {
    VBox dialogBox = new VBox(14);
    dialogBox.setAlignment(Pos.CENTER);
    dialogBox.setMaxSize(420, 320);
    dialogBox.setStyle(
        "-fx-background-color: #232d0f;" +
            "-fx-border-color: #8a9b3a; -fx-border-width: 2px;" +
            "-fx-background-radius: 14px; -fx-border-radius: 14px;" +
            "-fx-padding: 24px;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 15, 0, 0, 5);");

    Label lblTitulo = new Label("[ SERVIDORES DETECTADOS ]");
    lblTitulo.setFont(Font.font("Impact", FontWeight.BOLD, 22));
    lblTitulo.setTextFill(Color.web("#c9d873"));

    Label lblSub = new Label("Multiplos servidores responderam na rede. Selecione o servidor desejado:");
    lblSub.setFont(Font.font("Segoe UI", 13));
    lblSub.setTextFill(Color.web("#a4b455"));
    lblSub.setWrapText(true);
    lblSub.setTextAlignment(TextAlignment.CENTER);

    VBox listaIps = new VBox(8);
    listaIps.setAlignment(Pos.CENTER);

    StackPane overlay = new StackPane(dialogBox);
    overlay.setStyle("-fx-background-color: rgba(15, 20, 5, 0.70);");

    for (String ip : ips) {
      Button btnIp = new Button("Conectar em " + ip);
      btnIp.getStyleClass().add("btn-eden");
      btnIp.setPrefWidth(260);
      btnIp.setOnAction(e -> {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), overlay);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(ev -> {
          root.getChildren().remove(overlay);
          onSelected.accept(ip);
        });
        fadeOut.play();
      });
      addHoverScale(btnIp);
      listaIps.getChildren().add(btnIp);
    }

    Button btnCancelar = new Button("Cancelar");
    btnCancelar.setStyle("-fx-background-color: transparent; -fx-text-fill: #8a9b3a; -fx-font-size: 12px; -fx-cursor: hand;");
    btnCancelar.setOnAction(e -> {
      FadeTransition fadeOut = new FadeTransition(Duration.millis(200), overlay);
      fadeOut.setFromValue(1);
      fadeOut.setToValue(0);
      fadeOut.setOnFinished(ev -> root.getChildren().remove(overlay));
      fadeOut.play();
    });

    dialogBox.getChildren().addAll(lblTitulo, lblSub, listaIps, btnCancelar);

    overlay.setOpacity(0);
    root.getChildren().add(overlay);
    FadeTransition fadeIn = new FadeTransition(Duration.millis(200), overlay);
    fadeIn.setFromValue(0);
    fadeIn.setToValue(1);
    fadeIn.play();
  }

  // =========================================================================
  // OVERLAY DE INPUT CUSTOMIZADO (substitui TextInputDialog)
  // =========================================================================
  private void showInputOverlay(String title, String prompt, java.util.function.Consumer<String> onConfirm) {
    VBox box = new VBox(15);
    box.setAlignment(Pos.CENTER);
    box.setMaxSize(440, 280);
    box.setStyle(
        "-fx-background-color: #232d0f;" +
            "-fx-border-color: #8a9b3a; -fx-border-width: 2px;" +
            "-fx-background-radius: 14px; -fx-border-radius: 14px;" +
            "-fx-padding: 30px;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.45), 15, 0, 0, 5);");

    Label lblTitle = new Label(title);
    lblTitle.setFont(Font.font("Impact", FontWeight.BOLD, 24));
    lblTitle.setTextFill(Color.web("#c9d873"));

    Label lblPrompt = new Label(prompt);
    lblPrompt.setFont(Font.font("Segoe UI", 14));
    lblPrompt.setTextFill(Color.web("#a4b455"));

    TextField txtInput = new TextField();
    txtInput.setMaxWidth(300);
    txtInput.setStyle(
        "-fx-background-color: rgba(70,90,25,0.45); -fx-text-fill: #e5e8d7; -fx-prompt-text-fill: #8a9b3a; -fx-background-radius: 20px; -fx-padding: 10px 18px; -fx-font-size: 14px;");

    HBox buttons = new HBox(12);
    buttons.setAlignment(Pos.CENTER);

    Button btnOk = new Button("Confirmar");
    btnOk.getStyleClass().add("btn-eden");

    Button btnCancel = new Button("Cancelar");
    btnCancel.setStyle(
        "-fx-background-color: rgba(70, 85, 28, 0.5); -fx-text-fill: #c9d873; -fx-background-radius: 20px; -fx-border-color: #5b6623; -fx-border-radius: 20px; -fx-border-width: 1px; -fx-padding: 8px 20px; -fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand;");

    buttons.getChildren().addAll(btnOk, btnCancel);
    box.getChildren().addAll(lblTitle, lblPrompt, txtInput, buttons);

    StackPane overlay = new StackPane(box);
    overlay.setStyle("-fx-background-color: rgba(15, 20, 5, 0.65);");

    Runnable closeOverlay = () -> {
      FadeTransition fadeOut = new FadeTransition(Duration.millis(200), overlay);
      fadeOut.setFromValue(1);
      fadeOut.setToValue(0);
      fadeOut.setOnFinished(ev -> root.getChildren().remove(overlay));
      fadeOut.play();
    };

    btnOk.setOnAction(e -> {
      String val = txtInput.getText();
      closeOverlay.run();
      onConfirm.accept(val);
    });
    txtInput.setOnAction(e -> btnOk.fire());
    btnCancel.setOnAction(e -> closeOverlay.run());

    overlay.setOpacity(0);
    root.getChildren().add(overlay);
    FadeTransition fadeIn = new FadeTransition(Duration.millis(200), overlay);
    fadeIn.setFromValue(0);
    fadeIn.setToValue(1);
    fadeIn.play();

    txtInput.requestFocus();
  }

  // =========================================================================
  // OVERLAY DE ESCOLHA CUSTOMIZADO (substitui ChoiceDialog)
  // =========================================================================
  private void showChoiceOverlay(String title, String prompt, List<String> options,
      java.util.function.Consumer<String> onConfirm) {
    VBox box = new VBox(15);
    box.setAlignment(Pos.CENTER);
    box.setMaxSize(440, 380);
    box.setStyle(
        "-fx-background-color: #232d0f;" +
            "-fx-border-color: #8a9b3a; -fx-border-width: 2px;" +
            "-fx-background-radius: 14px; -fx-border-radius: 14px;" +
            "-fx-padding: 30px;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.45), 15, 0, 0, 5);");

    Label lblTitle = new Label(title);
    lblTitle.setFont(Font.font("Impact", FontWeight.BOLD, 24));
    lblTitle.setTextFill(Color.web("#c9d873"));

    Label lblPrompt = new Label(prompt);
    lblPrompt.setFont(Font.font("Segoe UI", 14));
    lblPrompt.setTextFill(Color.web("#a4b455"));

    ListView<String> listOptions = new ListView<>();
    listOptions.getItems().addAll(options);
    listOptions.setMaxHeight(160);
    listOptions.setMaxWidth(300);
    listOptions.setStyle("-fx-background-color: rgba(40,58,12,0.5); -fx-background-radius: 8px;");
    listOptions.setCellFactory(lv -> createStyledCell());
    listOptions.getSelectionModel().selectFirst();

    HBox buttons = new HBox(12);
    buttons.setAlignment(Pos.CENTER);

    Button btnOk = new Button("Entrar");
    btnOk.getStyleClass().add("btn-eden");

    Button btnCancel = new Button("Cancelar");
    btnCancel.setStyle(
        "-fx-background-color: rgba(70, 85, 28, 0.5); -fx-text-fill: #c9d873; -fx-background-radius: 20px; -fx-border-color: #5b6623; -fx-border-radius: 20px; -fx-border-width: 1px; -fx-padding: 8px 20px; -fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand;");

    buttons.getChildren().addAll(btnOk, btnCancel);
    box.getChildren().addAll(lblTitle, lblPrompt, listOptions, buttons);

    StackPane overlay = new StackPane(box);
    overlay.setStyle("-fx-background-color: rgba(15, 20, 5, 0.65);");

    Runnable closeOverlay = () -> {
      FadeTransition fadeOut = new FadeTransition(Duration.millis(200), overlay);
      fadeOut.setFromValue(1);
      fadeOut.setToValue(0);
      fadeOut.setOnFinished(ev -> root.getChildren().remove(overlay));
      fadeOut.play();
    };

    btnOk.setOnAction(e -> {
      String selected = listOptions.getSelectionModel().getSelectedItem();
      closeOverlay.run();
      if (selected != null)
        onConfirm.accept(selected);
    });
    btnCancel.setOnAction(e -> closeOverlay.run());

    // Duplo clique para entrar direto
    listOptions.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2)
        btnOk.fire();
    });

    overlay.setOpacity(0);
    root.getChildren().add(overlay);
    FadeTransition fadeIn = new FadeTransition(Duration.millis(200), overlay);
    fadeIn.setFromValue(0);
    fadeIn.setToValue(1);
    fadeIn.play();
  }

  // =========================================================================
  // CALLBACKS DO LISTENER (Thread UDP -> GUI)
  // =========================================================================
  @Override
  public void onMessageReceived(String idMensagem, String destino, InfoUser remetente, String mensagem, boolean isPrivate, boolean isVisualizacaoUnica) {
    Platform.runLater(() -> {
      String chatId;
      boolean ehPrivado = isPrivate || (destino != null && destino.trim().startsWith("@"));
      if (ehPrivado) {
        String nomeRemetente = remetente.getNome();
        if (nomeRemetente != null && nomeRemetente.startsWith("@")) {
          nomeRemetente = nomeRemetente.substring(1);
        }
        chatId = "[PVT] " + nomeRemetente;
        if (!masterUsersData.contains(nomeRemetente)) {
          masterUsersData.add(nomeRemetente);
        }
      } else {
        chatId = destino;
        if (chatId != null && !chatId.trim().isEmpty() && !chatId.startsWith("@")) {
          if (!masterGroupData.contains(chatId)) {
            masterGroupData.add(chatId);
          }
          // Adiciona o usuario a lista de conhecidos do grupo
          knownGroupMembers.putIfAbsent(chatId, new HashSet<>());
          knownGroupMembers.get(chatId).add(remetente.getNome());
        }
      }

      if (chatId != null && !chatId.trim().isEmpty()) {
        if (mensagem.equals("~JOINED~")) {
          addChatBubble(chatId, "SYSTEM", remetente.getNome() + " entrou no grupo.", false, false, true, null, false);
        } else if (mensagem.equals("~LEFT~")) {
          addChatBubble(chatId, "SYSTEM", remetente.getNome() + " saiu do grupo.", false, false, true, null, false);
          if (knownGroupMembers.containsKey(chatId)) {
            knownGroupMembers.get(chatId).remove(remetente.getNome());
          }
        } else {
          addChatBubble(chatId, remetente.getNome(), mensagem, false, ehPrivado, false, idMensagem, isVisualizacaoUnica);

          // Confirmar leitura (Status 3 = Lido) apenas se nao for de visualizacao unica.
          // Para mensagens VU, a confirmacao de leitura ocorre exclusivamente ao fechar o pop-up modal.
          if (!isVisualizacaoUnica) {
            if (chatId.equals(currentChat) && idMensagem != null && udp != null) {
              String destinoConfirm = ehPrivado ? ("@" + eu.getNome()) : chatId;
              udp.sendConfirm(idMensagem, 3, destinoConfirm, remetente.getNome());
            } else if (idMensagem != null && !mensagem.startsWith("~")) {
              unreadMessageIds.putIfAbsent(chatId, new ArrayList<>());
              unreadMessageIds.get(chatId).add(new MessageConfirmTask(idMensagem, remetente.getNome(), ehPrivado));
            }
          }
        }

        // Unread messages indicator
        if (!chatId.equals(currentChat)) {
          unreadCounts.put(chatId, unreadCounts.getOrDefault(chatId, 0) + 1);
          if (ehPrivado)
            onlineUsersList.refresh();
          else
            groupList.refresh();
        }
      }
    });
  }

  @Override
  public void onShutdown() {
    Platform.runLater(() -> {
      showErrorOverlay("Servidor Encerrado", "O servidor foi desligado. A aplicacao sera fechada.", () -> {
        Platform.exit();
        System.exit(0);
      });
    });
  }

  @Override
  public void onUpdateUsers() {
    Platform.runLater(() -> {
      refreshOnlineUsers();
    });
  }

  private String normalizarNomeUser(String nome) {
    if (nome == null) return "";
    String n = nome.trim();
    if (n.startsWith("@")) n = n.substring(1).trim();
    return n.toLowerCase();
  }

  @Override
  public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {
    Platform.runLater(() -> {
      if (idMensagem == null)
        return;
      Label lblTick = messageTickLabels.get(idMensagem);
      if (lblTick == null)
        return;

      String chatId = messageToChatMap.get(idMensagem);
      boolean isPrivate = (chatId != null && chatId.startsWith("[PVT] "));

      if (status == -1) {
        lblTick.setText(" \u2715");
        lblTick.setStyle("-fx-text-fill: #ff3344; -fx-font-weight: bold;");
        return;
      }

      if (isPrivate || chatId == null) {
        // Chat Privado (1 para 1): Transicao direta
        if (status == 1) {
          lblTick.setText(" \u2713");
          lblTick.setStyle("-fx-text-fill: #8a9b3a; -fx-font-weight: bold;"); // Chegou ao servidor
        } else if (status == 2) {
          lblTick.setText(" \u2713\u2713");
          lblTick.setStyle("-fx-text-fill: #c9d873; -fx-font-weight: bold;"); // Entregue ao dispositivo (Lima EDEN)
        } else if (status == 3) {
          lblTick.setText(" \u2713\u2713");
          lblTick.setStyle("-fx-text-fill: #00ff66; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, rgba(0,255,102,0.7), 6, 0.5, 0, 0);"); // Lido pelo destinatario (Verde Neon)
        }
      } else {
        // Chat de Grupo: Agregacao estrita por membro (Verde Neon apenas quando TODOS lerem)
        Set<String> readUsers = messageReadConfirmations.computeIfAbsent(idMensagem, k -> new HashSet<>());
        Set<String> deliveredUsers = messageDeliveryConfirmations.computeIfAbsent(idMensagem, k -> new HashSet<>());
        Set<String> expectedMembers = messageExpectedMembers.computeIfAbsent(idMensagem, k -> new HashSet<>());

        boolean lidoPorTodosAgregado = false;
        if (nomeConfirmou != null && !nomeConfirmou.isEmpty()) {
          String normConfirm = normalizarNomeUser(nomeConfirmou);
          if (normConfirm.equalsIgnoreCase("todos") || normConfirm.equalsIgnoreCase("servidor") || normConfirm.equalsIgnoreCase("global")) {
            if (status == 3) {
              lidoPorTodosAgregado = true;
              readUsers.addAll(expectedMembers);
              deliveredUsers.addAll(expectedMembers);
            }
          } else if (!normConfirm.isEmpty()) {
            if (status == 2)
              deliveredUsers.add(normConfirm);
            if (status == 3) {
              deliveredUsers.add(normConfirm);
              readUsers.add(normConfirm);
            }
          }
        }

        // Se expectedMembers ainda estiver vazio, tenta inferir de knownGroupMembers
        if (expectedMembers.isEmpty() && knownGroupMembers.containsKey(chatId)) {
          for (String km : knownGroupMembers.get(chatId)) {
            String norm = normalizarNomeUser(km);
            if (!norm.isEmpty() && eu != null && !norm.equalsIgnoreCase(normalizarNomeUser(eu.getNome()))) {
              expectedMembers.add(norm);
            }
          }
        }

        // Se expectedMembers ainda estiver vazio mas recebemos confirmacoes de membros
        if (expectedMembers.isEmpty()) {
          expectedMembers.addAll(deliveredUsers);
          expectedMembers.addAll(readUsers);
        }

        atualizarTickGrupo(lblTick, readUsers, deliveredUsers, expectedMembers, lidoPorTodosAgregado);
      }
    });
  }

  private void atualizarTickGrupo(Label lblTick, Set<String> readUsers, Set<String> deliveredUsers, Set<String> expectedMembers, boolean lidoPorTodosAgregado) {
    boolean todosLeram = lidoPorTodosAgregado || (!expectedMembers.isEmpty() && readUsers.containsAll(expectedMembers));
    boolean algumEntregue = !deliveredUsers.isEmpty() || !readUsers.isEmpty();

    if (todosLeram) {
      lblTick.setText(" \u2713\u2713");
      lblTick.setStyle("-fx-text-fill: #00ff66; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, rgba(0,255,102,0.7), 6, 0.5, 0, 0);"); // Lido por TODOS (Verde Neon)
    } else if (algumEntregue) {
      lblTick.setText(" \u2713\u2713");
      lblTick.setStyle("-fx-text-fill: #c9d873; -fx-font-weight: bold;"); // Entregue / Leitura parcial (Lima EDEN)
    } else {
      lblTick.setText(" \u2713");
      lblTick.setStyle("-fx-text-fill: #8a9b3a; -fx-font-weight: bold;"); // Chegou ao servidor
    }
  }

  private void desconectarLimpo() {
    // Sair explicitamente de todos os grupos para evitar membros fantasmas nos servidores dos colegas
    if (masterGroupData != null && !masterGroupData.isEmpty() && tcp != null && eu != null) {
      List<String> gruposAtuais = new ArrayList<>(masterGroupData);
      for (String grupo : gruposAtuais) {
        try {
          tcp.leave(grupo, eu);
        } catch (Exception ignored) {
        }
      }
    }
    if (tcp != null)
      tcp.fecharConexao();
    if (udp != null)
      udp.fecharConexao();
    tcp = null;
    udp = null;
    eu = null;
    chatHistories.clear();
    unreadCounts.clear();
    knownGroupMembers.clear();
    masterGroupData.clear();
    masterUsersData.clear();
    currentChat = null;
  }

  @Override
  public void stop() {
    desconectarLimpo();
    System.exit(0);
  }
}
