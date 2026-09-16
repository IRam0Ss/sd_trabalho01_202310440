/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: ClienteTCP
 * Funcao: Gerencia o canal de comunicacao confiavel TCP (controle, autenticacao e consultas de estado).
 */

package model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import Protocol.APDU;
import utils.InfoUser;
import utils.Protocolo;

/**
 * Classe responsavel por gerenciar a conexao de transporte TCP do cliente com o servidor central.
 * Realiza a transmissao e recepcao de comandos de controle serializados (JOIN, LEAVE, LIST, USERS, MEMBERS, REGISTER).
 * Possui suporte a servidores persistentes e servidores transientes (com reconexao automatica transparente).
 * Compativel com respostas tanto em formato objeto {@link APDU} quanto em formato textual {@link String} ("OK: ...", "ERRO: ...").
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class ClienteTCP {

  private final String ipServidor;
  private final int portaServidor;
  private Socket conexaoTCP;
  private ObjectOutputStream saidaObjetos;
  private ObjectInputStream entradaObjetos;


  /**
   * Construtor que inicializa a conexao TCP com o servidor e os fluxos de objetos.
   * 
   * @param ipServidor    Endereco IP do servidor.
   * @param portaServidor Porta TCP do servidor.
   * @throws UnknownHostException Caso o endereco do host nao seja resolvido.
   * @throws IOException          Em caso de falha de I/O na abertura do socket.
   */
  public ClienteTCP(String ipServidor, int portaServidor) throws UnknownHostException, IOException {
    this.ipServidor = ipServidor;
    this.portaServidor = portaServidor;
    conectar();
  }

  /**
   * Estabelece a conexao de socket com o servidor e inicializa os streams de objetos.
   * 
   * @throws IOException Em caso de erro de abertura de rede.
   */
  private synchronized void conectar() throws IOException {
    this.conexaoTCP = new Socket(ipServidor, portaServidor);
    this.saidaObjetos = new ObjectOutputStream(conexaoTCP.getOutputStream());
    this.saidaObjetos.flush();
    this.entradaObjetos = new ObjectInputStream(conexaoTCP.getInputStream());
    System.out.println("[CLIENTE:TCP] [INFO] Conectado ao servidor " + ipServidor + ":" + portaServidor);
  }

  /**
   * Garante que o socket esteja aberto e operante antes de enviar comandos.
   * 
   * @throws IOException Caso falhe a reconexao.
   */
  private synchronized void assegurarConexao() throws IOException {
    if (conexaoTCP == null || conexaoTCP.isClosed() || conexaoTCP.isInputShutdown() || conexaoTCP.isOutputShutdown()) {
      conectar();
    }
  }



  /**
   * Realiza a leitura e conversao polimorfica da resposta do servidor.
   * Aceita tanto objetos APDU quanto Strings serializadas padrao ("OK: ...", "ERRO: ...").
   * 
   * @return APDU interpretada com o resultado da operacao.
   * @throws IOException            Caso ocorra erro no canal de comunicacao.
   * @throws ClassNotFoundException Caso o tipo do objeto nao seja reconhecido.
   */
  private APDU lerResposta() throws IOException, ClassNotFoundException {
    Object obj = entradaObjetos.readObject();
    if (obj instanceof APDU) {
      return (APDU) obj;
    } else if (obj instanceof String) {
      String str = ((String) obj).trim();
      String operacao = Protocolo.OK;
      String msg = str;

      if (str.startsWith("OK")) {
        operacao = Protocolo.OK;
        msg = str.substring(2).trim();
      } else if (str.startsWith("ERRO")) {
        operacao = Protocolo.ERRO;
        msg = str.substring(4).trim();
      }

      while (msg.startsWith(":") || msg.startsWith("/") || msg.startsWith("~") || msg.startsWith("-") || msg.startsWith(" ")) {
        if (msg.startsWith("~/")) {
          msg = msg.substring(2).trim();
        } else {
          msg = msg.substring(1).trim();
        }
      }

      return new APDU(operacao, null, null, msg, 0);
    }
    return new APDU(Protocolo.ERRO, null, null, "Resposta de tipo desconhecido: " + obj, 0);
  }

  /**
   * Executa a transmissao de um comando APDU com tratamento de desconexoes e retransmissao.
   * 
   * @param apdu APDU de comando a ser transmitida.
   * @return APDU de resposta obtida do servidor.
   */
  private synchronized APDU executarComando(APDU apdu) {
    try {
      assegurarConexao();
      saidaObjetos.writeUnshared(apdu);
      saidaObjetos.flush();
      return lerResposta();
    } catch (IOException | ClassNotFoundException e) {
      // Servidores transientes fecham o socket apos cada resposta.
      // Tentativa de reconexao transparente automatica.
      try {
        fecharConexao();
        conectar();
        saidaObjetos.writeUnshared(apdu);
        saidaObjetos.flush();
        return lerResposta();
      } catch (Exception ex) {
        System.err.println("[CLIENTE:TCP] [ERROR] Falha na comunicacao TCP: " + ex.getMessage());
        return new APDU(Protocolo.ERRO, null, null, "Falha de conexao com o servidor: " + ex.getMessage(), 0);
      }
    }
  }

  /**
   * Envia requisicao JOIN para ingressar ou criar um grupo de mensagens.
   * 
   * @param nomeGrupo Nome do grupo desejado.
   * @param usuario   Dados do usuario solicitante.
   * @return APDU de resposta retornada pelo servidor.
   */
  public synchronized APDU join(String nomeGrupo, InfoUser usuario) {
    APDU apdu = new APDU("JOIN", nomeGrupo, usuario.getNome(), null, usuario.getPorta());
    System.out.println("[CLIENTE:TCP] [INFO] JOIN enviado ao servidor: " + usuario.toString());
    return executarComando(apdu);
  }

  /**
   * Envia requisicao LEAVE para sair de um grupo.
   * 
   * @param nomeGrupo Nome do grupo.
   * @param usuario   Dados do usuario solicitante.
   * @return APDU de resposta retornada pelo servidor.
   */
  public synchronized APDU leave(String nomeGrupo, InfoUser usuario) {
    APDU apdu = new APDU("LEAVE", nomeGrupo, usuario.getNome(), null, usuario.getPorta());
    System.out.println("[CLIENTE:TCP] [INFO] LEAVE enviado ao servidor: " + usuario.toString());
    return executarComando(apdu);
  }

  /**
   * Solicita a lista de grupos ativos cadastrados no servidor.
   * 
   * @return APDU com a listagem de grupos no campo textoMensagem.
   */
  public synchronized APDU list() {
    APDU apdu = new APDU("LIST", null, null, null, 0);
    System.out.println("[CLIENTE:TCP] [INFO] LIST enviado ao servidor");
    return executarComando(apdu);
  }

  /**
   * Envia requisicao REGISTER para registrar o cliente no servidor e validar unicidade do nome.
   * 
   * @param usuario Informacoes do usuario cliente (incluindo a porta UDP em que escuta).
   * @return APDU de resposta (OK ou ERRO).
   */
  public synchronized APDU register(InfoUser usuario) {
    APDU apdu = new APDU("REGISTER", "GLOBAL", usuario.getNome(), null, usuario.getPorta());
    return executarComando(apdu);
  }

  /**
   * Solicita a lista de todos os usuarios conectados atualmente ao servidor.
   * 
   * @return APDU com os nomes dos usuarios no campo textoMensagem.
   */
  public synchronized APDU listUsers() {
    APDU apdu = new APDU("USERS", null, null, null, 0);
    System.out.println("[CLIENTE:TCP] [INFO] USERS enviado ao servidor");
    return executarComando(apdu);
  }

  /**
   * Solicita a lista de membros que pertencem a um grupo especifico.
   * 
   * @param nomeGrupo Nome do grupo a consultar.
   * @return APDU com a listagem de membros no campo textoMensagem.
   */
  public synchronized APDU listMembers(String nomeGrupo) {
    APDU apdu = new APDU("MEMBERS", nomeGrupo, null, null, 0);
    System.out.println("[CLIENTE:TCP] [INFO] MEMBERS enviado ao servidor para grupo: " + nomeGrupo);
    return executarComando(apdu);
  }

  /**
   * Envia requisicao BLOCK para bloquear um usuario.
   * 
   * @param usuarioDestino Nome do usuario a ser bloqueado.
   * @param usuario        Dados do usuario solicitante.
   * @return APDU de resposta.
   */
  public synchronized APDU block(String usuarioDestino, InfoUser usuario) {
    APDU apdu = new APDU("BLOCK", null, usuario.getNome(), null, usuario.getPorta(), usuarioDestino);
    System.out.println("[CLIENTE:TCP] [INFO] BLOCK enviado ao servidor para: " + usuarioDestino);
    return executarComando(apdu);
  }

  /**
   * Envia requisicao UNBLOCK para desbloquear um usuario.
   * 
   * @param usuarioDestino Nome do usuario a ser desbloqueado.
   * @param usuario        Dados do usuario solicitante.
   * @return APDU de resposta.
   */
  public synchronized APDU unblock(String usuarioDestino, InfoUser usuario) {
    APDU apdu = new APDU("UNBLOCK", null, usuario.getNome(), null, usuario.getPorta(), usuarioDestino);
    System.out.println("[CLIENTE:TCP] [INFO] UNBLOCK enviado ao servidor para: " + usuarioDestino);
    return executarComando(apdu);
  }

  /**
   * Encerra o socket e os fluxos de objetos da conexao TCP.
   */
  public void fecharConexao() {
    try {
      if (saidaObjetos != null) {
        saidaObjetos.close();
      }
      if (entradaObjetos != null) {
        entradaObjetos.close();
      }
      if (conexaoTCP != null && !conexaoTCP.isClosed()) {
        conexaoTCP.close();
      }
      System.out.println("[CLIENTE:TCP] [INFO] Conexao encerrada com o servidor.");
    } catch (IOException e) {
      System.err.println("[CLIENTE:TCP] [ERROR] Erro ao fechar conexao: " + e.getMessage());
    }
  }

  /**
   * Obtem o endereco IP local vinculado a conexao TCP ativa.
   * 
   * @return String com o IP local.
   */
  public String getIpLocal() {
    try {
      if (conexaoTCP != null && !conexaoTCP.isClosed() && conexaoTCP.getLocalAddress() != null) {
        String ip = conexaoTCP.getLocalAddress().getHostAddress();
        if (!"0.0.0.0".equals(ip)) {
          return ip;
        }
      }
      return InetAddress.getLocalHost().getHostAddress();
    } catch (Exception e) {
      return "127.0.0.1";
    }
  }

} // fim da classe
