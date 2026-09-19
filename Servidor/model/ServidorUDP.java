/*****************************************************************
* Autor..............: Iury Ramos Sodre
* Matricula..........: 202310440
* Inicio.............: 15/06/2026
* Ultima alteracao...: 18/09/2026
* Nome...............: ServidorUDP
* Funcao.............: Servico de recepcao e roteamento de datagramas UDP (mensagens de grupo e diretas).
*************************************************************** */

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
  private final java.util.Map<String, String> donosDeMensagens = new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<String, MensagemGrupoRastreamento> rastreamentosGrupo = new java.util.concurrent.ConcurrentHashMap<>();

  /**
   * Rastreia o estado de entrega e leitura de mensagens de grupo para agregacao no servidor.
   */
  private static class MensagemGrupoRastreamento {
    final String idMensagem;
    final String nomeGrupo;
    final String autor;
    final java.util.Set<String> destinatariosEsperados;
    final java.util.Set<String> destinatariosEntregues = java.util.concurrent.ConcurrentHashMap.newKeySet();
    final java.util.Set<String> destinatariosLidos = java.util.concurrent.ConcurrentHashMap.newKeySet();
    volatile boolean entregueNotificado = false;
    volatile boolean lidoNotificado = false;

    MensagemGrupoRastreamento(String idMensagem, String nomeGrupo, String autor, java.util.Set<String> destinatariosEsperados) {
      this.idMensagem = idMensagem;
      this.nomeGrupo = nomeGrupo;
      this.autor = autor;
      this.destinatariosEsperados = destinatariosEsperados;
    }
  }

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

  /**
   * Laco principal de escuta de datagramas UDP.
   * Recebe pacotes, desserializa objetos APDU e delega o processamento e roteamento.
   */
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
          processarAPDU(conexaoUDP, apdu, pacoteDadoRecebido.getAddress(), pacoteDadoRecebido.getPort());
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
   * @param conexaoUDP        Socket UDP para envio de pacotes.
   * @param apdu              Objeto APDU recebido.
   * @param ipRemetente       Endereco IP de origem do pacote.
   * @param portaOrigemPacote Porta efemera ou de origem do pacote recebido.
   */
  private void processarAPDU(DatagramSocket conexaoUDP, Protocol.APDU apdu, java.net.InetAddress ipRemetente, int portaOrigemPacote) {

    String comando = apdu.getOperacao();
    if (comando == null || comando.isEmpty()) {
      return;
    }

    switch (comando) {
      case Protocolo.SEND:
      case Protocolo.SENDVU:
        String nomeGrupo = apdu.getNomeGrupo();
        int portaRemetente = apdu.getPortaClienteUDP() > 0 ? apdu.getPortaClienteUDP() : portaOrigemPacote;
        InfoUser usuarioRemetente = new InfoUser(apdu.getNomeUsuario(), ipRemetente.getHostAddress(), portaRemetente);
        String mensagem = apdu.getTextoMensagem();

        System.out.println("[SERVIDOR:UDP] [INFO] Recebido comando " + comando + " do usuario '" + usuarioRemetente.getNome()
            + "' para o grupo '" + nomeGrupo + "'");

        List<InfoUser> destinatarios = gerenciador.getMembrosEnvio(nomeGrupo, usuarioRemetente);

        // Registra o autor da mensagem no cache e prepara rastreamento de grupo para agregacao de CONFIRM
        if (apdu.getIdMensagem() != null && !apdu.getIdMensagem().trim().isEmpty()) {
          donosDeMensagens.put(apdu.getIdMensagem().trim(), usuarioRemetente.getNome());

          java.util.Set<String> esperados = new java.util.HashSet<>();
          for (InfoUser dest : destinatarios) {
            esperados.add(dest.getNome().trim().toLowerCase());
          }
          MensagemGrupoRastreamento rastreamento = new MensagemGrupoRastreamento(
              apdu.getIdMensagem().trim(),
              nomeGrupo,
              usuarioRemetente.getNome(),
              esperados
          );
          rastreamentosGrupo.put(apdu.getIdMensagem().trim(), rastreamento);

          // Auto-ACK do Servidor (Status 1: Mensagem recebida pelo servidor)
          try {
            Protocol.APDU ackServidor = new Protocol.APDU(Protocolo.CONFIRM, apdu.getIdMensagem(), 1, "SERVIDOR", nomeGrupo, usuarioRemetente.getNome());
            byte[] dadosAck = serializarAPDU(ackServidor);
            DatagramPacket pacoteAck = new DatagramPacket(dadosAck, dadosAck.length, ipRemetente, portaRemetente);
            conexaoUDP.send(pacoteAck);
            System.out.println("[SERVIDOR:UDP] [INFO] Auto-ACK (Status 1) enviado para '" + usuarioRemetente.getNome() + "'");
          } catch (Exception e) {
            System.err.println("[SERVIDOR:UDP] [ERROR] Falha ao enviar Auto-ACK Status 1: " + e.getMessage());
          }
        }

        // Repassa a APDU original para preservar idMensagem (UUID) e visualizacao unica
        byte[] dadosEnviados = serializarAPDU(apdu);

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

        int portaPvt = apdu.getPortaClienteUDP() > 0 ? apdu.getPortaClienteUDP() : portaOrigemPacote;
        InfoUser remetentePvt = new InfoUser(apdu.getNomeUsuario(), ipRemetente.getHostAddress(), portaPvt);

        System.out.println("[SERVIDOR:UDP] [INFO] Recebido comando SENDPVT de '" + remetentePvt.getNome() + "' para '"
            + nomeDestino + "'");

        // Registra o autor da mensagem no cache para roteamento confiavel de CONFIRM
        if (apdu.getIdMensagem() != null && !apdu.getIdMensagem().trim().isEmpty()) {
          donosDeMensagens.put(apdu.getIdMensagem().trim(), remetentePvt.getNome());

          // Auto-ACK do Servidor (Status 1: Mensagem recebida pelo servidor)
          try {
            Protocol.APDU ackServidor = new Protocol.APDU(Protocolo.CONFIRM, apdu.getIdMensagem(), 1, "SERVIDOR", "@" + nomeDestino, remetentePvt.getNome());
            byte[] dadosAck = serializarAPDU(ackServidor);
            DatagramPacket pacoteAck = new DatagramPacket(dadosAck, dadosAck.length, ipRemetente, portaPvt);
            conexaoUDP.send(pacoteAck);
            System.out.println("[SERVIDOR:UDP] [INFO] Auto-ACK (Status 1) enviado para '" + remetentePvt.getNome() + "'");
          } catch (Exception e) {
            System.err.println("[SERVIDOR:UDP] [ERROR] Falha ao enviar Auto-ACK Status 1: " + e.getMessage());
          }
        }

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
        String idMsg = apdu.getIdMensagem();
        String confirmador = apdu.getNomeUsuario();
        int status = apdu.getStatusRecebido();

        MensagemGrupoRastreamento rastreio = (idMsg != null) ? rastreamentosGrupo.get(idMsg.trim()) : null;

        if (rastreio != null) {
          // --- MENSAGEM DE GRUPO: AGREGACAO NO SERVIDOR ---
          String normConfirm = (confirmador != null) ? confirmador.trim().toLowerCase() : "";
          String autorMsg = rastreio.autor;

          if (status == 2) {
            if (!normConfirm.isEmpty()) {
              rastreio.destinatariosEntregues.add(normConfirm);
            }
            // Repassa Status 2 ao autor apenas na primeira entrega para exibir tick duplo (Entregue)
            if (!rastreio.entregueNotificado) {
              rastreio.entregueNotificado = true;
              InfoUser autorInfo = gerenciador.buscarUsuarioPorNome(autorMsg);
              if (autorInfo != null) {
                try {
                  byte[] dadosConfirm = serializarAPDU(apdu);
                  InetAddress ipAutor = InetAddress.getByName(autorInfo.getIp());
                  DatagramPacket pacoteConfirm = new DatagramPacket(dadosConfirm, dadosConfirm.length, ipAutor, autorInfo.getPorta());
                  conexaoUDP.send(pacoteConfirm);
                  System.out.println("[SERVIDOR:UDP] [INFO] CONFIRM (Status 2: Entregue) de '" + confirmador 
                      + "' repassado para o autor '" + autorMsg + "' (Grupo: " + rastreio.nomeGrupo + ")");
                } catch (Exception e) {
                  System.err.println("[SERVIDOR:UDP] [ERROR] Falha ao repassar CONFIRM Status 2 para '" + autorMsg + "': " + e.getMessage());
                }
              }
            } else {
              System.out.println("[SERVIDOR:UDP] [INFO] CONFIRM (Status 2) de '" + confirmador + "' registrado no grupo '" 
                  + rastreio.nomeGrupo + "' (" + rastreio.destinatariosEntregues.size() + "/" + rastreio.destinatariosEsperados.size() + ")");
            }
          } else if (status == 3) {
            if (!normConfirm.isEmpty()) {
              rastreio.destinatariosEntregues.add(normConfirm);
              rastreio.destinatariosLidos.add(normConfirm);
            }

            // Calcula membros ativos atuais do grupo (desconsiderando quem ja saiu)
            List<InfoUser> membrosAtuais = gerenciador.getTodosMembros(rastreio.nomeGrupo);
            java.util.Set<String> alvoEsperado = new java.util.HashSet<>();
            for (InfoUser m : membrosAtuais) {
              if (!m.getNome().trim().equalsIgnoreCase(autorMsg)) {
                alvoEsperado.add(m.getNome().trim().toLowerCase());
              }
            }
            if (alvoEsperado.isEmpty()) {
              alvoEsperado.addAll(rastreio.destinatariosEsperados);
            }

            boolean todosLeram = !alvoEsperado.isEmpty() && rastreio.destinatariosLidos.containsAll(alvoEsperado);

            if (todosLeram) {
              if (!rastreio.lidoNotificado) {
                rastreio.lidoNotificado = true;
                InfoUser autorInfo = gerenciador.buscarUsuarioPorNome(autorMsg);
                if (autorInfo != null) {
                  try {
                    // Notifica o autor com Status 3 indicando que TODOS leram
                    Protocol.APDU confirmTodos = new Protocol.APDU(
                        Protocolo.CONFIRM,
                        apdu.getIdMensagem(),
                        3,
                        "TODOS",
                        rastreio.nomeGrupo,
                        autorMsg
                    );
                    byte[] dadosConfirm = serializarAPDU(confirmTodos);
                    InetAddress ipAutor = InetAddress.getByName(autorInfo.getIp());
                    DatagramPacket pacoteConfirm = new DatagramPacket(dadosConfirm, dadosConfirm.length, ipAutor, autorInfo.getPorta());
                    conexaoUDP.send(pacoteConfirm);
                    System.out.println("[SERVIDOR:UDP] [INFO] TODOS OS MEMBROS (" + rastreio.destinatariosLidos.size() + "/" 
                        + alvoEsperado.size() + ") leram a mensagem no grupo '" + rastreio.nomeGrupo 
                        + "'! Repassado CONFIRM (Status 3) para o autor '" + autorMsg + "' (" + autorInfo.getIp() + ":" + autorInfo.getPorta() + ")");
                  } catch (Exception e) {
                    System.err.println("[SERVIDOR:UDP] [ERROR] Falha ao repassar CONFIRM Status 3 final para '" + autorMsg + "': " + e.getMessage());
                  }
                }
              }
            } else {
              System.out.println("[SERVIDOR:UDP] [INFO] CONFIRM (Status 3) de '" + confirmador + "' registrado no grupo '" 
                  + rastreio.nomeGrupo + "' (" + rastreio.destinatariosLidos.size() + "/" + alvoEsperado.size() 
                  + " leram). Aguardando leitura dos demais membros.");
            }
          }
          break;
        }

        // --- MENSAGEM PRIVADA OU SEM RASTREAMENTO DE GRUPO: REPASSE DIRETO ---
        String dono = apdu.getDonoDaMensagem();
        if (dono == null || dono.trim().isEmpty()) {
          dono = apdu.getDestinatario();
        }
        if (dono == null || dono.trim().isEmpty()) {
          if (apdu.getNomeGrupo() != null && apdu.getNomeGrupo().startsWith("@")) {
            dono = apdu.getNomeGrupo().substring(1);
          }
        }
        if ((dono == null || dono.trim().isEmpty()) && idMsg != null) {
          dono = donosDeMensagens.get(idMsg.trim());
        }

        if (dono != null) {
          dono = dono.trim();
          if (dono.startsWith("@")) {
            dono = dono.substring(1).trim();
          }
        }

        if (dono == null || dono.isEmpty()) {
          System.out.println("[SERVIDOR:UDP] [WARNING] CONFIRM descartado: Dono da mensagem desconhecido (ID: " + idMsg + ")");
          break;
        }

        InfoUser donoInfo = gerenciador.buscarUsuarioPorNome(dono);
        if (donoInfo != null) {
          try {
            byte[] dadosConfirm = serializarAPDU(apdu);
            InetAddress ipDono = InetAddress.getByName(donoInfo.getIp());
            DatagramPacket pacoteConfirm = new DatagramPacket(dadosConfirm, dadosConfirm.length, ipDono, donoInfo.getPorta());
            conexaoUDP.send(pacoteConfirm);
            System.out.println("[SERVIDOR:UDP] [INFO] CONFIRM (Status " + apdu.getStatusRecebido() + ") de '" 
                + apdu.getNomeUsuario() + "' repassado com sucesso para o autor '" + dono + "' (" + donoInfo.getIp() + ":" + donoInfo.getPorta() + ")");
          } catch (Exception e) {
            System.err.println("[SERVIDOR:UDP] [ERROR] Falha ao repassar CONFIRM para '" + dono + "': " + e.getMessage());
          }
        } else {
          System.out.println("[SERVIDOR:UDP] [WARNING] CONFIRM para '" + dono + "' nao repassado: Usuario nao encontrado online (ID: " + idMsg + ")");
        }
        break;

      default:
        System.out.println("[SERVIDOR:UDP] [WARNING] Comando desconhecido: " + comando);
        break;
    }

  } // fim do processarAPDU

} // fim ServidorUDP
