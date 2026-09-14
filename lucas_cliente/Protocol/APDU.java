/*****************************************************************
* Autor..............: Lucas de Menezes Chaves
* Matricula........: 202310282
* Inicio...........: 20/06/2026
* Ultima alteracao.: 09/09/2026
* Nome.............: APDU
* Funcao...........: Maneja as APDUs que vao ser utilizadas no projeto
*
* OPERACOES TCP (controle/estado):
*   JOIN    - Entrar num grupo
*   LEAVE   - Sair de um grupo
*   MEMBERS - Listar membros de um grupo
*   LIST    - Listar todos os grupos
*   BLOCK   - Bloquear um usuario
*   UNBLOCK - Desbloquear um usuario
*   USERS   - Listar todos os usuarios online (para chat privado)
*
* OPERACOES UDP (tempo real):
*   SEND    - Mensagem de grupo
*   SENDVU  - Mensagem de visualizacao unica (desaparece apos lida)
*   SENDPVT - Mensagem privada entre dois usuarios
*   CONFIRM - Confirmacao de recebimento/leitura (ticks do zap)
*************************************************************** */

package Protocol;

import java.io.Serializable;
import java.util.UUID;

public class APDU implements Serializable{
  //Identificador da versao da APDU - incrementado a cada mudanca estrutural
  private static final long serialVersionUID = 2L;

  // -------------------------------------------------------
  // Campos principais da APDU
  // -------------------------------------------------------
  private String operacao;       // SEND, JOIN, LEAVE, CONFIRM, SENDPVT, SENDVU, BLOCK, UNBLOCK, USERS
  private String nomeGrupo;      // nome do grupo; em SENDPVT contem "@" + nomeDestinatario
  private String nomeUsuario;    // quem originou a APDU
  private String textoMensagem;  // conteudo da mensagem (null para JOIN/LEAVE/BLOCK etc)
  private String donoDaMensagem; // quem enviou a mensagem original (usado no CONFIRM)
  private String destinatario;   // usuario de destino (SENDPVT, BLOCK, UNBLOCK)

  //PortaUDP para o SEND
  private int portaClienteUDP;

  //Campos Extras para a APDU "CONFIRM" (Os ticks do zap)
  private String idMensagem;    // ID unico gerado para cada mensagem enviada
  private int statusRecebido;   // 0=Criada | 1=Enviada(servidor recebeu) | 2=Entregue | 3=Lida

  //Flag para mensagem de visualizacao unica
  private boolean isVisualizacaoUnica; // true = SENDVU, some apos ser lida

  /*********************************************************************
  * Metodo: APDU (construtor geral - SEND de grupo)
  * Funcao: Construtor para JOIN, LEAVE, MEMBERS, LIST, SEND de grupo
  * @param operacao O tipo de operacao ("JOIN", "LEAVE", "SEND" etc).
  * @param nomeGrupo O grupo alvo.
  * @param nomeUsuario O usuario que esta enviando a requisicao.
  * @param textoMensagem O conteudo da mensagem (pode ser null para JOIN/LEAVE).
  * @param portaClienteUDP A porta UDP aberta no cliente para receber broadcasts.
  * @return void
  * ****************************************************************** */
  public APDU(String operacao, String nomeGrupo, String nomeUsuario, String textoMensagem, int portaClienteUDP) {
    this.operacao = operacao;
    this.nomeGrupo = nomeGrupo;
    this.nomeUsuario = nomeUsuario;
    this.textoMensagem = textoMensagem;
    this.portaClienteUDP = portaClienteUDP;
    this.isVisualizacaoUnica = false;

    //Se for SEND ou SENDVU, gera um ID unico para rastrear os ticks do zap
    if(this.operacao.equals("SEND") || this.operacao.equals("SENDVU")) {
      this.idMensagem = UUID.randomUUID().toString();
      this.statusRecebido = 0;
    }//fim do if
  }//fim do metodo

  /*********************************************************************
  * Metodo: APDU (construtor SENDVU - visualizacao unica de grupo)
  * Funcao: Construtor para mensagem de visualizacao unica num grupo
  * @param operacao Deve ser "SENDVU".
  * @param nomeGrupo O grupo alvo.
  * @param nomeUsuario O usuario que esta enviando.
  * @param textoMensagem O conteudo da mensagem.
  * @param portaClienteUDP A porta UDP do cliente.
  * @param isVisualizacaoUnica flag true para VU.
  * @return void
  * ****************************************************************** */
  public APDU(String operacao, String nomeGrupo, String nomeUsuario, String textoMensagem, int portaClienteUDP, boolean isVisualizacaoUnica) {
    this(operacao, nomeGrupo, nomeUsuario, textoMensagem, portaClienteUDP);
    this.isVisualizacaoUnica = isVisualizacaoUnica;
  }//fim do metodo

  /*********************************************************************
  * Metodo: APDU (construtor SENDPVT / BLOCK / UNBLOCK)
  * Funcao: Construtor para mensagem privada e operacoes de bloqueio
  * @param operacao "SENDPVT", "BLOCK" ou "UNBLOCK".
  * @param nomeGrupo null para BLOCK/UNBLOCK; "@" + destinatario para SENDPVT.
  * @param nomeUsuario O usuario remetente.
  * @param textoMensagem Conteudo da mensagem (null para BLOCK/UNBLOCK).
  * @param portaClienteUDP Porta UDP do cliente.
  * @param destinatario Nome do usuario de destino.
  * @return void
  * ****************************************************************** */
  public APDU(String operacao, String nomeGrupo, String nomeUsuario, String textoMensagem, int portaClienteUDP, String destinatario) {
    this.operacao = operacao;
    this.nomeGrupo = nomeGrupo;
    this.nomeUsuario = nomeUsuario;
    this.textoMensagem = textoMensagem;
    this.portaClienteUDP = portaClienteUDP;
    this.destinatario = destinatario;
    this.isVisualizacaoUnica = false;

    //SENDPVT gera ID para rastrear os ticks
    if(this.operacao.equals("SENDPVT")) {
      this.idMensagem = UUID.randomUUID().toString();
      this.statusRecebido = 0;
    }//fim do if
  }//fim do metodo

