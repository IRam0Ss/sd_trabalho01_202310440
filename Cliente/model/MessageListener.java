/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: MessageListener
 * Funcao: Interface de callback Observer para notificacoes assincronas da camada de rede para a GUI.
 */

package model;

import utils.InfoUser;

/**
 * Interface observer para despacho e tratamento de eventos de mensageria e ciclo de vida da rede.
 * Implementada pela camada de visao (GUI) para atualizacao reativa dos componentes graficos.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public interface MessageListener {

  /**
   * Evento acionado na recepcao de uma mensagem de texto (grupo ou privada).
   * 
   * @param idMensagem ID unico da mensagem recebida.
   * @param destino    Nome do grupo alvo ou usuario de destino.
   * @param remetente  Informacoes do usuario remetente da mensagem.
   * @param mensagem   Conteudo textual recebido.
   * @param isPrivate  Flag indicando se a mensagem e direta/privada (true) ou de grupo (false).
   */
  void onMessageReceived(String idMensagem, String destino, InfoUser remetente, String mensagem, boolean isPrivate);

  /**
   * Evento acionado quando o servidor envia sinalizacao de encerramento ou a conexao e perdida.
   */
  void onShutdown();

  /**
   * Evento acionado quando a lista de usuarios online cadastrados no servidor e modificada.
   */
  void onUpdateUsers();

  /**
   * Evento acionado na recepcao de confirmacao de entrega ou leitura de mensagem (tick).
   * 
   * @param idMensagem    Identificador unico da mensagem.
   * @param status        Status da confirmacao (1=Enviada, 2=Entregue, 3=Lida, -1=Erro).
   * @param nomeConfirmou Nome do usuario que confirmou a recepcao ou leitura.
   */
  void onTickReceived(String idMensagem, int status, String nomeConfirmou);
}
