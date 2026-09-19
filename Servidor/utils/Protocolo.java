/*****************************************************************
* Autor..............: Iury Ramos Sodre
* Matricula..........: 202310440
* Inicio.............: 15/06/2026
* Ultima alteracao...: 18/09/2026
* Nome...............: Protocolo
* Funcao.............: Centraliza as constantes de operacoes, codigos de resposta e configuracoes de rede.
*************************************************************** */

package utils;

/**
 * Constantes de controle e operacoes do protocolo de aplicacao do sistema E.D.E.N.
 * Define os comandos padronizados para comunicacao via TCP e UDP.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class Protocolo {

  /** Comando JOIN: entrar ou criar um grupo de bate-papo */
  public static final String JOIN = "JOIN";

  /** Comando LEAVE: sair de um grupo */
  public static final String LEAVE = "LEAVE";

  /** Comando SEND: enviar mensagem a um grupo */
  public static final String SEND = "SEND";

  /** Comando LIST: solicitar listagem de todos os grupos ativos */
  public static final String LIST = "LIST";

  /** Comando REGISTER: registrar cliente e validar nome no servidor */
  public static final String REGISTER = "REGISTER";

  /** Comando SHUTDOWN: notificacao broadcast de encerramento do servidor */
  public static final String SHUTDOWN = "SHUTDOWN";

  /** Comando LOGOUT: desconexao graciosa solicitada pelo cliente */
  public static final String LOGOUT = "LOGOUT";

  /** Comando SENDPVT: envio de mensagem privada direta */
  public static final String SENDPVT = "SENDPVT";

  /** Comando SENDVU: mensagem de visualizacao unica */
  public static final String SENDVU = "SENDVU";

  /** Comando CONFIRM: confirmacao de recebimento ou leitura de mensagem */
  public static final String CONFIRM = "CONFIRM";

  /** Comando BLOCK: bloquear comunicacao com determinado usuario */
  public static final String BLOCK = "BLOCK";

  /** Comando UNBLOCK: desbloquear comunicacao com determinado usuario */
  public static final String UNBLOCK = "UNBLOCK";

  /** Comando MEMBERS: solicitar membros de um grupo especifico */
  public static final String MEMBERS = "MEMBERS";

  /** Comando USERS: solicitar listagem de todos os usuarios online */
  public static final String USERS = "USERS";

  /** Comando UPDATE_USERS: notificacao UDP broadcast de alteracao nos usuarios ativos */
  public static final String UPDATE_USERS = "UPDATE_USERS";

  /** Codigo de operacao para respostas de sucesso do servidor */
  public static final String OK = "OK";

  /** Codigo de operacao para respostas de erro do servidor */
  public static final String ERRO = "ERRO";

  /** Porta padrao TCP do grupo */
  public static final int PORTA_SERVIDOR_TCP = 6789;

  /** Porta padrao UDP do grupo */
  public static final int PORTA_SERVIDOR_UDP = 7777;

  /** Porta padrao UDP de descoberta (broadcast do grupo) */
  public static final int PORTA_DISCOVERY = 8888;

  /** Porta legada TCP/UDP para compatibilidade */
  public static final int PORTA_SERVIDOR_LEGACY = 5000;

  /** Porta legada de descoberta EDEN */
  public static final int PORTA_DISCOVERY_EDEN = 5001;

  /** Porta TCP padrao de escuta do servidor */
  public static final int PORTA_SERVIDOR = PORTA_SERVIDOR_TCP;

} // fim Protocolo
