/*****************************************************************
* Autor..............: Iury Ramos Sodre
* Matricula..........: 202310440
* Inicio.............: 20/06/2026
* Ultima alteracao...: 14/09/2026
* Nome...............: APDU (Application Protocol Data Unit)
* Funcao.............: Modela e gerencia as unidades de dados do protocolo
*                      utilizadas na comunicacao cliente-servidor (TCP) e
*                      mensageria em tempo real (UDP), com suporte a serializacao.
*
* OPERACOES TCP (controle e gerenciamento de sessao):
*   JOIN    - Entrar ou criar um grupo de chat
*   LEAVE   - Sair de um grupo
*   MEMBERS - Listar membros de um grupo especifico
*   LIST    - Listar todos os grupos ativos
*   BLOCK   - Bloquear um usuario
*   UNBLOCK - Desbloquear um usuario
*   USERS   - Listar todos os usuarios online conectados
*   REGISTER- Registrar cliente no servidor
*   OK      - Resposta de sucesso do servidor
*   ERRO    - Resposta de falha/erro do servidor
*
* OPERACOES UDP (comunicacao em tempo real):
*   SEND        - Mensagem enviada para um grupo de chat
*   SENDVU      - Mensagem de visualizacao unica em grupo (descartada apos leitura)
*   SENDPVT     - Mensagem privada direta entre dois usuarios
*   CONFIRM     - Confirmacao de recebimento e leitura de mensagens (ticks)
*   UPDATE_USERS- Notificacao broadcast de alteracao na lista de usuarios online
*   SHUTDOWN    - Notificacao de encerramento do servidor
*************************************************************** */

package Protocol;

import java.io.Serializable;
import java.util.UUID;

/**
 * Classe responsavel por encapsular as mensagens e comandos trafegados
 * na camada de aplicacao do sistema E.D.E.N. Implementa {@link Serializable}
 * para permitir o trafego direto de objetos serializados via TCP e UDP.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 14/09/2026
 */
public class APDU implements Serializable {

  /** Identificador de versao da serializacao da classe */
  private static final long serialVersionUID = 2L;

  // -------------------------------------------------------
  // Campos principais da APDU
  // -------------------------------------------------------
  /** Tipo de comando ou operacao a ser executada */
  private String operacao;

  /** Nome do grupo alvo (em SENDPVT armazena "@" + destinatario) */
  private String nomeGrupo;

  /** Identificador do usuario que originou a APDU */
  private String nomeUsuario;

  /** Corpo ou conteudo textual da mensagem */
  private String textoMensagem;

  /** Usuario autor da mensagem original (utilizado em operacoes CONFIRM) */
  private String donoDaMensagem;

  /** Usuario de destino em mensagens privadas ou operacoes de bloqueio */
  private String destinatario;

  /** Porta UDP do cliente para recebimento de mensagens e notificacoes */
  private int portaClienteUDP;

  // -------------------------------------------------------
  // Campos para rastreamento e confirmacao de entrega (CONFIRM)
  // -------------------------------------------------------
  /** Identificador unico universal (UUID) da mensagem */
  private String idMensagem;

  /**
   * Estado de entrega/leitura da mensagem:
   * 0 = Criada | 1 = Enviada/Recebida pelo servidor | 2 = Entregue ao destinatario | 3 = Lida
   */
  private int statusRecebido;

  /** Flag indicando se a mensagem e de visualizacao unica (desaparece apos ser lida) */
  private boolean isVisualizacaoUnica;

