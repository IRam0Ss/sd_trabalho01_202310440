/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: AtendimentoCliente
 * Funcao: Thread dedicada para atendimento e processamento concorrente de requisicoes TCP de cada cliente.
 */

package controller;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.EOFException;
import java.net.Socket;
import java.util.List;
import Protocol.APDU;
import utils.InfoUser;
import utils.Protocolo;

/**
 * Classe responsavel por atender clientes individuais via conexao TCP concorrente.
 * Cada conexao de cliente executa em uma thread independente, processando objetos {@link APDU}.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class AtendimentoCliente implements Runnable {

  private Socket conexao;
  private GerenciadorGrupos gerenciador;
  private InfoUser usuarioAssociado = null;
  private ObjectOutputStream saidaObjetos;

  /**
   * Construtor do AtendimentoCliente.
   * 
   * @param conexao     Socket TCP da conexao atual
   * @param gerenciador Referencia ao gerenciador de grupos e usuarios
   */
  public AtendimentoCliente(Socket conexao, GerenciadorGrupos gerenciador) {
    this.conexao = conexao;
    this.gerenciador = gerenciador;
    try {
      this.saidaObjetos = new ObjectOutputStream(conexao.getOutputStream());
      this.saidaObjetos.flush();
    } catch (Exception e) {
      System.err.println("[ATENDIMENTO] [ERROR] Falha ao criar escritor TCP: " + e.getMessage());
    }
  }

  @Override
  public void run() {
    try {
      ObjectInputStream tradutorObjetos = new ObjectInputStream(this.conexao.getInputStream());
      Protocol.APDU apduRecebida;

      // Le e trata as APDUs recebidas
      while (!conexao.isClosed()) {
        try {
          apduRecebida = (Protocol.APDU) tradutorObjetos.readObject();
          if (apduRecebida != null) {
            processarAPDU(apduRecebida);
          }
        } catch (EOFException e) {
          // Encerramento normal do stream pelo cliente
          break;
        }
      }
    } catch (EOFException e) {
      // Stream finalizado
    } catch (Exception e) {
      System.err.println("[ATENDIMENTO] [INFO] Conexao encerrada com o cliente: " + e.getMessage());
    } finally {
      // Nao removemos o usuario da lista global ao fechar o socket TCP, pois clientes transientes
      // abrem e fecham a conexao TCP a cada comando (ficando ativos no UDP).
      try {
        if (this.saidaObjetos != null) {
          this.saidaObjetos.close();
        }
        if (this.conexao != null && !this.conexao.isClosed()) {
          this.conexao.close();
        }
      } catch (Exception ignored) {
      }
    }
  }

  /**
   * Envia uma resposta textual serializada padronizada para o cliente via ObjectOutputStream.
   * Compativeis com qualquer cliente que faca `(String) in.readObject()`.
   * 
   * @param operacao  Codigo de status (Protocolo.OK ou Protocolo.ERRO).
   * @param mensagem  Texto descritivo ou conteudo da listagem.
   */
  private void enviarResposta(String operacao, String mensagem) {
    try {
      String respostaStr;
      if (mensagem == null || mensagem.isEmpty()) {
        respostaStr = operacao + ": ";
      } else {
        respostaStr = operacao + ": " + mensagem;
      }
      this.saidaObjetos.writeUnshared(respostaStr);
      this.saidaObjetos.flush();
    } catch (Exception e) {
      System.err.println("[ATENDIMENTO] [ERROR] Erro ao enviar resposta: " + e.getMessage());
    }
  }

  /**
   * Processa a APDU recebida pelo servidor e designa o processamento para o
   * gerenciador.
   * 
   * @param apdu APDU recebida
   */
  private void processarAPDU(Protocol.APDU apdu) {
    String comando = apdu.getOperacao();

    System.out.println("[ATENDIMENTO] [INFO] Processando comando " + comando);

    if (comando == null || comando.isEmpty()) {
      return;
    }

    switch (comando) {
      case Protocolo.JOIN:
        String grupoJoin = apdu.getNomeGrupo();
        InfoUser usuarioJoin = new InfoUser(apdu.getNomeUsuario(), conexao.getInetAddress().getHostAddress(), apdu.getPortaClienteUDP());

        if (this.usuarioAssociado == null) {
          this.usuarioAssociado = usuarioJoin;
        }

        // Garante presenca global no servidor mesmo se o cliente nao mandou REGISTER previo
        if (this.gerenciador.buscarUsuarioPorNome(usuarioJoin.getNome()) == null) {
          this.gerenciador.registrarUsuario(usuarioJoin);
          System.out.println("\n[SERVIDOR] >>> CLIENTE REGISTRADO (via JOIN): Nome='" + usuarioJoin.getNome()
              + "' | IP=" + usuarioJoin.getIp() + " | Porta UDP=" + usuarioJoin.getPorta() + "\n");
          this.gerenciador.notificarAtualizacaoUsuarios();
        }

        boolean checkJoin = this.gerenciador.join(grupoJoin, usuarioJoin);
        if (checkJoin) {
          enviarResposta(Protocolo.OK, "Entrou no grupo " + grupoJoin);
          System.out.println(
              "[ATENDIMENTO] [INFO] Processamento de JOIN de '" + usuarioJoin.getNome() + "' concluido.");
        } else {
          enviarResposta(Protocolo.ERRO, "Voce ja esta no grupo " + grupoJoin);
          System.out
              .println("[ATENDIMENTO] [WARNING] Processamento de JOIN de '" + usuarioJoin.getNome()
                  + "' falhou.");
        }
        break;

      case Protocolo.REGISTER:
        InfoUser usuarioRegister = new InfoUser(apdu.getNomeUsuario(), conexao.getInetAddress().getHostAddress(), apdu.getPortaClienteUDP());
        if (this.usuarioAssociado == null) {
          this.usuarioAssociado = usuarioRegister;
        }
        boolean registrado = this.gerenciador.registrarUsuario(usuarioRegister);
        if (registrado) {
          enviarResposta(Protocolo.OK, "registrado com sucesso");
          System.out.println("\n[SERVIDOR] >>> CLIENTE REGISTRADO: Nome='" + usuarioRegister.getNome()
              + "' | IP=" + usuarioRegister.getIp() + " | Porta UDP=" + usuarioRegister.getPorta() + "\n");
          this.gerenciador.notificarAtualizacaoUsuarios();
        } else {
          enviarResposta(Protocolo.ERRO, "Este nome de usuario ja esta em uso. Escolha outro.");
          System.out.println("[ATENDIMENTO] [WARNING] Registro falhou para '" + usuarioRegister.getNome()
              + "' - Nome duplicado.");
        }
        break;

      case Protocolo.LOGOUT:
        InfoUser usuarioLogout = new InfoUser(apdu.getNomeUsuario(), conexao.getInetAddress().getHostAddress(), apdu.getPortaClienteUDP());
        this.gerenciador.removerUsuario(usuarioLogout);
        this.gerenciador.notificarAtualizacaoUsuarios();
        enviarResposta(Protocolo.OK, "Desconectado com sucesso");
        System.out.println("[ATENDIMENTO] [INFO] Processamento de LOGOUT de '" + usuarioLogout.getNome() + "' concluido.");
        break;

      case Protocolo.LEAVE:
        String grupoLeave = apdu.getNomeGrupo();
        InfoUser usuarioLeave = new InfoUser(apdu.getNomeUsuario(), conexao.getInetAddress().getHostAddress(), apdu.getPortaClienteUDP());
        if (grupoLeave != null && !grupoLeave.equalsIgnoreCase("GLOBAL")) {
          boolean checkLeave = this.gerenciador.leave(grupoLeave, usuarioLeave);
          if (checkLeave) {
            enviarResposta(Protocolo.OK, "Saiu do grupo " + grupoLeave);
            System.out.println("[ATENDIMENTO] [INFO] Processamento de LEAVE de '" + usuarioLeave.getNome()
                + "' concluido.");
          } else {
            enviarResposta(Protocolo.ERRO, "Voce nao esta no grupo " + grupoLeave);
            System.out
                .println("[ATENDIMENTO] [WARNING] Processamento de LEAVE de '" + usuarioLeave.getNome()
                    + "' falhou.");
          }
        } else {
          this.gerenciador.removerUsuario(usuarioLeave);
          this.gerenciador.notificarAtualizacaoUsuarios();
          enviarResposta(Protocolo.OK, "Desconectado do servidor");
        }
        break;

      case Protocolo.LIST:
        List<String> grupos = this.gerenciador.listarGrupos();
        if (grupos.isEmpty()) {
          enviarResposta(Protocolo.OK, "");
        } else {
          enviarResposta(Protocolo.OK, String.join(",", grupos));
        }
        System.out.println("[ATENDIMENTO] [INFO] Processamento de LIST concluido.");
        break;

      case Protocolo.USERS:
        java.util.Set<InfoUser> usuarios = this.gerenciador.getTodosUsuariosAtivos();
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (InfoUser u : usuarios) {
          if (!first)
            sb.append(",");
          sb.append(u.getNome());
          first = false;
        }
        enviarResposta(Protocolo.OK, sb.toString());
        System.out.println(
            "[ATENDIMENTO] [INFO] Processamento de USERS concluido. Total: " + usuarios.size());
        break;

      case Protocolo.MEMBERS:
        String grupoList = apdu.getNomeGrupo();
        List<InfoUser> membrosGrupo = this.gerenciador.getTodosMembros(grupoList);
        StringBuilder sbMembers = new StringBuilder();
        boolean firstMember = true;
        for (InfoUser u : membrosGrupo) {
          if (!firstMember)
            sbMembers.append(",");
          sbMembers.append(u.getNome());
          firstMember = false;
        }
        enviarResposta(Protocolo.OK, sbMembers.toString());
        System.out.println(
            "[ATENDIMENTO] [INFO] Processamento de MEMBERS concluido para grupo: " + grupoList);
        break;

      case Protocolo.BLOCK:
        String usuarioABloquear = apdu.getDestinatario();
        String bloqueador = apdu.getNomeUsuario();
        this.gerenciador.bloquear(bloqueador, usuarioABloquear);
        System.out.println("[ATENDIMENTO] [INFO] Usuario '" + bloqueador + "' bloqueou '" + usuarioABloquear + "'");
        enviarResposta(Protocolo.OK, "Usuario " + usuarioABloquear + " bloqueado com sucesso");
        break;

      case Protocolo.UNBLOCK:
        String usuarioADesbloquear = apdu.getDestinatario();
        String desbloqueador = apdu.getNomeUsuario();
        this.gerenciador.desbloquear(desbloqueador, usuarioADesbloquear);
        System.out.println("[ATENDIMENTO] [INFO] Usuario '" + desbloqueador + "' desbloqueou '" + usuarioADesbloquear + "'");
        enviarResposta(Protocolo.OK, "Usuario " + usuarioADesbloquear + " desbloqueado com sucesso");
        break;

      default:
        System.err.println("[ATENDIMENTO] [ERROR] Comando desconhecido: " + comando);
        enviarResposta(Protocolo.ERRO, "Comando desconhecido: " + comando);
        break;
    }
  }
}