  /*********************************************************************
  * Metodo: APDU (construtor CONFIRM simples)
  * Funcao: Construtor do objeto APDU para CONFIRM
  * @param operacao "CONFIRM".
  * @param idMensagem Id da mensagem enviada.
  * @param statusRecebido status atual de recebimento.
  * @param nomeUsuario usuario que esta confirmando o recebimento/leitura
  * @return void
  * ****************************************************************** */
  public APDU(String operacao, String idMensagem, int statusRecebido, String nomeUsuario) {
    this.operacao = operacao.toUpperCase();
    this.idMensagem = idMensagem;
    this.statusRecebido = statusRecebido;
    this.nomeUsuario = nomeUsuario;
  }//fim do metodo

  /*********************************************************************
  * Metodo: APDU (construtor CONFIRM com grupo e dono)
  * Funcao: Construtor completo do objeto APDU para CONFIRM
  * @param operacao "CONFIRM".
  * @param idMensagem Id da mensagem enviada.
  * @param statusRecebido status atual de recebimento.
  * @param nomeUsuario usuario que esta confirmando o recebimento/leitura
  * @param nomeGrupo grupo em que foi mandada a mensagem (ou "@remetente" para privado)
  * @param donoDaMensagem usuario que mandou a mensagem
  * @return void
  * ****************************************************************** */
  public APDU(String operacao, String idMensagem, int statusRecebido, String nomeUsuario, String nomeGrupo, String donoDaMensagem) {
    this.operacao = operacao.toUpperCase();
    this.idMensagem = idMensagem;
    this.statusRecebido = statusRecebido;
    this.nomeUsuario = nomeUsuario;
    this.nomeGrupo = nomeGrupo;
    this.donoDaMensagem = donoDaMensagem;
  }//fim do metodo

  // -------------------------------------------------------
  // Getters
  // -------------------------------------------------------

  /*********************************************************************
  * Metodo: getOperacao
  * Funcao: Retornar a operacao
  * @return operacao
  * ****************************************************************** */
  public String getOperacao() {
    return operacao;
  }//fim do metodo

  /*********************************************************************
  * Metodo: getNomeGrupo
  * Funcao: Retornar o nome do grupo (ou "@destinatario" para privado)
  * @return nome do grupo
  * ****************************************************************** */
  public String getNomeGrupo() {
    return nomeGrupo;
  }//fim do metodo

  /*********************************************************************
  * Metodo: getNomeUsuario
  * Funcao: Retornar o nome do usuario
  * @return nome do usuario
  * ****************************************************************** */
  public String getNomeUsuario() {
    return nomeUsuario;
  }//fim do metodo

  /*********************************************************************
  * Metodo: getTextoMensagem
  * Funcao: Retornar o texto da mensagem
  * @return texto da mensagem
  * ****************************************************************** */
  public String getTextoMensagem() {
    return textoMensagem;
  }//fim do metodo

  /*********************************************************************
  * Metodo: getPortaClienteUDP
  * Funcao: Retornar a portaClienteUDP
  * @return portaClienteUDP
  * ****************************************************************** */
  public int getPortaClienteUDP(){
    return portaClienteUDP;
  }//fim do metodo

  /*********************************************************************
  * Metodo: getIdMensagem
  * Funcao: Retornar o id da mensagem
  * @return id da mensagem
  * ****************************************************************** */
  public String getIdMensagem() {
    return idMensagem;
  }//fim do metodo

  /*********************************************************************
  * Metodo: getStatusRecebido
  * Funcao: Retornar o status de recebido
  * @return status de recebido
  * ****************************************************************** */
  public int getStatusRecebido() {
    return statusRecebido;
  }//fim do metodo

  /*********************************************************************
  * Metodo: getDonoDaMensagem
  * Funcao: Retornar o dono da mensagem
  * @return dono da mensagem
  * ****************************************************************** */
  public String getDonoDaMensagem() {
    return donoDaMensagem;
  }//fim do metodo

  /*********************************************************************
  * Metodo: getDestinatario
  * Funcao: Retornar o usuario destinatario (SENDPVT, BLOCK, UNBLOCK)
  * @return nome do destinatario
  * ****************************************************************** */
  public String getDestinatario() {
    return destinatario;
  }//fim do metodo

  /*********************************************************************
  * Metodo: isVisualizacaoUnica
  * Funcao: Retornar se a mensagem e de visualizacao unica
  * @return true se SENDVU, false caso contrario
  * ****************************************************************** */
  public boolean isVisualizacaoUnica() {
    return isVisualizacaoUnica;
  }//fim do metodo

  /*********************************************************************
  * Metodo: setVisualizacaoUnica
  * Funcao: Definir se a mensagem e de visualizacao unica
  * @param isVisualizacaoUnica boolean
  * ****************************************************************** */
  public void setVisualizacaoUnica(boolean isVisualizacaoUnica) {
    this.isVisualizacaoUnica = isVisualizacaoUnica;
  }//fim do metodo

  /*********************************************************************
  * Metodo: toString
  * Funcao: Facilitar o debug alterando o log
  * @return String formatada
  * ****************************************************************** */
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
  }//fim do metodo
}//fim da classe