  /*********************************************************************
  * Metodo: APDU (construtor geral - mensagens de grupo e comandos de controle)
  * Funcao: Inicializa uma APDU basica para JOIN, LEAVE, MEMBERS, LIST e SEND.
  * @param operacao O tipo de operacao (ex: "JOIN", "LEAVE", "SEND", "LIST").
  * @param nomeGrupo O grupo alvo da operacao.
  * @param nomeUsuario O usuario remetente/solicitante da requisicao.
  * @param textoMensagem Conteudo textual (pode ser null para operacoes de controle).
  * @param portaClienteUDP A porta UDP do cliente para recebimento de dados.
  ******************************************************************* */
  public APDU(String operacao, String nomeGrupo, String nomeUsuario, String textoMensagem, int portaClienteUDP) {
    this.operacao = operacao;
    this.nomeGrupo = nomeGrupo;
    this.nomeUsuario = nomeUsuario;
    this.textoMensagem = textoMensagem;
    this.portaClienteUDP = portaClienteUDP;
    this.isVisualizacaoUnica = false;

    // Gera identificador unico para mensagens rastreaveis (SEND e SENDVU)
    if (this.operacao != null && (this.operacao.equals("SEND") || this.operacao.equals("SENDVU"))) {
      this.idMensagem = UUID.randomUUID().toString();
      this.statusRecebido = 0;
    }
  }

  /*********************************************************************
  * Metodo: APDU (construtor SENDVU - visualizacao unica de grupo)
  * Funcao: Inicializa uma APDU com flag de visualizacao unica habilitada.
  * @param operacao Tipo da operacao (geralmente "SENDVU").
  * @param nomeGrupo O grupo alvo.
  * @param nomeUsuario O usuario remetente.
  * @param textoMensagem O conteudo da mensagem.
  * @param portaClienteUDP A porta UDP do cliente.
  * @param isVisualizacaoUnica Flag indicando se a mensagem deve sumir apos lida.
  ******************************************************************* */
  public APDU(String operacao, String nomeGrupo, String nomeUsuario, String textoMensagem, int portaClienteUDP, boolean isVisualizacaoUnica) {
    this(operacao, nomeGrupo, nomeUsuario, textoMensagem, portaClienteUDP);
    this.isVisualizacaoUnica = isVisualizacaoUnica;
  }

  /*********************************************************************
  * Metodo: APDU (construtor SENDPVT / BLOCK / UNBLOCK)
  * Funcao: Inicializa uma APDU voltada para comunicacao ponto-a-ponto ou controle de usuarios.
  * @param operacao Tipo de operacao ("SENDPVT", "BLOCK", "UNBLOCK").
  * @param nomeGrupo Nome do grupo ou "@" + destinatario no caso de privado.
  * @param nomeUsuario Usuario de origem/remetente.
  * @param textoMensagem Conteudo da mensagem (null para operacoes de bloqueio).
  * @param portaClienteUDP Porta UDP do remetente.
  * @param destinatario Nome do usuario alvo da acao.
  ******************************************************************* */
  public APDU(String operacao, String nomeGrupo, String nomeUsuario, String textoMensagem, int portaClienteUDP, String destinatario) {
    this.operacao = operacao;
    this.nomeGrupo = nomeGrupo;
    this.nomeUsuario = nomeUsuario;
    this.textoMensagem = textoMensagem;
    this.portaClienteUDP = portaClienteUDP;
    this.destinatario = destinatario;
    this.isVisualizacaoUnica = false;

    // Mensagens privadas possuem ID para controle de entrega
    if (this.operacao != null && this.operacao.equals("SENDPVT")) {
      this.idMensagem = UUID.randomUUID().toString();
      this.statusRecebido = 0;
    }
  }

  /*********************************************************************
  * Metodo: APDU (construtor CONFIRM simples)
  * Funcao: Inicializa uma APDU de confirmacao de status de mensagem.
  * @param operacao "CONFIRM".
  * @param idMensagem Identificador unico da mensagem sendo confirmada.
  * @param statusRecebido Novo estado de entrega/leitura (1=Enviada, 2=Entregue, 3=Lida).
  * @param nomeUsuario Usuario confirmando o status.
  ******************************************************************* */
  public APDU(String operacao, String idMensagem, int statusRecebido, String nomeUsuario) {
    this.operacao = operacao != null ? operacao.toUpperCase() : null;
    this.idMensagem = idMensagem;
    this.statusRecebido = statusRecebido;
    this.nomeUsuario = nomeUsuario;
  }

