/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 15/09/2026
 * Nome: ClienteUDP
 * Funcao: Gerencia o envio e recepcao assincrona de datagramas UDP para mensagens em tempo real.
 */

package model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import Protocol.APDU;
import utils.InfoUser;

/**
 * Classe responsavel por gerenciar as conexoes e canais UDP do cliente.
 * Implementa {@link Runnable} para escutar em background mensagens de grupo, mensagens privadas e notificacoes.
 * Suporta o envio e recepcao de confirmacoes de mensagem (ticks de entrega/leitura).
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class ClienteUDP implements Runnable {

  private int portaServidor;
  private final InetAddress IP_SERVIDOR;
  private DatagramSocket socketUDP;
  private static final int BUFFER = 4096;
  private MessageListener listener;
  private String meuNome;

  /**
   * Construtor que inicializa o socket UDP em uma porta efemera disponivel.
   * 
   * @param ipServidor    Endereco IP do servidor alvo.
   * @param portaServidor Porta UDP de escuta do servidor.
   * @throws UnknownHostException Caso o host nao seja resolvido.
   * @throws SocketException      Caso haja erro ao vincular a porta local.
   */
  public ClienteUDP(String ipServidor, int portaServidor)
      throws UnknownHostException, SocketException {
    this.IP_SERVIDOR = InetAddress.getByName(ipServidor);
    this.portaServidor = portaServidor;

    // Construtor sem porta vincula a uma porta livre alocada pelo S.O.
    socketUDP = new DatagramSocket();

    System.out.println("[CLIENTE:UDP] [INFO] Socket vinculado a porta efemera " + socketUDP.getLocalPort());
  }

  /**
   * Retorna a porta local atribuida pelo sistema operacional ao socket.
   * 
   * @return Numero da porta local.
   */
  public int getPortaLocal() {
    return socketUDP.getLocalPort();
  }

  /**
   * Define o listener para despacho de eventos de mensagens e notificacoes para a GUI.
   * 
   * @param listener O ouvinte que implementa {@link MessageListener}.
   */
  public void setListener(MessageListener listener) {
    this.listener = listener;
  }

  /**
   * Define o nome do usuario cliente local para autorrespostas de confirmacao (CONFIRM).
   * 
   * @param meuNome Nome do usuario logado.
   */
  public void setMeuNome(String meuNome) {
    this.meuNome = meuNome;
  }

  /**
   * Converte uma APDU em array de bytes para transmissao via datagrama UDP.
   * 
   * @param apdu Objeto APDU a ser serializado.
   * @return Vetor de bytes serializado.
   * @throws IOException Caso ocorra falha na serializacao.
   */
  private byte[] serializarAPDU(APDU apdu) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(apdu);
    oos.flush();
    return baos.toByteArray();
  }

  /**
   * Envia uma mensagem destinada a um grupo de chat via datagrama UDP ao servidor.
   * 
   * @param nomeGrupo Nome do grupo de destino.
   * @param usuario   Dados do usuario remetente.
   * @param mensagem  Texto da mensagem a ser enviada.
   * @return ID unico gerado para a mensagem (para rastreamento de ticks).
   * @throws exceptions.ConexaoException Caso ocorra erro de envio no socket.
   */
  public String send(String nomeGrupo, InfoUser usuario, String mensagem) throws exceptions.ConexaoException {
    APDU apdu = new APDU("SEND", nomeGrupo, usuario.getNome(), mensagem, usuario.getPorta());
    try {
      byte[] dadosEnviados = serializarAPDU(apdu);
      DatagramPacket pacoteEnvio = new DatagramPacket(dadosEnviados, dadosEnviados.length, IP_SERVIDOR, portaServidor);
      socketUDP.send(pacoteEnvio);
      System.out.println("[CLIENTE:UDP] [INFO] Mensagem enviada ao servidor. ID: " + apdu.getIdMensagem());
      return apdu.getIdMensagem();
    } catch (IOException e) {
      System.out.println("[CLIENTE:UDP] [ERROR] Falha ao enviar mensagem para o servidor.");
      e.printStackTrace();
      throw new exceptions.ConexaoException("Falha ao enviar mensagem via UDP", e);
    }
  }

  /**
   * Envia uma mensagem privada direta a um usuario especifico via datagrama UDP.
   * 
   * @param nomeDestinatario Nome do usuario destinatario.
   * @param usuario          Dados do remetente.
   * @param mensagem         Texto da mensagem privada.
   * @return ID unico gerado para a mensagem (para rastreamento de ticks).
   * @throws exceptions.ConexaoException Caso ocorra erro de envio no socket.
   */
  public String sendPvt(String nomeDestinatario, InfoUser usuario, String mensagem) throws exceptions.ConexaoException {
    APDU apdu = new APDU("SENDPVT", "@" + nomeDestinatario, usuario.getNome(), mensagem, usuario.getPorta(), nomeDestinatario);
    try {
      byte[] dadosEnviados = serializarAPDU(apdu);
      DatagramPacket pacoteEnvio = new DatagramPacket(dadosEnviados, dadosEnviados.length, IP_SERVIDOR, portaServidor);
      socketUDP.send(pacoteEnvio);

      System.out.println("[CLIENTE:UDP] [INFO] Mensagem privada enviada ao servidor. ID: " + apdu.getIdMensagem());
      return apdu.getIdMensagem();
    } catch (IOException e) {
      System.out.println("[CLIENTE:UDP] [ERROR] Falha ao enviar mensagem privada.");
      e.printStackTrace();
      throw new exceptions.ConexaoException("Falha ao enviar mensagem privada via UDP", e);
    }
  }

  /**
   * Envia uma APDU de confirmacao (CONFIRM) de recebimento ou leitura para o servidor.
   * 
   * @param idMensagem         Identificador unico da mensagem recebida.
   * @param status             Status da confirmacao (2=Entregue, 3=Lida).
   * @param nomeGrupoOuDestino Nome do grupo ou "@destinatario".
   * @param donoDaMensagem     Nome do remetente original da mensagem.
   */
  public void sendConfirm(String idMensagem, int status, String nomeGrupoOuDestino, String donoDaMensagem) {
    if (idMensagem == null || idMensagem.isEmpty()) {
      return;
    }
    String remetenteConfirm = (this.meuNome != null) ? this.meuNome : "Anonimo";
    APDU confirmApdu = new APDU("CONFIRM", idMensagem, status, remetenteConfirm, nomeGrupoOuDestino, donoDaMensagem);
    try {
      byte[] dadosEnviados = serializarAPDU(confirmApdu);
      DatagramPacket pacoteEnvio = new DatagramPacket(dadosEnviados, dadosEnviados.length, IP_SERVIDOR, portaServidor);
      socketUDP.send(pacoteEnvio);
      System.out.println("[CLIENTE:UDP] [INFO] CONFIRM (Status " + status + ") enviado para mensagem: " + idMensagem);
    } catch (IOException e) {
      System.err.println("[CLIENTE:UDP] [ERROR] Falha ao enviar CONFIRM: " + e.getMessage());
    }
  }

  /**
   * Loop de recepcao assincrona de datagramas UDP e deserializacao de objetos APDU.
   */
  @Override
  public void run() {
    byte[] bufferRecepcao = new byte[BUFFER];

    while (!socketUDP.isClosed()) {
      DatagramPacket pacoteRecebido = new DatagramPacket(bufferRecepcao, bufferRecepcao.length);
      try {
        socketUDP.receive(pacoteRecebido);

        ByteArrayInputStream bais = new ByteArrayInputStream(pacoteRecebido.getData(), 0, pacoteRecebido.getLength());
        ObjectInputStream ois = new ObjectInputStream(bais);
        APDU apdu = (APDU) ois.readObject();

        String comando = apdu.getOperacao();
        if (utils.Protocolo.SHUTDOWN.equals(comando)) {
          System.out.println("\n[SISTEMA] O servidor foi encerrado. A aplicacao sera finalizada.");
          if (listener != null)
            listener.onShutdown();
          else
            System.exit(0);
        }

        if (utils.Protocolo.UPDATE_USERS.equals(comando)) {
          System.out.println("[CLIENTE:UDP] [INFO] Recebida notificacao de atualizacao de usuarios.");
          if (listener != null)
            listener.onUpdateUsers();
          continue;
        }

        if (utils.Protocolo.CONFIRM.equals(comando)) {
          System.out.println("[CLIENTE:UDP] [INFO] Confirmacao (tick) recebida para mensagem: " + apdu.getIdMensagem()
              + " Status: " + apdu.getStatusRecebido());
          if (listener != null) {
            listener.onTickReceived(apdu.getIdMensagem(), apdu.getStatusRecebido(), apdu.getNomeUsuario());
          }
          continue;
        }

        if (utils.Protocolo.SENDPVT.equals(comando)) {
          String nomeRemetente = apdu.getNomeUsuario();
          if (nomeRemetente != null && nomeRemetente.startsWith("@")) {
            nomeRemetente = nomeRemetente.substring(1);
          }
          InfoUser remetente = new InfoUser(nomeRemetente, pacoteRecebido.getAddress().getHostAddress(), apdu.getPortaClienteUDP());
          String mensagemPvt = apdu.getTextoMensagem();
          System.out.println("\n[MENSAGEM PRIVADA] " + remetente.getNome() + " diz: " + mensagemPvt);
          
          // Auto-ACK de entrega (Status 2: Entregue no dispositivo do cliente)
          if (apdu.getIdMensagem() != null) {
            sendConfirm(apdu.getIdMensagem(), 2, "@" + meuNome, remetente.getNome());
          }

          if (listener != null)
            listener.onMessageReceived(apdu.getIdMensagem(), remetente.getNome(), remetente, mensagemPvt, true);
          continue;
        }

        InfoUser usuario = new InfoUser(apdu.getNomeUsuario(), pacoteRecebido.getAddress().getHostAddress(), apdu.getPortaClienteUDP());
        String mensagem = apdu.getTextoMensagem();
        String grupo = apdu.getNomeGrupo();
        System.out.println("\n[CLIENTE:UDP] [INFO] Nova mensagem recebida no grupo " + grupo + ":\n"
            + usuario.toString() + " enviou: " + mensagem);

        // Auto-ACK de entrega (Status 2: Entregue no dispositivo do cliente)
        if (apdu.getIdMensagem() != null && mensagem != null && !mensagem.startsWith("~")) {
          sendConfirm(apdu.getIdMensagem(), 2, grupo, usuario.getNome());
        }

        if (listener != null)
          listener.onMessageReceived(apdu.getIdMensagem(), grupo, usuario, mensagem, false);

      } catch (SocketException e) {
        // Excecao esperada no encerramento normal do socket
      } catch (ClassNotFoundException | IOException e) {
        System.out.println("[CLIENTE:UDP] [ERROR] Falha ao receber mensagem do servidor.");
        e.printStackTrace();
      } catch (Exception e) {
        System.out.println("[CLIENTE:UDP] [ERROR] Falha ao processar o pacote APDU recebido. Erro interno.");
        e.printStackTrace();
      }
    }

  }

  /**
   * Fecha o socket UDP liberando a porta alocada.
   */
  public void fecharConexao() {
    this.socketUDP.close();
  }

}
