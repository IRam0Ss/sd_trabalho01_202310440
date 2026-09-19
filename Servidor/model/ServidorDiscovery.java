/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: ServidorDiscovery
 * Funcao: Servico de farol UDP para descoberta automatica do IP do servidor por broadcast na rede local.
 */

package model;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import utils.Protocolo;

/**
 * Thread de discovery local (farol UDP) com suporte a multiplas portas.
 * Responde a buscas padronizadas do grupo ("SERVIDOR_IP" -> "IP") e legadas ("DISCOVER_EDEN" -> "EDEN_HERE").
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class ServidorDiscovery implements Runnable {

  private final int porta;
  private DatagramSocket socket;

  /**
   * Construtor padrao: escuta na porta de discovery universal (8888).
   */
  public ServidorDiscovery() {
    this(Protocolo.PORTA_DISCOVERY);
  }

  /**
   * Construtor parametrizado para porta especifica.
   * 
   * @param porta Porta UDP em que o beacon ira escutar.
   */
  public ServidorDiscovery(int porta) {
    this.porta = porta;
  }

  @Override
  public void run() {
    try {
      socket = new DatagramSocket(porta);
      socket.setBroadcast(true);
      System.out.println("[DISCOVERY:" + porta + "] Farol UDP ligado na porta " + porta + " aguardando buscas...");

      byte[] buffer = new byte[256];

      while (!socket.isClosed()) {
        DatagramPacket pacoteRecebido = new DatagramPacket(buffer, buffer.length);
        socket.receive(pacoteRecebido);

        String mensagem = new String(pacoteRecebido.getData(), 0, pacoteRecebido.getLength(),
            StandardCharsets.UTF_8).trim();

        if ("SERVIDOR_IP".equalsIgnoreCase(mensagem)) {
          System.out.println("[DISCOVERY:" + porta + "] Busca padrao ('SERVIDOR_IP') recebida de: "
              + pacoteRecebido.getAddress().getHostAddress() + ":" + pacoteRecebido.getPort());

          byte[] dadosResposta = "IP".getBytes(StandardCharsets.UTF_8);
          DatagramPacket pacoteResposta = new DatagramPacket(
              dadosResposta,
              dadosResposta.length,
              pacoteRecebido.getAddress(),
              pacoteRecebido.getPort());

          socket.send(pacoteResposta);
          System.out.println("[DISCOVERY:" + porta + "] Resposta 'IP' enviada para: "
              + pacoteRecebido.getAddress().getHostAddress() + ":" + pacoteRecebido.getPort());
        } else if ("DISCOVER_EDEN".equalsIgnoreCase(mensagem)) {
          System.out.println("[DISCOVERY:" + porta + "] Busca EDEN ('DISCOVER_EDEN') recebida de: "
              + pacoteRecebido.getAddress().getHostAddress() + ":" + pacoteRecebido.getPort());

          byte[] dadosResposta = "EDEN_HERE".getBytes(StandardCharsets.UTF_8);
          DatagramPacket pacoteResposta = new DatagramPacket(
              dadosResposta,
              dadosResposta.length,
              pacoteRecebido.getAddress(),
              pacoteRecebido.getPort());

          socket.send(pacoteResposta);
          System.out.println("[DISCOVERY:" + porta + "] Resposta 'EDEN_HERE' enviada para: "
              + pacoteRecebido.getAddress().getHostAddress() + ":" + pacoteRecebido.getPort());
        } else {
          System.out.println("[DISCOVERY:" + porta + "] Mensagem desconhecida recebida de "
              + pacoteRecebido.getAddress().getHostAddress() + ":" + pacoteRecebido.getPort()
              + " -> '" + mensagem + "'");
        }
      }
    } catch (Exception e) {
      System.err.println("[DISCOVERY:" + porta + "] [ERROR] Falha no farol: " + e.getMessage());
    } finally {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
    }
  }
}