  /*********************************************************************
  * Metodo: APDU (construtor CONFIRM completo)
  * Funcao: Inicializa uma APDU completa de confirmacao com dados do grupo e remetente original.
  * @param operacao "CONFIRM".
  * @param idMensagem Identificador unico da mensagem confirmada.
  * @param statusRecebido Novo estado de entrega/leitura.
  * @param nomeUsuario Usuario confirmando o status.
  * @param nomeGrupo Grupo onde a mensagem trafegou (ou "@remetente" para privado).
  * @param donoDaMensagem Usuario autor da mensagem original.
  ******************************************************************* */
  public APDU(String operacao, String idMensagem, int statusRecebido, String nomeUsuario, String nomeGrupo, String donoDaMensagem) {
    this.operacao = operacao != null ? operacao.toUpperCase() : null;
    this.idMensagem = idMensagem;
    this.statusRecebido = statusRecebido;
    this.nomeUsuario = nomeUsuario;
    this.nomeGrupo = nomeGrupo;
    this.donoDaMensagem = donoDaMensagem;
  }

  // -------------------------------------------------------
  // Metodos de Acesso (Getters e Setters)
  // -------------------------------------------------------

  /**
   * Obtem a operacao/comando da APDU.
   * @return String contendo a operacao.
   */
  public String getOperacao() {
    return operacao;
  }

  /**
   * Obtem o nome do grupo ou identificador de conversa privada.
   * @return Nome do grupo.
   */
  public String getNomeGrupo() {
    return nomeGrupo;
  }

  /**
   * Obtem o nome do usuario remetente.
   * @return Nome do usuario.
   */
  public String getNomeUsuario() {
    return nomeUsuario;
  }

  /**
   * Obtem o texto ou conteudo da mensagem.
   * @return Texto da mensagem.
   */
  public String getTextoMensagem() {
    return textoMensagem;
  }

  /**
   * Obtem a porta UDP onde o cliente escuta pacotes.
   * @return Numero da porta UDP.
   */
  public int getPortaClienteUDP() {
    return portaClienteUDP;
  }

  /**
   * Obtem o identificador unico (UUID) da mensagem.
   * @return UUID da mensagem como String.
   */
  public String getIdMensagem() {
    return idMensagem;
  }

  /**
   * Obtem o status atual de recebimento/leitura da mensagem.
   * @return Codigo de status (0=Criada, 1=Enviada, 2=Entregue, 3=Lida).
   */
  public int getStatusRecebido() {
    return statusRecebido;
  }

  /**
   * Obtem o autor original da mensagem associada a uma confirmacao.
   * @return Nome do autor original.
   */
  public String getDonoDaMensagem() {
    return donoDaMensagem;
  }

  /**
   * Obtem o usuario destinatario da mensagem ou acao.
   * @return Nome do usuario de destino.
   */
  public String getDestinatario() {
    return destinatario;
  }

  /**
   * Verifica se a mensagem foi marcada para visualizacao unica.
   * @return true se visualizacao unica (SENDVU), false caso contrario.
   */
  public boolean isVisualizacaoUnica() {
    return isVisualizacaoUnica;
  }

  /**
   * Define o status de visualizacao unica da mensagem.
   * @param isVisualizacaoUnica true para ativar modo de visualizacao unica.
   */
  public void setVisualizacaoUnica(boolean isVisualizacaoUnica) {
    this.isVisualizacaoUnica = isVisualizacaoUnica;
  }

  /**
   * Gera uma representacao textual formatada da APDU para facilitacao de depuracao e logs.
   * @return String formatada com os principais campos da APDU.
   */
  @Override
  public String toString() {
    return String.format("APDU[%s | Grupo/Destino: %s | Usuario: %s | Destinatario: %s | MensagemID: %s | VU: %s]",
      operacao,
      (nomeGrupo != null ? nomeGrupo : "N/A"),
      nomeUsuario,
      (destinatario != null ? destinatario : "N/A"),
      (idMensagem != null ? idMensagem : "N/A"),
      isVisualizacaoUnica
    );
  }
}
