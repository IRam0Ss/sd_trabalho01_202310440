/*****************************************************************
* Autor..............: Lucas de Menezes Chaves
* Matricula........: 202310282
* Inicio...........: 23/06/2026
* Ultima alteracao.: 09/09/2026
* Nome.............: ChatController
* Funcao...........: Controlador da interface grafica
*                    Suporta: grupos, chat privado (SENDPVT),
*                    mensagem de visualizacao unica (SENDVU) e
*                    block/unblock de usuarios
*************************************************************** */
package Controller;

import Protocol.APDU;
import Network.Enviador;
import Network.RecebedorUDP;
import Network.MensagemListener;
import Network.Mensagens;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;
import javafx.stage.Stage;

public class ChatController implements MensagemListener {
  private Enviador cliente;
  private RecebedorUDP recebedor;
  private String nomeUsuario;
  private int portaUDPCliente;
  private String grupoAtual; //pode ser nome de grupo ou "@usuario" para privado

  //Armazena mensagens de grupos E de conversas privadas (chave "@nome" para privado)
  private Map<String, List<MensagemWrapper>> mensagensGrupo;
  //Mapa de id -> wrapper para atualizar os ticks
  private Map<String, MensagemWrapper> mapaMensagensEnviadas;
  //Contador de nao lidas por "conversa" (grupo ou "@usuario")
  private Map<String, Integer> mensagensNaoLidas;
  //Flag que indica se a proxima mensagem a enviar e de visualizacao unica
  private boolean modoVisualizacaoUnica = false;

  @FXML private ListView<String> listaGrupos;
  @FXML private ListView<javafx.scene.text.TextFlow> areaChat;
  @FXML private TextField campoMensagem;
  @FXML private Button botaoEnviar;
  @FXML private Button botaoEntrar;
  @FXML private Button botaoSair;
  @FXML private Button botaoVerMembros;
  @FXML private TextField campoEntrarGrupo;
  @FXML private Button botaoListarGrupos;
  @FXML private Button botaoChatPrivado;
  @FXML private Button botaoVisualizacaoUnica;
  @FXML private Button botaoBloquear;
  @FXML private Label labelStatusVU;

  private Stage stage;

  // -------------------------------------------------------
  // MensagemWrapper - encapsula APDU + estado da mensagem
  // -------------------------------------------------------
  public static class MensagemWrapper {
    public APDU mensagem;
    public boolean eMinha;
    public int statusTick;
    public boolean jaLida;        //para SENDVU: true = ja lida, deve sumir
    public boolean isVU;          //true = e uma mensagem de visualizacao unica

    /*********************************************************************
    * Metodo: MensagemWrapper
    * Funcao: Construtor do wrapper da mensagem
    * @param msg mensagem APDU
    * @param eMinha booleano indicando se eu enviei
    * @return void
    * ****************************************************************** */
    public MensagemWrapper(APDU msg, boolean eMinha) {
      this.mensagem = msg;
      this.eMinha = eMinha;
      this.statusTick = 0;
      this.jaLida = false;
      this.isVU = msg.isVisualizacaoUnica() ||
                  msg.getOperacao().equals("SENDVU");
    }//fim do metodo
  }//fim da classe interna

  /*********************************************************************
  * Metodo: ChatController
  * Funcao: Construtor do Controlador
  * @param cliente objeto cliente
  * @param recebedor recebedor UDP
  * @param nomeUsuario nome do usuario
  * @param portaUDPCliente porta UDP
  * @return void
  * ****************************************************************** */
  public ChatController(Enviador cliente, RecebedorUDP recebedor, String nomeUsuario, int portaUDPCliente) {
    this.cliente = cliente;
    this.recebedor = recebedor;
    this.nomeUsuario = nomeUsuario;
    this.portaUDPCliente = portaUDPCliente;
    this.mensagensGrupo = new HashMap<>();
    this.mapaMensagensEnviadas = new HashMap<>();
    this.mensagensNaoLidas = new HashMap<>();
    recebedor.setListener(this);
  }//fim do metodo

  /*********************************************************************
  * Metodo: setStage
  * Funcao: Define a janela principal
  * @param stage stage do JavaFX
  * @return void
  * ****************************************************************** */
  public void setStage(Stage stage) {
    this.stage = stage;
    atualizarTitulo(this.nomeUsuario);
    stage.setOnCloseRequest(e -> encerrar());
  }//fim do metodo

