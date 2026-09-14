/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: Servidor (Core Orchestrator)
 * Funcao: Orquestra e inicializa os servicos TCP, UDP e Discovery do Servidor E.D.E.N.
 */

package model;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Set;

import Protocol.APDU;
import controller.GerenciadorGrupos;
import utils.InfoUser;
import utils.Protocolo;

/**
 * Classe responsavel por inicializar e supervisionar os subsistemas do servidor:
 * escuta de conexoes confiaveis (TCP), canal de dados/notificacoes (UDP) e beacon de descoberta local (Discovery).
 * Opera em portas padronizadas universais (TCP 6789, UDP 7777, Discovery 8888)
 * e portas legadas (TCP/UDP 5000, Discovery 5001) simultaneamente.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class Servidor {
  
  /**
   * Serializa uma APDU em vetor de bytes para transmissao via datagrama UDP.
   * 
   * @param apdu Objeto APDU a ser serializado.
   * @return Vetor de bytes com o objeto serializado.
   */
  private byte[] serializarAPDU(APDU apdu) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeUnshared(apdu);
      oos.flush();
      return baos.toByteArray();
    } catch (Exception e) {
      System.err.println("[SERVIDOR] [ERROR] Falha ao serializar APDU: " + e.getMessage());
      return new byte[0];
    }
  }

  /**
   * Inicializa as threads dos servicos TCP, UDP e Discovery do servidor.
   * Registra shutdown hook para notificacao graciosa dos clientes conectados em caso de encerramento.
   */
  public void iniciar() {
    GerenciadorGrupos gerenciador = new GerenciadorGrupos();

    // Servicos TCP: Porta padrao universal (6789) e porta de compatibilidade (5000)
    Thread tcpPadrao = new Thread(new ServidorTCP(Protocolo.PORTA_SERVIDOR_TCP, gerenciador));
    Thread tcpLegado = new Thread(new ServidorTCP(Protocolo.PORTA_SERVIDOR_LEGACY, gerenciador));

    // Servicos UDP: Porta padrao universal (7777) e porta de compatibilidade (5000)
    Thread udpPadrao = new Thread(new ServidorUDP(Protocolo.PORTA_SERVIDOR_UDP, gerenciador));
    Thread udpLegado = new Thread(new ServidorUDP(Protocolo.PORTA_SERVIDOR_LEGACY, gerenciador));

    // Servicos Discovery: Porta padrao universal (8888) e porta EDEN (5001)
    Thread discoveryPadrao = new Thread(new ServidorDiscovery(Protocolo.PORTA_DISCOVERY));
    Thread discoveryEden = new Thread(new ServidorDiscovery(Protocolo.PORTA_DISCOVERY_EDEN));

    tcpPadrao.start();
    tcpLegado.start();
    udpPadrao.start();
    udpLegado.start();
    discoveryPadrao.start();
    discoveryEden.start();

    System.out.println("=== Servidor IM iniciado ===");
    System.out.println("TCP escutando em " + Protocolo.PORTA_SERVIDOR_TCP + " (padrao) e " + Protocolo.PORTA_SERVIDOR_LEGACY + " (legado)");
    System.out.println("UDP escutando em " + Protocolo.PORTA_SERVIDOR_UDP + " (padrao) e " + Protocolo.PORTA_SERVIDOR_LEGACY + " (legado)");
    System.out.println("Discovery escutando em " + Protocolo.PORTA_DISCOVERY + " (padrao) e " + Protocolo.PORTA_DISCOVERY_EDEN + " (EDEN)");

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("\n[SERVIDOR] Recebido sinal de encerramento. Notificando clientes...");
      Set<InfoUser> usuarios = gerenciador.getTodosUsuariosAtivos();
      try (DatagramSocket socketUDP = new DatagramSocket()) {
        for (InfoUser u : usuarios) {
          APDU apdu = new APDU(Protocolo.SHUTDOWN, "GLOBAL", u.getNome(), "Desligando", u.getPorta());
          byte[] dados = serializarAPDU(apdu);
          
          DatagramPacket pacote = new DatagramPacket(dados, dados.length,
              InetAddress.getByName(u.getIp()), u.getPorta());
          socketUDP.send(pacote);
        }
      } catch (Exception e) {
        System.err.println("[SERVIDOR] Erro ao notificar: " + e.getMessage());
      }
    }));
  }

}
