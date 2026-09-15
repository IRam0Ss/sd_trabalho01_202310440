/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: ServidorUDP
 * Funcao: Servico de recepcao e roteamento de datagramas UDP (mensagens de grupo e diretas).
 */

package model;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import controller.GerenciadorGrupos;
import Protocol.APDU;
import utils.InfoUser;
import utils.Protocolo;

/**
 * Servico responsavel pelo recebimento, deserializacao e roteamento/repasso de datagramas UDP
 * contendo objetos {@link APDU} para mensagens de grupo e mensagens privadas.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class ServidorUDP implements Runnable {

  private int porta;
  private GerenciadorGrupos gerenciador;
  private static final int BUFFER = 4096;

  /**
   * Construtor do ServidorUDP.
   * 
   * @param porta       A porta UDP a ser escutada.
   * @param gerenciador O gerenciador compartilhado de grupos e usuarios.
   */
  public ServidorUDP(int porta, GerenciadorGrupos gerenciador) {
    this.porta = porta;
    this.gerenciador = gerenciador;
  }

  @Override
  public void run() {
    try (DatagramSocket conexaoUDP = new DatagramSocket(this.porta)) {

      System.out.println("[SERVIDOR:UDP] [INFO] Escutando na porta " + porta);
      byte[] dadosEntrada = new byte[BUFFER];

      while (true) {
        DatagramPacket pacoteDadoRecebido = new DatagramPacket(dadosEntrada, dadosEntrada.length);
        conexaoUDP.receive(pacoteDadoRecebido);

        try {
          ByteArrayInputStream bais = new ByteArrayInputStream(pacoteDadoRecebido.getData(), 0, pacoteDadoRecebido.getLength());
          ObjectInputStream ois = new ObjectInputStream(bais);
          Protocol.APDU apdu = (Protocol.APDU) ois.readObject();
          processarAPDU(conexaoUDP, apdu, pacoteDadoRecebido.getAddress());
        } catch (Exception ex) {
          System.err.println("[SERVIDOR:UDP] [ERROR] Falha ao ler APDU via UDP: " + ex.getMessage());
        }
      }

    } catch (Exception e) {
      System.err.println("[SERVIDOR:UDP] [FATAL ERROR] Erro critico no socket UDP: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Converte uma APDU em array de bytes para transmissao via datagrama UDP.
   * 
   * @param apdu Objeto APDU a ser serializado.
   * @return Vetor de bytes serializado.
   */
  private byte[] serializarAPDU(Protocol.APDU apdu) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeUnshared(apdu);
      oos.flush();
      return baos.toByteArray();
    } catch (Exception e) {
      System.err.println("[SERVIDOR:UDP] [ERROR] Falha ao serializar APDU: " + e.getMessage());
      return new byte[0];
    }
  }

  /**
   * Processa a APDU recebida via UDP e encaminha para os destinatarios apropriados.
   * 
   * @param conexaoUDP  Socket UDP para envio de pacotes.
   * @param apdu        Objeto APDU recebido.
   * @param ipRemetente Endereco IP de origem do pacote.
   */
  @SuppressWarnings("unused")
  private void processarAPDU(DatagramSocket conexaoUDP, Protocol.APDU apdu, java.net.InetAddress ipRemetente) {

    String comando = apdu.getOperacao();
    if (comando == null || comando.isEmpty()) {
      return;
    }

    switch (comando) {
      case Protocolo.SEND:
      case Protocolo.SENDVU:
        String nomeGrupo = apdu.getNomeGrupo();
        InfoUser usuarioRemetente = new InfoUser(apdu.getNomeUsuario(), ipRemetente.getHostAddress(), apdu.getPortaClienteUDP());
        String mensagem = apdu.getTextoMensagem();

        System.out.println("[SERVIDOR:UDP] [INFO] Recebido comando " + comando + " do usuario '" + usuarioRemetente.getNome()
            + "' para o grupo '" + nomeGrupo + "'");

        // Repassa a APDU original para preservar idMensagem (UUID) e visualizacao unica
        byte[] dadosEnviados = serializarAPDU(apdu);

        List<InfoUser> destinatarios = gerenciador.getMembrosEnvio(nomeGrupo, usuarioRemetente);

        for (InfoUser membroDestinatario : destinatarios) {
          try {
            byte[] dadosParaEnviar;
            if (gerenciador.isBloqueadoMutuo(usuarioRemetente.getNome(), membroDestinatario.getNome())) {
              Protocol.APDU apduBloqueada = new Protocol.APDU(comando, nomeGrupo, usuarioRemetente.getNome(), "~BLOCKED~", usuarioRemetente.getPorta(), apdu.isVisualizacaoUnica());
              dadosParaEnviar = serializarAPDU(apduBloqueada);
              System.out.println("[SERVIDOR:UDP] [INFO] Mascarando mensagem do grupo '" + nomeGrupo + "' de '" + usuarioRemetente.getNome() + "' para '" + membroDestinatario.getNome() + "' (Bloqueado)");
            } else {
              dadosParaEnviar = dadosEnviados;
            }

            InetAddress ipDestinatario = InetAddress.getByName(membroDestinatario.getIp());
            DatagramPacket pacoteEnvio = new DatagramPacket(dadosParaEnviar, dadosParaEnviar.length, ipDestinatario,
                membroDestinatario.getPorta());

            conexaoUDP.send(pacoteEnvio);
            System.out
                .println("[SERVIDOR:UDP] [INFO] Encaminhando APDU " + comando + " para '" + membroDestinatario.getNome() + "'");

          } catch (Exception e) {
            System.err.println("[SERVIDOR:UDP] [ERROR] Falha ao enviar para '" + membroDestinatario.getNome() + "' - "
                + e.getMessage());
          }
        }

        break;

      case Protocolo.SENDPVT:
        // Prioriza getDestinatario(); caso vazio, utiliza getNomeGrupo() e remove eventual prefixo '@'
        String nomeDestino = apdu.getDestinatario();
        if (nomeDestino == null || nomeDestino.trim().isEmpty()) {
          nomeDestino = apdu.getNomeGrupo();
        }
        if (nomeDestino != null) {
          nomeDestino = nomeDestino.trim();
          if (nomeDestino.startsWith("@")) {
            nomeDestino = nomeDestino.substring(1);
          }
        }

        InfoUser remetentePvt = new InfoUser(apdu.getNomeUsuario(), ipRemetente.getHostAddress(), apdu.getPortaClienteUDP());

        System.out.println("[SERVIDOR:UDP] [INFO] Recebido comando SENDPVT de '" + remetentePvt.getNome() + "' para '"
            + nomeDestino + "'");

        InfoUser destinoInfo = gerenciador.buscarUsuarioPorNome(nomeDestino);

        if (gerenciador.isBloqueadoMutuo(remetentePvt.getNome(), nomeDestino)) {
          System.out.println("[SERVIDOR:UDP] [WARNING] SENDPVT bloqueado entre '" + remetentePvt.getNome() + "' e '" + nomeDestino + "'");
          if (apdu.getIdMensagem() != null) {
            Protocol.APDU confirmErro = new Protocol.APDU("CONFIRM", apdu.getIdMensagem(), -1, remetentePvt.getNome(), "@" + nomeDestino, remetentePvt.getNome());
            byte[] dadosConfirm = serializarAPDU(confirmErro);
            try {
              DatagramPacket pacoteConfirm = new DatagramPacket(dadosConfirm, dadosConfirm.length, ipRemetente, remetentePvt.getPorta());
              conexaoUDP.send(pacoteConfirm);
            } catch (Exception e) {
            }
          }
          break;
        }

        if (destinoInfo != null) {
          try {
            // Repassa a apdu recebida diretamente para a porta UDP do destinatario
            byte[] dadosPvt = serializarAPDU(apdu);
            InetAddress ipDest = InetAddress.getByName(destinoInfo.getIp());
            DatagramPacket pacotePvt = new DatagramPacket(dadosPvt, dadosPvt.length, ipDest, destinoInfo.getPorta());
            conexaoUDP.send(pacotePvt);
            System.out.println("[SERVIDOR:UDP] [INFO] SENDPVT encaminhado com sucesso para '" + nomeDestino + "'");
          } catch (Exception e) {
            System.err.println("[SERVIDOR:UDP] [ERROR] Falha ao encaminhar SENDPVT: " + e.getMessage());
          }
        } else {
          System.out.println("[SERVIDOR:UDP] [WARNING] Destinatario '" + nomeDestino + "' nao encontrado online.");
        }
        break;

      case Protocolo.CONFIRM:
        String dono = apdu.getDonoDaMensagem();
        if (dono != null && !dono.trim().isEmpty()) {
          InfoUser donoInfo = gerenciador.buscarUsuarioPorNome(dono.trim());
          if (donoInfo != null) {
            try {
              byte[] dadosConfirm = serializarAPDU(apdu);
              InetAddress ipDono = InetAddress.getByName(donoInfo.getIp());
              DatagramPacket pacoteConfirm = new DatagramPacket(dadosConfirm, dadosConfirm.length, ipDono, donoInfo.getPorta());
              conexaoUDP.send(pacoteConfirm);
              System.out.println("[SERVIDOR:UDP] [INFO] CONFIRM repassado para '" + dono + "'");
            } catch (Exception e) {
              System.err.println("[SERVIDOR:UDP] [ERROR] Falha ao repassar CONFIRM: " + e.getMessage());
            }
          }
        }
        break;

      default:
        System.out.println("[SERVIDOR:UDP] [WARNING] Comando desconhecido: " + comando);
        break;
    }

  } // fim do processarAPDU

} // fim ServidorUDP