  /*********************************************************************
  * Metodo: initialize
  * Funcao: Inicializa os componentes FXML
  * @return void
  * ****************************************************************** */
  @FXML
  public void initialize() {
    //Cell factory para mostrar badge de nao lidas e prefixo de tipo
    listaGrupos.setCellFactory(lv -> new ListCell<String>() {
      private final HBox hbox = new HBox(5);
      private final Label prefixLabel = new Label();
      private final Label nameLabel = new Label();
      private final Label unreadLabel = new Label();
      private final Region spacer = new Region();

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        hbox.setAlignment(Pos.CENTER_LEFT);
        prefixLabel.setStyle("-fx-font-size: 12px;");
        unreadLabel.setStyle("-fx-background-color: #25D366; -fx-text-fill: white; " +
          "-fx-background-radius: 10; -fx-padding: 2 6; -fx-font-weight: bold; -fx-font-size: 10px;");
        hbox.getChildren().addAll(prefixLabel, nameLabel, spacer, unreadLabel);
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if(empty || item == null) {
          setGraphic(null);
          setText(null);
        } else {
          //Diferencia grupo de conversa privada pelo prefixo "@"
          if(item.startsWith("@")) {
            prefixLabel.setText("[P] ");
            nameLabel.setText(item.substring(1)); //exibe sem o "@"
          } else {
            prefixLabel.setText("[G] ");
            nameLabel.setText(item);
          }//fim do if-else

          int unread = mensagensNaoLidas.getOrDefault(item, 0);
          if(unread > 0) {
            unreadLabel.setText(String.valueOf(unread));
            unreadLabel.setVisible(true);
            unreadLabel.setManaged(true);
          } else {
            unreadLabel.setVisible(false);
            unreadLabel.setManaged(false);
          }//fim do if-else
          setGraphic(hbox);
        }//fim do if-else
      }//fim do metodo
    });

    listaGrupos.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      selecionarGrupo(newVal);
    });

    renderizarChat(null, null);
  }//fim do metodo

  // -------------------------------------------------------
  // Handlers FXML
  // -------------------------------------------------------

  @FXML private void handleEntrarGrupo()        { entrarGrupo(campoEntrarGrupo.getText().trim()); }
  @FXML private void handleSairGrupo()          { sairGrupo(listaGrupos.getSelectionModel().getSelectedItem()); }
  @FXML private void handleEnviarMensagem()     { enviarMensagem(campoMensagem.getText().trim()); }
  @FXML private void handleChatPrivado()        { iniciarChatPrivado(); }
  @FXML private void handleVisualizacaoUnica()  { toggleModoVU(); }
  @FXML private void handleBloquear()           { gerenciarBloqueio(); }

  // -------------------------------------------------------
  // Logica de grupos
  // -------------------------------------------------------

  /*********************************************************************
  * Metodo: entrarGrupo
  * Funcao: entra num grupo do chat via TCP JOIN
  * @param g nome do grupo
  * @return void
  * ****************************************************************** */
  public void entrarGrupo(String g) {
    if(!g.isEmpty() && !listaGrupos.getItems().contains(g)) {
      APDU mensagemJoin = new APDU("JOIN", g, nomeUsuario, null, portaUDPCliente);
      Mensagens.meuNome = nomeUsuario;
      String resposta = cliente.enviarComandoTCP(mensagemJoin);

      if(resposta != null && (resposta.startsWith("ERRO") || resposta.startsWith("[JOIN-ERRO]"))) {
        if(resposta.contains("uso")) {
          mostrarErro("Usuario ja existente no grupo", "Erro de Usuario");
          String novoNome = pedirNovoNome();
          if(novoNome != null && !novoNome.trim().isEmpty()) {
            this.nomeUsuario = novoNome.trim();
            atualizarTitulo(this.nomeUsuario);
            Mensagens.meuNome = this.nomeUsuario;
          }//fim do if
        } else {
          mostrarErro(resposta, "Erro ao Entrar");
        }//fim do if-else
      } else {
        listaGrupos.getItems().add(g);
        listaGrupos.getSelectionModel().select(g);
        campoEntrarGrupo.clear();

        //Avisa no chat que o usuario entrou
        APDU mensagemAviso = new APDU("SEND", g, nomeUsuario,
          "[Aviso] " + nomeUsuario + " entrou no grupo!", portaUDPCliente);
        MensagemWrapper mwAviso = new MensagemWrapper(mensagemAviso, true);
        mensagensGrupo.putIfAbsent(g, new ArrayList<>());
        mensagensGrupo.get(g).add(mwAviso);
        mapaMensagensEnviadas.put(mensagemAviso.getIdMensagem(), mwAviso);
        cliente.enviarMensagemUDP(mensagemAviso, 7777);
      }//fim do if-else
    }//fim do if
  }//fim do metodo

  /*********************************************************************
  * Metodo: sairGrupo
  * Funcao: sai de um grupo do chat via TCP LEAVE
  * @param g nome do grupo (ou "@usuario" para privado)
  * @return void
  * ****************************************************************** */
  public void sairGrupo(String g) {
    if(g != null) {
      //Chats privados sao apenas removidos da lista local
      if(g.startsWith("@")) {
        listaGrupos.getItems().remove(g);
        if(listaGrupos.getItems().isEmpty()) {
          selecionarGrupo(null);
        } else {
          listaGrupos.getSelectionModel().selectFirst();
        }//fim do if-else
        return;
      }//fim do if

      //Grupos: avisa que saiu e manda LEAVE ao servidor
      APDU mensagemAviso = new APDU("SEND", g, nomeUsuario,
        "[Aviso] " + nomeUsuario + " saiu do grupo!", portaUDPCliente);
      cliente.enviarMensagemUDP(mensagemAviso, 7777);

      listaGrupos.getItems().remove(g);
      APDU mensagemLeave = new APDU("LEAVE", g, nomeUsuario, null, portaUDPCliente);
      cliente.enviarComandoTCP(mensagemLeave);

      if(listaGrupos.getItems().isEmpty()) {
        selecionarGrupo(null);
      } else {
        listaGrupos.getSelectionModel().selectFirst();
      }//fim do if-else
    }//fim do if
  }//fim do metodo

  /*********************************************************************
  * Metodo: selecionarGrupo
  * Funcao: seleciona o grupo atual (ou conversa privada "@usuario")
  * @param grupo nome do grupo ou "@usuario"
  * @return void
  * ****************************************************************** */
  public void selecionarGrupo(String grupo) {
    grupoAtual = grupo;

    if(grupo == null) {
      campoMensagem.setDisable(true);
      botaoEnviar.setDisable(true);
      botaoBloquear.setDisable(true);
      botaoVisualizacaoUnica.setDisable(true);
    } else {
      mensagensNaoLidas.put(grupo, 0);
      listaGrupos.refresh();

      campoMensagem.setDisable(false);
      botaoEnviar.setDisable(false);
      botaoVisualizacaoUnica.setDisable(false);

      //Botao bloquear so aparece em conversas privadas
      boolean ePrivado = grupo.startsWith("@");
      botaoBloquear.setDisable(false);
      botaoBloquear.setText(ePrivado ? "Bloquear/Desbloquear" : "Bloquear Usuario");

      //Marca mensagens como lidas (apenas nao-VU)
      if(mensagensGrupo.containsKey(grupo)) {
        for(MensagemWrapper mw : mensagensGrupo.get(grupo)) {
          if(!mw.eMinha && mw.statusTick < 3 && !mw.isVU) {
            mw.statusTick = 3;
            String donoDaMensagem = mw.mensagem.getNomeUsuario() != null ?
              mw.mensagem.getNomeUsuario().trim() : null;
            APDU lido = new APDU("CONFIRM", mw.mensagem.getIdMensagem(), 3,
              nomeUsuario, grupo, donoDaMensagem);
            cliente.enviarMensagemUDP(lido, 7777);
          }//fim do if
        }//fim do for
      }//fim do if
    }//fim do if-else

    renderizarChat(grupoAtual, mensagensGrupo.getOrDefault(grupoAtual, new ArrayList<>()));
  }//fim do metodo

  // -------------------------------------------------------
  // Logica de envio de mensagem
  // -------------------------------------------------------

  /*********************************************************************
  * Metodo: enviarMensagem
  * Funcao: envia mensagem (grupo ou privado) com suporte a VU
  * @param texto texto da mensagem
  * @return void
  * ****************************************************************** */
  public void enviarMensagem(String texto) {
    if(!texto.isEmpty() && grupoAtual != null) {
      APDU mensagemSend;

      if(grupoAtual.startsWith("@")) {
        //Conversa privada: usa SENDPVT
        String nomeDestinatario = grupoAtual.substring(1);
        String chaveConversa = grupoAtual; //ex: "@Alice"
        mensagemSend = new APDU(
          "SENDPVT",
          chaveConversa,          //nomeGrupo = "@Alice" (identifica a conversa)
          nomeUsuario,            //remetente
          texto,
          portaUDPCliente,
          nomeDestinatario        //destinatario real
        );
        if(modoVisualizacaoUnica) {
          //SENDPVT com VU: cria a APDU e marca o wrapper como visualizacao unica
          mensagemSend.setVisualizacaoUnica(true);
          MensagemWrapper mw = new MensagemWrapper(mensagemSend, true);
          mw.isVU = true;
          mensagensGrupo.putIfAbsent(grupoAtual, new ArrayList<MensagemWrapper>());
          mensagensGrupo.get(grupoAtual).add(mw);
          mapaMensagensEnviadas.put(mensagemSend.getIdMensagem(), mw);
          cliente.enviarMensagemUDP(mensagemSend, 7777);
          desativarModoVU();
          campoMensagem.clear();
          renderizarChat(grupoAtual, mensagensGrupo.getOrDefault(grupoAtual, new ArrayList<MensagemWrapper>()));
          return;
        }//fim do if
      } else if(modoVisualizacaoUnica) {
        //Mensagem VU para grupo
        mensagemSend = new APDU("SENDVU", grupoAtual, nomeUsuario, texto, portaUDPCliente, true);
        desativarModoVU();
      } else {
        //Mensagem normal de grupo
        mensagemSend = new APDU("SEND", grupoAtual, nomeUsuario, texto, portaUDPCliente);
      }//fim do if-elseif-else

      MensagemWrapper mw = new MensagemWrapper(mensagemSend, true);
      mensagensGrupo.putIfAbsent(grupoAtual, new ArrayList<>());
      mensagensGrupo.get(grupoAtual).add(mw);
      mapaMensagensEnviadas.put(mensagemSend.getIdMensagem(), mw);

      cliente.enviarMensagemUDP(mensagemSend, 7777);
      campoMensagem.clear();
      renderizarChat(grupoAtual, mensagensGrupo.getOrDefault(grupoAtual, new ArrayList<>()));
    }//fim do if
  }//fim do metodo

  // -------------------------------------------------------
  // Chat privado
  // -------------------------------------------------------

  /*********************************************************************
  * Metodo: iniciarChatPrivado
  * Funcao: abre dialogo para selecionar usuario e iniciar DM
  * @return void
  * ****************************************************************** */
  public void iniciarChatPrivado() {
    APDU req = new APDU("USERS", null, nomeUsuario, null, portaUDPCliente);
    String resposta = cliente.enviarComandoTCP(req);

    if(resposta != null && resposta.startsWith("OK: ")) {
      String usuariosStr = resposta.substring(4);
      String[] lista = usuariosStr.split(",");

      ListView<HBox> listView = new ListView<>();
      boolean temUsuarios = false;

      for(String u : lista) {
        u = u.trim();
        if(!u.isEmpty() && !u.equals(nomeUsuario)) {
          HBox hbox = new HBox(10);
          hbox.setAlignment(Pos.CENTER_LEFT);
          Label nameLabel = new Label("[P] " + u);
          Region spacer = new Region();
          HBox.setHgrow(spacer, Priority.ALWAYS);

          final String nomeDestFinal = u;
          Button btnConversar = new Button("Conversar");
          btnConversar.setStyle("-fx-background-color: #128C7E; -fx-text-fill: white; -fx-font-weight: bold;");
          btnConversar.setOnAction(e -> {
            abrirConversaPrivada(nomeDestFinal);
          });

          hbox.getChildren().addAll(nameLabel, spacer, btnConversar);
          listView.getItems().add(hbox);
          temUsuarios = true;
        }//fim do if
      }//fim do for

      if(!temUsuarios) {
        listView.getItems().add(new HBox(new Label("Nenhum outro usuario online no momento.")));
      }//fim do if

      Alert alert = new Alert(Alert.AlertType.INFORMATION);
      alert.setTitle("Chat Privado");
      alert.setHeaderText("Selecione um usuario para conversar:");
      alert.getDialogPane().setContent(listView);
      alert.getDialogPane().setPrefSize(400, 300);
      alert.showAndWait();

    } else {
      mostrarErro("Nao foi possivel obter a lista de usuarios.\nVoce precisa estar em um grupo primeiro.", "Erro");
    }//fim do if-else
  }//fim do metodo

  /*********************************************************************
  * Metodo: abrirConversaPrivada
  * Funcao: abre (ou foca) a conversa privada com o usuario informado
  * @param nomeDestinatario nome do usuario para conversar
  * @return void
  * ****************************************************************** */
  public void abrirConversaPrivada(String nomeDestinatario) {
    String chave = "@" + nomeDestinatario;
    if(!listaGrupos.getItems().contains(chave)) {
      listaGrupos.getItems().add(chave);
      mensagensGrupo.putIfAbsent(chave, new ArrayList<>());
    }//fim do if
    listaGrupos.getSelectionModel().select(chave);
  }//fim do metodo

  // -------------------------------------------------------
  // Block / Unblock
  // -------------------------------------------------------

  /*********************************************************************
  * Metodo: gerenciarBloqueio
  * Funcao: abre dialogo para bloquear ou desbloquear usuario
  * @return void
  * ****************************************************************** */
  public void gerenciarBloqueio() {
    //Se estiver numa conversa privada, sugere o nome do interlocutor
    String sugestao = "";
    if(grupoAtual != null && grupoAtual.startsWith("@")) {
      sugestao = grupoAtual.substring(1);
    }//fim do if

    //Opcoes: bloquear ou desbloquear
    Alert escolha = new Alert(Alert.AlertType.CONFIRMATION);
    escolha.setTitle("Gerenciar Bloqueio");
    escolha.setHeaderText("O que deseja fazer?");

    ButtonType btnBloquear    = new ButtonType("Bloquear Usuario");
    ButtonType btnDesbloquear = new ButtonType("Desbloquear Usuario");
    ButtonType btnCancelar    = new ButtonType("Cancelar");
    escolha.getButtonTypes().setAll(btnBloquear, btnDesbloquear, btnCancelar);

    Optional<ButtonType> resultado = escolha.showAndWait();
    if(!resultado.isPresent() || resultado.get() == btnCancelar) return;

    //Pede o nome do usuario
    TextInputDialog dialog = new TextInputDialog(sugestao);
    dialog.setTitle(resultado.get() == btnBloquear ? "Bloquear Usuario" : "Desbloquear Usuario");
    dialog.setHeaderText(resultado.get() == btnBloquear ?
      "Digite o nome do usuario a bloquear:" :
      "Digite o nome do usuario a desbloquear:");
    dialog.setContentText("Nome:");

    Optional<String> nomeOpt = dialog.showAndWait();
    if(!nomeOpt.isPresent()) return;
    String nomeAlvo = nomeOpt.get().trim();
    if(nomeAlvo.isEmpty()) return;

    if(resultado.get() == btnBloquear) {
      bloquearUsuario(nomeAlvo);
    } else {
      desbloquearUsuario(nomeAlvo);
    }//fim do if-else
  }//fim do metodo

  /*********************************************************************
  * Metodo: bloquearUsuario
  * Funcao: envia TCP BLOCK para bloquear o usuario informado
  * @param nomeAlvo nome do usuario a bloquear
  * @return void
  * ****************************************************************** */
  public void bloquearUsuario(String nomeAlvo) {
    APDU req = new APDU("BLOCK", null, nomeUsuario, null, portaUDPCliente, nomeAlvo);
    String resposta = cliente.enviarComandoTCP(req);

    if(resposta != null && resposta.startsWith("OK:")) {
      Alert alert = new Alert(Alert.AlertType.INFORMATION);
      alert.setTitle("Usuario Bloqueado");
      alert.setHeaderText(null);
      alert.setContentText(nomeAlvo + " foi bloqueado com sucesso.\nEle nao podera mais te enviar mensagens privadas.");
      alert.showAndWait();
    } else {
      mostrarErro(resposta != null ? resposta : "Erro ao bloquear usuario.", "Erro");
    }//fim do if-else
  }//fim do metodo

  /*********************************************************************
  * Metodo: desbloquearUsuario
  * Funcao: envia TCP UNBLOCK para desbloquear o usuario informado
  * @param nomeAlvo nome do usuario a desbloquear
  * @return void
  * ****************************************************************** */
  public void desbloquearUsuario(String nomeAlvo) {
    APDU req = new APDU("UNBLOCK", null, nomeUsuario, null, portaUDPCliente, nomeAlvo);
    String resposta = cliente.enviarComandoTCP(req);

    if(resposta != null && resposta.startsWith("OK:")) {
      Alert alert = new Alert(Alert.AlertType.INFORMATION);
      alert.setTitle("Usuario Desbloqueado");
      alert.setHeaderText(null);
      alert.setContentText(nomeAlvo + " foi desbloqueado.");
      alert.showAndWait();
    } else {
      mostrarErro(resposta != null ? resposta : "Erro ao desbloquear usuario.", "Erro");
    }//fim do if-else
  }//fim do metodo

  // -------------------------------------------------------
  // Modo Visualizacao Unica
  // -------------------------------------------------------

  /*********************************************************************
  * Metodo: toggleModoVU
  * Funcao: ativa ou desativa o modo de visualizacao unica
  * @return void
  * ****************************************************************** */
  public void toggleModoVU() {
    modoVisualizacaoUnica = !modoVisualizacaoUnica;
    if(modoVisualizacaoUnica) {
      botaoVisualizacaoUnica.setStyle("-fx-background-color: #ff6b35; -fx-text-fill: white; -fx-font-weight: bold;");
      labelStatusVU.setText("[VU] Modo Visualizacao Unica ATIVO - proxima mensagem some apos lida");
      labelStatusVU.setVisible(true);
    } else {
      desativarModoVU();
    }//fim do if-else
  }//fim do metodo

  /*********************************************************************
  * Metodo: desativarModoVU
  * Funcao: desativa o modo de visualizacao unica
  * @return void
  * ****************************************************************** */
  private void desativarModoVU() {
    modoVisualizacaoUnica = false;
    botaoVisualizacaoUnica.setStyle("");
    labelStatusVU.setVisible(false);
  }//fim do metodo

  // -------------------------------------------------------
  // Callbacks do MensagemListener
  // -------------------------------------------------------

  /*********************************************************************
  * Metodo: onMessageReceived
  * Funcao: callback quando recebe mensagem de grupo (SEND ou SENDVU)
  * @param apdu objeto apdu
  * @return void
  * ****************************************************************** */
  @Override
  public void onMessageReceived(APDU apdu) {
    Platform.runLater(() -> {
      String grupo = apdu.getNomeGrupo();
      if(grupo != null) grupo = grupo.trim();

      mensagensGrupo.putIfAbsent(grupo, new ArrayList<>());
      MensagemWrapper mw = new MensagemWrapper(apdu, false);
      mensagensGrupo.get(grupo).add(mw);

      if(grupoAtual != null && grupo.equals(grupoAtual.trim())) {
        if(!mw.isVU) {
          mw.statusTick = 3;
          String remetente = apdu.getNomeUsuario() != null ? apdu.getNomeUsuario().trim() : null;
          APDU lido = new APDU("CONFIRM", apdu.getIdMensagem(), 3, nomeUsuario, grupo, remetente);
          cliente.enviarMensagemUDP(lido, 7777);
        }
        renderizarChat(grupoAtual, mensagensGrupo.getOrDefault(grupoAtual, new ArrayList<>()));
      } else {
        mensagensNaoLidas.put(grupo, mensagensNaoLidas.getOrDefault(grupo, 0) + 1);
        listaGrupos.refresh();
      }//fim do if-else
    });
  }//fim do metodo

  /*********************************************************************
  * Metodo: onPrivateMessageReceived
  * Funcao: callback quando recebe mensagem privada (SENDPVT)
  * @param apdu objeto apdu
  * @return void
  * ****************************************************************** */
  @Override
  public void onPrivateMessageReceived(APDU apdu) {
    Platform.runLater(() -> {
      //A chave da conversa privada e "@" + nome do remetente
      String chave = "@" + apdu.getNomeUsuario().trim();

      //Adiciona a conversa na lista se ainda nao existir
      if(!listaGrupos.getItems().contains(chave)) {
        listaGrupos.getItems().add(chave);
      }//fim do if

      mensagensGrupo.putIfAbsent(chave, new ArrayList<>());
      MensagemWrapper mw = new MensagemWrapper(apdu, false);
      mensagensGrupo.get(chave).add(mw);

      if(grupoAtual != null && chave.equals(grupoAtual.trim())) {
        if(!mw.isVU) {
          mw.statusTick = 3;
          String remetente = apdu.getNomeUsuario() != null ? apdu.getNomeUsuario().trim() : null;
          APDU lido = new APDU("CONFIRM", apdu.getIdMensagem(), 3, nomeUsuario, chave, remetente);
          cliente.enviarMensagemUDP(lido, 7777);
        }
        renderizarChat(grupoAtual, mensagensGrupo.getOrDefault(grupoAtual, new ArrayList<>()));
      } else {
        mensagensNaoLidas.put(chave, mensagensNaoLidas.getOrDefault(chave, 0) + 1);
        listaGrupos.refresh();
      }//fim do if-else
    });
  }//fim do metodo

  /*********************************************************************
  * Metodo: onTickReceived
  * Funcao: callback quando recebe tick de confirmacao
  * @param apdu objeto apdu
  * @return void
  * ****************************************************************** */
  @Override
  public void onTickReceived(APDU apdu) {
    Platform.runLater(() -> {
      String id = apdu.getIdMensagem();
      if(mapaMensagensEnviadas.containsKey(id)) {
        MensagemWrapper mw = mapaMensagensEnviadas.get(id);

        //Status -1 = destinatario offline ou bloqueou o remetente
        if(apdu.getStatusRecebido() == -1) {
          mw.statusTick = -1;
          if(grupoAtual != null && grupoAtual.equals(mw.mensagem.getNomeGrupo())) {
            renderizarChat(grupoAtual, mensagensGrupo.getOrDefault(grupoAtual, new ArrayList<>()));
          }//fim do if
          return;
        }//fim do if

        if(apdu.getStatusRecebido() > mw.statusTick) {
          mw.statusTick = apdu.getStatusRecebido();
          if(apdu.getStatusRecebido() >= 3 && mw.isVU) {
            mw.jaLida = true;
          }
          if(grupoAtual != null) {
            renderizarChat(grupoAtual, mensagensGrupo.getOrDefault(grupoAtual, new ArrayList<>()));
          }//fim do if
        }//fim do if
      }//fim do if
    });
  }//fim do metodo

  // -------------------------------------------------------
  // Handlers FXML de grupos
  // -------------------------------------------------------

  /*********************************************************************
  * Metodo: handleVerMembros
  * Funcao: Handler do botao Ver membros
  * @return void
  * ****************************************************************** */
  @FXML
  private void handleVerMembros() {
    if(grupoAtual != null && !grupoAtual.startsWith("@")) {
      APDU req = new APDU("MEMBERS", grupoAtual, nomeUsuario, null, portaUDPCliente);
      String resposta = cliente.enviarComandoTCP(req);
      if(resposta != null && resposta.startsWith("OK: ")) {
        String membros = resposta.substring(4);
        String[] lista = membros.split(",");
        StringBuilder sb = new StringBuilder("Membros do grupo " + grupoAtual + ":\n");
        for(String m : lista) {
          if(!m.isEmpty()) {
            sb.append("- ").append(m).append("\n");
          }//fim do if
        }//fim do for
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Membros do Grupo");
        alert.setHeaderText(null);
        alert.setContentText(sb.toString());
        alert.showAndWait();
      } else {
        mostrarErro("Nao foi possivel obter a lista de membros.", "Erro");
      }//fim do if-else
    } else if(grupoAtual != null && grupoAtual.startsWith("@")) {
      //Em chat privado: mostra informacoes da conversa
      Alert alert = new Alert(Alert.AlertType.INFORMATION);
      alert.setTitle("Chat Privado");
      alert.setHeaderText(null);
      alert.setContentText("Conversa privada com: " + grupoAtual.substring(1));
      alert.showAndWait();
    }//fim do if-else
  }//fim do metodo

  /*********************************************************************
  * Metodo: handleListarGrupos
  * Funcao: Handler do botao Listar Grupos Existentes
  * @return void
  * ****************************************************************** */
  @FXML
  private void handleListarGrupos() {
    APDU req = new APDU("LIST", null, nomeUsuario, null, portaUDPCliente);
    String resposta = cliente.enviarComandoTCP(req);

    if(resposta != null && resposta.startsWith("OK: ")) {
      String grupos = resposta.substring(4);
      String[] lista = grupos.split(",");

      ListView<HBox> listView = new ListView<>();
      boolean temGrupos = false;

      for(String g : lista) {
        if(!g.isEmpty()) {
          HBox hbox = new HBox(10);
          hbox.setAlignment(Pos.CENTER_LEFT);
          Label nameLabel = new Label("[G] " + g);
          Region spacer = new Region();
          HBox.setHgrow(spacer, Priority.ALWAYS);

          Button btnEntrar = new Button("Entrar");
          btnEntrar.setOnAction(e -> entrarGrupo(g));

          hbox.getChildren().addAll(nameLabel, spacer, btnEntrar);
          listView.getItems().add(hbox);
          temGrupos = true;
        }//fim do if
      }//fim do for

      if(!temGrupos) {
        listView.getItems().add(new HBox(new Label("Nenhum grupo existente no momento.")));
      }//fim do if

      Alert alert = new Alert(Alert.AlertType.INFORMATION);
      alert.setTitle("Grupos Existentes");
      alert.setHeaderText("Selecione um grupo para entrar:");
      alert.getDialogPane().setContent(listView);
      alert.getDialogPane().setPrefSize(400, 300);
      alert.showAndWait();
    } else {
      mostrarErro("Nao foi possivel obter a lista de grupos.", "Erro");
    }//fim do if-else
  }//fim do metodo

  // -------------------------------------------------------
  // Renderizacao
  // -------------------------------------------------------

  /*********************************************************************
  * Metodo: renderizarChat
  * Funcao: atualiza as mensagens na tela
  *         Mensagens SENDVU sao removidas da lista apos serem lidas
  * @param grupoAtual nome do grupo ou "@usuario"
  * @param msgs lista de mensagens
  * @return void
  * ****************************************************************** */
  public void renderizarChat(String grupoAtual, List<MensagemWrapper> msgs) {
    areaChat.getItems().clear();

    if(grupoAtual == null) {
      javafx.scene.text.Text msg = new javafx.scene.text.Text(
        "Nenhum grupo ou conversa selecionada.\nUse o painel esquerdo para entrar em um grupo ou iniciar um chat privado.");
      msg.setStyle("-fx-fill: gray; -fx-font-style: italic;");
      areaChat.getItems().add(new javafx.scene.text.TextFlow(msg));
      return;
    }//fim do if

    if(msgs != null) {
      for(MensagemWrapper mw : msgs) {
        String remetente = mw.eMinha ? "Voce" : (mw.mensagem.getNomeUsuario() != null ? mw.mensagem.getNomeUsuario() : "Anonimo");
        String cor = mw.eMinha ? "#0066cc" : "#009933";

        javafx.scene.text.Text remetenteText = new javafx.scene.text.Text(remetente + ": ");
        remetenteText.setStyle("-fx-font-weight: bold; -fx-fill: " + cor + ";");

        javafx.scene.text.TextFlow tf = new javafx.scene.text.TextFlow();
        tf.getChildren().add(remetenteText);

        if(mw.isVU) {
          javafx.scene.text.Text prefixoVUText = new javafx.scene.text.Text("[VU] ");
          prefixoVUText.setStyle("-fx-fill: #ff6b35; -fx-font-weight: bold;");
          tf.getChildren().add(prefixoVUText);

          boolean lida = mw.jaLida || (mw.eMinha && mw.statusTick >= 3);

          if(lida) {
            // Mensagem ja foi visualizada (Tanto para remetente quanto para destinatario)
            javafx.scene.text.Text textoAviso = new javafx.scene.text.Text("Esta mensagem já foi lida");
            textoAviso.setStyle("-fx-fill: gray; -fx-font-style: italic;");
            tf.getChildren().add(textoAviso);
          } else if(mw.eMinha) {
            // Remetente vendo sua própria mensagem VU ainda não lida pelo destinatario
            String textoOriginal = mw.mensagem.getTextoMensagem();
            javafx.scene.text.Text conteudoText = new javafx.scene.text.Text(textoOriginal != null ? textoOriginal : "");
            conteudoText.setStyle("-fx-fill: black;");
            tf.getChildren().add(conteudoText);
          } else {
            // Destinatario vendo mensagem VU recebida ainda nao revelada
            javafx.scene.text.Text textoClique = new javafx.scene.text.Text("Clique para revelar a mensagem");
            textoClique.setStyle("-fx-fill: #ff6b35; -fx-font-weight: bold; -fx-underline: true;");
            tf.getChildren().add(textoClique);

            tf.setCursor(javafx.scene.Cursor.HAND);
            tf.setOnMouseClicked(event -> {
              // Popup com o conteudo da mensagem
              Alert alert = new Alert(Alert.AlertType.INFORMATION);
              alert.setTitle("Visualizacao Unica");
              alert.setHeaderText("Mensagem de " + (mw.mensagem.getNomeUsuario() != null ? mw.mensagem.getNomeUsuario() : "Usuario"));
              alert.setContentText(mw.mensagem.getTextoMensagem());
              alert.showAndWait();

              // Assim que fechar o popup:
              mw.jaLida = true;
              mw.statusTick = 3;

              // Envia confirmacao de leitura (CONFIRM status 3) para o servidor/remetente
              String donoDaMensagem = mw.mensagem.getNomeUsuario() != null ? mw.mensagem.getNomeUsuario().trim() : null;
              APDU lido = new APDU("CONFIRM", mw.mensagem.getIdMensagem(), 3, nomeUsuario, grupoAtual, donoDaMensagem);
              cliente.enviarMensagemUDP(lido, 7777);

              // Atualiza o chat na UI
              renderizarChat(grupoAtual, msgs);
            });
          }
        } else {
          // Mensagem normal (nao VU)
          String texto = mw.mensagem.getTextoMensagem();
          javafx.scene.text.Text conteudoText = new javafx.scene.text.Text(texto != null ? texto : "");
          conteudoText.setStyle("-fx-fill: black;");
          tf.getChildren().add(conteudoText);
        }

        // Ticks de confirmacao (apenas para mensagens enviadas por mim)
        if(mw.eMinha) {
          javafx.scene.text.Text tickText = new javafx.scene.text.Text("");
          if(mw.statusTick == -1) {
            tickText.setText(" [X]");
            tickText.setStyle("-fx-fill: red; -fx-font-weight: bold;");
          } else if(mw.statusTick == 0) {
            tickText.setText(" ...");
            tickText.setStyle("-fx-fill: gray;");
          } else if(mw.statusTick == 1) {
            tickText.setText(" \u2713");
            tickText.setStyle("-fx-fill: gray; -fx-font-weight: bold;");
          } else if(mw.statusTick == 2) {
            tickText.setText(" \u2713\u2713");
            tickText.setStyle("-fx-fill: gray; -fx-font-weight: bold;");
          } else if(mw.statusTick >= 3) {
            tickText.setText(" \u2713\u2713");
            tickText.setStyle("-fx-fill: #34B7F1; -fx-font-weight: bold;");
          }
          tf.getChildren().add(tickText);
        }

        areaChat.getItems().add(tf);
      }
    }

    int lastIndex = areaChat.getItems().size() - 1;
    if(lastIndex >= 0) {
      areaChat.scrollTo(lastIndex);
    }
  }//fim do metodo

  // -------------------------------------------------------
  // Utilitarios
  // -------------------------------------------------------

  public void mostrarErro(String mensagem, String titulo) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(titulo);
    alert.setHeaderText(null);
    alert.setContentText(mensagem);
    alert.showAndWait();
  }//fim do metodo

  public String pedirNovoNome() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Nome de Usuario");
    dialog.setHeaderText("Escolha outro nome de usuario:");
    dialog.setContentText("Nome:");
    Optional<String> result = dialog.showAndWait();
    return result.orElse(null);
  }//fim do metodo

  public void atualizarTitulo(String nome) {
    if(stage != null) {
      stage.setTitle("WhatsApp - " + nome);
    }//fim do if
  }//fim do metodo

  public void encerrar() {
    System.out.println("A encerrar cliente...");
    System.exit(0);
  }//fim do metodo
}//fim da classe
